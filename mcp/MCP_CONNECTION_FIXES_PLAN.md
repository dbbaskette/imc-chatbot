# MCP Connection Loss Issue - Implementation Plan

This document outlines the specific code changes needed to fix the issue where the imc-chatbot loses its ability to connect to the policy-mcp server after running for a while.

## Problem Analysis
The chatbot appears to lose MCP connection over time due to:
1. Inadequate heartbeat retry logic
2. Insufficient connection recovery mechanisms 
3. Missing proper resource cleanup for stale connections
4. Suboptimal circuit breaker configuration

## Solution Implementation

### 1. Enhanced Heartbeat Service (`McpConnectionHeartbeatService.java`)

**Changes to make:**
- Add exponential backoff with maximum retry attempts
- Implement connection reset capability
- Improve health status detection

**Modified Code:**
```java
// Add to McpConnectionHeartbeatService.java
private static final int MAX_HEARTBEAT_RETRIES = 5;
private static final long INITIAL_BACKOFF_MS = 1000;
private static final long MAX_BACKOFF_MS = 30000;
private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
private final AtomicLong currentBackoff = new AtomicLong(INITIAL_BACKOFF_MS);

// Enhanced sendHeartbeat method with improved error handling
@Scheduled(fixedDelay = 30000)
public void sendHeartbeat() {
    if (toolCallbackProvider == null) {
        log.debug("MCP tools not available - skipping heartbeat");
        return;
    }

    // Skip if backoff period is still active
    long timeSinceLastHeartbeat = System.currentTimeMillis() - lastSuccessfulHeartbeat.get().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    if (timeSinceLastHeartbeat < currentHeartbeatInterval.get()) {
        log.debug("Skipping heartbeat - adaptive interval not reached yet ({}ms remaining)", 
            currentHeartbeatInterval.get() - timeSinceLastHeartbeat);
        return;
    }

    try {
        // Simple tool count check acts as a heartbeat
        var toolCallbacks = toolCallbackProvider.getToolCallbacks();
        int toolCount = toolCallbacks != null ? toolCallbacks.length : 0;
        
        // Update statistics
        heartbeatCount.incrementAndGet();
        lastSuccessfulHeartbeat.set(LocalDateTime.now());
        consecutiveFailures.set(0);
        
        // Reset to minimum interval on success
        currentHeartbeatInterval.set(MIN_HEARTBEAT_INTERVAL_MS);
        
        // Log heartbeat success (debug level to avoid spam)
        log.debug("💓 MCP heartbeat successful - {} tools available (total: {}, failed: {}, interval: {}ms)", 
            toolCount, heartbeatCount.get(), failedHeartbeats.get(), currentHeartbeatInterval.get());
        
        // If we have a connection health service, update it
        if (connectionHealthService != null) {
            connectionHealthService.markHeartbeatReceived();
        }
        
    } catch (Exception e) {
        handleHeartbeatFailure("Heartbeat failed: " + e.getMessage(), e);
    }
}

private void handleHeartbeatFailure(String errorMessage, Exception e) {
    failedHeartbeats.incrementAndGet();
    int failures = consecutiveFailures.incrementAndGet();
    
    lastFailedHeartbeat.set(LocalDateTime.now());
    lastHeartbeatError.set(errorMessage);
    
    // Implement exponential backoff with max limit
    long newInterval = Math.min(
        currentHeartbeatInterval.get() * BACKOFF_MULTIPLIER, 
        MAX_HEARTBEAT_INTERVAL_MS
    );
    currentHeartbeatInterval.set(newInterval);
    
    // Update backoff for next retry attempt
    long newBackoff = Math.min(
        currentBackoff.get() * 2, 
        MAX_BACKOFF_MS
    );
    currentBackoff.set(newBackoff);
    
    log.warn("⚠️  MCP heartbeat failed (consecutive: {}): {} - backing off to {}ms", 
        failures, errorMessage, newInterval);
    
    // If we have a connection health service, notify it of the failure
    if (connectionHealthService != null) {
        connectionHealthService.markHeartbeatFailed(errorMessage);
    }
    
    // If we've had too many consecutive failures, trigger reconnection
    if (failures >= MAX_HEARTBEAT_RETRIES) {
        log.warn("🚨 Max heartbeat retries exceeded - attempting connection reset");
        if (connectionHealthService != null) {
            connectionHealthService.triggerReconnectionAttempt();
        }
    }
    
    // Log the full exception at debug level for troubleshooting
    log.debug("Heartbeat failure details", e);
}
```

### 2. Enhanced Connection Health Service (`McpConnectionHealthService.java`)

**Changes to make:**
- Improve circuit breaker logic
- Add connection reset capability
- Implement better error categorization

**Modified Code:**
```java
// Add to McpConnectionHealthService.java
private static final int MAX_CONNECTION_ATTEMPTS = 3;
private static final long CONNECTION_TIMEOUT_MS = 60000;

// Add connection reset capability
public void resetConnection() {
    log.info("🔄 Resetting MCP connection state");
    
    // Force reconnection by marking as unhealthy
    isHealthy.set(false);
    lastError.set("Connection reset requested");
    
    // Reset circuit breaker
    circuitOpen.set(false);
    failureCount.set(0);
    lastFailureTimeForCircuit.set(null);
    
    // Clear any existing tool callback references
    if (toolCallbackProvider != null) {
        try {
            // Force a fresh connection attempt
            var toolCallbacks = toolCallbackProvider.getToolCallbacks();
            log.debug("Connection reset complete - {} tools available", 
                toolCallbacks != null ? toolCallbacks.length : 0);
        } catch (Exception e) {
            log.debug("Connection reset failed: {}", e.getMessage());
        }
    }
}

// Enhanced health check with better timeout handling
@Scheduled(fixedRate = 60000, initialDelay = 30000)
public void performHealthCheck() {
    if (toolCallbackProvider == null) {
        log.debug("MCP tools not available - skipping health check");
        return;
    }

    // Check circuit breaker first
    if (isCircuitOpen()) {
        log.debug("🚨 Circuit breaker open - skipping health check");
        return;
    }

    try {
        // Use a shorter timeout for health checks to prevent hanging
        long startTime = System.currentTimeMillis();
        
        // Test MCP connection by attempting to get tool callbacks with timeout
        var toolCallbacks = toolCallbackProvider.getToolCallbacks();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        if (duration > CONNECTION_TIMEOUT_MS) {
            log.warn("⚠️  Health check took too long ({}ms) - possible connection issue", duration);
        }
        
        if (toolCallbacks != null && toolCallbacks.length > 0) {
            // Connection is healthy - tools are available
            if (!isHealthy.get()) {
                log.info("✅ MCP connection recovered - {} tool(s) available", toolCallbacks.length);
            }
            onHealthCheckSuccess();
        } else {
            // No tools available - potential connection issue
            onHealthCheckFailure("No MCP tools available - connection may be lost");
        }
        
    } catch (Exception e) {
        onHealthCheckFailure("MCP health check failed: " + e.getMessage());
    }
}

// Enhanced circuit breaker logic
private boolean isCircuitOpen() {
    if (!circuitOpen.get()) {
        return false;
    }
    
    // Check if timeout has passed - use a more aggressive timeout
    LocalDateTime lastFailure = lastFailureTimeForCircuit.get();
    if (lastFailure != null && 
        Duration.between(lastFailure, LocalDateTime.now()).compareTo(Duration.ofMinutes(1)) > 0) {
        log.info("⏰ Circuit breaker timeout expired (1 min) - attempting recovery");
        circuitOpen.set(false);
        failureCount.set(0);
        return false;
    }
    
    return true;
}
```

### 3. Enhanced Connection State Manager (`McpConnectionStateManager.java`)

**Changes to make:**
- Add automatic reconnection logic
- Implement proper connection timeout detection
- Improve state transition handling

**Modified Code:**
```java
// Add to McpConnectionStateManager.java
private static final long CONNECTION_TIMEOUT_THRESHOLD_MS = 300000; // 5 minutes
private final AtomicReference<LocalDateTime> lastHeartbeatReceived = new AtomicReference<>();

// Add connection timeout monitoring
public boolean isConnectionStale() {
    LocalDateTime lastHeartbeat = lastHeartbeatReceived.get();
    if (lastHeartbeat == null) {
        return true;
    }
    
    long timeSinceLastHeartbeat = ChronoUnit.MILLIS.between(lastHeartbeat, LocalDateTime.now());
    return timeSinceLastHeartbeat > CONNECTION_TIMEOUT_THRESHOLD_MS;
}

// Enhanced state transition with timeout detection
public void onConnectionSuccess() {
    successfulConnections.incrementAndGet();
    connectionAttempts.set(0);
    lastConnectionTime.set(LocalDateTime.now());
    
    // Reset heartbeat tracking
    lastHeartbeatReceived.set(LocalDateTime.now());
    
    // Calculate connection duration if we were connecting
    if (currentState.get() == ConnectionState.CONNECTING) {
        LocalDateTime lastChange = lastStateChangeTime.get();
        if (lastChange != null) {
            Duration connectionDuration = Duration.between(lastChange, LocalDateTime.now());
            lastConnectionDuration.set(connectionDuration);
            updateAverageConnectionDuration(connectionDuration);
            log.debug("⏱️ Connection established in {}ms", connectionDuration.toMillis());
        }
    }
    
    transitionTo(ConnectionState.CONNECTED);
}

// Enhanced onConnectionFailure to handle stale connections
public void onConnectionFailure(String reason) {
    failedConnections.incrementAndGet();
    log.warn("❌ MCP connection failed: {}", reason);
    
    // Check if the connection might be stale
    if (isConnectionStale()) {
        log.warn("⚠️ Connection appears stale - triggering reset");
        // Force reconnection attempt
        initiateReconnection();
        return;
    }
    
    if (currentState.get() == ConnectionState.CONNECTING) {
        // If we were trying to connect, go back to disconnected
        transitionTo(ConnectionState.DISCONNECTED);
    } else if (currentState.get() == ConnectionState.CONNECTED) {
        // If we were connected, mark as degraded
        transitionTo(ConnectionState.DEGRADED);
    }
}

// Add heartbeat tracking method
public void onHeartbeatReceived() {
    lastHeartbeatReceived.set(LocalDateTime.now());
    
    // If we were in degraded state and now have heartbeat, mark as healthy
    if (currentState.get() == ConnectionState.DEGRADED) {
        transitionTo(ConnectionState.CONNECTED);
    }
}
```

### 4. Configuration Updates

**Add to `application-mcp.properties`:**
```properties
# Enhanced MCP Connection Settings for Resilience
mcp.connection.reset.timeout=60000
mcp.heartbeat.max.retries=5
mcp.connection.stale.threshold=300000

# Shorter circuit breaker timeout for faster recovery
mcp.resilience.circuit-breaker.timeout=60s

# Enhanced connection timeout settings
spring.ai.mcp.client.sse.connections.imc-policy.connection-timeout=60s
spring.ai.mcp.client.sse.connections.imc-policy.read-timeout=60s
spring.ai.mcp.client.sse.connections.imc-policy.keep-alive-interval=30s
```

### 5. Integration Updates

**Update `McpConnectionHealthService.java` to handle connection resets:**
```java
// Add this method to McpConnectionHealthService.java
public void triggerReconnectionAttempt() {
    log.info("🔄 Manually triggering MCP reconnection attempt...");
    
    // Reset the circuit breaker
    circuitOpen.set(false);
    failureCount.set(0);
    
    // Force health check to re-establish connection
    performHealthCheck();
}
```

### Implementation Steps

1. Create a new branch in your git repository for these changes:
   ```bash
   git checkout -b feature/mcp-connection-resilience
   ```

2. Apply the code changes to the respective files:
   - `McpConnectionHeartbeatService.java`
   - `McpConnectionHealthService.java` 
   - `McpConnectionStateManager.java`

3. Update configuration properties in `application-mcp.properties`

4. Run tests to ensure the changes don't break existing functionality

5. Merge the branch after successful testing

### Expected Benefits

These changes should:
- Reduce connection loss due to idle timeouts
- Implement more aggressive recovery from failed connections  
- Provide better detection of stale connections
- Improve overall system resilience to network interruptions
- Enable faster reconnection when policy-mcp server becomes available again
