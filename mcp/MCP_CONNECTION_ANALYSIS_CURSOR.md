# MCP Connection Loss Issue - Cursor Analysis

This document contains my analysis of the MCP connection issues between imc-chatbot and policy-mcp-server, based on code review without referencing the existing fixes plan.

## Problem Analysis

After examining the codebase, I've identified several potential causes for the MCP connection loss over time:

### 1. **SSE Connection Timeout Issues**
The chatbot uses Server-Sent Events (SSE) to connect to the policy-mcp-server. SSE connections are inherently long-lived and can be terminated by:
- Network infrastructure (load balancers, proxies)
- Cloud platform timeouts (Cloud Foundry has default connection timeouts)
- Browser/HTTP client connection limits

**Evidence in code:**
- `application-mcp.properties` shows SSE endpoint configuration
- Connection uses HTTPS to `imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com`
- No explicit connection keep-alive mechanisms visible in the core configuration

### 2. **Insufficient Connection Monitoring**
While there are health check and heartbeat services, they may not be aggressive enough:

**Current monitoring:**
- Health checks every 60 seconds (`@Scheduled(fixedRate = 60000)`)
- Heartbeats every 30 seconds (`@Scheduled(fixedDelay = 30000)`)
- Circuit breaker timeout of 2 minutes

**Issues identified:**
- 60-second health check interval may be too long for production environments
- No proactive connection validation before tool usage
- Health checks only run when explicitly triggered

### 3. **Resource Cleanup and Connection Pooling**
The code shows sophisticated connection state management but may have gaps:

**Potential issues:**
- No explicit connection pool management
- Spring AI MCP client may be holding onto stale connections
- No forced reconnection when connections become idle

### 4. **Cloud Platform Specific Issues**
The deployment appears to be on Cloud Foundry, which has specific characteristics:

**Cloud Foundry considerations:**
- Default connection timeouts (typically 60-300 seconds)
- Load balancer connection limits
- Container lifecycle management affecting long-running connections

### 5. **SSL/TLS Connection Handling**
The application disables SSL validation for development, but this could mask connection issues:

**SSL configuration concerns:**
- Global SSL bypass may interfere with connection health detection
- No certificate validation means connection failures may not be properly detected
- SSL handshake timeouts could cause silent connection drops

## Root Cause Hypotheses

### **Primary Hypothesis: SSE Connection Timeout**
The most likely cause is that the SSE connection to the policy-mcp-server is being terminated by network infrastructure after a period of inactivity, and the current health check mechanisms are not detecting this quickly enough.

### **Secondary Hypothesis: Resource Exhaustion**
Long-running connections may be consuming resources (file descriptors, memory) that eventually cause the connection to fail, especially in containerized environments.

### **Tertiary Hypothesis: Spring AI MCP Client Limitations**
The Spring AI MCP client may have internal connection management that doesn't properly handle long-lived connections or network interruptions.

## Recommended Solutions

### 1. **Enhanced Connection Keep-Alive**
- Implement more aggressive heartbeat intervals (15-20 seconds)
- Add explicit connection validation before each tool call
- Implement connection pooling with rotation

### 2. **Improved Health Monitoring**
- Reduce health check interval to 30 seconds
- Add connection quality metrics (latency, response time)
- Implement proactive connection testing

### 3. **Connection Recovery Mechanisms**
- Force reconnection after idle periods
- Implement connection rotation to prevent staleness
- Add retry logic with exponential backoff

### 4. **Cloud Platform Optimization**
- Configure appropriate connection timeouts for Cloud Foundry
- Implement graceful connection shutdown
- Add connection metrics for monitoring

### 5. **SSL and Network Configuration**
- Review SSL configuration for production
- Add network-level connection monitoring
- Implement connection quality scoring

## Code Areas Requiring Attention

1. **McpConnectionHeartbeatService** - Increase heartbeat frequency and add connection quality metrics
2. **McpConnectionHealthService** - Reduce health check intervals and add proactive testing
3. **McpConnectionStateManager** - Implement connection rotation and forced reconnection
4. **ChatService** - Add connection validation before tool usage
5. **Application configuration** - Optimize timeouts and connection settings for cloud deployment

## Monitoring and Debugging

To properly diagnose the issue, I recommend:
1. Add detailed connection logging
2. Monitor connection lifecycle events
3. Track connection quality metrics
4. Implement alerting for connection failures
5. Add connection state visualization in the web interface

This analysis is based on code review and common patterns in distributed systems, particularly those using SSE connections in cloud environments.
