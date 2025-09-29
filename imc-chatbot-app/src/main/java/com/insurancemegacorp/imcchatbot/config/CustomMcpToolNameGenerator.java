package com.insurancemegacorp.imcchatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Custom tool name generator for MCP tools to provide semantic tool names
 * instead of generic "SyncMcpToolCallback" names
 */
@Component
public class CustomMcpToolNameGenerator {

    private static final Logger log = LoggerFactory.getLogger(CustomMcpToolNameGenerator.class);

    /**
     * Generate semantic tool name based on MCP connection and tool metadata
     */
    public String generateToolName(String connectionName, String originalToolName) {
        // Generate semantic names for the AI model to understand
        String semanticName = generateSemanticName(connectionName, originalToolName);
        log.info("🎯 Generated semantic tool name: {} -> {}", originalToolName, semanticName);
        return semanticName;
    }

    private String generateSemanticName(String connectionName, String toolName) {
        // Remove connection prefix if present
        String baseName = toolName;
        if (toolName != null && toolName.contains("_")) {
            String[] parts = toolName.split("_", 2);
            if (parts.length > 1) {
                baseName = parts[1];
            }
        }

        // Generate semantic names based on known patterns
        if (baseName != null) {
            switch (baseName.toLowerCase()) {
                case "getpolicyvehicles":
                case "get_policy_vehicles":
                    return "get_policy_vehicles";
                case "getpolicydetails":
                case "get_policy_details":
                    return "get_policy_details";
                case "searchpolicies":
                case "search_policies":
                    return "search_policies";
                default:
                    // Convert camelCase to snake_case for unknown tools
                    return camelToSnakeCase(baseName);
            }
        }

        return toolName != null ? toolName : "unknown_tool";
    }

    private String camelToSnakeCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }

        return camelCase
            .replaceAll("([a-z])([A-Z])", "$1_$2")
            .toLowerCase();
    }
}