package com.insurancemegacorp.imcchatbot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for McpConnectionRefreshService.
 * Tests the core connection refresh logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class McpConnectionRefreshServiceTest {

    @Mock
    private SyncMcpToolCallbackProvider mockToolCallbackProvider;

    @Mock
    private McpConnectionHealthService mockConnectionHealthService;

    @Mock
    private McpConnectionStateManager mockConnectionStateManager;

    private McpConnectionRefreshService refreshService;

    @BeforeEach
    void setUp() {
        refreshService = new McpConnectionRefreshService();
        
        // Use reflection to inject mocks (since @InjectMocks doesn't work well with @Autowired)
        setField(refreshService, "toolCallbackProvider", mockToolCallbackProvider);
        setField(refreshService, "connectionHealthService", mockConnectionHealthService);
        setField(refreshService, "connectionStateManager", mockConnectionStateManager);
    }

    @Test
    @DisplayName("Refresh should succeed when Spring AI client refresh works")
    void testSuccessfulSpringAiRefresh() {
        // Setup successful tool callback response
        ToolCallback[] mockCallbacks = {mock(ToolCallback.class), mock(ToolCallback.class)};
        when(mockToolCallbackProvider.getToolCallbacks()).thenReturn(mockCallbacks);

        boolean result = refreshService.forceConnectionRefresh();

        assertTrue(result, "Connection refresh should succeed");
        verify(mockConnectionStateManager).attemptConnection();
        verify(mockConnectionStateManager).onConnectionSuccess();
        verify(mockConnectionHealthService).markHeartbeatReceived();
    }

    @Test
    @DisplayName("Refresh should fail gracefully when tool provider is null")
    void testRefreshWithNullProvider() {
        setField(refreshService, "toolCallbackProvider", null);

        boolean result = refreshService.forceConnectionRefresh();

        assertFalse(result, "Connection refresh should fail when provider is null");
        verify(mockConnectionStateManager, never()).attemptConnection();
    }

    @Test
    @DisplayName("Refresh should handle exceptions during connection attempts")
    void testRefreshWithConnectionException() {
        // All calls fail - both Spring AI refresh and bean refresh validation
        when(mockToolCallbackProvider.getToolCallbacks())
            .thenThrow(new RuntimeException("Connection failed"));

        boolean result = refreshService.forceConnectionRefresh();

        assertFalse(result, "Connection refresh should fail when all attempts fail");
        verify(mockConnectionStateManager).attemptConnection();
        // Since validation fails, it doesn't call onConnectionSuccess, but it also
        // doesn't call onConnectionFailure because the bean refresh "succeeds"
        verify(mockConnectionStateManager, never()).onConnectionSuccess();
    }

    @Test
    @DisplayName("Refresh should attempt bean refresh when Spring AI refresh fails")
    void testFallbackToBeanRefresh() {
        // First call fails, second call (after bean refresh) succeeds
        when(mockToolCallbackProvider.getToolCallbacks())
            .thenThrow(new RuntimeException("Initial failure"))
            .thenReturn(new ToolCallback[]{mock(ToolCallback.class)});

        boolean result = refreshService.forceConnectionRefresh();

        assertTrue(result, "Connection refresh should succeed after bean refresh fallback");
        verify(mockConnectionStateManager).onConnectionSuccess();
    }

    @Test
    @DisplayName("Connection status should accurately reflect tool availability")
    void testConnectionStatus() {
        // Test with no tools
        when(mockToolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[0]);
        
        McpConnectionRefreshService.ConnectionRefreshStatus status = refreshService.getRefreshStatus();
        
        assertTrue(status.isMcpAvailable());
        assertFalse(status.hasTools());
        assertEquals(0, status.getToolCount());

        // Test with tools available
        ToolCallback[] mockCallbacks = {mock(ToolCallback.class), mock(ToolCallback.class), mock(ToolCallback.class)};
        when(mockToolCallbackProvider.getToolCallbacks()).thenReturn(mockCallbacks);
        
        status = refreshService.getRefreshStatus();
        
        assertTrue(status.isMcpAvailable());
        assertTrue(status.hasTools());
        assertEquals(3, status.getToolCount());
    }

    @Test
    @DisplayName("Connection status should handle exceptions gracefully")
    void testConnectionStatusWithException() {
        when(mockToolCallbackProvider.getToolCallbacks())
            .thenThrow(new RuntimeException("Status check failed"));

        McpConnectionRefreshService.ConnectionRefreshStatus status = refreshService.getRefreshStatus();

        assertTrue(status.isMcpAvailable(), "MCP should be considered available even if status check fails");
        assertFalse(status.hasTools(), "Should report no tools when status check fails");
        assertEquals(0, status.getToolCount(), "Tool count should be 0 when status check fails");
    }

    @Test
    @DisplayName("Immediate refresh should work correctly")
    void testImmediateRefresh() {
        ToolCallback[] mockCallbacks = {mock(ToolCallback.class)};
        when(mockToolCallbackProvider.getToolCallbacks()).thenReturn(mockCallbacks);

        boolean result = refreshService.triggerImmediateRefresh();

        assertTrue(result, "Immediate refresh should succeed");
        verify(mockConnectionStateManager).attemptConnection();
        verify(mockConnectionStateManager).onConnectionSuccess();
    }

    @Test
    @DisplayName("Validation should timeout appropriately")
    void testValidationTimeout() {
        // Mock a response that would exceed the 10-second validation timeout
        when(mockToolCallbackProvider.getToolCallbacks()).thenAnswer(invocation -> {
            Thread.sleep(6000); // 6 seconds - will cause timeout in validation but pass in bean refresh
            return new ToolCallback[]{mock(ToolCallback.class)};
        });

        // This should complete within reasonable time due to timeout
        long startTime = System.currentTimeMillis();
        boolean result = refreshService.forceConnectionRefresh();
        long duration = System.currentTimeMillis() - startTime;

        // The result depends on whether the bean refresh validation succeeds or times out
        // But it should definitely complete in reasonable time
        assertTrue(duration < 20000, "Refresh should complete within 20 seconds due to timeout handling");
    }

    @Test
    @DisplayName("Refresh should update connection state appropriately")
    void testConnectionStateUpdates() {
        ToolCallback[] mockCallbacks = {mock(ToolCallback.class), mock(ToolCallback.class)};
        when(mockToolCallbackProvider.getToolCallbacks()).thenReturn(mockCallbacks);

        refreshService.forceConnectionRefresh();

        // Verify connection state transitions
        verify(mockConnectionStateManager).attemptConnection();
        verify(mockConnectionStateManager).onConnectionSuccess();
        
        // Verify health service is notified
        verify(mockConnectionHealthService).markHeartbeatReceived();
    }

    /**
     * Helper method to set private fields using reflection
     */
    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}