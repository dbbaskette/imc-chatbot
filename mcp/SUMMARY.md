# MCP Connection Management Summary

## 1. Problem Summary

The imc-chatbot was losing its ability to connect to the policy-mcp-server after running for a while. This was caused by Server-Sent Events (SSE) connections timing out, insufficient connection monitoring, and the connection state in the chatbot becoming corrupted over time, requiring a full restart to resolve.

## 2. Root Cause Analysis

The root cause of the MCP connection loss was a combination of factors:

*   **SSE Connection Timeouts:** Long-lived SSE connections were being terminated by network infrastructure (load balancers, proxies) and cloud platform timeouts.
*   **Insufficient Connection Monitoring:** The health check and heartbeat intervals were not aggressive enough to detect and prevent connection loss in a timely manner.
*   **Connection State Corruption:** The connection state within the chatbot application would become corrupted over time, and the existing recovery mechanisms were unable to reset this corrupted state without a manual restart.
*   **Inadequate Recovery Mechanisms:** The system lacked robust mechanisms to automatically recover from a corrupted connection state.

## 3. Solution Implemented

A comprehensive connection management system was implemented to address the root causes of the MCP connection loss. The system provides automatic connection recovery without requiring manual restarts.

The solution includes the following key components:

*   **Connection State Management:** A state machine was implemented to track the connection lifecycle, including states for `DISCONNECTED`, `CONNECTING`, `CONNECTED`, `DEGRADED`, and `RECONNECTING`.
*   **Circuit Breaker Pattern:** A circuit breaker was implemented to prevent the system from repeatedly trying to connect to a failing server. The circuit opens after a configurable number of consecutive failures and closes after a timeout period.
*   **Adaptive Heartbeat Intervals:** The fixed heartbeat interval was replaced with an adaptive interval that adjusts based on the connection's health. The interval increases on consecutive failures (exponential backoff) and resets on success.
*   **Enhanced Health Checks:** The health checking mechanism was improved to be more proactive and to trigger reconnection attempts when the connection is unhealthy.
*   **Forced Connection Refresh:** A mechanism was added to forcefully refresh the connection state, which is triggered after a certain number of consecutive failures.

## 4. Key Features of the Solution

*   **Automatic Connection Maintenance:** A heartbeat mechanism automatically keeps MCP SSE connections alive.
*   **Proactive Timeout Prevention:** The adaptive heartbeat interval helps prevent common 60-second timeouts.
*   **Automatic Recovery:** The system automatically detects connection issues and attempts to recover without manual intervention.
*   **Graceful Degradation:** The chatbot continues to function with basic OpenAI chat capabilities even when MCP servers are unavailable.
*   **Visibility:** API endpoints and CLI commands provide visibility into connection health and heartbeat status.
*   **Environment-Appropriate Defaults:** The local development profile disables MCP by default to prevent unnecessary connection errors.

## 5. Configuration

The following configuration properties were added to `application-mcp.properties` to control the new connection resilience features:

```properties
# Adaptive heartbeat with exponential backoff
mcp.heartbeat.enabled=true
mcp.heartbeat.min-interval=30s
mcp.heartbeat.max-interval=5m
mcp.heartbeat.backoff-multiplier=2

# Circuit Breaker Configuration
mcp.resilience.circuit-breaker.threshold=5
mcp.resilience.circuit-breaker.timeout=2m

# Retry and resilience settings
spring.ai.mcp.client.retry.max-attempts=3
spring.ai.mcp.client.retry.backoff.initial-interval=1s
spring.ai.mcp.client.retry.backoff.multiplier=2.0
spring.ai.mcp.client.retry.backoff.max-interval=30s
```

## 6. Monitoring and Validation

The health of the MCP connection can be monitored via API endpoints and CLI commands.

### API Endpoints

*   **`GET /api/tools/health`**: Returns detailed connection health information, including heartbeat status, connection state, and circuit breaker status.
*   **`POST /api/tools/reconnect`**: Manually triggers a reconnection attempt.
*   **`GET /api/tools/heartbeat`**: Returns heartbeat status and statistics.

### CLI Commands

*   **`status`**: Shows detailed health information.
*   **`heartbeat`**: Shows heartbeat status and statistics.
*   **`send-heartbeat`**: Sends an immediate heartbeat to MCP servers.
