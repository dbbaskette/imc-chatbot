# MCP Connection Health and Persistence Improvements

## Overview

This document describes the improvements made to handle MCP (Model Context Protocol) server disconnections and maintain persistent connections in the IMC Chatbot application.

## Problem

The MCP server connections were experiencing several issues:

1. **Connection Timeouts**: SSE connections timing out after periods of inactivity
2. **Database Errors**: MCP servers attempting to query non-existent database tables ("customers" table)
3. **Status Endpoint Blocking**: Status API endpoint blocking for 30+ seconds when MCP tools are unavailable
4. **Profile Mismatch**: MCP connections enabled in local development without proper server availability

## Solution

### 1. Enhanced MCP Configuration

**File**: `src/main/resources/application-mcp.properties`

Added the following configuration improvements:

```properties
# Connection timeout and resilience settings
spring.ai.mcp.client.request-timeout=30s
spring.ai.mcp.client.initialized=true

# Enable connection monitoring and health checks
spring.ai.mcp.client.root-change-notification=true

# Heartbeat Configuration - Keep connections alive
# Heartbeat every 45 seconds to prevent 60-second timeouts
mcp.heartbeat.enabled=true
mcp.heartbeat.interval=45s
mcp.heartbeat.initial-delay=10s
```

- **Request timeout**: Increased from default 20s to 30s for more robust connections
- **Resilient connections**: Already enabled (`spring.ai.mcp.client.connection.resilient=true`)
- **Root change notifications**: Enables connection state monitoring
- **Heartbeat mechanism**: Prevents connection timeouts by sending periodic pings

### 2. Connection Health Monitoring Service

**File**: `src/main/java/com/insurancemegacorp/imcchatbot/service/McpConnectionHealthService.java`

Created a comprehensive health monitoring service that:

- **Scheduled Health Checks**: Runs every 60 seconds to verify MCP connection status
- **Connection Recovery**: Automatically triggers reconnection attempts when issues are detected
- **Health Status Tracking**: Maintains connection health state and error information
- **Manual Recovery**: Provides API to manually trigger reconnection attempts
- **Heartbeat Integration**: Receives heartbeat status updates to maintain connection health

Key features:
- Non-intrusive monitoring (only logs issues when status changes)
- Tracks last successful check and failure times
- Provides detailed error information for debugging
- Integrates with heartbeat service for real-time health updates

### 3. Connection Heartbeat Service

**File**: `src/main/java/com/insurancemegacorp/imcchatbot/service/McpConnectionHeartbeatService.java`

New service that maintains MCP SSE connections by:

- **Periodic Heartbeats**: Sends heartbeats every 45 seconds to prevent 60-second timeouts
- **Connection Activity**: Keeps SSE connections alive through regular tool availability checks
- **Statistics Tracking**: Monitors heartbeat success/failure rates and timing
- **Health Status**: Provides real-time heartbeat health assessment
- **Manual Control**: Allows immediate heartbeat sending and statistics reset

Key features:
- Runs every 45 seconds with 10-second initial delay
- Tracks total heartbeats, failures, and success rates
- Integrates with health service for comprehensive connection monitoring
- Provides detailed statistics and health status

### 4. Enhanced Chat Service Integration

**File**: `src/main/java/com/insurancemegacorp/imcchatbot/service/ChatService.java`

Updated the chat service to:

- **Pre-flight Health Checks**: Verifies MCP connection health before attempting tool usage
- **Automatic Fallback**: Falls back to basic chat when MCP tools are unavailable
- **Recovery Attempts**: Triggers reconnection when tool access fails
- **Graceful Degradation**: Continues functioning even when MCP tools are disconnected

### 5. API Endpoints for Health Monitoring

**File**: `src/main/java/com/insurancemegacorp/imcchatbot/controller/ToolsController.java`

Added new endpoints:

- **GET `/api/tools/health`**: Returns detailed connection health information including heartbeat status
- **POST `/api/tools/reconnect`**: Manually triggers reconnection attempts
- **GET `/api/tools/heartbeat`**: Returns heartbeat status and statistics
- **POST `/api/tools/heartbeat`**: Sends immediate heartbeat to MCP servers
- **POST `/api/tools/heartbeat/reset`**: Resets heartbeat statistics

### 6. CLI Commands for Connection Management

**File**: `src/main/java/com/insurancemegacorp/imcchatbot/cli/CliRunner.java`

Added new CLI commands:

- **`heartbeat`**: Shows heartbeat status and statistics
- **`send-heartbeat`**: Sends immediate heartbeat to MCP servers
- **`reset-heartbeat`**: Resets heartbeat statistics

### 7. Status Controller Timeout Protection

**File**: `src/main/java/com/insurancemegacorp/imcchatbot/controller/StatusController.java`

Enhanced the status endpoint to:
- **Non-blocking Tool Checks**: Prevents 30-second timeouts when MCP tools are unavailable
- **Graceful Degradation**: Returns partial status information when MCP connections fail
- **Better Error Handling**: Logs but doesn't propagate MCP connection errors to users

### 8. Local Development Profile Updates

**File**: `src/main/resources/application-local.properties`

Updated local development configuration to:
- **Disable MCP by Default**: Prevents connection errors during local development
- **Clear Documentation**: Comments explain when to enable MCP connections
- **Explicit Tool Callback Control**: `spring.ai.mcp.client.toolcallback.enabled=false`

### 9. Scheduling Configuration

**File**: `src/main/java/com/insurancemegacorp/imcchatbot/config/SchedulingConfiguration.java`

Enables Spring's `@Scheduled` annotation support for health monitoring and heartbeat tasks when the MCP profile is active.

## Benefits

### 1. **Automatic Connection Maintenance**
The heartbeat mechanism automatically keeps MCP SSE connections alive, preventing timeouts due to inactivity.

### 2. **Proactive Timeout Prevention**
By sending heartbeats every 45 seconds, the system prevents the common 60-second timeout issues that plague long-lived SSE connections.

### 3. **Automatic Recovery**
The system now automatically detects connection issues and attempts to recover without manual intervention.

### 4. **Continued Operation**
Even when MCP servers are unavailable, the chatbot continues to function with basic OpenAI chat capabilities.

### 5. **Visibility**
Users and administrators can monitor connection health and heartbeat status through CLI commands and API endpoints.

### 6. **Proactive Monitoring**
The system proactively checks connection health and maintains connections rather than waiting for failures during actual usage.

### 7. **Fast Status Responses**
Status API endpoint now responds quickly even when MCP connections are unavailable.

### 8. **Environment-Appropriate Defaults**
Local development profile disables MCP by default to prevent unnecessary connection errors.

### 9. **Spring AI Compatibility**
Leverages Spring AI's built-in resilient connection features while adding additional monitoring, recovery, and heartbeat capabilities.

## Usage

### Monitor Connection Health (CLI)
```bash
# Run the status command to see detailed health information
status

# Check heartbeat status and statistics
heartbeat

# Send immediate heartbeat
send-heartbeat

# Reset heartbeat statistics
reset-heartbeat
```

### Monitor Connection Health (API)
```bash
# Get health information including heartbeat status
curl http://localhost:8080/api/tools/health

# Get heartbeat statistics
curl http://localhost:8080/api/tools/heartbeat

# Manually trigger reconnection
curl -X POST http://localhost:8080/api/tools/reconnect

# Send immediate heartbeat
curl -X POST http://localhost:8080/api/tools/heartbeat

# Reset heartbeat statistics
curl -X POST http://localhost:8080/api/tools/heartbeat/reset
```

### Automatic Behavior
- Health checks run every 60 seconds automatically
- Heartbeats are sent every 45 seconds automatically
- Recovery attempts are triggered when issues are detected
- Chat service automatically falls back to basic mode when tools are unavailable

## Configuration Options

All features are automatically enabled when the `mcp` profile is active. The services will:

- Start health checks 30 seconds after application startup
- Start heartbeats 10 seconds after application startup
- Run health checks every 60 seconds
- Send heartbeats every 45 seconds
- Log status changes (healthy ↔ unhealthy transitions)
- Provide detailed error information when connections fail
- Track heartbeat success rates and timing

## Troubleshooting

### Connection Issues
1. Check the `status` command output for detailed health information
2. Use the `heartbeat` command to see heartbeat statistics
3. Try `send-heartbeat` to test immediate connection
4. Check application logs for detailed error messages

### Heartbeat Failures
1. Verify MCP server availability
2. Check network connectivity to MCP server
3. Review SSL configuration if using HTTPS
4. Check server-side timeout settings

### Performance Issues
1. Monitor heartbeat success rates
2. Check for frequent reconnection attempts
3. Review timeout configuration values
4. Monitor server resource usage

## Technical Details

### Heartbeat Mechanism
The heartbeat service works by:
1. Calling `toolCallbackProvider.getToolCallbacks()` every 45 seconds
2. This simple call keeps the SSE connection active
3. Success/failure is tracked and reported
4. Health service is notified of heartbeat status

### Health Monitoring Integration
The health and heartbeat services work together:
1. Health service provides overall connection status
2. Heartbeat service maintains connection activity
3. Both services share status information
4. Combined monitoring provides comprehensive connection health

### Timeout Prevention
The 45-second heartbeat interval is designed to:
1. Prevent common 60-second load balancer timeouts
2. Maintain connection activity without excessive overhead
3. Provide early warning of connection issues
4. Enable automatic recovery before timeouts occur
