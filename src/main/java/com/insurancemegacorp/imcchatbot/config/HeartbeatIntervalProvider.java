package com.insurancemegacorp.imcchatbot.config;

import com.insurancemegacorp.imcchatbot.service.McpConnectionHeartbeatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Provides dynamic heartbeat intervals for the MCP connection heartbeat service.
 * This allows the heartbeat service to adapt its timing based on connection health.
 */
@Configuration
@Profile("mcp")
public class HeartbeatIntervalProvider {
    
    @Autowired
    private McpConnectionHeartbeatService heartbeatService;
    
    /**
     * Provides the current heartbeat interval in milliseconds.
     * This is used by the @Scheduled annotation to dynamically adjust timing.
     */
    @Bean(name = "intervalProvider")
    public IntervalProvider createIntervalProvider() {
        return new IntervalProvider() {
            @Override
            public long getInterval() {
                // Get the current interval from the heartbeat service
                // Default to 30 seconds if service is not available
                try {
                    return heartbeatService.getCurrentHeartbeatInterval();
                } catch (Exception e) {
                    return 30000; // 30 seconds default
                }
            }
        };
    }
    
    /**
     * Interface for getting the current heartbeat interval
     */
    public interface IntervalProvider {
        long getInterval();
    }
}
