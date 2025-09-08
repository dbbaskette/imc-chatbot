package com.insurancemegacorp.imcchatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * MCP Configuration that only activates when MCP dependencies are present
 * and the MCP profile is active
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.ai.mcp.client.McpClient")
@Profile("mcp")
public class McpConfiguration {
    
    private static final Logger log = LoggerFactory.getLogger(McpConfiguration.class);

    /**
     * Enhanced ChatClient with MCP tool support when available
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.ai.mcp.client.enabled", havingValue = "true", matchIfMissing = true)
    public ChatClient mcpEnabledChatClient(OpenAiChatModel chatModel, List<Advisor> advisors) {
        log.info("🔧 Configuring MCP-enabled ChatClient with {} advisors", advisors.size());
        
        return ChatClient.builder(chatModel)
            .defaultAdvisors(advisors)
            .build();
    }
}