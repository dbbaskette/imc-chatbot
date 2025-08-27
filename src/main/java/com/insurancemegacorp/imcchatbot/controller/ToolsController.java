package com.insurancemegacorp.imcchatbot.controller;

import com.insurancemegacorp.imcchatbot.dto.ToolInfo;
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
    private final ObjectMapper objectMapper;
    
    public ToolsController(@Autowired(required = false) SyncMcpToolCallbackProvider toolCallbackProvider) {
        this.toolCallbackProvider = toolCallbackProvider;
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
        
        // Simple health check based on tool availability
        int toolCount = 0;
        boolean hasTools = false;
        
        if (toolCallbackProvider != null) {
            var toolCallbacks = toolCallbackProvider.getToolCallbacks();
            toolCount = toolCallbacks.length;
            hasTools = toolCount > 0;
        }
        
        healthInfo.put("status", hasTools ? "healthy" : "no_tools");
        healthInfo.put("toolCount", toolCount);
        healthInfo.put("message", hasTools ? 
            "MCP tools available via Spring AI" : 
            "No MCP tools available (check MCP profile configuration)");
        
        return ResponseEntity.ok(healthInfo);
    }
}