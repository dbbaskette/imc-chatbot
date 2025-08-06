package com.insurancemegacorp.imcchatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;

@Configuration
public class ChatConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ChatConfiguration.class);

    @Configuration
    @Profile("local")
    static class LocalProfileConfiguration {
        
        @PostConstruct
        public void validateLocalConfiguration() {
            String openAiApiKey = System.getenv("OPENAI_API_KEY");
            if (openAiApiKey == null || openAiApiKey.trim().isEmpty()) {
                String errorMessage = """
                    ❌ MISSING REQUIRED CONFIGURATION for 'local' profile:
                    
                    OPENAI_API_KEY environment variable is required for local development.
                    
                    Please set it using:
                    export OPENAI_API_KEY=your-openai-api-key
                    
                    Or run with cloud profile:
                    ./imc-chatbot.sh --profile cloud
                    """;
                log.error(errorMessage);
                throw new IllegalStateException("Missing OPENAI_API_KEY for local profile");
            }
            
            log.info("✅ Local configuration validated - OpenAI API key found");
        }
    }
    
    @Configuration
    @Profile("cloud")
    static class CloudProfileConfiguration {
        
        @PostConstruct
        public void validateCloudConfiguration() {
            log.info("✅ Cloud configuration validated - expecting OpenAI service binding");
        }
    }
}