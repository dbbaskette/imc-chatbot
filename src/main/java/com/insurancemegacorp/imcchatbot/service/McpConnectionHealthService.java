package com.insurancemegacorp.imcchatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Profile("mcp")
public class McpConnectionHealthService {

    private static final Logger log = LoggerFactory.getLogger(McpConnectionHealthService.class);
    
    // Circuit breaker configuration
    @Value("${mcp.resilience.circuit-breaker.threshold:5}")
    private int circuitBreakerThreshold;
    
    @Value("${mcp.resilience.circuit-breaker.timeout:2m}")
    private Duration circuitBreakerTimeout;
    
    // Health tracking
    private final AtomicBoolean isHealthy = new AtomicBoolean(true);
    private final AtomicReference<LocalDateTime> lastSuccessfulCheck = new AtomicReference<>(LocalDateTime.now());
    private final AtomicReference<LocalDateTime> lastFailureTime = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    
    // Circuit breaker state
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicReference<LocalDateTime> lastFailureTimeForCircuit = new AtomicReference<>();
    
    @Autowired(required = false)
    private SyncMcpToolCallbackProvider toolCallbackProvider;
    
    @Autowired(required = false)
    private McpConnectionStateManager connectionStateManager;
    
    @Autowired(required = false)
    private McpConnectionRefreshService connectionRefreshService;

    /**
     * Performs a health check every 60 seconds to ensure MCP connections are active
     */
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
            // Test MCP connection by attempting to get tool callbacks
            var toolCallbacks = toolCallbackProvider.getToolCallbacks();
            
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

    /**
     * Performs an immediate health check and returns the current status
     */
    public boolean isConnectionHealthy() {
        // Perform immediate check if it's been more than 2 minutes since last check
        LocalDateTime lastCheck = lastSuccessfulCheck.get();
        if (lastCheck != null && ChronoUnit.MINUTES.between(lastCheck, LocalDateTime.now()) > 2) {
            performHealthCheck();
        }
        return isHealthy.get();
    }

    /**
     * Gets the number of available MCP tools
     */
    public int getAvailableToolCount() {
        if (toolCallbackProvider == null) {
            return 0;
        }
        
        try {
            var toolCallbacks = toolCallbackProvider.getToolCallbacks();
            return toolCallbacks != null ? toolCallbacks.length : 0;
        } catch (Exception e) {
            log.debug("Failed to get tool count: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Gets the last known error message
     */
    public String getLastError() {
        return lastError.get();
    }

    /**
     * Gets the time of the last successful health check
     */
    public LocalDateTime getLastSuccessfulCheck() {
        return lastSuccessfulCheck.get();
    }

    /**
     * Gets the time of the last failure (if any)
     */
    public LocalDateTime getLastFailureTime() {
        return lastFailureTime.get();
    }

    private void markHealthy() {
        isHealthy.set(true);
        lastSuccessfulCheck.set(LocalDateTime.now());
        lastError.set(null);
    }

    private void handleConnectionIssue(String errorMessage) {
        boolean wasHealthy = isHealthy.getAndSet(false);
        lastError.set(errorMessage);
        lastFailureTime.set(LocalDateTime.now());
        
        if (wasHealthy) {
            log.warn("⚠️  MCP connection issue detected: {}", errorMessage);
            log.info("🔄 MCP connections are resilient - Spring AI will automatically attempt to reconnect");
        } else {
            log.debug("MCP connection still unhealthy: {}", errorMessage);
        }
    }

    /**
     * Manual method to trigger connection recovery attempts.
     * Now uses the enhanced connection refresh service for forced reconnection.
     */
    public void triggerReconnectionAttempt() {
        log.info("🔄 Manually triggering MCP reconnection attempt...");
        
        // Use the new connection refresh service for forced reconnection
        if (connectionRefreshService != null) {
            boolean refreshSuccess = connectionRefreshService.forceConnectionRefresh();
            if (refreshSuccess) {
                log.info("✅ Connection refresh successful via refresh service");
                return;
            } else {
                log.warn("⚠️ Connection refresh failed, falling back to health check");
            }
        }
        
        // Fallback to regular health check
        performHealthCheck();
    }
    
    /**
     * Marks that a heartbeat was received successfully.
     * This helps maintain connection health status between scheduled health checks.
     */
    public void markHeartbeatReceived() {
        // Update last successful check time to reflect recent activity
        lastSuccessfulCheck.set(LocalDateTime.now());
        
        // If we were unhealthy and now have heartbeat activity, mark as healthy
        if (!isHealthy.get()) {
            log.info("💓 Heartbeat received - marking MCP connection as healthy");
            markHealthy();
        }
    }
    
    /**
     * Marks that a heartbeat failed.
     * This provides early warning of connection issues between scheduled health checks.
     */
    public void markHeartbeatFailed(String errorMessage) {
        // Don't immediately mark as unhealthy, but update error information
        lastError.set("Heartbeat failed: " + errorMessage);
        lastFailureTime.set(LocalDateTime.now());
        
        log.debug("💓 Heartbeat failed: {}", errorMessage);
        
        // If we've had multiple recent heartbeat failures, consider connection unhealthy
        // This is a more conservative approach than immediate failure marking
    }
    
    /**
     * Circuit breaker implementation - prevents hammering a failing server
     */
    private boolean isCircuitOpen() {
        if (!circuitOpen.get()) {
            return false;
        }
        
        // Check if timeout has passed
        LocalDateTime lastFailure = lastFailureTimeForCircuit.get();
        if (lastFailure != null && 
            Duration.between(lastFailure, LocalDateTime.now()).compareTo(circuitBreakerTimeout) > 0) {
            log.info("⏰ Circuit breaker timeout expired - attempting recovery");
            circuitOpen.set(false);
            failureCount.set(0);
            return false;
        }
        
        return true;
    }
    
    /**
     * Called when a health check succeeds
     */
    private void onHealthCheckSuccess() {
        failureCount.set(0);
        circuitOpen.set(false);
        markHealthy();
        
        // Update connection state if available
        if (connectionStateManager != null) {
            connectionStateManager.onConnectionSuccess();
        }
    }
    
    /**
     * Called when a health check fails
     */
    private void onHealthCheckFailure(String reason) {
        int failures = failureCount.incrementAndGet();
        lastFailureTimeForCircuit.set(LocalDateTime.now());
        
        log.warn("⚠️ Health check failed (attempt {}): {}", failures, reason);
        
        if (failures >= circuitBreakerThreshold) {
            circuitOpen.set(true);
            log.error("🚨 Circuit breaker opened after {} failures: {}", failures, reason);
        }
        
        handleConnectionIssue(reason);
        
        // Update connection state if available
        if (connectionStateManager != null) {
            connectionStateManager.onConnectionFailure(reason);
        }
    }
    
    /**
     * Gets circuit breaker status
     */
    public boolean isCircuitBreakerOpen() {
        return circuitOpen.get();
    }
    
    /**
     * Gets failure count for circuit breaker
     */
    public int getFailureCount() {
        return failureCount.get();
    }
    
    /**
     * Gets circuit breaker timeout
     */
    public Duration getCircuitBreakerTimeout() {
        return circuitBreakerTimeout;
    }
}
