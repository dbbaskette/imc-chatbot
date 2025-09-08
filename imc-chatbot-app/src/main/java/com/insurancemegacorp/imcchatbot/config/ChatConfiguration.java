package com.insurancemegacorp.imcchatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfiguration {
    
    private static final Logger log = LoggerFactory.getLogger(ChatConfiguration.class);

    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        log.info("🔧 Configuring ChatClient with OpenAI model");
        
        return ChatClient.builder(chatModel)
            .build();
    }
}