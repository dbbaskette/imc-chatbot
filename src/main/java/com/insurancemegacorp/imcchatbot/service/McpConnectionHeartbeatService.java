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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service to maintain MCP SSE connections by sending periodic heartbeats.
 * This prevents connection timeouts that commonly occur with long-lived SSE connections.
 */
@Service
@Profile("mcp")
public class McpConnectionHeartbeatService {
    
    private static final Logger log = LoggerFactory.getLogger(McpConnectionHeartbeatService.class);
    
    // Heartbeat every 45 seconds to prevent 60-second timeouts
    private static final long HEARTBEAT_INTERVAL_MS = 45000;
    
    // Track heartbeat statistics
    private final AtomicInteger heartbeatCount = new AtomicInteger(0);
    private final AtomicInteger failedHeartbeats = new AtomicInteger(0);
    private final AtomicReference<LocalDateTime> lastSuccessfulHeartbeat = new AtomicReference<>(LocalDateTime.now());
    private final AtomicReference<LocalDateTime> lastFailedHeartbeat = new AtomicReference<>();
    private final AtomicReference<String> lastHeartbeatError = new AtomicReference<>();
    
    @Autowired
    private SyncMcpToolCallbackProvider toolCallbackProvider;
    
    @Autowired(required = false)
    private McpConnectionHealthService connectionHealthService;
    
    /**
     * Sends periodic heartbeats to keep MCP connections alive.
     * Runs every 45 seconds with a 10-second initial delay.
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS, initialDelay = 10000)
    public void sendHeartbeat() {
        if (toolCallbackProvider == null) {
            log.debug("MCP tools not available - skipping heartbeat");
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
            
            // Log heartbeat success (debug level to avoid spam)
            log.debug("💓 MCP heartbeat sent - {} tools available (total: {}, failed: {})", 
                toolCount, heartbeatCount.get(), failedHeartbeats.get());
            
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
        lastFailedHeartbeat.set(LocalDateTime.now());
        lastHeartbeatError.set(errorMessage);
        
        log.warn("⚠️  MCP heartbeat failed: {}", errorMessage);
        
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
