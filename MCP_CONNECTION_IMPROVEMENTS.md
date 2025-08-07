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
```

- **Request timeout**: Increased from default 20s to 30s for more robust connections
- **Resilient connections**: Already enabled (`spring.ai.mcp.client.connection.resilient=true`)
- **Root change notifications**: Enables connection state monitoring

### 2. Connection Health Monitoring Service

**File**: `src/main/java/com/insurancemegacorp/imcchatbot/service/McpConnectionHealthService.java`

Created a comprehensive health monitoring service that:

- **Scheduled Health Checks**: Runs every 60 seconds to verify MCP connection status
- **Connection Recovery**: Automatically triggers reconnection attempts when issues are detected
- **Health Status Tracking**: Maintains connection health state and error information
- **Manual Recovery**: Provides API to manually trigger reconnection attempts

Key features:
- Non-intrusive monitoring (only logs issues when status changes)
- Tracks last successful check and failure times
- Provides detailed error information for debugging

### 3. Enhanced Chat Service Integration

**File**: `src/main/java/com/insurancemegacorp/imcchatbot/service/ChatService.java`

Updated the chat service to:

- **Pre-flight Health Checks**: Verifies MCP connection health before attempting tool usage
- **Automatic Fallback**: Falls back to basic chat when MCP tools are unavailable
- **Recovery Attempts**: Triggers reconnection when tool access fails
- **Graceful Degradation**: Continues functioning even when MCP tools are disconnected

### 4. API Endpoints for Health Monitoring

**File**: `src/main/java/com/insurancemegacorp/imcchatbot/controller/ToolsController.java`

Added new endpoints:

- **GET `/api/tools/health`**: Returns detailed connection health information
- **POST `/api/tools/reconnect`**: Manually triggers reconnection attempts

### 5. Status Controller Timeout Protection

**File**: `src/main/java/com/insurancemegacorp/imcchatbot/controller/StatusController.java`

Enhanced the status endpoint to:
- **Non-blocking Tool Checks**: Prevents 30-second timeouts when MCP tools are unavailable
- **Graceful Degradation**: Returns partial status information when MCP connections fail
- **Better Error Handling**: Logs but doesn't propagate MCP connection errors to users

### 6. Local Development Profile Updates

**File**: `src/main/resources/application-local.properties`

Updated local development configuration to:
- **Disable MCP by Default**: Prevents connection errors during local development
- **Clear Documentation**: Comments explain when to enable MCP connections
- **Explicit Tool Callback Control**: `spring.ai.mcp.client.toolcallback.enabled=false`

### 7. Scheduling Configuration

**File**: `src/main/java/com/insurancemegacorp/imcchatbot/config/SchedulingConfiguration.java`

Enables Spring's `@Scheduled` annotation support for health monitoring tasks when the MCP profile is active.

## Benefits

### 1. **Automatic Recovery**
The system now automatically detects connection issues and attempts to recover without manual intervention.

### 2. **Continued Operation**
Even when MCP servers are unavailable, the chatbot continues to function with basic OpenAI chat capabilities.

### 3. **Visibility**
Users and administrators can monitor connection health through CLI commands and API endpoints.

### 4. **Proactive Monitoring**
The system proactively checks connection health rather than waiting for failures during actual usage.

### 5. **Fast Status Responses**
Status API endpoint now responds quickly even when MCP connections are unavailable.

### 6. **Environment-Appropriate Defaults**
Local development profile disables MCP by default to prevent unnecessary connection errors.

### 7. **Spring AI Compatibility**
Leverages Spring AI's built-in resilient connection features while adding additional monitoring and recovery capabilities.

## Usage

### Monitor Connection Health (CLI)
```bash
# Run the status command to see detailed health information
status
```

### Monitor Connection Health (API)
```bash
# Get health information
curl http://localhost:8080/api/tools/health

# Manually trigger reconnection
curl -X POST http://localhost:8080/api/tools/reconnect
```

### Automatic Behavior
- Health checks run every 60 seconds automatically
- Recovery attempts are triggered when issues are detected
- Chat service automatically falls back to basic mode when tools are unavailable

## Configuration Options

All features are automatically enabled when the `mcp` profile is active. The health monitoring service will:

- Start health checks 30 seconds after application startup
- Run checks every 60 seconds
- Log status changes (healthy ↔ unhealthy transitions)
- Provide detailed error information when connections fail

## Troubleshooting

### Connection Issues
1. Check the `status` command output for detailed error information
2. Review application logs for connection error details
3. Verify MCP server availability and configuration
4. Use the manual reconnect API endpoint if needed

### Performance
The health monitoring has minimal performance impact:
- Health checks run asynchronously
- Only logs significant status changes
- Uses atomic operations for thread safety
- Gracefully handles failures without affecting main application

## Future Enhancements

Potential improvements for future versions:
- Configurable health check intervals
- Multiple MCP server health tracking
- Connection quality metrics
- Custom recovery strategies per server type
