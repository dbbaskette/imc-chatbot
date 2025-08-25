package com.insurancemegacorp.imcchatbot.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Enhanced WebClient configuration for MCP connections.
 * Addresses the critical issue of inadequate connection management for long-running SSE connections.
 */
@Configuration
public class WebClientConfig {

    @Bean
    @Primary
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(createHttpClient()))
            .codecs(configurer -> {
                // Increase buffer size for large MCP responses
                configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024); // 10MB
            });
    }

    /**
     * Creates an optimized HttpClient for MCP SSE connections.
     * This addresses connection pooling, timeouts, and lifecycle management issues.
     */
    private HttpClient createHttpClient() {
        // Create connection provider with proper pooling and lifecycle management
        ConnectionProvider connectionProvider = ConnectionProvider.builder("mcp-pool")
            .maxConnections(10)                              // Max connections per pool
            .maxIdleTime(Duration.ofMinutes(5))             // Close idle connections after 5 minutes
            .maxLifeTime(Duration.ofMinutes(15))            // Force connection refresh after 15 minutes
            .pendingAcquireMaxCount(256)                    // Max pending requests
            .evictInBackground(Duration.ofMinutes(2))       // Background cleanup every 2 minutes
            .build();

        return HttpClient.create(connectionProvider)
            // Connection timeouts
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)  // 30 second connect timeout
            .option(ChannelOption.SO_KEEPALIVE, true)             // Enable TCP keep-alive
            .option(ChannelOption.TCP_NODELAY, true)              // Disable Nagle's algorithm
            
            // Response timeout for the entire request-response cycle
            .responseTimeout(Duration.ofMinutes(2))               // 2 minute response timeout
            
            // Keep-alive settings for long-running connections
            .keepAlive(true)
            
            // Enable compression for better performance
            .compress(true)
            
            // Add timeout handlers at the Netty level
            .doOnConnected(conn -> {
                conn.addHandlerLast(new ReadTimeoutHandler(120, TimeUnit.SECONDS));   // 2 minute read timeout
                conn.addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS));   // 30 second write timeout
            });
    }

    /**
     * Create a specialized WebClient for MCP SSE connections.
     * This can be injected where specific MCP connection handling is needed.
     */
    @Bean("mcpWebClient")
    public WebClient mcpWebClient() {
        return webClientBuilder()
            .defaultHeader("Accept", "text/event-stream")
            .defaultHeader("Cache-Control", "no-cache")
            .defaultHeader("Connection", "keep-alive")
            .build();
    }
}