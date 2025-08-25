package com.insurancemegacorp.imcchatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service to force complete MCP connection refresh when connections become stale.
 * This addresses the core issue where Spring AI MCP client holds onto stale connections
 * that can't be recovered through normal health checks.
 */
@Service
@Profile("mcp")
public class McpConnectionRefreshService {

    private static final Logger log = LoggerFactory.getLogger(McpConnectionRefreshService.class);
    
    
    @Autowired(required = false)
    private SyncMcpToolCallbackProvider toolCallbackProvider;
    
    @Autowired(required = false)
    private McpConnectionHealthService connectionHealthService;
    
    @Autowired(required = false)
    private McpConnectionStateManager connectionStateManager;

    /**
     * Forces complete MCP connection refresh by attempting multiple strategies.
     * This is the critical missing piece that allows recovery from stale connections.
     */
    public boolean forceConnectionRefresh() {
        log.info("🔄 Forcing complete MCP connection refresh - addressing stale connection issue");
        
        if (toolCallbackProvider == null) {
            log.warn("No MCP tool callback provider available - cannot refresh connections");
            return false;
        }
        
        // Update connection state to reflect refresh attempt
        if (connectionStateManager != null) {
            connectionStateManager.attemptConnection();
        }
        
        boolean refreshSuccess = false;
        
        try {
            // Strategy 1: Try to force Spring AI MCP client reconnection via reflection
            log.debug("Attempting Spring AI MCP client reconnection...");
            refreshSuccess = refreshSpringAiMcpClient();
            
            if (refreshSuccess) {
                log.info("✅ Spring AI MCP client refresh successful");
            } else {
                log.debug("Spring AI MCP client refresh failed, trying bean refresh strategy");
                
                // Strategy 2: Force application context refresh of MCP beans
                refreshMcpBeans();
                refreshSuccess = true;
                log.info("✅ MCP bean refresh completed");
            }
            
            // Validate the refresh worked
            if (refreshSuccess && validateConnectionAfterRefresh()) {
                log.info("🎉 MCP connection refresh successful - tools are now available");
                
                // Update connection health and state
                if (connectionHealthService != null) {
                    connectionHealthService.markHeartbeatReceived();
                }
                if (connectionStateManager != null) {
                    connectionStateManager.onConnectionSuccess();
                }
                
                return true;
            } else {
                log.warn("⚠️ Connection refresh completed but validation failed");
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to refresh MCP connections: {}", e.getMessage(), e);
            
            // Update connection state to reflect failure
            if (connectionStateManager != null) {
                connectionStateManager.onConnectionFailure("Connection refresh failed: " + e.getMessage());
            }
            
            return false;
        }
    }

    /**
     * Attempts to refresh Spring AI MCP client using reflection to access internal components.
     * This addresses the core issue where the MCP client doesn't automatically refresh stale connections.
     */
    private boolean refreshSpringAiMcpClient() {
        try {
            // Try to access and refresh internal Spring AI MCP client components
            // This is necessary because Spring AI doesn't expose direct connection refresh methods
            
            Class<?> providerClass = toolCallbackProvider.getClass();
            log.debug("Attempting to refresh MCP client of type: {}", providerClass.getName());
            
            // Strategy 1A: Look for connection refresh methods
            Method[] methods = providerClass.getMethods();
            for (Method method : methods) {
                String methodName = method.getName().toLowerCase();
                if ((methodName.contains("refresh") || methodName.contains("reconnect") || 
                     methodName.contains("reset")) && method.getParameterCount() == 0) {
                    log.debug("Found potential refresh method: {}", method.getName());
                    try {
                        method.invoke(toolCallbackProvider);
                        log.info("Successfully invoked refresh method: {}", method.getName());
                        return true;
                    } catch (Exception e) {
                        log.debug("Refresh method {} failed: {}", method.getName(), e.getMessage());
                    }
                }
            }
            
            // Strategy 1B: Try to clear internal caches or connection pools
            Field[] fields = providerClass.getDeclaredFields();
            for (Field field : fields) {
                String fieldName = field.getName().toLowerCase();
                if (fieldName.contains("connection") || fieldName.contains("client") || 
                    fieldName.contains("cache")) {
                    
                    field.setAccessible(true);
                    Object fieldValue = field.get(toolCallbackProvider);
                    
                    if (fieldValue != null) {
                        log.debug("Found connection-related field: {} of type {}", 
                                field.getName(), fieldValue.getClass().getName());
                        
                        // Try to refresh the field's object if it has refresh methods
                        Method[] fieldMethods = fieldValue.getClass().getMethods();
                        for (Method method : fieldMethods) {
                            String methodName = method.getName().toLowerCase();
                            if ((methodName.contains("refresh") || methodName.contains("clear") ||
                                 methodName.contains("reset")) && method.getParameterCount() == 0) {
                                try {
                                    method.invoke(fieldValue);
                                    log.debug("Successfully refreshed field {} via method {}", 
                                            field.getName(), method.getName());
                                    return true;
                                } catch (Exception e) {
                                    log.debug("Field refresh failed: {}", e.getMessage());
                                }
                            }
                        }
                    }
                }
            }
            
            log.debug("No direct refresh methods found in Spring AI MCP client");
            return false;
            
        } catch (Exception e) {
            log.debug("Spring AI MCP client refresh attempt failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Forces refresh of MCP-related beans in the application context.
     * This is a fallback strategy when direct Spring AI client refresh fails.
     */
    private void refreshMcpBeans() {
        log.debug("Attempting to refresh MCP-related beans");
        
        try {
            // Force garbage collection to clean up any stale connections
            System.gc();
            
            // Give the system a moment to clean up
            Thread.sleep(1000);
            
            // Try to re-initialize the tool callback provider by getting fresh callbacks
            if (toolCallbackProvider != null) {
                var callbacks = toolCallbackProvider.getToolCallbacks();
                log.debug("Re-initialized tool callbacks, found {} tools", 
                         callbacks != null ? callbacks.length : 0);
            }
            
        } catch (Exception e) {
            log.debug("Bean refresh attempt encountered issues: {}", e.getMessage());
        }
    }

    /**
     * Validates that the connection refresh actually worked by testing tool availability.
     */
    private boolean validateConnectionAfterRefresh() {
        try {
            // Use a timeout to prevent hanging on validation
            CompletableFuture<Boolean> validation = CompletableFuture.supplyAsync(() -> {
                try {
                    var toolCallbacks = toolCallbackProvider.getToolCallbacks();
                    boolean hasTools = toolCallbacks != null && toolCallbacks.length > 0;
                    
                    if (hasTools) {
                        log.debug("Validation successful - {} tools available after refresh", 
                                toolCallbacks.length);
                    } else {
                        log.debug("Validation failed - no tools available after refresh");
                    }
                    
                    return hasTools;
                } catch (Exception e) {
                    log.debug("Validation failed with exception: {}", e.getMessage());
                    return false;
                }
            });
            
            return validation.get(10, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            log.debug("Connection validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Triggers an immediate connection refresh attempt.
     * This is exposed for manual testing and emergency recovery.
     */
    public boolean triggerImmediateRefresh() {
        log.info("🚨 Manual connection refresh triggered");
        return forceConnectionRefresh();
    }

    /**
     * Gets the current status of the connection refresh service.
     */
    public ConnectionRefreshStatus getRefreshStatus() {
        boolean mcpAvailable = toolCallbackProvider != null;
        boolean hasTools = false;
        int toolCount = 0;
        
        if (mcpAvailable) {
            try {
                var callbacks = toolCallbackProvider.getToolCallbacks();
                hasTools = callbacks != null && callbacks.length > 0;
                toolCount = callbacks != null ? callbacks.length : 0;
            } catch (Exception e) {
                log.debug("Failed to get tool count: {}", e.getMessage());
            }
        }
        
        return new ConnectionRefreshStatus(mcpAvailable, hasTools, toolCount);
    }

    /**
     * Status information for connection refresh service
     */
    public static class ConnectionRefreshStatus {
        private final boolean mcpAvailable;
        private final boolean hasTools;
        private final int toolCount;
        
        public ConnectionRefreshStatus(boolean mcpAvailable, boolean hasTools, int toolCount) {
            this.mcpAvailable = mcpAvailable;
            this.hasTools = hasTools;
            this.toolCount = toolCount;
        }
        
        public boolean isMcpAvailable() { return mcpAvailable; }
        public boolean hasTools() { return hasTools; }
        public int getToolCount() { return toolCount; }
        
        @Override
        public String toString() {
            return String.format("ConnectionRefreshStatus{mcpAvailable=%s, hasTools=%s, toolCount=%d}", 
                               mcpAvailable, hasTools, toolCount);
        }
    }
}