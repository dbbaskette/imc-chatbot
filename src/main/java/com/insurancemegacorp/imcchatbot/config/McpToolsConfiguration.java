package com.insurancemegacorp.imcchatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;

@Configuration
@Profile("mcp")
public class McpToolsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpToolsConfiguration.class);

    @Autowired(required = false)
    private SyncMcpToolCallbackProvider toolCallbackProvider;

    @PostConstruct
    public void reportMcpStatus() {
        if (toolCallbackProvider != null) {
            try {
                var toolCallbacks = toolCallbackProvider.getToolCallbacks();
                log.info("✅ MCP Tools initialized - {} tool(s) available", toolCallbacks.length);
                
                for (var callback : toolCallbacks) {
                    var definition = callback.getToolDefinition();
                    String toolName = extractToolName(definition.name());
                    log.debug("🔧 Available tool: {}", toolName);
                }
            } catch (Exception e) {
                log.warn("⚠️ MCP Tools initialization had issues, but continuing anyway: {}", e.getMessage());
            }
        } else {
            log.info("⚠️ MCP Tools disabled - running in chat-only mode");
        }
    }

    private String extractToolName(String fullName) {
        if (fullName == null || !fullName.contains("_")) {
            return fullName;
        }
        String[] parts = fullName.split("_");
        return parts[parts.length - 1];
    }
}