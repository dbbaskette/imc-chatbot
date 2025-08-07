package com.insurancemegacorp.imcchatbot.config;

import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@Profile("mcp")
public class SchedulingConfiguration {
    // This configuration enables @Scheduled annotations for MCP connection health monitoring
}
