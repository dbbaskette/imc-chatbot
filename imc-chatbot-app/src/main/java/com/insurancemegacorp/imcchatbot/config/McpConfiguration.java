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

/**
 * Simplified MCP Configuration following PlumChat's working pattern
 * Creates ChatClient with MCP tools when available
 */
@Configuration
public class McpConfiguration {
    
    private static final Logger log = LoggerFactory.getLogger(McpConfiguration.class);

    @Value("${imc.chatbot.system-prompt}")
    private String systemPrompt;

    /**
     * ChatClient with MCP tool support - PlumChat's exact working pattern
     */
    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder, @Autowired(required = false) ToolCallbackProvider tools) {
        log.info("🔧 Configuring MCP-enabled ChatClient with tools");
        log.info("🛠️  ToolCallbackProvider available: {}", tools != null);
        
        if (tools != null) {
            log.info("✅ MCP tools will be integrated into ChatClient");
        } else {
            log.warn("⚠️  No MCP tools available - running without tool integration");
        }
        
        // Use PlumChat's exact pattern
        return chatClientBuilder
            .defaultToolCallbacks(tools)
            .defaultSystem(systemPrompt)
            .build();
    }
    
    /**
     * Report MCP status after application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reportMcpStatus() {
        log.info("🎯 ========== MCP STATUS REPORT ==========");
        log.info("🟢 MCP Configuration: ACTIVE");
        log.info("🔧 Tools discovered from MCP servers will be available for AI usage");
        log.info("✅ MCP integration ready!");
        log.info("🎯 =======================================");
    }
}