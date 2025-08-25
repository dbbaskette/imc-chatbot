# MCP Connection Stability Fixes - Development Plan

**Project:** imc-chatbot MCP connection resilience improvements  
**Problem:** imc-chatbot loses ability to connect to policy-mcp-server after running for extended periods  
**Impact:** HIGH - affects web interface users who depend on chatbot's MCP tool access for policy information  
**Branch:** `feature/mcp-connection-resilience`

## Root Cause Analysis
The connection loss stems from:
- Missing forced connection refresh mechanism in Spring AI MCP client
- Basic WebClient configuration without connection pooling/lifecycle management  
- No proactive connection validation before tool usage
- Insufficient error classification (all errors treated equally)
- Potential resource leaks in long-running connections

**Expected Outcome:** 95% reduction in connection loss incidents with <30 second recovery times

---

<!-- devplan:start -->
## Phase: Critical Connection Management
- [ ] Create McpConnectionRefreshService to force complete MCP client reconnection
- [ ] Implement fallback mechanism using application context refresh of MCP beans
- [ ] Add reflection-based Spring AI MCP client reconnection logic
- [ ] Enhance WebClientConfig with Reactor Netty connection pooling (max 10 connections)
- [ ] Configure connection timeouts: 30s connect, 2min response, 5min max idle
- [ ] Enable keep-alive, compression, and background connection eviction (2min intervals)
- [ ] Add proactive connection validation in ChatService with 5-second timeout
- [ ] Implement non-blocking async validation before MCP tool usage
- [ ] Create integration tests for connection refresh under load
- [ ] Run 24-hour stability test to validate Phase 1 fixes
- [ ] Test network interruption recovery scenarios
- [ ] Verify all existing chat functionality preserved

## Phase: Advanced Resilience Features  
- [ ] Create McpConnectionRotationService for proactive connection rotation every 30min
- [ ] Make rotation intervals configurable via application properties
- [ ] Integrate rotation service with connection refresh service
- [ ] Implement error classification enum (NETWORK_TIMEOUT, CONNECTION_REFUSED, SSL_HANDSHAKE, READ_TIMEOUT, UNKNOWN)
- [ ] Add intelligent retry strategies based on error type classification
- [ ] Enhance McpConnectionHealthService with error-specific handling logic
- [ ] Improve logging with detailed error context and classification
- [ ] Add rotation and error handling configuration properties to application-mcp.properties
- [ ] Implement error-specific retry configurations with different backoff strategies
- [ ] Create environment-specific configuration defaults
- [ ] Add configuration validation for new properties
- [ ] Test error classification accuracy (target >95%)
- [ ] Validate different retry strategies work correctly per error type

## Phase: Production Optimizations
- [ ] Optimize connection timeouts for Cloud Foundry (45s connect/read timeouts)
- [ ] Configure aggressive heartbeat intervals (15s min, 3min max) for CF environment
- [ ] Add CF-specific retry strategies and load balancer compatibility settings
- [ ] Update manifest.yml with CF-optimized environment variables
- [ ] Create McpMetricsConfiguration for comprehensive metrics collection
- [ ] Implement McpConnectionEvent DTO for structured event tracking
- [ ] Add connection success/failure rate metrics with Micrometer
- [ ] Track recovery time measurements and error classification metrics
- [ ] Monitor heartbeat success rates and connection quality scores
- [ ] Export metrics in dashboard-ready format (Prometheus/Grafana compatible)
- [ ] Create McpConnectionLoadTest for high-load testing (100+ concurrent requests)
- [ ] Run extended stability testing (7+ days continuous operation)
- [ ] Validate Cloud Foundry deployment with all optimizations
- [ ] Establish performance baselines for monitoring and alerting
- [ ] Create comprehensive deployment documentation
- [ ] Set up automated monitoring alerts for connection health
<!-- devplan:end -->

---

## Success Criteria by Phase

### Phase 1: Critical Connection Management
- ✅ 95% reduction in connection loss incidents  
- ✅ Recovery time < 30 seconds from connection failures
- ✅ All existing chat functionality preserved
- ✅ Integration tests passing with 24-hour stability

### Phase 2: Advanced Resilience Features  
- ✅ 99% uptime for MCP tool availability
- ✅ Intelligent retry strategies operational
- ✅ Error classification accuracy > 95%
- ✅ Proactive connection management preventing issues

### Phase 3: Production Optimizations
- ✅ Cloud Foundry deployment optimized and stable
- ✅ Comprehensive monitoring and alerting active  
- ✅ Performance baselines established
- ✅ 7-day continuous stability test passed

## Risk Mitigation

**Spring AI Integration Complexity (Medium Risk)**
- Mitigation: Incremental implementation with thorough testing at each step
- Fallback: Use application context refresh if direct Spring AI integration fails

**Resource Usage Increase (Low Risk)**  
- Mitigation: Configurable intervals and performance monitoring
- Target: Keep resource increase < 10% of baseline

**Configuration Complexity (Medium Risk)**
- Mitigation: Profile-based defaults and comprehensive documentation
- Provide environment-specific configuration templates

## Files to be Modified/Created

### New Files
- `src/main/java/com/insurancemegacorp/imcchatbot/service/McpConnectionRefreshService.java`
- `src/main/java/com/insurancemegacorp/imcchatbot/service/McpConnectionRotationService.java` 
- `src/main/java/com/insurancemegacorp/imcchatbot/config/McpMetricsConfiguration.java`
- `src/main/java/com/insurancemegacorp/imcchatbot/dto/McpConnectionEvent.java`
- `src/test/java/com/insurancemegacorp/imcchatbot/integration/McpConnectionIntegrationTest.java`
- `src/test/java/com/insurancemegacorp/imcchatbot/performance/McpConnectionLoadTest.java`

### Modified Files
- `src/main/java/com/insurancemegacorp/imcchatbot/config/WebClientConfig.java`
- `src/main/java/com/insurancemegacorp/imcchatbot/service/ChatService.java`
- `src/main/java/com/insurancemegacorp/imcchatbot/service/McpConnectionHealthService.java`
- `src/main/resources/application-mcp.properties`
- `manifest.yml`

## Testing Strategy

### Unit Tests (Target: 80% coverage)
- Connection refresh logic
- Error classification accuracy  
- Proactive validation mechanisms

### Integration Tests
- 24-hour stability testing
- Network interruption recovery
- High-load concurrent requests
- Cloud Foundry deployment validation

### Monitoring Validation
- Connection success rate > 99%
- Recovery time < 30 seconds  
- Resource usage increase < 10%
- Error classification accuracy > 95%