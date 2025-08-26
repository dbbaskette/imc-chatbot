package com.insurancemegacorp.imcchatbot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Arrays;

/**
 * Configuration for ChatClient with explicit MCP tool integration
 * Based on Spring AI documentation recommendations
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder,
                                @Value("${imc.chatbot.system-prompt}") String systemPrompt,
                                @Autowired(required = false) SyncMcpToolCallbackProvider toolCallbackProvider) {
        
        ChatClient.Builder builder = chatClientBuilder.defaultSystem(systemPrompt);
        
        // Explicitly add MCP tools to ChatClient if available
        if (toolCallbackProvider != null) {
            ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
            if (toolCallbacks.length > 0) {
                builder = builder.defaultToolCallbacks(List.of(toolCallbacks));
            }
        }
        
        return builder.build();
    }
}