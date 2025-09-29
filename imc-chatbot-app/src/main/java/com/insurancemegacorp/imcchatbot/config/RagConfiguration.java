package com.insurancemegacorp.imcchatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Enhanced RAG Configuration for Insurance Domain
 * Implements query enhancement via system prompts and MCP tool optimization
 */
@Configuration
public class RagConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RagConfiguration.class);

    @Value("${imc.chatbot.rag.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Value("${imc.chatbot.rag.max-results:5}")
    private int maxResults;

    // Enhanced system prompt removed - now using main system prompt from properties
    // which includes mandatory table formatting instructions

    /**
     * Enhanced ChatClient with optimized system prompt and MCP integration
     */
    @Bean
    @Primary
    public ChatClient enhancedChatClient(
            ChatClient.Builder chatClientBuilder,
            @Autowired(required = false) ToolCallbackProvider tools,
            @Value("${imc.chatbot.system-prompt}") String systemPrompt) {

        log.info("🔧 Configuring Enhanced Insurance ChatClient");
        log.info("🛠️  ToolCallbackProvider available: {}", tools != null);
        log.info("🎯 Enhanced query strategies enabled with main system prompt (includes table formatting)");
        log.info("   📊 Target Similarity Threshold: {}", similarityThreshold);
        log.info("   📈 Target Max Results: {}", maxResults);

        return chatClientBuilder
            .defaultToolCallbacks(tools)
            .defaultSystem(systemPrompt)
            .build();
    }

    /**
     * Provide access to RAG configuration values
     */
    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public int getMaxResults() {
        return maxResults;
    }
}