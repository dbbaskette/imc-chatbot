package com.insurancemegacorp.imcchatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;

@Configuration
public class McpConditionalConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpConditionalConfiguration.class);

    @Configuration
    @Profile("!mcp")
    static class McpDisabledConfiguration {
        
        @PostConstruct
        public void logMcpDisabled() {
            log.info("⚠️ MCP Tools disabled - running in chat-only mode");
            log.info("💡 To enable MCP tools, add 'mcp' profile: --spring.profiles.active=local,mcp");
        }
    }
    
    @Configuration
    @Profile("mcp")
    static class McpEnabledConfiguration {
        
        @PostConstruct
        public void logMcpEnabled() {
            log.info("✅ MCP Tools enabled - attempting to connect to MCP servers");
        }
    }
}