# MCP Connection Loss Issue - Comprehensive Analysis & Fixes Plan

This document combines the analysis from both Cursor (AI assistant) and the existing fixes plan, with clear attribution of ideas and deduplication of overlapping concepts.

## Problem Summary

The imc-chatbot loses its ability to connect to the policy-mcp-server after running for a while. This affects the web interface (imc-web) that users interact with, as it depends on the chatbot's MCP tool access for policy information.

## Root Cause Analysis

### **Primary Issue: SSE Connection Timeout (Cursor + Qwen Consensus)**
Both analyses identified that Server-Sent Events (SSE) connections are inherently long-lived and can be terminated by:
- Network infrastructure (load balancers, proxies)
- Cloud platform timeouts (Cloud Foundry default: 60-300 seconds)
- Browser/HTTP client connection limits

**Evidence:**
- SSE endpoint configuration in `application-mcp.properties`
- Connection to `imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com`
- No explicit connection keep-alive mechanisms

### **Secondary Issue: Insufficient Connection Monitoring (Cursor + Qwen Consensus)**
Both analyses found the current monitoring intervals are too conservative:
- Health checks: 60 seconds (too long for production)
- Heartbeats: 30 seconds (may not prevent timeouts)
- Circuit breaker: 2 minutes (allows too much degradation)

### **Tertiary Issue: Resource Management (Cursor Analysis)**
- No explicit connection pool management
- Spring AI MCP client may hold stale connections
- No forced reconnection for idle connections

### **Cloud Platform Issues (Cursor Analysis)**
- Cloud Foundry specific connection timeouts
- Load balancer connection limits
- Container lifecycle management affecting long-running connections

## Solution Implementation Plan

### 1. Enhanced Heartbeat Service (Qwen Implementation + Cursor Enhancement)

**Qwen's Implementation:**
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
    // ... existing implementation with enhanced error handling
}

private void handleHeartbeatFailure(String errorMessage, Exception e) {
    // ... enhanced failure handling with exponential backoff
}
```

**Cursor's Enhancement:**
- Reduce heartbeat interval to 15-20 seconds (more aggressive)
- Add connection quality metrics (latency, response time)
- Implement connection pooling with rotation

### 2. Enhanced Connection Health Service (Qwen Implementation + Cursor Enhancement)

**Qwen's Implementation:**
```java
// Add to McpConnectionHealthService.java
private static final int MAX_CONNECTION_ATTEMPTS = 3;
private static final long CONNECTION_TIMEOUT_MS = 60000;

public void resetConnection() {
    // ... connection reset implementation
}

// Enhanced health check with better timeout handling
@Scheduled(fixedRate = 60000, initialDelay = 30000)
public void performHealthCheck() {
    // ... enhanced health check with timeout detection
}
```

**Cursor's Enhancement:**
- Reduce health check interval to 30 seconds (more aggressive)
- Add proactive connection testing before tool usage
- Implement connection quality scoring

### 3. Enhanced Connection State Manager (Qwen Implementation + Cursor Enhancement)

**Qwen's Implementation:**
```java
// Add to McpConnectionStateManager.java
private static final long CONNECTION_TIMEOUT_THRESHOLD_MS = 300000; // 5 minutes
private final AtomicReference<LocalDateTime> lastHeartbeatReceived = new AtomicReference<>();

public boolean isConnectionStale() {
    // ... stale connection detection
}

public void onHeartbeatReceived() {
    // ... heartbeat tracking
}
```

**Cursor's Enhancement:**
- Implement connection rotation to prevent staleness
- Add forced reconnection after idle periods
- Implement connection lifecycle monitoring

### 4. Configuration Updates (Qwen Implementation + Cursor Enhancement)

**Qwen's Configuration:**
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

**Cursor's Enhancement:**
- Optimize timeouts for Cloud Foundry deployment
- Add connection quality monitoring configuration
- Implement graceful connection shutdown settings

### 5. ChatService Integration (Cursor Analysis)

**Add connection validation before tool usage:**
```java
// In ChatService.chat() method
if (toolCallbackProvider != null) {
    // Check connection health before attempting to use MCP tools
    boolean connectionHealthy = connectionHealthService == null || 
                               connectionHealthService.isConnectionHealthy();
    
    if (!connectionHealthy && connectionHealthService != null) {
        log.warn("⚠️  MCP connection unhealthy, triggering reconnection attempt");
        connectionHealthService.triggerReconnectionAttempt();
        connectionHealthy = connectionHealthService.isConnectionHealthy();
    }
    
    // ... rest of tool usage logic
}
```

## Implementation Priority

### **Phase 1: Immediate Fixes (Week 1)**
1. Implement enhanced heartbeat service with exponential backoff
2. Reduce health check intervals to 30 seconds
3. Add connection reset capability

### **Phase 2: Enhanced Monitoring (Week 2)**
1. Implement connection quality metrics
2. Add proactive connection testing
3. Enhance connection state management

### **Phase 3: Cloud Optimization (Week 3)**
1. Optimize timeouts for Cloud Foundry
2. Implement connection pooling
3. Add connection lifecycle monitoring

## Expected Benefits

These changes should:
- **Reduce connection loss** due to idle timeouts
- **Implement faster recovery** from failed connections
- **Provide better detection** of stale connections
- **Improve overall system resilience** to network interruptions
- **Enable faster reconnection** when policy-mcp server becomes available

## Monitoring and Validation

### **Metrics to Track:**
1. Connection success/failure rates
2. Heartbeat success rates
3. Connection recovery times
4. Tool availability percentages
5. Connection quality scores

### **Testing Scenarios:**
1. Long-running connections (24+ hours)
2. Network interruption simulation
3. Cloud platform restart scenarios
4. High-load connection testing

## Risk Mitigation

### **Potential Risks:**
1. Increased resource usage from more frequent health checks
2. Potential performance impact from connection validation
3. Configuration complexity increase

### **Mitigation Strategies:**
1. Implement adaptive intervals based on connection health
2. Use async health checks to minimize blocking
3. Provide configuration profiles for different environments

## Conclusion

The MCP connection loss issue stems from a combination of SSE connection timeouts, insufficient monitoring intervals, and lack of proactive connection management. The combined solution addresses these issues through enhanced heartbeat services, more aggressive health monitoring, and improved connection state management.

Both analyses identified similar root causes, with Cursor providing additional insights into cloud platform specifics and resource management, while Qwen provided detailed implementation code. The combined approach creates a comprehensive solution that should significantly improve connection reliability.
