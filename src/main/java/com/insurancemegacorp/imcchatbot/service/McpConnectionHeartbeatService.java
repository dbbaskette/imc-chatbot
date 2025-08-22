package com.insurancemegacorp.imcchatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service to maintain MCP SSE connections by sending periodic heartbeats.
 * This prevents connection timeouts that commonly occur with long-lived SSE connections.
 */
@Service
@Profile("mcp")
public class McpConnectionHeartbeatService {
    
    private static final Logger log = LoggerFactory.getLogger(McpConnectionHeartbeatService.class);
    
    // Adaptive heartbeat intervals
    private static final long MIN_HEARTBEAT_INTERVAL_MS = 30000; // 30 seconds
    private static final long MAX_HEARTBEAT_INTERVAL_MS = 300000; // 5 minutes
    private static final long BACKOFF_MULTIPLIER = 2;
    
    // Current heartbeat interval (starts at minimum)
    private final AtomicLong currentHeartbeatInterval = new AtomicLong(MIN_HEARTBEAT_INTERVAL_MS);
    
    // Track heartbeat statistics
    private final AtomicInteger heartbeatCount = new AtomicInteger(0);
    private final AtomicInteger failedHeartbeats = new AtomicInteger(0);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<LocalDateTime> lastSuccessfulHeartbeat = new AtomicReference<>(LocalDateTime.now());
    private final AtomicReference<LocalDateTime> lastFailedHeartbeat = new AtomicReference<>();
    private final AtomicReference<String> lastHeartbeatError = new AtomicReference<>();
    
    @Autowired
    private SyncMcpToolCallbackProvider toolCallbackProvider;
    
    @Autowired(required = false)
    private McpConnectionHealthService connectionHealthService;
    
    /**
     * Sends periodic heartbeats to keep MCP connections alive.
     * Uses adaptive intervals based on connection health.
     */
    @Scheduled(fixedDelay = 30000) // 30 seconds base interval
    public void sendHeartbeat() {
        if (toolCallbackProvider == null) {
            log.debug("MCP tools not available - skipping heartbeat");
            return;
        }
        
        // Implement adaptive interval logic within the method
        long timeSinceLastHeartbeat = System.currentTimeMillis() - lastSuccessfulHeartbeat.get().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (timeSinceLastHeartbeat < currentHeartbeatInterval.get()) {
            log.debug("Skipping heartbeat - adaptive interval not reached yet ({}ms remaining)", 
                currentHeartbeatInterval.get() - timeSinceLastHeartbeat);
            return;
        }
        
        try {
            // Simple tool count check acts as a heartbeat
            // This keeps the SSE connection active without sending actual data
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
                // This will help the health service know the connection is active
                connectionHealthService.markHeartbeatReceived();
            }
            
        } catch (Exception e) {
            handleHeartbeatFailure("Heartbeat failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Sends an immediate heartbeat (useful for manual testing or recovery)
     */
    public boolean sendImmediateHeartbeat() {
        if (toolCallbackProvider == null) {
            log.warn("Cannot send immediate heartbeat - MCP tools not available");
            return false;
        }
        
        try {
            var toolCallbacks = toolCallbackProvider.getToolCallbacks();
            int toolCount = toolCallbacks != null ? toolCallbacks.length : 0;
            
            heartbeatCount.incrementAndGet();
            lastSuccessfulHeartbeat.set(LocalDateTime.now());
            
            log.info("💓 Immediate MCP heartbeat sent - {} tools available", toolCount);
            
            if (connectionHealthService != null) {
                connectionHealthService.markHeartbeatReceived();
            }
            
            return true;
            
        } catch (Exception e) {
            handleHeartbeatFailure("Immediate heartbeat failed: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Gets the current heartbeat interval in milliseconds
     */
    public long getCurrentHeartbeatInterval() {
        return currentHeartbeatInterval.get();
    }
    
    /**
     * Gets the number of consecutive failures
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }
    
    /**
     * Gets heartbeat statistics
     */
    public HeartbeatStats getHeartbeatStats() {
        return new HeartbeatStats(
            heartbeatCount.get(),
            failedHeartbeats.get(),
            lastSuccessfulHeartbeat.get(),
            lastFailedHeartbeat.get(),
            lastHeartbeatError.get(),
            getHeartbeatHealthStatus()
        );
    }
    
    /**
     * Gets the current heartbeat health status
     */
    public HeartbeatHealthStatus getHeartbeatHealthStatus() {
        LocalDateTime lastSuccess = lastSuccessfulHeartbeat.get();
        LocalDateTime lastFailure = lastFailedHeartbeat.get();
        
        if (lastSuccess == null) {
            return HeartbeatHealthStatus.UNKNOWN;
        }
        
        // If we've never had a successful heartbeat, we're unhealthy
        if (heartbeatCount.get() == 0) {
            return HeartbeatHealthStatus.UNHEALTHY;
        }
        
        // If we've had a recent failure and no recent success, we're unhealthy
        if (lastFailure != null && lastSuccess != null) {
            if (lastFailure.isAfter(lastSuccess)) {
                long minutesSinceFailure = ChronoUnit.MINUTES.between(lastFailure, LocalDateTime.now());
                if (minutesSinceFailure < 2) {
                    return HeartbeatHealthStatus.UNHEALTHY;
                }
            }
        }
        
        // If we haven't had a successful heartbeat in the last 2 minutes, we're unhealthy
        long minutesSinceLastSuccess = ChronoUnit.MINUTES.between(lastSuccess, LocalDateTime.now());
        if (minutesSinceLastSuccess > 2) {
            return HeartbeatHealthStatus.UNHEALTHY;
        }
        
        return HeartbeatHealthStatus.HEALTHY;
    }
    
    /**
     * Resets heartbeat statistics (useful for testing or recovery)
     */
    public void resetHeartbeatStats() {
        heartbeatCount.set(0);
        failedHeartbeats.set(0);
        lastSuccessfulHeartbeat.set(LocalDateTime.now());
        lastFailedHeartbeat.set(null);
        lastHeartbeatError.set(null);
        log.info("🔄 Heartbeat statistics reset");
    }
    
    private void handleHeartbeatFailure(String errorMessage, Exception e) {
        failedHeartbeats.incrementAndGet();
        consecutiveFailures.incrementAndGet();
        lastFailedHeartbeat.set(LocalDateTime.now());
        lastHeartbeatError.set(errorMessage);
        
        // Implement exponential backoff
        long newInterval = Math.min(
            currentHeartbeatInterval.get() * BACKOFF_MULTIPLIER, 
            MAX_HEARTBEAT_INTERVAL_MS
        );
        currentHeartbeatInterval.set(newInterval);
        
        log.warn("⚠️  MCP heartbeat failed (consecutive: {}): {} - backing off to {}ms", 
            consecutiveFailures.get(), errorMessage, newInterval);
        
        // If we have a connection health service, notify it of the failure
        if (connectionHealthService != null) {
            connectionHealthService.markHeartbeatFailed(errorMessage);
        }
        
        // Log the full exception at debug level for troubleshooting
        log.debug("Heartbeat failure details", e);
    }
    
    /**
     * Heartbeat statistics data class
     */
    public static class HeartbeatStats {
        private final int totalHeartbeats;
        private final int failedHeartbeats;
        private final LocalDateTime lastSuccessfulHeartbeat;
        private final LocalDateTime lastFailedHeartbeat;
        private final String lastError;
        private final HeartbeatHealthStatus healthStatus;
        
        public HeartbeatStats(int totalHeartbeats, int failedHeartbeats, 
                            LocalDateTime lastSuccessfulHeartbeat, LocalDateTime lastFailedHeartbeat,
                            String lastError, HeartbeatHealthStatus healthStatus) {
            this.totalHeartbeats = totalHeartbeats;
            this.failedHeartbeats = failedHeartbeats;
            this.lastSuccessfulHeartbeat = lastSuccessfulHeartbeat;
            this.lastFailedHeartbeat = lastFailedHeartbeat;
            this.lastError = lastError;
            this.healthStatus = healthStatus;
        }
        
        // Getters
        public int getTotalHeartbeats() { return totalHeartbeats; }
        public int getFailedHeartbeats() { return failedHeartbeats; }
        public LocalDateTime getLastSuccessfulHeartbeat() { return lastSuccessfulHeartbeat; }
        public LocalDateTime getLastFailedHeartbeat() { return lastFailedHeartbeat; }
        public String getLastError() { return lastError; }
        public HeartbeatHealthStatus getHealthStatus() { return healthStatus; }
        public double getSuccessRate() { 
            return totalHeartbeats > 0 ? (double)(totalHeartbeats - failedHeartbeats) / totalHeartbeats * 100 : 0.0; 
        }
        
        /**
         * Gets the current heartbeat interval in milliseconds
         */
        public long getCurrentInterval() {
            // This would need to be passed from the parent service
            // For now, return a default value
            return 30000;
        }
        
        /**
         * Gets the number of consecutive failures
         */
        public int getConsecutiveFailures() {
            // This would need to be passed from the parent service
            // For now, return a default value
            return 0;
        }
    }
    
    /**
     * Heartbeat health status enum
     */
    public enum HeartbeatHealthStatus {
        HEALTHY("Healthy"),
        UNHEALTHY("Unhealthy"),
        UNKNOWN("Unknown");
        
        private final String displayName;
        
        HeartbeatHealthStatus(String displayName) {
            this.displayName = displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
}
