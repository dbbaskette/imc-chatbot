package com.insurancemegacorp.imcchatbot.controller;

import com.insurancemegacorp.imcchatbot.dto.ToolInfo;
import com.insurancemegacorp.imcchatbot.service.McpConnectionHealthService;
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
    private final ObjectMapper objectMapper;
    
    public ToolsController(@Autowired(required = false) SyncMcpToolCallbackProvider toolCallbackProvider,
                          @Autowired(required = false) McpConnectionHealthService connectionHealthService) {
        this.toolCallbackProvider = toolCallbackProvider;
        this.connectionHealthService = connectionHealthService;
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
}