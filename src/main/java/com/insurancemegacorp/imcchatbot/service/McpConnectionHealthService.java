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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Profile("mcp")
public class McpConnectionHealthService {

    private static final Logger log = LoggerFactory.getLogger(McpConnectionHealthService.class);
    
    private final AtomicBoolean isHealthy = new AtomicBoolean(true);
    private final AtomicReference<LocalDateTime> lastSuccessfulCheck = new AtomicReference<>(LocalDateTime.now());
    private final AtomicReference<LocalDateTime> lastFailureTime = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    
    @Autowired(required = false)
    private SyncMcpToolCallbackProvider toolCallbackProvider;

    /**
     * Performs a health check every 60 seconds to ensure MCP connections are active
     */
    @Scheduled(fixedRate = 60000, initialDelay = 30000)
    public void performHealthCheck() {
        if (toolCallbackProvider == null) {
            log.debug("MCP tools not available - skipping health check");
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
                markHealthy();
            } else {
                // No tools available - potential connection issue
                handleConnectionIssue("No MCP tools available - connection may be lost");
            }
            
        } catch (Exception e) {
            handleConnectionIssue("MCP health check failed: " + e.getMessage());
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
     * Manual method to trigger connection recovery attempts
     */
    public void triggerReconnectionAttempt() {
        log.info("🔄 Manually triggering MCP reconnection attempt...");
        performHealthCheck();
    }
}
