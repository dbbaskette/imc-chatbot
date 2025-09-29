package com.insurancemegacorp.imcchatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simplified MCP Configuration using Spring AI 1.1.0 auto-configuration
 * Creates ChatClient with MCP tools when available
 */
@Configuration
public class McpConfiguration {
    
    private static final Logger log = LoggerFactory.getLogger(McpConfiguration.class);

    @Value("${imc.chatbot.system-prompt}")
    private String systemPrompt;

    // Table formatting prompt removed - consolidated into main system prompt
    
    @Value("${imc.chatbot.mcp.profile:UNKNOWN}")
    private String mcpProfile;
    
    @Value("${spring.ai.mcp.client.streamable-http.connections.imc-policy.url:NOT_CONFIGURED}")
    private String mcpServerUrl;
    
    private ToolCallbackProvider toolCallbackProvider;

    // Cache for tool names to avoid repeated reflection
    private final AtomicReference<List<String>> cachedToolNames = new AtomicReference<>();
    private volatile boolean toolNamesExtracted = false;

    /**
     * ChatClient with MCP tool support using Spring AI 1.1.0 auto-configuration
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder, @Autowired(required = false) ToolCallbackProvider tools) {
        log.info("🔧 Configuring MCP-enabled ChatClient with Spring AI 1.1.0");
        log.info("🛠️  ToolCallbackProvider available: {}", tools != null);
        
        // Store the tools for later inspection
        this.toolCallbackProvider = tools;
        
        if (tools != null) {
            log.info("✅ MCP tools will be integrated into ChatClient");
            try {
                log.info("🔍 Tool callback provider class: {}", tools.getClass().getSimpleName());

                // Cache tool names for faster subsequent access
                cacheToolNames(tools);

            } catch (Exception e) {
                log.debug("Could not get tool provider details: {}", e.getMessage());
            }
        } else {
            log.warn("⚠️  No MCP tools available - running without tool integration");
        }
        
        return chatClientBuilder
            .defaultToolCallbacks(tools)
            .defaultSystem(systemPrompt)  // Core prompt only - formatting added dynamically
            .build();
    }
    
    /**
     * Report MCP status after application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reportMcpStatus() {
        log.info("🎯 ========== MCP STATUS REPORT ==========");
        log.info("🟢 MCP Configuration: ACTIVE");
        log.info("🌐 Transport: Streamable HTTP");
        log.info("📋 Profile: {}", mcpProfile);
        log.info("📡 Server URL: {}", mcpServerUrl);
        
        // Report tool discovery status
        if (toolCallbackProvider != null) {
            try {
                String toolProviderClass = toolCallbackProvider.getClass().getSimpleName();
                log.info("🛠️  Tool Provider: {}", toolProviderClass);

                // Use cached tool names for fast reporting
                reportCachedToolNames();

                log.info("✅ MCP Tools: REGISTERED");
            } catch (Exception e) {
                log.warn("⚠️  Error inspecting tool provider: {}", e.getMessage());
                log.info("🛠️  MCP Tools: REGISTERED but not inspectable");
            }
        } else {
            log.warn("❌ MCP Tools: NONE DISCOVERED");
            log.warn("🔍 Possible issues:");
            log.warn("   - MCP server not running or not reachable");
            log.warn("   - MCP server not exposing tools correctly");
            log.warn("   - Network connectivity issues");
            log.warn("   - MCP client configuration problems");
            
            if (mcpServerUrl.contains("localhost")) {
                log.warn("💡 Start local server: cd ../imc-policy-mcp-server && ./mcp-server.sh --local");
            }
        }
        
        log.info("🎯 =======================================");
    }
    
    /**
     * Cache tool names from the ToolCallbackProvider for fast subsequent access
     */
    private void cacheToolNames(ToolCallbackProvider toolProvider) {
        if (toolNamesExtracted) {
            return; // Already cached
        }

        try {
            List<String> toolNames = extractToolNamesFromProvider(toolProvider);
            cachedToolNames.set(toolNames);
            toolNamesExtracted = true;

            // Log immediately during caching
            if (toolNames.isEmpty()) {
                log.info("🔧 Registered Tools: NONE (cached)");
            } else {
                log.info("🔧 Registered Tools ({} total - cached):", toolNames.size());
                int index = 1;
                for (String toolName : toolNames) {
                    log.info("   {}. 🛠️  {}", index++, toolName);
                }
            }
        } catch (Exception e) {
            log.debug("Could not cache tool names: {}", e.getMessage());
            toolNamesExtracted = true; // Mark as attempted to avoid retries
        }
    }

    /**
     * Report cached tool names (fast operation)
     */
    private void reportCachedToolNames() {
        List<String> toolNames = cachedToolNames.get();
        if (toolNames == null) {
            log.info("🔧 Registered Tools: Not cached (extraction failed)");
            return;
        }

        if (toolNames.isEmpty()) {
            log.info("🔧 Registered Tools: NONE");
        } else {
            log.info("🔧 Registered Tools ({} total):", toolNames.size());
            int index = 1;
            for (String toolName : toolNames) {
                log.info("   {}. 🛠️  {}", index++, toolName);
            }
        }
    }

    /**
     * Extract tool names from provider and return as list
     */
    private List<String> extractToolNamesFromProvider(ToolCallbackProvider toolProvider) {
        List<String> toolNames = new java.util.ArrayList<>();

        try {
            // Debug: Show all available methods
            log.debug("🔍 Debugging ToolCallbackProvider methods for class: {}", toolProvider.getClass().getName());
            java.lang.reflect.Method[] methods = toolProvider.getClass().getDeclaredMethods();
            for (java.lang.reflect.Method method : methods) {
                log.debug("   Available method: {} with {} parameters", method.getName(), method.getParameterCount());
            }

            // Use reflection to get the tool names from the provider
            java.lang.reflect.Method getCallbacksMethod = null;

            // Try different possible method names to get the callbacks
            String[] methodNames = {"getCallbacks", "getToolCallbacks", "getAllCallbacks", "getTools", "getToolFunctions", "resolveToolCallbacks"};

            for (String methodName : methodNames) {
                try {
                    getCallbacksMethod = toolProvider.getClass().getMethod(methodName);
                    log.debug("🎯 Found method: {}", methodName);
                    break;
                } catch (NoSuchMethodException e) {
                    log.debug("❌ Method {} not found", methodName);
                }
            }

            if (getCallbacksMethod != null) {
                log.debug("🎯 Calling method: {}", getCallbacksMethod.getName());
                Object callbacks = getCallbacksMethod.invoke(toolProvider);
                log.debug("🎯 Method returned: {} (type: {})", callbacks, callbacks != null ? callbacks.getClass().getName() : "null");


                if (callbacks instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, ?> toolMap = (java.util.Map<String, ?>) callbacks;
                    toolNames.addAll(toolMap.keySet());
                    log.debug("🎯 Extracted {} tools from Map: {}", toolNames.size(), toolNames);
                    return toolNames;
                }

                if (callbacks instanceof java.util.Collection) {
                    java.util.Collection<?> toolCollection = (java.util.Collection<?>) callbacks;
                    for (Object tool : toolCollection) {
                        String toolName = extractToolName(tool);
                        toolNames.add(toolName);
                    }
                    log.debug("🎯 Extracted {} tools from Collection: {}", toolNames.size(), toolNames);
                    return toolNames;
                }

                if (callbacks.getClass().isArray()) {
                    Object[] toolArray = (Object[]) callbacks;
                    for (Object tool : toolArray) {
                        String toolName = extractToolName(tool);
                        toolNames.add(toolName);
                    }
                    log.debug("🎯 Extracted {} tools from Array: {}", toolNames.size(), toolNames);
                    return toolNames;
                }

                log.debug("⚠️  Callbacks object is neither Map, Collection, nor Array: {}", callbacks.getClass().getName());
            } else {
                log.debug("❌ No known method found for tool extraction");
                // Try alternative extraction
                return tryAlternativeToolExtractionForList(toolProvider);
            }

        } catch (Exception e) {
            log.warn("❌ Could not extract tool names via reflection: {}", e.getMessage(), e);
            return tryAlternativeToolExtractionForList(toolProvider);
        }

        return toolNames;
    }
    
    /**
     * Try alternative methods to extract tool information as list
     */
    private List<String> tryAlternativeToolExtractionForList(ToolCallbackProvider toolProvider) {
        List<String> toolNames = new java.util.ArrayList<>();
        try {
            // Try toString method which might contain tool information
            String providerString = toolProvider.toString();
            if (providerString.contains("tool") || providerString.contains("Tool")) {
                // Try to parse tool names from string representation
                // This is a fallback and may not always work
                toolNames.add("tools_available_but_not_extractable");
                log.debug("Tool provider string: {}", providerString);
            }
        } catch (Exception e) {
            log.debug("Alternative extraction failed: {}", e.getMessage());
        }
        return toolNames;
    }
    
    /**
     * Extract tool name from a tool object
     */
    private String extractToolName(Object tool) {
        if (tool == null) {
            return "unknown";
        }

        try {
            // For SyncMcpToolCallback, try to get the actual tool name
            if (tool.getClass().getSimpleName().equals("SyncMcpToolCallback")) {
                // Try to access the tool name field via reflection
                try {
                    java.lang.reflect.Field nameField = tool.getClass().getDeclaredField("name");
                    nameField.setAccessible(true);
                    Object name = nameField.get(tool);
                    if (name != null) {
                        return name.toString();
                    }
                } catch (Exception e) {
                    log.debug("Could not access name field: {}", e.getMessage());
                }

                // Try getName method
                try {
                    java.lang.reflect.Method getNameMethod = tool.getClass().getMethod("getName");
                    Object name = getNameMethod.invoke(tool);
                    if (name != null) {
                        return name.toString();
                    }
                } catch (Exception e) {
                    log.debug("Could not access getName method: {}", e.getMessage());
                }

                // Try toString and parse for tool name
                String toolString = tool.toString();
                log.info("🔍 Tool string representation: {}", toolString);

                // Parse various patterns that might contain the tool name
                if (toolString.contains("name=")) {
                    String[] parts = toolString.split("name=");
                    if (parts.length > 1) {
                        String namePart = parts[1].split("[,\\]}]")[0].trim();
                        String extractedName = namePart.replaceAll("[\"']", "");
                        log.info("🎯 Extracted tool name from toString: {}", extractedName);
                        return extractedName;
                    }
                }

                // Try more parsing patterns
                if (toolString.contains("toolName")) {
                    String[] parts = toolString.split("toolName[=:]");
                    if (parts.length > 1) {
                        String namePart = parts[1].split("[,\\]}\\s]")[0].trim();
                        String extractedName = namePart.replaceAll("[\"']", "");
                        log.info("🎯 Extracted tool name from toolName: {}", extractedName);
                        return extractedName;
                    }
                }

                // Try to extract from description or other fields
                if (toolString.contains("description")) {
                    return "MCP_Tool_" + System.identityHashCode(tool);
                }

                // Deep dive into SyncMcpToolCallback to extract the actual tool definition
                return extractFromSyncMcpToolCallback(tool);
            }

            // Generic fallback for other tool types
            try {
                java.lang.reflect.Method getNameMethod = tool.getClass().getMethod("getName");
                Object name = getNameMethod.invoke(tool);
                if (name != null) {
                    return name.toString();
                }
            } catch (Exception e) {
                // Fall back to class name or toString
            }

            return tool.getClass().getSimpleName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Extract actual tool name from SyncMcpToolCallback by accessing internal fields
     */
    private String extractFromSyncMcpToolCallback(Object toolCallback) {
        try {
            log.info("🔍 Attempting deep extraction from SyncMcpToolCallback");

            // Try to access internal fields that might contain the tool definition
            java.lang.reflect.Field[] fields = toolCallback.getClass().getDeclaredFields();
            log.info("🔍 Available fields in SyncMcpToolCallback:");
            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(toolCallback);
                log.info("   Field: {} = {} (type: {})",
                    field.getName(),
                    value,
                    value != null ? value.getClass().getSimpleName() : "null");

                // Look for fields that might contain the tool definition
                if (field.getName().toLowerCase().contains("tool") ||
                    field.getName().toLowerCase().contains("definition") ||
                    field.getName().toLowerCase().contains("schema")) {

                    if (value != null) {
                        String toolName = extractToolNameFromDefinition(value);
                        if (toolName != null && !toolName.equals("unknown")) {
                            log.info("🎯 Extracted tool name from field {}: {}", field.getName(), toolName);
                            return toolName;
                        }
                    }
                }
            }

            return "SyncMcpToolCallback";
        } catch (Exception e) {
            log.debug("Could not extract from SyncMcpToolCallback: {}", e.getMessage());
            return "SyncMcpToolCallback";
        }
    }

    /**
     * Extract tool name from a tool definition object
     */
    private String extractToolNameFromDefinition(Object definition) {
        try {
            // Check if it's a tool definition with getName method
            try {
                java.lang.reflect.Method getNameMethod = definition.getClass().getMethod("getName");
                Object name = getNameMethod.invoke(definition);
                if (name != null) {
                    return name.toString();
                }
            } catch (Exception ignored) {}

            // Check if it's a tool definition with name field
            try {
                java.lang.reflect.Field nameField = definition.getClass().getDeclaredField("name");
                nameField.setAccessible(true);
                Object name = nameField.get(definition);
                if (name != null) {
                    return name.toString();
                }
            } catch (Exception ignored) {}

            // Parse toString for tool name patterns
            String defString = definition.toString();
            if (defString.contains("name=")) {
                String[] parts = defString.split("name=");
                if (parts.length > 1) {
                    String namePart = parts[1].split("[,\\]}]")[0].trim();
                    return namePart.replaceAll("[\"']", "");
                }
            }

            return "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    // Table formatting prompt method removed - no longer needed
}