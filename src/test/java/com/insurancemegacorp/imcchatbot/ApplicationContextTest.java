package com.insurancemegacorp.imcchatbot;

import com.insurancemegacorp.imcchatbot.cli.CliRunner;
import com.insurancemegacorp.imcchatbot.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic application context test to verify Spring Boot startup
 * and component wiring without external dependencies
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ChatService chatService;

    @Autowired
    private CliRunner cliRunner;

    @Test
    void shouldLoadApplicationContext() {
        // Given: Spring Boot application context
        // When: Context is loaded
        // Then: Context should be available
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void shouldWireChatService() {
        // Given: Spring Boot application context is loaded
        // When: ChatService is autowired
        // Then: ChatService should be properly initialized
        assertThat(chatService).isNotNull();
    }

    @Test
    void shouldWireCliRunner() {
        // Given: Spring Boot application context is loaded
        // When: CliRunner is autowired
        // Then: CliRunner should be properly initialized
        assertThat(cliRunner).isNotNull();
    }

    @Test
    void shouldHaveRequiredBeans() {
        // Given: Application context is loaded
        // When: Checking for required beans
        // Then: All essential beans should be present
        assertThat(applicationContext.containsBean("chatService")).isTrue();
        assertThat(applicationContext.containsBean("cliRunner")).isTrue();
    }
}