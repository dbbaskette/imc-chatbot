package com.insurancemegacorp.imcchatbot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Clean ChatClient configuration using Spring AI best practices
 * MCP tools are auto-configured by Spring Boot when MCP client starter is present
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel,
                                @Value("${imc.chatbot.system-prompt}") String systemPrompt) {
        
        // Create ChatClient with system prompt - Spring AI auto-registers MCP tools
        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .build();
    }
}