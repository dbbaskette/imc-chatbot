package com.insurancemegacorp.imcchatbot.controller;

import com.insurancemegacorp.imcchatbot.dto.ToolInfo;
import com.insurancemegacorp.imcchatbot.service.McpConnectionHealthService;
import com.insurancemegacorp.imcchatbot.service.McpConnectionHeartbeatService;
import com.insurancemegacorp.imcchatbot.service.McpConnectionStateManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tools")
@CrossOrigin(origins = "*")
public class ToolsController {

    private static final Logger log = LoggerFactory.getLogger(ToolsController.class);
    
    private final SyncMcpToolCallbackProvider toolCallbackProvider;
    private final McpConnectionHealthService connectionHealthService;
    private final McpConnectionHeartbeatService heartbeatService;
    private final McpConnectionStateManager connectionStateManager;
    private final ObjectMapper objectMapper;
    
    public ToolsController(@Autowired(required = false) SyncMcpToolCallbackProvider toolCallbackProvider,
                          @Autowired(required = false) McpConnectionHealthService connectionHealthService,
                          @Autowired(required = false) McpConnectionHeartbeatService heartbeatService,
                          @Autowired(required = false) McpConnectionStateManager connectionStateManager) {
        this.toolCallbackProvider = toolCallbackProvider;
        this.connectionHealthService = connectionHealthService;
        this.heartbeatService = heartbeatService;
        this.connectionStateManager = connectionStateManager;
        this.objectMapper = new ObjectMapper();
    }
    
    @GetMapping
    public ResponseEntity<List<ToolInfo>> listTools() {
        if (toolCallbackProvider == null) {
            return ResponseEntity.ok(List.of());
        }
        
        try {
            var toolCallbacks = toolCallbackProvider.getToolCallbacks();
            List<ToolInfo> tools = new ArrayList<>();
            
            for (var callback : toolCallbacks) {
                var definition = callback.getToolDefinition();
                String shortName = extractToolName(definition.name());
                
                tools.add(new ToolInfo(
                    shortName,
                    definition.name(),
                    definition.description(),
                    definition.inputSchema()
                ));
            }
            
            log.debug("Listed {} available tools", tools.size());
            return ResponseEntity.ok(tools);
            
        } catch (Exception e) {
            log.error("Error listing tools: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{toolName}")
    public ResponseEntity<ToolInfo> describeTool(@PathVariable String toolName) {
        if (toolCallbackProvider == null) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            ToolCallback tool = findToolByName(toolName);
            if (tool == null) {
                return ResponseEntity.notFound().build();
            }
            
            var definition = tool.getToolDefinition();
            String shortName = extractToolName(definition.name());
            
            ToolInfo toolInfo = new ToolInfo(
                shortName,
                definition.name(),
                definition.description(),
                definition.inputSchema()
            );
            
            log.debug("Described tool: {}", toolName);
            return ResponseEntity.ok(toolInfo);
            
        } catch (Exception e) {
            log.error("Error describing tool {}: {}", toolName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/{toolName}")
    public ResponseEntity<String> invokeTool(@PathVariable String toolName, @RequestBody Map<String, Object> parameters) {
        if (toolCallbackProvider == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Tool integration is disabled");
        }
        
        try {
            ToolCallback tool = findToolByName(toolName);
            if (tool == null) {
                return ResponseEntity.notFound().build();
            }
            
            String jsonParameters = objectMapper.writeValueAsString(parameters);
            log.debug("Invoking tool: {} with parameters: {}", toolName, jsonParameters);
            
            String result = tool.call(jsonParameters);
            
            log.debug("Tool {} executed successfully", toolName);
            return ResponseEntity.ok(result);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid tool invocation for {}: {}", toolName, e.getMessage());
            return ResponseEntity.badRequest().body("Invalid parameters: " + e.getMessage());
        } catch (Exception e) {
            log.error("Tool invocation error for {}: {}", toolName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Tool execution failed: " + e.getMessage());
        }
    }
    
    private String extractToolName(String fullName) {
        if (fullName == null || !fullName.contains("_")) {
            return fullName;
        }
        String[] parts = fullName.split("_");
        return parts[parts.length - 1];
    }
    
    private ToolCallback findToolByName(String toolName) {
        if (toolCallbackProvider == null) {
            return null;
        }
        
        var toolCallbacks = toolCallbackProvider.getToolCallbacks();
        
        for (var callback : toolCallbacks) {
            String fullName = callback.getToolDefinition().name();
            String shortName = extractToolName(fullName);
            if (shortName.equals(toolName) || fullName.equals(toolName)) {
                return callback;
            }
        }
        
        return null;
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getConnectionHealth() {
        Map<String, Object> healthInfo = new java.util.HashMap<>();
        
        if (connectionHealthService == null) {
            healthInfo.put("status", "unavailable");
            healthInfo.put("message", "MCP health monitoring not available (MCP profile not active)");
            return ResponseEntity.ok(healthInfo);
        }
        
        boolean isHealthy = connectionHealthService.isConnectionHealthy();
        int toolCount = connectionHealthService.getAvailableToolCount();
        
        healthInfo.put("status", isHealthy ? "healthy" : "unhealthy");
        healthInfo.put("toolCount", toolCount);
        healthInfo.put("lastSuccessfulCheck", connectionHealthService.getLastSuccessfulCheck());
        
        if (!isHealthy) {
            healthInfo.put("lastError", connectionHealthService.getLastError());
            healthInfo.put("lastFailureTime", connectionHealthService.getLastFailureTime());
        }
        
        // Add heartbeat information if available
        if (heartbeatService != null) {
            var heartbeatStats = heartbeatService.getHeartbeatStats();
            Map<String, Object> heartbeatData = new java.util.HashMap<>();
            heartbeatData.put("status", heartbeatStats.getHealthStatus().toString().toLowerCase());
            heartbeatData.put("totalHeartbeats", heartbeatStats.getTotalHeartbeats());
            heartbeatData.put("successRate", String.format("%.1f%%", heartbeatStats.getSuccessRate()));
            heartbeatData.put("lastSuccessfulHeartbeat", heartbeatStats.getLastSuccessfulHeartbeat());
            heartbeatData.put("currentInterval", heartbeatStats.getCurrentInterval());
            heartbeatData.put("consecutiveFailures", heartbeatStats.getConsecutiveFailures());
            healthInfo.put("heartbeat", heartbeatData);
        }
        
        // Add connection state information if available
        if (connectionStateManager != null) {
            var connectionStats = connectionStateManager.getConnectionStats();
            Map<String, Object> connectionState = new java.util.HashMap<>();
            connectionState.put("state", connectionStats.getCurrentState().toString().toLowerCase());
            connectionState.put("connectionAttempts", connectionStats.getConnectionAttempts());
            connectionState.put("successRate", String.format("%.1f%%", connectionStats.getSuccessRate()));
            connectionState.put("lastConnectionTime", connectionStats.getLastConnectionTime());
            connectionState.put("averageConnectionDuration", 
                connectionStats.getAverageConnectionDuration() != null ? 
                connectionStats.getAverageConnectionDuration().toMillis() + "ms" : "N/A");
            healthInfo.put("connectionState", connectionState);
        }
        
        // Add circuit breaker information if available
        if (connectionHealthService != null) {
            healthInfo.put("circuitBreaker", Map.of(
                "open", connectionHealthService.isCircuitBreakerOpen(),
                "failureCount", connectionHealthService.getFailureCount(),
                "timeout", connectionHealthService.getCircuitBreakerTimeout().toString()
            ));
        }
        
        return ResponseEntity.ok(healthInfo);
    }
    
    @PostMapping("/reconnect")
    public ResponseEntity<Map<String, Object>> triggerReconnect() {
        Map<String, Object> result = new java.util.HashMap<>();
        
        if (connectionHealthService == null) {
            result.put("success", false);
            result.put("message", "MCP health monitoring not available (MCP profile not active)");
            return ResponseEntity.ok(result);
        }
        
        log.info("Manual MCP reconnection triggered via API");
        connectionHealthService.triggerReconnectionAttempt();
        
        boolean isHealthy = connectionHealthService.isConnectionHealthy();
        result.put("success", isHealthy);
        result.put("status", isHealthy ? "healthy" : "unhealthy");
        result.put("toolCount", connectionHealthService.getAvailableToolCount());
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/heartbeat")
    public ResponseEntity<Map<String, Object>> getHeartbeatStatus() {
        Map<String, Object> heartbeatInfo = new java.util.HashMap<>();
        
        if (heartbeatService == null) {
            heartbeatInfo.put("status", "unavailable");
            heartbeatInfo.put("message", "MCP heartbeat service not available (MCP profile not active)");
            return ResponseEntity.ok(heartbeatInfo);
        }
        
        var stats = heartbeatService.getHeartbeatStats();
        
        heartbeatInfo.put("status", stats.getHealthStatus().toString().toLowerCase());
        heartbeatInfo.put("totalHeartbeats", stats.getTotalHeartbeats());
        heartbeatInfo.put("failedHeartbeats", stats.getFailedHeartbeats());
        heartbeatInfo.put("successRate", String.format("%.1f%%", stats.getSuccessRate()));
        heartbeatInfo.put("lastSuccessfulHeartbeat", stats.getLastSuccessfulHeartbeat());
        
        if (stats.getLastFailedHeartbeat() != null) {
            heartbeatInfo.put("lastFailedHeartbeat", stats.getLastFailedHeartbeat());
            heartbeatInfo.put("lastError", stats.getLastError());
        }
        
        return ResponseEntity.ok(heartbeatInfo);
    }
    
    @PostMapping("/heartbeat")
    public ResponseEntity<Map<String, Object>> sendImmediateHeartbeat() {
        Map<String, Object> result = new java.util.HashMap<>();
        
        if (heartbeatService == null) {
            result.put("success", false);
            result.put("message", "MCP heartbeat service not available (MCP profile not active)");
            return ResponseEntity.ok(result);
        }
        
        log.info("Manual MCP heartbeat triggered via API");
        boolean success = heartbeatService.sendImmediateHeartbeat();
        
        result.put("success", success);
        result.put("message", success ? "Heartbeat sent successfully" : "Heartbeat failed");
        
        if (success) {
            var stats = heartbeatService.getHeartbeatStats();
            result.put("totalHeartbeats", stats.getTotalHeartbeats());
            result.put("successRate", String.format("%.1f%%", stats.getSuccessRate()));
        }
        
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/heartbeat/reset")
    public ResponseEntity<Map<String, Object>> resetHeartbeatStats() {
        Map<String, Object> result = new java.util.HashMap<>();
        
        if (heartbeatService == null) {
            result.put("success", false);
            result.put("message", "MCP heartbeat service not available (MCP profile not active)");
            return ResponseEntity.ok(result);
        }
        
        log.info("Heartbeat statistics reset triggered via API");
        heartbeatService.resetHeartbeatStats();
        
        result.put("success", true);
        result.put("message", "Heartbeat statistics reset successfully");
        
        return ResponseEntity.ok(result);
    }
}