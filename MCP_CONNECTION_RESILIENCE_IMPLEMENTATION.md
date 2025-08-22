# MCP Connection Resilience Implementation

## Overview

This document describes the enhanced MCP (Model Context Protocol) connection resilience implementation that addresses the timeout and connection stability issues identified in the production logs.

## Problem Analysis

From the production logs, we identified several key issues:

1. **404 Errors**: MCP server endpoints returning 404 errors
2. **30-second Timeouts**: Every operation timing out after 30 seconds
3. **Connection Failures**: Persistent connection issues with SSE connections
4. **Resource Waste**: Maintaining broken connections that don't recover

## Solution Architecture

### 1. **Connection State Management**

**File**: `src/main/java/com/insurancemegacorp/imcchatbot/service/McpConnectionStateManager.java`

The connection state manager implements a state machine approach to track connection lifecycle:

- **DISCONNECTED**: No active connection
- **CONNECTING**: Attempting to establish connection
- **CONNECTED**: Successfully connected and healthy
- **DEGRADED**: Connected but experiencing issues
- **RECONNECTING**: Actively attempting to reconnect

**Key Features**:
- Tracks connection attempts, successes, and failures
- Measures connection duration and performance
- Provides comprehensive connection statistics
- Enables better debugging and monitoring

### 2. **Circuit Breaker Pattern**

**File**: `src/main/java/com/insurancemegacorp/imcchatbot/service/McpConnectionHealthService.java`

Implements circuit breaker pattern to prevent hammering failing servers:

- **Threshold-based**: Opens circuit after N consecutive failures
- **Timeout-based**: Automatically closes circuit after timeout period
- **State tracking**: Monitors failure counts and timing
- **Automatic recovery**: Attempts recovery when conditions improve

**Configuration**:
```properties
mcp.resilience.circuit-breaker.threshold=5
mcp.resilience.circuit-breaker.timeout=2m
```

### 3. **Adaptive Heartbeat Intervals**

**File**: `src/main/java/com/insurancemegacorp/imcchatbot/service/McpConnectionHeartbeatService.java`

Replaces fixed 45-second intervals with adaptive timing:

- **Minimum Interval**: 30 seconds (when healthy)
- **Maximum Interval**: 5 minutes (when failing)
- **Exponential Backoff**: Doubles interval on consecutive failures
- **Dynamic Scheduling**: Uses Spring's dynamic scheduling capabilities

**Configuration**:
```properties
mcp.heartbeat.min-interval=30s
mcp.heartbeat.max-interval=5m
mcp.heartbeat.backoff-multiplier=2
```

### 4. **Dynamic Heartbeat Scheduling**

**File**: `src/main/java/com/insurancemegacorp/imcchatbot/config/HeartbeatIntervalProvider.java`

Provides dynamic heartbeat intervals for Spring's `@Scheduled` annotation:

- **Bean-based**: Integrates with Spring's scheduling framework
- **Runtime Updates**: Allows heartbeat service to adjust timing
- **Fallback Values**: Provides default intervals if service unavailable

## Configuration Updates

### Enhanced MCP Properties

**File**: `src/main/resources/application-mcp.properties`

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

## API Enhancements

### Enhanced Health Endpoint

**Endpoint**: `GET /api/tools/health`

Now includes comprehensive connection information:

```json
{
  "status": "healthy",
  "toolCount": 3,
  "lastSuccessfulCheck": "2025-08-20T14:20:00",
  "heartbeat": {
    "status": "healthy",
    "totalHeartbeats": 45,
    "successRate": "95.6%",
    "currentInterval": 30000,
    "consecutiveFailures": 0
  },
  "connectionState": {
    "state": "connected",
    "connectionAttempts": 1,
    "successRate": "100.0%",
    "lastConnectionTime": "2025-08-20T14:15:00",
    "averageConnectionDuration": "2500ms"
  },
  "circuitBreaker": {
    "open": false,
    "failureCount": 0,
    "timeout": "PT2M"
  }
}
```

## Resilience Benefits

### 1. **Prevents Server Hammering**
- Circuit breaker opens after 5 consecutive failures
- 2-minute timeout prevents indefinite blocking
- Reduces load on failing MCP servers

### 2. **Adaptive Connection Maintenance**
- Heartbeats adapt to connection health
- Exponential backoff reduces unnecessary traffic
- Faster recovery when connections improve

### 3. **Better Error Handling**
- Comprehensive state tracking
- Detailed failure analysis
- Automatic recovery mechanisms

### 4. **Improved Monitoring**
- Real-time connection state visibility
- Performance metrics and statistics
- Early warning of connection issues

## Implementation Details

### State Transitions

```
DISCONNECTED → CONNECTING → CONNECTED
     ↑            ↓           ↓
     ← RECONNECTING ← DEGRADED
```

### Circuit Breaker States

```
CLOSED → OPEN → HALF_OPEN → CLOSED
  ↓       ↓        ↓         ↓
Healthy  Failing  Testing   Recovered
```

### Heartbeat Interval Calculation

```
Success: interval = MIN_INTERVAL (30s)
Failure: interval = min(current * 2, MAX_INTERVAL)
```

## Usage Examples

### Monitor Connection Health

```bash
# Check overall health including resilience metrics
curl http://localhost:8080/api/tools/health

# View detailed connection state
curl http://localhost:8080/api/tools/health | jq '.connectionState'

# Check circuit breaker status
curl http://localhost:8080/api/tools/health | jq '.circuitBreaker'
```

### CLI Commands

```bash
# Check connection status
status

# View heartbeat information
heartbeat

# Send immediate heartbeat
send-heartbeat
```

## Monitoring and Alerting

### Key Metrics to Monitor

1. **Circuit Breaker Status**: Open/closed state
2. **Connection State**: Current connection lifecycle
3. **Heartbeat Success Rate**: Connection health indicator
4. **Failure Counts**: Trend analysis for proactive maintenance
5. **Connection Duration**: Performance monitoring

### Alerting Recommendations

- **Circuit Breaker Open**: Immediate attention required
- **High Failure Rate**: Investigate connection stability
- **Long Connection Duration**: Check network performance
- **Frequent State Changes**: Monitor for connection instability

## Testing and Validation

### Test Scenarios

1. **Normal Operation**: Verify healthy connections work normally
2. **Connection Failure**: Test circuit breaker opening
3. **Recovery**: Verify automatic recovery mechanisms
4. **Adaptive Timing**: Test heartbeat interval adjustments
5. **State Transitions**: Verify all state changes work correctly

### Performance Impact

- **Minimal Overhead**: State tracking uses atomic operations
- **Efficient Scheduling**: Dynamic scheduling adapts to conditions
- **Resource Management**: Circuit breaker prevents resource waste

## Future Enhancements

### Potential Improvements

1. **Multiple MCP Server Support**: Extend to multiple connections
2. **Custom Recovery Strategies**: Per-server recovery policies
3. **Metrics Export**: Prometheus/Grafana integration
4. **Configuration Hot-reload**: Runtime configuration updates
5. **Advanced Backoff Strategies**: More sophisticated retry logic

### Monitoring Integration

1. **Health Check Endpoints**: Kubernetes/Cloud Foundry integration
2. **Metrics Collection**: Application performance monitoring
3. **Log Aggregation**: Centralized logging and analysis
4. **Alerting Rules**: Automated incident response

## Conclusion

The enhanced MCP connection resilience implementation provides:

- **Robust Connection Management**: State machine approach for lifecycle tracking
- **Intelligent Failure Handling**: Circuit breaker pattern prevents server hammering
- **Adaptive Maintenance**: Dynamic heartbeat intervals based on connection health
- **Comprehensive Monitoring**: Detailed metrics and state information
- **Automatic Recovery**: Self-healing connections with minimal intervention

This implementation addresses the production timeout issues while maintaining the performance benefits of long-lived SSE connections. The system now gracefully handles connection failures, prevents resource waste, and provides better visibility into connection health.
