package com.insurancemegacorp.imcchatbot.integration;

import com.insurancemegacorp.imcchatbot.service.ChatService;
import com.insurancemegacorp.imcchatbot.service.McpConnectionRefreshService;
import com.insurancemegacorp.imcchatbot.service.McpConnectionHealthService;
import com.insurancemegacorp.imcchatbot.dto.StructuredResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;

/**
 * Integration tests for MCP connection refresh functionality.
 * Tests the critical connection management features implemented in Phase 1.
 */
@SpringBootTest
@ActiveProfiles({"test", "mcp"})
class McpConnectionIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired(required = false)
    private McpConnectionRefreshService connectionRefreshService;

    @Autowired(required = false)
    private McpConnectionHealthService connectionHealthService;


    @MockBean
    private SyncMcpToolCallbackProvider mockToolCallbackProvider;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(mockToolCallbackProvider);
    }

    @Test
    @DisplayName("Connection refresh service should be available when MCP profile is active")
    void connectionRefreshServiceAvailability() {
        assertNotNull(connectionRefreshService, "McpConnectionRefreshService should be available in MCP profile");
        assertNotNull(connectionHealthService, "McpConnectionHealthService should be available in MCP profile");
    }

    @Test
    @DisplayName("Connection refresh should handle stale connections")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testConnectionRefreshWithStaleConnection() {
        // Simulate stale connection - getToolCallbacks throws exception
        when(mockToolCallbackProvider.getToolCallbacks())
            .thenThrow(new RuntimeException("Connection timeout"))
            .thenReturn(createMockToolCallbacks(2)); // Second call succeeds after refresh

        // Attempt connection refresh
        boolean refreshResult = connectionRefreshService.forceConnectionRefresh();

        // Should attempt refresh and eventually succeed
        assertTrue(refreshResult, "Connection refresh should succeed after handling stale connection");
        
        // Verify that getToolCallbacks was called multiple times (initial failure + refresh attempts)
        verify(mockToolCallbackProvider, atLeast(2)).getToolCallbacks();
    }

    @Test
    @DisplayName("Chat service should use proactive validation before tool usage")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testProactiveConnectionValidationInChat() {
        // Setup mock to return tools (healthy connection)
        when(mockToolCallbackProvider.getToolCallbacks()).thenReturn(createMockToolCallbacks(1));

        // Send a chat message that would trigger MCP tool usage
        StructuredResponse response = chatService.chat("test-session", "What insurance policies are available?");

        // Should receive a response (even if basic chat due to mocking)
        assertNotNull(response, "Chat response should not be null");
        assertNotNull(response.content(), "Chat response content should not be null");

        // Verify proactive validation was performed
        verify(mockToolCallbackProvider, atLeast(1)).getToolCallbacks();
    }

    @Test
    @DisplayName("Connection refresh should recover from network interruptions")
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testNetworkInterruptionRecovery() {
        // Simulate network interruption - multiple failures followed by recovery
        when(mockToolCallbackProvider.getToolCallbacks())
            .thenThrow(new RuntimeException("Network unreachable"))
            .thenThrow(new RuntimeException("Connection reset"))
            .thenReturn(createMockToolCallbacks(3)); // Recovery after 2 failures

        // Test connection status before recovery attempt
        McpConnectionRefreshService.ConnectionRefreshStatus initialStatus = 
            connectionRefreshService.getRefreshStatus();
        
        assertFalse(initialStatus.hasTools(), "Should have no tools initially due to network issues");

        // Trigger connection refresh
        boolean refreshResult = connectionRefreshService.forceConnectionRefresh();

        assertTrue(refreshResult, "Connection should recover after network interruption");

        // Verify recovery
        McpConnectionRefreshService.ConnectionRefreshStatus recoveredStatus = 
            connectionRefreshService.getRefreshStatus();
        
        assertTrue(recoveredStatus.hasTools(), "Should have tools after recovery");
        assertEquals(3, recoveredStatus.getToolCount(), "Should have correct tool count after recovery");
    }

    @Test
    @DisplayName("Connection refresh should fail gracefully with persistent errors")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testPersistentConnectionFailure() {
        // Simulate persistent connection failure
        when(mockToolCallbackProvider.getToolCallbacks())
            .thenThrow(new RuntimeException("Server unavailable"));

        // Attempt connection refresh
        boolean refreshResult = connectionRefreshService.forceConnectionRefresh();

        // Should fail gracefully without hanging
        assertFalse(refreshResult, "Connection refresh should fail gracefully with persistent errors");

        // Verify multiple attempts were made
        verify(mockToolCallbackProvider, atLeast(1)).getToolCallbacks();
    }

    @Test
    @DisplayName("Health service should integrate with refresh service")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testHealthServiceIntegration() {
        // Setup successful connection after refresh
        when(mockToolCallbackProvider.getToolCallbacks()).thenReturn(createMockToolCallbacks(2));

        // Trigger reconnection via health service (which should use refresh service)
        connectionHealthService.triggerReconnectionAttempt();

        // Connection should be marked as healthy
        assertTrue(connectionHealthService.isConnectionHealthy(), 
                  "Connection should be healthy after successful refresh");

        verify(mockToolCallbackProvider, atLeast(1)).getToolCallbacks();
    }

    @Test
    @DisplayName("Connection validation should timeout appropriately")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConnectionValidationTimeout() {
        // Simulate slow/hanging connection
        when(mockToolCallbackProvider.getToolCallbacks()).thenAnswer(invocation -> {
            Thread.sleep(8000); // 8 second delay (longer than 5s timeout)
            return createMockToolCallbacks(1);
        });

        // Connection validation should timeout and not hang the chat service
        StructuredResponse response = chatService.chat("timeout-test", "Test timeout handling");

        // Should get a response even with connection timeout (fallback to basic chat)
        assertNotNull(response, "Should receive response even with connection timeout");
        assertNotNull(response.content(), "Response content should not be null");
    }

    @Test
    @DisplayName("Refresh service status should accurately reflect connection state")
    void testRefreshServiceStatus() {
        // Test with no tools available
        when(mockToolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[0]);

        McpConnectionRefreshService.ConnectionRefreshStatus status = connectionRefreshService.getRefreshStatus();

        assertTrue(status.isMcpAvailable(), "MCP should be available (provider exists)");
        assertFalse(status.hasTools(), "Should report no tools available");
        assertEquals(0, status.getToolCount(), "Tool count should be 0");

        // Test with tools available
        when(mockToolCallbackProvider.getToolCallbacks()).thenReturn(createMockToolCallbacks(3));

        status = connectionRefreshService.getRefreshStatus();

        assertTrue(status.isMcpAvailable(), "MCP should be available");
        assertTrue(status.hasTools(), "Should report tools available");
        assertEquals(3, status.getToolCount(), "Tool count should be 3");
    }

    @Test
    @DisplayName("Connection refresh should handle concurrent requests safely")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testConcurrentConnectionRefresh() throws InterruptedException {
        // Setup mock to simulate connection recovery
        when(mockToolCallbackProvider.getToolCallbacks()).thenReturn(createMockToolCallbacks(1));

        // Run multiple concurrent refresh attempts
        Thread[] threads = new Thread[5];
        boolean[] results = new boolean[5];

        for (int i = 0; i < 5; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                results[index] = connectionRefreshService.forceConnectionRefresh();
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // All refresh attempts should succeed without interfering with each other
        for (int i = 0; i < 5; i++) {
            assertTrue(results[i], "Concurrent refresh attempt " + i + " should succeed");
        }
    }

    /**
     * Helper method to create mock tool callbacks
     */
    private ToolCallback[] createMockToolCallbacks(int count) {
        ToolCallback[] callbacks = new ToolCallback[count];
        for (int i = 0; i < count; i++) {
            // Create simple mock objects that represent tool callbacks
            callbacks[i] = mock(ToolCallback.class);
        }
        return callbacks;
    }
}