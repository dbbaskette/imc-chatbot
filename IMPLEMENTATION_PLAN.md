# IMC Chatbot Implementation Plan

## 📋 Project Overview

**Project:** Insurance MegaCorp AI Chatbot with MCP Tool Integration  
**Current State:** Rebranded MCP testing tool with SSE transport  
**Target State:** Multi-access AI chatbot (CLI, Web UI, REST API) with transparent MCP tool calling

**Technical Stack:**
- Java 21, Spring Boot 3.3.2, Spring AI 1.0.0
- OpenAI ChatClient for LLM integration
- MCP servers: imc-policy-mcp-server, imc-accident-mcp-server
- Deployment: Local development + Cloud Foundry

---

## 🎯 Development Phases

### **Phase 1: Foundation & Core LLM Integration** ⚡
**Branch:** `phase-1-foundation`  
**Duration:** 2-3 days  
**Risk:** Low  
**Dependencies:** OpenAI API access

#### **Step 1.1: Dependencies & Configuration**
**Estimated Time:** 4 hours

**Tasks:**
- [ ] **Add Spring AI OpenAI dependency**
  - Add `spring-ai-openai-spring-boot-starter` to pom.xml
  - Version should align with Spring AI BOM (1.0.0)
  
- [ ] **Create application-local.properties**
  ```properties
  # OpenAI Configuration
  spring.ai.openai.api-key=${OPENAI_API_KEY}
  spring.ai.openai.chat.model=gpt-4
  spring.ai.openai.chat.temperature=0.7
  spring.ai.openai.chat.max-tokens=2000
  
  # MCP Configuration (existing SSE connections)
  spring.ai.mcp.client.sse.connections.imc-policy.url=http://localhost:8080/policy
  spring.ai.mcp.client.sse.connections.imc-policy.sse-endpoint=/sse
  spring.ai.mcp.client.sse.connections.imc-accident.url=http://localhost:8081/accident  
  spring.ai.mcp.client.sse.connections.imc-accident.sse-endpoint=/sse
  
  # Application Settings
  spring.application.name=imc-chatbot
  spring.main.web-application-type=none
  logging.level.com.insurancemegacorp.imcchatbot=DEBUG
  ```

- [ ] **Create application-cloud.properties**
  ```properties
  # Cloud Foundry - OpenAI via service binding
  # No API key needed - bound service "chat-model"
  spring.ai.openai.chat.model=gpt-4
  spring.ai.openai.chat.temperature=0.7
  spring.ai.openai.chat.max-tokens=2000
  
  # MCP Configuration (cloud endpoints)
  spring.ai.mcp.client.sse.connections.imc-policy.url=${POLICY_MCP_URL:http://localhost:8080/policy}
  spring.ai.mcp.client.sse.connections.imc-policy.sse-endpoint=/sse
  spring.ai.mcp.client.sse.connections.imc-accident.url=${ACCIDENT_MCP_URL:http://localhost:8081/accident}
  spring.ai.mcp.client.sse.connections.imc-accident.sse-endpoint=/sse
  
  logging.level.com.insurancemegacorp.imcchatbot=INFO
  ```

- [ ] **Add environment variable validation**
  - Create `@ConfigurationProperties` class for validation
  - Add startup checks for required environment variables
  - Graceful failure with helpful error messages

**Acceptance Criteria:**
- ✅ Application starts with `local` profile and validates OPENAI_API_KEY
- ✅ Application starts with `cloud` profile (no API key validation)
- ✅ Clear error messages for missing configuration
- ✅ MCP connections configured but not yet integrated

#### **Step 1.2: Core Chat Service**
**Estimated Time:** 6 hours

**Tasks:**
- [ ] **Create ChatService class**
  ```java
  @Service
  public class ChatService {
      private final ChatClient chatClient;
      private final Map<String, List<Message>> conversationHistory;
      
      public ChatService(ChatClient.Builder chatClientBuilder) {
          this.chatClient = chatClientBuilder.build();
          this.conversationHistory = new ConcurrentHashMap<>();
      }
      
      public String chat(String sessionId, String userMessage) {
          // Implementation details below
      }
      
      public void clearSession(String sessionId) {
          // Clear conversation history
      }
  }
  ```

- [ ] **Implement conversation management**
  - Maintain conversation history per session
  - Use Spring AI Message API (UserMessage, AssistantMessage)
  - Implement conversation context window management
  - Handle conversation history truncation (token limits)

- [ ] **Add comprehensive error handling**
  - OpenAI API failures (rate limits, network issues)
  - Invalid responses or empty responses
  - Token limit exceeded scenarios
  - Graceful degradation strategies

- [ ] **Implement logging and debugging**
  - Log all user inputs (sanitized)
  - Log all AI responses
  - Log token usage and costs
  - Performance metrics (response time)

- [ ] **Create ChatService unit tests**
  - Mock ChatClient for testing
  - Test conversation history management
  - Test error scenarios
  - Test session management

**Technical Implementation Details:**
```java
public String chat(String sessionId, String userMessage) {
    try {
        // Get or create conversation history
        List<Message> history = conversationHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());
        
        // Add user message to history
        UserMessage userMsg = new UserMessage(userMessage);
        history.add(userMsg);
        
        // Build prompt with context
        Prompt prompt = new Prompt(history);
        
        // Call OpenAI
        ChatResponse response = chatClient.call(prompt);
        
        // Extract response and add to history
        String assistantReply = response.getResult().getOutput().getContent();
        history.add(new AssistantMessage(assistantReply));
        
        // Manage conversation window size
        manageConversationHistory(history);
        
        return assistantReply;
    } catch (Exception e) {
        log.error("Chat error for session {}: {}", sessionId, e.getMessage(), e);
        return handleChatError(e);
    }
}
```

**Acceptance Criteria:**
- ✅ ChatService successfully integrates with OpenAI ChatClient
- ✅ Conversation history maintained per session
- ✅ Proper error handling with user-friendly messages
- ✅ Unit tests achieve >80% code coverage

#### **Step 1.3: Enhanced CLI Chat Mode**
**Estimated Time:** 6 hours

**Tasks:**
- [ ] **Add chat command to CliRunner**
  - Extend `processCommand()` method with `chat` case
  - Create `handleChat()` method
  - Implement chat loop with exit conditions

- [ ] **Implement interactive chat loop**
  ```java
  private void handleChat() {
      System.out.println("\n🤖 === IMC Chatbot - Chat Mode === 🤖");
      System.out.println("Type 'exit-chat' to return to tool mode, 'help-chat' for commands");
      System.out.println("Connected to: OpenAI GPT-4");
      
      String sessionId = UUID.randomUUID().toString();
      boolean chatActive = true;
      
      while (chatActive && running) {
          System.out.print("You: ");
          String input = scanner.nextLine().trim();
          
          if (input.equalsIgnoreCase("exit-chat")) {
              chatActive = false;
              continue;
          }
          
          if (input.equalsIgnoreCase("help-chat")) {
              showChatHelp();
              continue;
          }
          
          if (input.isEmpty()) continue;
          
          // Show typing indicator
          System.out.print("IMC Assistant is thinking");
          CompletableFuture<String> chatFuture = CompletableFuture.supplyAsync(() -> 
              chatService.chat(sessionId, input));
              
          // Animate typing indicator
          animateTypingIndicator(chatFuture);
          
          try {
              String response = chatFuture.get();
              System.out.println("IMC Assistant: " + response);
          } catch (Exception e) {
              System.err.println("❌ Chat error: " + e.getMessage());
          }
      }
      
      // Clean up session
      chatService.clearSession(sessionId);
      System.out.println("Exited chat mode. Back to tool testing mode.");
  }
  ```

- [ ] **Add chat-specific help and commands**
  - `help-chat` - show chat-specific commands
  - `clear-history` - clear conversation history
  - `show-session` - display session information
  - `exit-chat` - return to tool mode

- [ ] **Enhance user experience**
  - Typing indicators during AI response
  - Better formatting for responses
  - Color-coded messages (user vs assistant)
  - Response time indicators

- [ ] **Update main help and welcome messages**
  - Update `printWelcome()` to mention chat mode
  - Add chat command to help text
  - Update application description

**Acceptance Criteria:**
- ✅ `chat` command launches interactive chat mode
- ✅ Users can have natural conversations with OpenAI
- ✅ Chat maintains context within session
- ✅ `exit-chat` returns to tool testing mode
- ✅ Chat-specific help commands work
- ✅ Typing indicators and UX enhancements functional

#### **Step 1.4: Basic Integration Testing**
**Estimated Time:** 4 hours

**Tasks:**
- [ ] **Test local profile with OpenAI**
  - Set OPENAI_API_KEY environment variable
  - Run `./imc-chatbot.sh --profile local`
  - Verify chat functionality end-to-end
  - Test various conversation scenarios

- [ ] **Test error scenarios**
  - Missing OPENAI_API_KEY
  - Invalid API key
  - Network connectivity issues
  - Rate limiting scenarios
  - Very long conversations (token limits)

- [ ] **Create integration test suite**
  - Test chat service integration
  - Test CLI chat mode functionality
  - Test error handling pathways
  - Performance benchmarking

- [ ] **Documentation updates**
  - Update CLAUDE.md with Phase 1 features
  - Update README with chat usage examples
  - Create troubleshooting guide for common issues

**Acceptance Criteria:**
- ✅ Full end-to-end chat functionality works with OpenAI
- ✅ All error scenarios handled gracefully
- ✅ Integration tests pass
- ✅ Documentation updated and accurate

---

### **Phase 2: MCP Tool Integration & Enhanced CLI** 🔧
**Branch:** `phase-2-mcp-tools`  
**Duration:** 3-4 days  
**Risk:** Medium  
**Dependencies:** Phase 1 complete, MCP servers running

#### **Step 2.1: MCP Tool Discovery & Registration**
**Estimated Time:** 8 hours

**Tasks:**
- [ ] **Create ToolRegistrationService**
  ```java
  @Service
  public class ToolRegistrationService {
      private final SyncMcpToolCallbackProvider mcpToolProvider;
      private final List<ToolCallback> availableTools;
      
      @PostConstruct
      public void discoverAndRegisterTools() {
          // Discover MCP tools
          // Register with ChatClient
          // Handle connection failures
      }
      
      public List<ToolDefinition> getAvailableTools() {
          // Return list of available tools
      }
      
      public boolean isToolAvailable(String toolName) {
          // Check if specific tool is available
      }
  }
  ```

- [ ] **Integrate MCP tools with ChatClient**
  - Configure ChatClient with MCP tool callbacks
  - Use Spring AI Advisors for tool calling
  - Implement `McpToolAdvisor` extending `AbstractChatMemoryAdvisor`

- [ ] **Configure imc-policy-mcp-server connection**
  - Update application properties with correct endpoints
  - Add authentication if required
  - Test connection and tool discovery
  - Handle connection failures gracefully

- [ ] **Configure imc-accident-mcp-server connection**
  - Update application properties with correct endpoints
  - Add authentication if required
  - Test connection and tool discovery
  - Handle connection failures gracefully

- [ ] **Implement tool discovery health checks**
  - Monitor MCP server connectivity
  - Automatic reconnection on failures
  - Tool availability status reporting

**Technical Implementation:**
```java
@Configuration
public class ChatClientConfiguration {
    
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, 
                                ToolRegistrationService toolService) {
        return builder
            .defaultSystem("You are IMC Assistant, an AI helper for Insurance MegaCorp. " +
                          "You help with insurance policies and accident claims. " +
                          "Use available tools to provide accurate, helpful information.")
            .defaultAdvisors(
                new McpToolCallingAdvisor(toolService.getAvailableTools())
            )
            .build();
    }
}
```

**Acceptance Criteria:**
- ✅ MCP tools automatically discovered and registered with ChatClient
- ✅ Both imc-policy and imc-accident servers connected
- ✅ Tool discovery health checks functional
- ✅ Connection failures handled gracefully

#### **Step 2.2: Transparent Tool Calling**
**Estimated Time:** 10 hours

**Tasks:**
- [ ] **Implement automatic tool invocation**
  - Configure ChatClient to automatically call MCP tools when appropriate
  - No explicit tool commands needed from user
  - Tools called based on conversation context

- [ ] **Add tool execution logging**
  ```java
  @Component
  public class ToolExecutionLogger {
      public void logToolInvocation(String toolName, String parameters, String sessionId) {
          log.info("Tool invoked: {} with parameters: {} for session: {}", 
                   toolName, parameters, sessionId);
      }
      
      public void logToolResult(String toolName, String result, long executionTime, String sessionId) {
          log.info("Tool {} completed in {}ms for session: {}, result length: {}", 
                   toolName, executionTime, sessionId, result.length());
      }
      
      public void logToolError(String toolName, String error, String sessionId) {
          log.error("Tool {} failed for session: {}, error: {}", 
                    toolName, sessionId, error);
      }
  }
  ```

- [ ] **Handle tool execution errors gracefully**
  - Network timeouts to MCP servers
  - Invalid tool parameters
  - Tool execution failures
  - Partial tool results

- [ ] **Implement tool result integration**
  - Seamlessly integrate tool results into AI responses
  - Format tool results for natural conversation
  - Handle multiple tool calls in single conversation turn

- [ ] **Test with both MCP servers**
  - Test policy-related queries (imc-policy-mcp-server)
  - Test accident-related queries (imc-accident-mcp-server)
  - Test mixed queries requiring both servers
  - Test edge cases and error scenarios

**Example Conversation Flow:**
```
User: "What's the coverage limit for auto insurance policy P123456?"

[Transparent tool calling happens:]
1. ChatClient determines policy lookup needed
2. Calls imc-policy-mcp-server tool: getPolicyDetails(policyNumber="P123456")
3. Receives tool result with policy details
4. Integrates result into natural response

IMC Assistant: "Policy P123456 is an auto insurance policy with a coverage limit of $500,000. The policy includes comprehensive and collision coverage with a $500 deductible. Would you like more details about this policy?"
```

**Acceptance Criteria:**
- ✅ Tools automatically invoked based on conversation context
- ✅ Tool execution is transparent to users (no explicit commands needed)
- ✅ Tool results seamlessly integrated into AI responses
- ✅ Both MCP servers functional with comprehensive testing
- ✅ Error handling prevents chat disruption

#### **Step 2.3: CLI Enhancements**
**Estimated Time:** 6 hours

**Tasks:**
- [ ] **Add tool usage indicators in chat mode**
  - Show when tools are being invoked
  - Display which tools are being used
  - Show tool execution progress

- [ ] **Enhance tool testing commands**
  - Improve `list-tools` to show MCP server source
  - Add `test-connection` command for MCP servers
  - Add `tool-status` command showing tool availability

- [ ] **Add debug commands in chat mode**
  - `show-tools` - display available tools in current session
  - `debug-mode on/off` - toggle verbose tool execution logging
  - `connection-status` - show MCP server connection status

- [ ] **Enhanced status reporting**
  ```java
  private void handleStatus() {
      System.out.println("=== IMC Chatbot Status ===");
      System.out.println("OpenAI Integration: " + (chatService.isHealthy() ? "✅ Connected" : "❌ Failed"));
      
      // MCP Server Status
      System.out.println("\nMCP Servers:");
      toolRegistrationService.getServerStatus().forEach((server, status) -> {
          System.out.printf("  %s: %s (%d tools available)%n", 
                           server, status.isConnected() ? "✅ Connected" : "❌ Disconnected", 
                           status.getToolCount());
      });
      
      // Tool Summary
      System.out.printf("%nTotal Tools Available: %d%n", toolRegistrationService.getTotalToolCount());
      System.out.printf("Active Chat Sessions: %d%n", chatService.getActiveSessionCount());
  }
  ```

**Acceptance Criteria:**
- ✅ Users can see when tools are being invoked during chat
- ✅ Enhanced debugging commands available
- ✅ Comprehensive status reporting functional
- ✅ Tool testing commands improved

#### **Step 2.4: Configuration Management**
**Estimated Time:** 4 hours

**Tasks:**
- [ ] **Update application-local.properties for MCP servers**
  - Configure correct endpoints for local development
  - Add authentication tokens if needed
  - Configure connection timeouts and retry policies

- [ ] **Update application-cloud.properties for production**
  - Use environment variables for MCP server URLs
  - Configure for production MCP server endpoints
  - Add production-specific timeouts and resilience settings

- [ ] **Add connection health checks**
  - Periodic health check pings to MCP servers
  - Automatic retry on connection failures
  - Circuit breaker pattern for failed servers

- [ ] **Environment variable documentation**
  - Document all required environment variables
  - Provide example configurations
  - Add validation and error messages

**Example Configuration:**
```properties
# application-local.properties
spring.ai.mcp.client.sse.connections.imc-policy.url=http://localhost:8080/policy
spring.ai.mcp.client.sse.connections.imc-policy.sse-endpoint=/sse
spring.ai.mcp.client.sse.connections.imc-policy.headers.Authorization=Bearer ${POLICY_MCP_TOKEN:}
spring.ai.mcp.client.sse.connections.imc-policy.timeout=30s
spring.ai.mcp.client.sse.connections.imc-policy.retry-attempts=3

spring.ai.mcp.client.sse.connections.imc-accident.url=http://localhost:8081/accident
spring.ai.mcp.client.sse.connections.imc-accident.sse-endpoint=/sse
spring.ai.mcp.client.sse.connections.imc-accident.headers.Authorization=Bearer ${ACCIDENT_MCP_TOKEN:}
spring.ai.mcp.client.sse.connections.imc-accident.timeout=30s
spring.ai.mcp.client.sse.connections.imc-accident.retry-attempts=3
```

**Acceptance Criteria:**
- ✅ All MCP server connections properly configured
- ✅ Health checks and resilience patterns implemented
- ✅ Environment variables documented
- ✅ Configuration works in both local and cloud profiles

---

### **Phase 3: REST API & Web Infrastructure** 🌐
**Branch:** `phase-3-rest-api`  
**Duration:** 4-5 days  
**Risk:** Medium  
**Dependencies:** Phase 2 complete

#### **Step 3.1: Web Layer Setup**
**Estimated Time:** 6 hours

**Tasks:**
- [ ] **Configure Spring WebMVC**
  - Change `spring.main.web-application-type=servlet` in properties
  - Add `spring-boot-starter-web` dependency
  - Configure embedded Tomcat settings
  - Add web security basic configuration

- [ ] **Add web starter dependencies to pom.xml**
  ```xml
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
  </dependency>
  ```

- [ ] **Create ChatController for REST endpoints**
  ```java
  @RestController
  @RequestMapping("/api")
  @CrossOrigin(origins = "*") // Configure appropriately for production
  @Validated
  public class ChatController {
      
      private final ChatService chatService;
      
      @PostMapping("/chat")
      public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
          // Implementation
      }
      
      @GetMapping("/chat/stream/{conversationId}")
      public SseEmitter streamChat(@PathVariable String conversationId) {
          // Implementation
      }
  }
  ```

- [ ] **Add CORS configuration**
  ```java
  @Configuration
  public class WebConfig implements WebMvcConfigurer {
      
      @Override
      public void addCorsMappings(CorsRegistry registry) {
          registry.addMapping("/api/**")
                  .allowedOrigins("*") // Configure appropriately
                  .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                  .allowedHeaders("*")
                  .allowCredentials(false);
      }
  }
  ```

**Acceptance Criteria:**
- ✅ Web application starts successfully with embedded Tomcat
- ✅ REST endpoints accessible
- ✅ CORS configured for cross-origin requests
- ✅ Basic web security configured

#### **Step 3.2: Chat REST Endpoints**
**Estimated Time:** 10 hours

**Tasks:**
- [ ] **Create request/response DTOs**
  ```java
  public class ChatRequest {
      @NotBlank(message = "Message cannot be empty")
      @Size(max = 4000, message = "Message too long")
      private String message;
      
      private String conversationId; // Optional
      private Map<String, Object> context; // Optional
      
      // getters, setters, validation
  }
  
  public class ChatResponse {
      private String response;
      private String conversationId;
      private List<String> toolsUsed;
      private long responseTimeMs;
      private String timestamp;
      
      // getters, setters, builders
  }
  ```

- [ ] **Implement POST /api/chat endpoint**
  ```java
  @PostMapping("/chat")
  public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
      try {
          long startTime = System.currentTimeMillis();
          
          // Generate conversation ID if not provided
          String conversationId = request.getConversationId() != null 
              ? request.getConversationId() 
              : UUID.randomUUID().toString();
          
          // Process chat request
          String response = chatService.chat(conversationId, request.getMessage());
          
          // Build response
          ChatResponse chatResponse = ChatResponse.builder()
              .response(response)
              .conversationId(conversationId)
              .toolsUsed(chatService.getLastToolsUsed(conversationId))
              .responseTimeMs(System.currentTimeMillis() - startTime)
              .timestamp(Instant.now().toString())
              .build();
          
          return ResponseEntity.ok(chatResponse);
          
      } catch (Exception e) {
          log.error("Chat API error: {}", e.getMessage(), e);
          return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body(ChatResponse.error("Chat service temporarily unavailable"));
      }
  }
  ```

- [ ] **Add conversation management**
  - Extend ChatService to track tool usage per conversation
  - Add conversation metadata (creation time, message count, etc.)
  - Implement conversation cleanup for inactive sessions

- [ ] **Add comprehensive validation and error handling**
  - Request validation with detailed error messages
  - Rate limiting per IP/session
  - Input sanitization and security checks
  - Proper HTTP status codes for different error types

**Acceptance Criteria:**
- ✅ POST /api/chat accepts valid chat requests
- ✅ Returns structured JSON responses with conversation ID
- ✅ Tracks and reports tools used in responses
- ✅ Comprehensive error handling with appropriate HTTP status codes

#### **Step 3.3: SSE Streaming Support**
**Estimated Time:** 12 hours

**Tasks:**
- [ ] **Implement SSE streaming endpoint**
  ```java
  @GetMapping("/chat/stream/{conversationId}")
  public SseEmitter streamChat(@PathVariable String conversationId,
                              @RequestParam String message) {
      SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
      
      CompletableFuture.runAsync(() -> {
          try {
              // Stream chat response with events
              chatService.chatWithStreaming(conversationId, message, new StreamingChatCallback() {
                  @Override
                  public void onPartialResponse(String partial) {
                      try {
                          emitter.send(SseEmitter.event()
                              .name("message")
                              .data(partial));
                      } catch (IOException e) {
                          emitter.completeWithError(e);
                      }
                  }
                  
                  @Override
                  public void onToolInvocation(String toolName, String parameters) {
                      try {
                          emitter.send(SseEmitter.event()
                              .name("tool_start")
                              .data(Map.of("tool", toolName, "parameters", parameters)));
                      } catch (IOException e) {
                          emitter.completeWithError(e);
                      }
                  }
                  
                  @Override
                  public void onComplete(String fullResponse) {
                      try {
                          emitter.send(SseEmitter.event()
                              .name("complete")
                              .data(Map.of("response", fullResponse, "conversationId", conversationId)));
                          emitter.complete();
                      } catch (IOException e) {
                          emitter.completeWithError(e);
                      }
                  }
              });
              
          } catch (Exception e) {
              emitter.completeWithError(e);
          }
      });
      
      return emitter;
  }
  ```

- [ ] **Extend ChatService for streaming**
  - Add streaming interface to ChatService
  - Implement partial response callbacks
  - Handle streaming errors and connection drops

- [ ] **Handle connection management**
  - Track active SSE connections
  - Implement connection timeout handling
  - Clean up resources on disconnect
  - Handle client reconnection scenarios

- [ ] **Create JavaScript client example**
  ```javascript
  function connectToChat(conversationId, message) {
      const eventSource = new EventSource(
          `/api/chat/stream/${conversationId}?message=${encodeURIComponent(message)}`
      );
      
      eventSource.addEventListener('message', function(event) {
          const data = JSON.parse(event.data);
          appendPartialMessage(data);
      });
      
      eventSource.addEventListener('tool_start', function(event) {
          const data = JSON.parse(event.data);
          showToolIndicator(data.tool);
      });
      
      eventSource.addEventListener('complete', function(event) {
          const data = JSON.parse(event.data);
          finalizeMessage(data.response);
          eventSource.close();
      });
      
      eventSource.onerror = function(event) {
          console.error('SSE connection error:', event);
          eventSource.close();
      };
  }
  ```

**Acceptance Criteria:**
- ✅ SSE streaming endpoint functional
- ✅ Streams partial responses in real-time
- ✅ Reports tool usage during streaming
- ✅ Connection management handles errors gracefully
- ✅ JavaScript client example works

#### **Step 3.4: Administrative Endpoints**
**Estimated Time:** 6 hours

**Tasks:**
- [ ] **Implement GET /api/tools endpoint**
  ```java
  @GetMapping("/tools")
  public ResponseEntity<ToolsResponse> getAvailableTools() {
      List<ToolInfo> tools = toolRegistrationService.getAvailableTools().stream()
          .map(tool -> ToolInfo.builder()
              .name(extractToolName(tool.getName()))
              .description(tool.getDescription())
              .server(getServerForTool(tool.getName()))
              .parameters(tool.getInputSchema())
              .available(toolRegistrationService.isToolAvailable(tool.getName()))
              .build())
          .collect(Collectors.toList());
          
      return ResponseEntity.ok(ToolsResponse.builder()
          .tools(tools)
          .totalCount(tools.size())
          .availableCount((int) tools.stream().filter(ToolInfo::isAvailable).count())
          .lastUpdated(Instant.now())
          .build());
  }
  ```

- [ ] **Implement GET /api/status endpoint**
  ```java
  @GetMapping("/status")
  public ResponseEntity<SystemStatus> getSystemStatus() {
      SystemStatus status = SystemStatus.builder()
          .application("IMC Chatbot")
          .version(getClass().getPackage().getImplementationVersion())
          .status("healthy")
          .openaiConnection(chatService.isHealthy())
          .mcpServers(getMcpServerStatus())
          .activeConversations(chatService.getActiveSessionCount())
          .totalToolsAvailable(toolRegistrationService.getTotalToolCount())
          .uptime(getUptime())
          .timestamp(Instant.now())
          .build();
          
      return ResponseEntity.ok(status);
  }
  ```

- [ ] **Add health check endpoints**
  - Spring Boot Actuator integration
  - Custom health indicators for MCP servers
  - Readiness and liveness probes for Cloud Foundry

- [ ] **Create API documentation**
  - Add OpenAPI/Swagger configuration
  - Document all endpoints with examples
  - Include authentication requirements
  - Add rate limiting documentation

**Example API Documentation Structure:**
```yaml
openapi: 3.0.1
info:
  title: IMC Chatbot API
  description: REST API for Insurance MegaCorp AI Chatbot
  version: 2.0.0
paths:
  /api/chat:
    post:
      summary: Send chat message
      description: Send a message to the AI chatbot and receive response
      requestBody:
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ChatRequest'
      responses:
        200:
          description: Chat response
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ChatResponse'
```

**Acceptance Criteria:**
- ✅ Administrative endpoints provide comprehensive system information
- ✅ Health check endpoints functional for monitoring
- ✅ API documentation generated and accessible
- ✅ All endpoints properly tested

---

### **Phase 4: Web Interface & Real-time Features** 💬
**Branch:** `phase-4-web-interface`  
**Duration:** 5-6 days  
**Risk:** Medium-High  
**Dependencies:** Phase 3 complete

#### **Step 4.1: Static Web Resources**
**Estimated Time:** 8 hours

**Tasks:**
- [ ] **Create static web resources structure**
  ```
  src/main/resources/static/
  ├── index.html
  ├── css/
  │   ├── chat.css
  │   └── components.css
  ├── js/
  │   ├── chat.js
  │   ├── sse-client.js
  │   └── websocket-client.js
  └── assets/
      └── images/
  ```

- [ ] **Create basic HTML chat interface**
  ```html
  <!DOCTYPE html>
  <html lang="en">
  <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>IMC Chatbot - Insurance Assistant</title>
      <link rel="stylesheet" href="/css/chat.css">
  </head>
  <body>
      <div class="chat-container">
          <header class="chat-header">
              <h1>IMC Assistant</h1>
              <div class="status-indicator" id="connection-status">Connected</div>
          </header>
          
          <div class="chat-messages" id="chat-messages">
              <div class="welcome-message">
                  Hello! I'm your IMC Assistant. I can help you with insurance policies and accident claims.
              </div>
          </div>
          
          <div class="chat-input-container">
              <input type="text" id="message-input" placeholder="Type your message..." 
                     maxlength="4000" autocomplete="off">
              <button id="send-button">Send</button>
          </div>
          
          <div class="typing-indicator" id="typing-indicator" style="display: none;">
              <span>IMC Assistant is typing</span>
              <div class="typing-dots">
                  <span></span><span></span><span></span>
              </div>
          </div>
      </div>
      
      <script src="/js/chat.js"></script>
  </body>
  </html>
  ```

- [ ] **Create CSS for iMessage-style bubbles**
  ```css
  .chat-messages {
      height: 400px;
      overflow-y: auto;
      padding: 20px;
      background: #f5f5f5;
  }
  
  .message {
      margin: 10px 0;
      display: flex;
      align-items: flex-end;
  }
  
  .message.user {
      justify-content: flex-end;
  }
  
  .message.assistant {
      justify-content: flex-start;
  }
  
  .message-bubble {
      max-width: 70%;
      padding: 12px 16px;
      border-radius: 18px;
      position: relative;
      word-wrap: break-word;
  }
  
  .message.user .message-bubble {
      background: #007AFF;
      color: white;
      border-bottom-right-radius: 4px;
  }
  
  .message.assistant .message-bubble {
      background: #E5E5EA;
      color: black;
      border-bottom-left-radius: 4px;
  }
  
  .tool-indicator {
      font-size: 0.8em;
      color: #666;
      font-style: italic;
      margin: 5px 0;
  }
  ```

- [ ] **Create JavaScript for chat functionality**
  ```javascript
  class ChatClient {
      constructor() {
          this.conversationId = this.generateConversationId();
          this.messageInput = document.getElementById('message-input');
          this.sendButton = document.getElementById('send-button');
          this.messagesContainer = document.getElementById('chat-messages');
          this.typingIndicator = document.getElementById('typing-indicator');
          
          this.initializeEventListeners();
      }
      
      async sendMessage(message) {
          this.addMessage(message, 'user');
          this.showTypingIndicator();
          
          try {
              const response = await fetch('/api/chat', {
                  method: 'POST',
                  headers: {
                      'Content-Type': 'application/json',
                  },
                  body: JSON.stringify({
                      message: message,
                      conversationId: this.conversationId
                  })
              });
              
              if (!response.ok) {
                  throw new Error(`HTTP ${response.status}: ${response.statusText}`);
              }
              
              const chatResponse = await response.json();
              this.hideTypingIndicator();
              this.addMessage(chatResponse.response, 'assistant');
              
              if (chatResponse.toolsUsed && chatResponse.toolsUsed.length > 0) {
                  this.showToolsUsed(chatResponse.toolsUsed);
              }
              
          } catch (error) {
              this.hideTypingIndicator();
              this.addMessage('Sorry, I encountered an error. Please try again.', 'assistant', 'error');
              console.error('Chat error:', error);
          }
      }
      
      addMessage(content, sender, type = 'normal') {
          const messageDiv = document.createElement('div');
          messageDiv.className = `message ${sender}`;
          
          const bubbleDiv = document.createElement('div');
          bubbleDiv.className = `message-bubble ${type}`;
          bubbleDiv.textContent = content;
          
          messageDiv.appendChild(bubbleDiv);
          this.messagesContainer.appendChild(messageDiv);
          this.scrollToBottom();
      }
  }
  ```

**Acceptance Criteria:**
- ✅ Professional web interface with iMessage-style design
- ✅ Responsive design works on desktop and mobile
- ✅ Basic chat functionality through REST API
- ✅ Error handling and user feedback

#### **Step 4.2: WebSocket Support**
**Estimated Time:** 10 hours

**Tasks:**
- [ ] **Add WebSocket dependency**
  ```xml
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-websocket</artifactId>
  </dependency>
  ```

- [ ] **Configure WebSocket endpoints**
  ```java
  @Configuration
  @EnableWebSocket
  public class WebSocketConfig implements WebSocketConfigurer {
      
      @Override
      public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
          registry.addHandler(new ChatWebSocketHandler(chatService), "/ws/chat")
                  .setAllowedOrigins("*"); // Configure for production
      }
  }
  ```

- [ ] **Implement ChatWebSocketHandler**
  ```java
  @Component
  public class ChatWebSocketHandler extends TextWebSocketHandler {
      
      private final ChatService chatService;
      private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();
      
      @Override
      public void afterConnectionEstablished(WebSocketSession session) {
          String sessionId = session.getId();
          activeSessions.put(sessionId, session);
          
          // Send welcome message
          sendMessage(session, new WebSocketMessage("welcome", 
              "Connected to IMC Assistant. How can I help you today?"));
      }
      
      @Override
      protected void handleTextMessage(WebSocketSession session, TextMessage message) {
          try {
              ChatWebSocketRequest request = objectMapper.readValue(
                  message.getPayload(), ChatWebSocketRequest.class);
                  
              String conversationId = request.getConversationId() != null 
                  ? request.getConversationId() 
                  : session.getId();
              
              // Process chat with streaming callback
              chatService.chatWithStreaming(conversationId, request.getMessage(), 
                  new WebSocketStreamingCallback(session, conversationId));
                  
          } catch (Exception e) {
              sendError(session, "Failed to process message: " + e.getMessage());
          }
      }
      
      private class WebSocketStreamingCallback implements StreamingChatCallback {
          private final WebSocketSession session;
          private final String conversationId;
          
          @Override
          public void onPartialResponse(String partial) {
              sendMessage(session, new WebSocketMessage("partial", partial, conversationId));
          }
          
          @Override
          public void onToolInvocation(String toolName, String parameters) {
              sendMessage(session, new WebSocketMessage("tool_start", 
                  Map.of("tool", toolName, "parameters", parameters), conversationId));
          }
          
          @Override
          public void onComplete(String fullResponse) {
              sendMessage(session, new WebSocketMessage("complete", fullResponse, conversationId));
          }
      }
  }
  ```

- [ ] **Create WebSocket client JavaScript**
  ```javascript
  class WebSocketChatClient {
      constructor() {
          this.ws = null;
          this.conversationId = this.generateConversationId();
          this.reconnectAttempts = 0;
          this.maxReconnectAttempts = 5;
          
          this.connect();
      }
      
      connect() {
          const wsUrl = `ws://${window.location.host}/ws/chat`;
          this.ws = new WebSocket(wsUrl);
          
          this.ws.onopen = (event) => {
              console.log('WebSocket connected');
              this.reconnectAttempts = 0;
              this.updateConnectionStatus('Connected');
          };
          
          this.ws.onmessage = (event) => {
              const message = JSON.parse(event.data);
              this.handleMessage(message);
          };
          
          this.ws.onclose = (event) => {
              console.log('WebSocket disconnected');
              this.updateConnectionStatus('Disconnected');
              this.attemptReconnect();
          };
          
          this.ws.onerror = (error) => {
              console.error('WebSocket error:', error);
              this.updateConnectionStatus('Error');
          };
      }
      
      sendMessage(message) {
          if (this.ws && this.ws.readyState === WebSocket.OPEN) {
              this.ws.send(JSON.stringify({
                  message: message,
                  conversationId: this.conversationId,
                  timestamp: new Date().toISOString()
              }));
              
              this.addMessage(message, 'user');
              this.showTypingIndicator();
          } else {
              this.addMessage('Connection lost. Trying to reconnect...', 'system');
          }
      }
      
      handleMessage(wsMessage) {
          switch (wsMessage.type) {
              case 'welcome':
                  this.addMessage(wsMessage.data, 'assistant');
                  break;
              case 'partial':
                  this.updatePartialMessage(wsMessage.data);
                  break;
              case 'tool_start':
                  this.showToolIndicator(wsMessage.data.tool);
                  break;
              case 'complete':
                  this.hideTypingIndicator();
                  this.finalizeMessage(wsMessage.data);
                  break;
              case 'error':
                  this.hideTypingIndicator();
                  this.addMessage('Error: ' + wsMessage.data, 'assistant', 'error');
                  break;
          }
      }
  }
  ```

**Acceptance Criteria:**
- ✅ WebSocket connection established and maintained
- ✅ Real-time bidirectional communication functional
- ✅ Connection management handles disconnections and reconnections
- ✅ WebSocket and REST API can be used interchangeably

#### **Step 4.3: Advanced Web Features**
**Estimated Time:** 10 hours

**Tasks:**
- [ ] **Implement SSE integration in web interface**
  ```javascript
  class SSEChatClient {
      constructor() {
          this.currentEventSource = null;
          this.conversationId = this.generateConversationId();
      }
      
      sendMessage(message) {
          this.addMessage(message, 'user');
          this.showTypingIndicator();
          
          // Close any existing SSE connection
          if (this.currentEventSource) {
              this.currentEventSource.close();
          }
          
          // Start new SSE connection for streaming response
          const url = `/api/chat/stream/${this.conversationId}?message=${encodeURIComponent(message)}`;
          this.currentEventSource = new EventSource(url);
          
          this.currentEventSource.addEventListener('message', (event) => {
              const data = JSON.parse(event.data);
              this.updatePartialMessage(data);
          });
          
          this.currentEventSource.addEventListener('tool_start', (event) => {
              const data = JSON.parse(event.data);
              this.showToolIndicator(data.tool);
          });
          
          this.currentEventSource.addEventListener('complete', (event) => {
              const data = JSON.parse(event.data);
              this.hideTypingIndicator();
              this.finalizeMessage(data.response);
              this.currentEventSource.close();
              this.currentEventSource = null;
          });
          
          this.currentEventSource.onerror = (event) => {
              console.error('SSE error:', event);
              this.hideTypingIndicator();
              this.addMessage('Connection error. Please try again.', 'assistant', 'error');
              this.currentEventSource.close();
              this.currentEventSource = null;
          };
      }
  }
  ```

- [ ] **Add real-time typing indicators**
  - Animate typing dots during response generation
  - Show tool execution indicators
  - Display connection status

- [ ] **Implement message history display**
  ```javascript
  class MessageHistory {
      constructor() {
          this.messages = [];
          this.maxMessages = 100;
      }
      
      addMessage(content, sender, metadata = {}) {
          const message = {
              id: this.generateMessageId(),
              content: content,
              sender: sender,
              timestamp: new Date(),
              toolsUsed: metadata.toolsUsed || [],
              responseTime: metadata.responseTime || null
          };
          
          this.messages.push(message);
          
          // Limit message history
          if (this.messages.length > this.maxMessages) {
              this.messages = this.messages.slice(-this.maxMessages);
          }
          
          this.renderMessage(message);
          this.saveToLocalStorage();
      }
      
      loadFromLocalStorage() {
          const saved = localStorage.getItem('imc-chat-history');
          if (saved) {
              this.messages = JSON.parse(saved);
              this.renderAllMessages();
          }
      }
      
      clearHistory() {
          this.messages = [];
          this.clearDisplay();
          localStorage.removeItem('imc-chat-history');
      }
  }
  ```

- [ ] **Add error handling in UI**
  - Network error recovery
  - Invalid response handling
  - User-friendly error messages
  - Retry mechanisms

- [ ] **Create mobile-responsive design**
  - Touch-friendly interface
  - Mobile keyboard handling
  - Responsive layout adjustments
  - Progressive Web App features

**Acceptance Criteria:**
- ✅ SSE streaming works seamlessly in web interface
- ✅ Real-time typing indicators and tool usage display
- ✅ Message history persistence and management
- ✅ Comprehensive error handling and recovery
- ✅ Mobile-responsive design

#### **Step 4.4: Session Management Scaffolding**
**Estimated Time:** 8 hours

**Tasks:**
- [ ] **Create session management interfaces**
  ```java
  public interface ConversationRepository {
      Conversation save(Conversation conversation);
      Optional<Conversation> findById(String conversationId);
      List<Conversation> findByUserId(String userId);
      void deleteById(String conversationId);
      void deleteOlderThan(LocalDateTime cutoff);
  }
  
  public interface MessageRepository {
      Message save(Message message);
      List<Message> findByConversationId(String conversationId);
      List<Message> findByConversationIdWithPagination(String conversationId, int page, int size);
      void deleteByConversationId(String conversationId);
  }
  ```

- [ ] **Create domain models for persistence**
  ```java
  @Entity
  @Table(name = "conversations")
  public class Conversation {
      @Id
      private String id;
      
      @Column(name = "user_id")
      private String userId; // For future user management
      
      @Column(name = "title")
      private String title; // Auto-generated from first message
      
      @Column(name = "created_at")
      private LocalDateTime createdAt;
      
      @Column(name = "updated_at")
      private LocalDateTime updatedAt;
      
      @Column(name = "message_count")
      private Integer messageCount;
      
      @Column(name = "tools_used")
      @Convert(converter = StringListConverter.class)
      private List<String> toolsUsed;
      
      // getters, setters, constructors
  }
  
  @Entity
  @Table(name = "messages")
  public class Message {
      @Id
      private String id;
      
      @Column(name = "conversation_id")
      private String conversationId;
      
      @Column(name = "sender")
      @Enumerated(EnumType.STRING)
      private MessageSender sender; // USER, ASSISTANT, SYSTEM
      
      @Column(name = "content", length = 4000)
      private String content;
      
      @Column(name = "tools_invoked")
      @Convert(converter = StringListConverter.class)
      private List<String> toolsInvoked;
      
      @Column(name = "response_time_ms")
      private Long responseTimeMs;
      
      @Column(name = "created_at")
      private LocalDateTime createdAt;
      
      // getters, setters, constructors
  }
  ```

- [ ] **Add conversation persistence framework**
  ```java
  @Service
  public class ConversationService {
      private final ConversationRepository conversationRepository;
      private final MessageRepository messageRepository;
      
      public Conversation startConversation(String userId) {
          Conversation conversation = new Conversation();
          conversation.setId(UUID.randomUUID().toString());
          conversation.setUserId(userId);
          conversation.setCreatedAt(LocalDateTime.now());
          conversation.setMessageCount(0);
          return conversationRepository.save(conversation);
      }
      
      public void addMessage(String conversationId, String content, MessageSender sender, 
                           List<String> toolsUsed, Long responseTimeMs) {
          Message message = new Message();
          message.setId(UUID.randomUUID().toString());
          message.setConversationId(conversationId);
          message.setSender(sender);
          message.setContent(content);
          message.setToolsInvoked(toolsUsed);
          message.setResponseTimeMs(responseTimeMs);
          message.setCreatedAt(LocalDateTime.now());
          
          messageRepository.save(message);
          updateConversationStats(conversationId, toolsUsed);
      }
      
      public List<Message> getConversationHistory(String conversationId) {
          return messageRepository.findByConversationId(conversationId);
      }
      
      // Future: implement search, export, analytics
  }
  ```

- [ ] **Prepare for future implementation**
  - Database schema design
  - Migration scripts preparation
  - Configuration for different storage backends
  - Performance considerations for large conversation histories

- [ ] **Create documentation for session features**
  - API design for session management
  - Data retention policies
  - Privacy and security considerations
  - Scaling strategies for high-volume deployments

**Future Session Management Features (documented but not implemented):**
- User authentication and authorization
- Conversation search and filtering
- Export conversation history
- Conversation analytics and reporting
- Multi-user conversation sharing
- Conversation templates and shortcuts

**Acceptance Criteria:**
- ✅ Session management interfaces and models defined
- ✅ Framework ready for persistence implementation
- ✅ Database schema designed and documented
- ✅ Clear path for future session management features

---

### **Phase 5: Production Readiness & Cloud Deployment** 🚀
**Branch:** `phase-5-production`  
**Duration:** 3-4 days  
**Risk:** Medium  
**Dependencies:** Phase 4 complete

#### **Step 5.1: Cloud Foundry Configuration**
**Estimated Time:** 8 hours

**Tasks:**
- [ ] **Create manifest.yml for CF deployment**
  ```yaml
  ---
  applications:
  - name: imc-chatbot
    memory: 1G
    instances: 2
    buildpacks:
      - java_buildpack
    path: target/imc-chatbot-2.0.0.jar
    env:
      SPRING_PROFILES_ACTIVE: cloud
      JBP_CONFIG_OPEN_JDK_JRE: '{ jre: { version: 21.+ } }'
    services:
      - chat-model  # OpenAI service binding
    routes:
      - route: imc-chatbot.apps.internal.com
    health-check-type: http
    health-check-http-endpoint: /actuator/health
    timeout: 180
  ```

- [ ] **Configure service binding for "chat-model"**
  ```java
  @ConfigurationProperties(prefix = "vcap.services.chat-model.credentials")
  @Data
  public class ChatModelServiceProperties {
      private String apiKey;
      private String baseUrl;
      private String model;
      private Map<String, Object> parameters;
  }
  
  @Configuration
  @Profile("cloud")
  public class CloudConfiguration {
      
      @Bean
      @ConditionalOnProperty("vcap.services.chat-model.credentials.api-key")
      public ChatClient cloudChatClient(ChatClient.Builder builder, 
                                       ChatModelServiceProperties serviceProps) {
          return builder
              .defaultOptions(OpenAiChatOptions.builder()
                  .withModel(serviceProps.getModel())
                  .withTemperature(0.7f)
                  .withMaxTokens(2000)
                  .build())
              .build();
      }
  }
  ```

- [ ] **Configure environment-specific MCP server connections**
  ```properties
  # application-cloud.properties
  spring.ai.mcp.client.sse.connections.imc-policy.url=${POLICY_MCP_URL}
  spring.ai.mcp.client.sse.connections.imc-policy.sse-endpoint=/sse
  spring.ai.mcp.client.sse.connections.imc-policy.headers.Authorization=Bearer ${POLICY_MCP_TOKEN}
  
  spring.ai.mcp.client.sse.connections.imc-accident.url=${ACCIDENT_MCP_URL}
  spring.ai.mcp.client.sse.connections.imc-accident.sse-endpoint=/sse
  spring.ai.mcp.client.sse.connections.imc-accident.headers.Authorization=Bearer ${ACCIDENT_MCP_TOKEN}
  
  # Production timeouts and resilience
  spring.ai.mcp.client.sse.connections.imc-policy.timeout=60s
  spring.ai.mcp.client.sse.connections.imc-policy.retry-attempts=3
  spring.ai.mcp.client.sse.connections.imc-accident.timeout=60s
  spring.ai.mcp.client.sse.connections.imc-accident.retry-attempts=3
  ```

- [ ] **Test cloud profile configuration**
  - Mock service binding for local testing
  - Validate environment variable injection
  - Test with CF CLI in development environment

**Acceptance Criteria:**
- ✅ Manifest.yml properly configured for Cloud Foundry deployment
- ✅ Service binding for OpenAI chat model functional
- ✅ Environment-specific configurations work
- ✅ Cloud profile tested locally with mocked services

#### **Step 5.2: Production Features**
**Estimated Time:** 12 hours

**Tasks:**
- [ ] **Add application metrics and monitoring**
  ```java
  @Component
  public class ChatMetrics {
      private final MeterRegistry meterRegistry;
      private final Counter chatRequestsTotal;
      private final Timer chatResponseTime;
      private final Counter toolInvocationsTotal;
      private final Gauge activeConversationsGauge;
      
      public ChatMetrics(MeterRegistry meterRegistry) {
          this.meterRegistry = meterRegistry;
          this.chatRequestsTotal = Counter.builder("chat.requests.total")
              .description("Total number of chat requests")
              .tag("type", "chat")
              .register(meterRegistry);
              
          this.chatResponseTime = Timer.builder("chat.response.time")
              .description("Chat response time in milliseconds")
              .register(meterRegistry);
              
          this.toolInvocationsTotal = Counter.builder("tools.invocations.total")
              .description("Total number of tool invocations")
              .register(meterRegistry);
              
          this.activeConversationsGauge = Gauge.builder("conversations.active")
              .description("Number of active conversations")
              .register(meterRegistry, this, ChatMetrics::getActiveConversationCount);
      }
      
      public void recordChatRequest(String endpoint) {
          chatRequestsTotal.increment(Tags.of("endpoint", endpoint));
      }
      
      public Timer.Sample startChatTimer() {
          return Timer.start(meterRegistry);
      }
      
      public void recordToolInvocation(String toolName, String server) {
          toolInvocationsTotal.increment(Tags.of("tool", toolName, "server", server));
      }
  }
  ```

- [ ] **Implement proper logging configuration**
  ```yaml
  # application-cloud.yml
  logging:
    level:
      com.insurancemegacorp.imcchatbot: INFO
      org.springframework.ai: WARN
      org.springframework.web: WARN
    pattern:
      console: "%d{ISO8601} [%thread] %-5level [%logger{36}] - %msg%n"
      file: "%d{ISO8601} [%thread] %-5level [%logger{36}] - %msg%n"
    logback:
      rollingpolicy:
        max-file-size: 100MB
        max-history: 30
        total-size-cap: 3GB
  ```

- [ ] **Add security headers and CSRF protection**
  ```java
  @Configuration
  @EnableWebSecurity
  public class SecurityConfiguration {
      
      @Bean
      public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
          http
              .headers(headers -> headers
                  .frameOptions().deny()
                  .contentTypeOptions().and()
                  .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                      .maxAgeInSeconds(31536000)
                      .includeSubdomains(true))
                  .and())
              .csrf(csrf -> csrf
                  .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                  .ignoringRequestMatchers("/api/chat/**", "/ws/**"))
              .sessionManagement(session -> session
                  .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
              .authorizeHttpRequests(authz -> authz
                  .requestMatchers("/api/status", "/actuator/health").permitAll()
                  .requestMatchers("/api/**").authenticated()
                  .anyRequest().permitAll());
                  
          return http.build();
      }
  }
  ```

- [ ] **Implement performance optimizations**
  ```properties
  # application-cloud.properties
  # Connection pooling
  server.tomcat.max-connections=8192
  server.tomcat.max-threads=200
  server.tomcat.min-spare-threads=10
  
  # JVM tuning
  JAVA_OPTS=-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Xms512m -Xmx1024m
  
  # HTTP/2 support
  server.http2.enabled=true
  
  # Compression
  server.compression.enabled=true
  server.compression.mime-types=text/html,text/css,application/javascript,application/json,text/plain
  
  # Caching
  spring.web.resources.cache.cachecontrol.max-age=7d
  spring.web.resources.cache.use-last-modified=true
  ```

- [ ] **Add rate limiting and circuit breaker**
  ```java
  @Component
  public class RateLimitingService {
      private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
      
      public boolean isAllowed(String clientId) {
          RateLimiter rateLimiter = rateLimiters.computeIfAbsent(clientId, 
              k -> RateLimiter.create(10.0)); // 10 requests per second
          return rateLimiter.tryAcquire();
      }
  }
  
  @Component
  public class CircuitBreakerService {
      private final CircuitBreaker openAiCircuitBreaker;
      private final CircuitBreaker mcpCircuitBreaker;
      
      public CircuitBreakerService() {
          this.openAiCircuitBreaker = CircuitBreaker.ofDefaults("openai");
          this.mcpCircuitBreaker = CircuitBreaker.ofDefaults("mcp");
      }
      
      public String chatWithCircuitBreaker(String sessionId, String message) {
          return openAiCircuitBreaker.executeSupplier(() -> 
              chatService.chat(sessionId, message));
      }
  }
  ```

**Acceptance Criteria:**
- ✅ Comprehensive metrics and monitoring implemented
- ✅ Production-grade logging configuration
- ✅ Security headers and CSRF protection active
- ✅ Performance optimizations applied
- ✅ Rate limiting and circuit breakers functional

#### **Step 5.3: Documentation & Deployment**
**Estimated Time:** 8 hours

**Tasks:**
- [ ] **Update README with deployment instructions**
  ```markdown
  # IMC Chatbot Deployment Guide
  
  ## Local Development
  ```bash
  # Set environment variables
  export OPENAI_API_KEY=your-openai-api-key
  
  # Run locally
  ./imc-chatbot.sh --profile local
  ```
  
  ## Cloud Foundry Deployment
  ```bash
  # Build application
  ./mvnw clean package
  
  # Deploy to Cloud Foundry
  cf push
  
  # Bind OpenAI service (if not in manifest)
  cf bind-service imc-chatbot chat-model
  cf restage imc-chatbot
  ```
  
  ## Environment Variables
  - `OPENAI_API_KEY` - OpenAI API key (local profile only)
  - `POLICY_MCP_URL` - URL for imc-policy-mcp-server
  - `POLICY_MCP_TOKEN` - Authentication token for policy server
  - `ACCIDENT_MCP_URL` - URL for imc-accident-mcp-server  
  - `ACCIDENT_MCP_TOKEN` - Authentication token for accident server
  ```

- [ ] **Create deployment runbooks**
  ```markdown
  # Deployment Runbook
  
  ## Pre-deployment Checklist
  - [ ] All tests passing
  - [ ] Security scan completed
  - [ ] Performance testing completed
  - [ ] Database migrations ready (if applicable)
  - [ ] Environment variables configured
  - [ ] Service bindings verified
  
  ## Deployment Steps
  1. Build and test locally
  2. Deploy to staging environment
  3. Run smoke tests in staging
  4. Deploy to production with blue-green deployment
  5. Monitor metrics and logs
  6. Run post-deployment verification
  
  ## Rollback Procedure
  1. Scale down new version
  2. Scale up previous version
  3. Update routes if necessary
  4. Verify rollback success
  5. Investigate deployment issues
  
  ## Monitoring and Alerting
  - Application health: /actuator/health
  - Metrics endpoint: /actuator/metrics
  - Custom dashboards: [Grafana URLs]
  - Alert contacts: [Team distribution list]
  ```

- [ ] **Add troubleshooting guides**
  ```markdown
  # Troubleshooting Guide
  
  ## Common Issues
  
  ### OpenAI API Connection Issues
  **Symptoms:** Chat responses failing, API key errors
  **Solutions:**
  - Verify OPENAI_API_KEY is set correctly
  - Check API key permissions and billing status
  - Review rate limiting errors in logs
  - Verify network connectivity to OpenAI API
  
  ### MCP Server Connection Issues  
  **Symptoms:** Tools not available, connection timeouts
  **Solutions:**
  - Check MCP server URLs and endpoints
  - Verify authentication tokens
  - Review MCP server logs
  - Test direct connectivity to MCP servers
  
  ### Performance Issues
  **Symptoms:** Slow responses, timeouts
  **Solutions:**
  - Check JVM memory usage
  - Review database connection pool
  - Analyze slow query logs
  - Monitor network latency
  
  ### WebSocket Connection Issues
  **Symptoms:** Real-time features not working
  **Solutions:**
  - Check proxy/load balancer WebSocket support
  - Verify CORS configuration
  - Review browser console for errors
  - Test with different browsers
  ```

- [ ] **Update API documentation with deployment info**
  - Add production API endpoints
  - Include authentication requirements
  - Document rate limits and quotas
  - Add example client implementations

**Acceptance Criteria:**
- ✅ Comprehensive deployment documentation
- ✅ Runbooks for deployment and operations
- ✅ Troubleshooting guides for common issues
- ✅ API documentation updated with production details

#### **Step 5.4: Final Testing & Validation**
**Estimated Time:** 8 hours

**Tasks:**
- [ ] **End-to-end testing across all features**
  ```java
  @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
  @TestProfile("integration")
  public class EndToEndIntegrationTest {
      
      @Test
      public void testFullChatWorkflow() {
          // Test CLI chat mode
          // Test REST API chat
          // Test WebSocket chat
          // Test SSE streaming
          // Verify tool integration
          // Check metrics recording
      }
      
      @Test
      public void testMcpToolIntegration() {
          // Test policy server tools
          // Test accident server tools
          // Test error handling
          // Test connection resilience
      }
      
      @Test
      public void testWebInterface() {
          // Test static resource serving
          // Test API endpoints from web UI
          // Test error scenarios
          // Test mobile responsiveness
      }
  }
  ```

- [ ] **Load testing for chat endpoints**
  ```bash
  # JMeter test plan for chat API
  # Simulate concurrent users
  # Test sustained load
  # Measure response times
  # Check memory usage under load
  ```

- [ ] **Security testing**
  - OWASP security scanning
  - Input validation testing
  - XSS and CSRF protection verification
  - API security testing
  - Authentication and authorization testing

- [ ] **Cloud Foundry deployment verification**
  - Deploy to CF development space
  - Test service bindings
  - Verify environment variable injection  
  - Test scaling and health checks
  - Monitor application logs and metrics

**Test Scenarios:**
1. **Happy Path Testing**
   - Normal chat conversations
   - Tool invocation scenarios
   - Multi-user concurrent access
   - All access modes (CLI, Web, API)

2. **Error Scenario Testing**
   - OpenAI API failures
   - MCP server unavailability
   - Network timeouts
   - Invalid inputs
   - Resource exhaustion

3. **Performance Testing**
   - Response time under normal load
   - Concurrent user limits
   - Memory usage patterns
   - Tool execution performance
   - WebSocket connection limits

4. **Security Testing**
   - Input sanitization
   - XSS prevention
   - CSRF protection
   - Rate limiting effectiveness
   - Authentication bypass attempts

**Acceptance Criteria:**
- ✅ All end-to-end tests pass
- ✅ Load testing shows acceptable performance
- ✅ Security testing reveals no critical vulnerabilities
- ✅ Cloud Foundry deployment successful
- ✅ Production monitoring and alerting functional

---

## 📊 Success Metrics

**Phase 1 Success Criteria:**
- Chat functionality works with OpenAI
- CLI chat mode operational
- Basic error handling functional

**Phase 2 Success Criteria:**  
- MCP tools integrated with ChatClient
- Transparent tool calling operational
- Both MCP servers connected and functional

**Phase 3 Success Criteria:**
- REST API provides full chat functionality
- SSE streaming works for real-time responses
- Administrative endpoints operational

**Phase 4 Success Criteria:**
- Web interface provides professional chat experience
- WebSocket real-time communication functional
- Mobile-responsive design complete

**Phase 5 Success Criteria:**
- Cloud Foundry deployment successful
- Production monitoring and security active
- Complete documentation and runbooks

## 🎯 Definition of Done

Each phase is considered complete when:
- [ ] All acceptance criteria met
- [ ] Code reviewed and merged to main
- [ ] Tests written and passing (unit + integration)
- [ ] Documentation updated
- [ ] Demo-ready functionality working
- [ ] Performance acceptable for expected load
- [ ] Security review completed
- [ ] Deployment tested in target environment

## 📅 Timeline Summary

| Phase | Duration | Cumulative |
|-------|----------|------------|
| Phase 1 | 2-3 days | 2-3 days |
| Phase 2 | 3-4 days | 5-7 days |  
| Phase 3 | 4-5 days | 9-12 days |
| Phase 4 | 5-6 days | 14-18 days |
| Phase 5 | 3-4 days | 17-22 days |

**Total Estimated Duration: 17-22 days**

This plan provides a comprehensive roadmap for transforming the current MCP testing tool into a full-featured AI chatbot with multiple access modes while maintaining system stability and following engineering best practices.