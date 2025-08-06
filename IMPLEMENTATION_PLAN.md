# IMC Chatbot - Implementation Plan

This document outlines the development plan for the IMC Chatbot, a conversational AI assistant for Insurance MegaCorp.

## Phase 1: Core Chatbot and CLI

**Goal:** Build a functional command-line chatbot with core conversational abilities and integration with existing MCP tools.

**Timeline:** 2 weeks

---

### **Step 1.1: Project Setup & Initial Structure**
**Estimated Time:** 4 hours
**Status:** ✅ **COMPLETED**

**Tasks:**
- [x] **Initialize Spring Boot project**
  - Use Spring Initializr with Java 21, Maven, Spring Web, Spring Boot DevTools.
  - Integrate Spring AI starter for OpenAI.

- [x] **Set up project structure**
  - Create packages: `config`, `cli`, `service`.

- [x] **Add basic CLI runner**
  - Create `CliRunner` class implementing `CommandLineRunner`.
  - Implement a simple loop to accept user input.
  - Add basic commands: `help`, `exit`.

- [x] **Configure OpenAI integration**
  - Add `spring-ai-openai-spring-boot-starter` dependency.
  - Configure `application.properties` for OpenAI API key.
  - Create a simple health check to verify OpenAI connection.

- [x] **Update README and create this plan**
  - Update `README.md` with project description and setup instructions.
  - Create `IMPLEMENTATION_PLAN.md`.

**Acceptance Criteria:**
- ✅ Application starts without errors.
- ✅ CLI displays a welcome message and prompt.
- ✅ `help` command shows available commands.
- ✅ `exit` command terminates the application.
- ✅ OpenAI API key is loaded correctly from environment variables.

---

### **Step 1.2: Core Chat Service**
**Estimated Time:** 6 hours
**Status:** ⏳ **IN PROGRESS**

**Tasks:**
- [x] **Create ChatService class**
  ```java
  @Service
  public class ChatService {
      private final ChatModel chatModel;
      // ...
  }
  ```

- [x] **Implement conversation management**
  - Maintain conversation history per session.
  - Use Spring AI Message API (UserMessage, AssistantMessage).
  - Implement conversation context window management.
  - Handle conversation history truncation (token limits).

- [x] **Add comprehensive error handling**
  - OpenAI API failures (rate limits, network issues).
  - Invalid responses or empty responses.
  - Token limit exceeded scenarios.
  - Graceful degradation strategies.

- [x] **Implement logging and debugging**
  - Log all user inputs (sanitized).
  - Log all AI responses.
  - Log token usage and costs (partially implemented with response time).
  - Performance metrics (response time).

- [ ] **Create ChatService unit tests**
  - Mock `ChatModel` to test service logic.
  - Test conversation history management.
  - Test error handling paths.
  - Test session clearing.

**Acceptance Criteria:**
- ✅ ChatService successfully integrates with OpenAI ChatClient.
- ✅ Conversation history maintained per session.
- ✅ Proper error handling with user-friendly messages.
- ❌ Unit tests achieve >80% code coverage (pending).

---

### **Step 1.3: Enhanced CLI Chat Mode**
**Estimated Time:** 6 hours
**Status:** ✅ **COMPLETED**

**Tasks:**
- [x] **Add chat command to CliRunner**
  - ✅ Extended `processCommand()` method with `chat` case.
  - ✅ Created `handleChat()` method to manage the interactive session.
  - ✅ Implemented a chat loop with `exit-chat` as the exit condition.

- [x] **Implement interactive chat loop**
  - ✅ Managed a unique `sessionId` for each chat session.
  - ✅ Called `chatService.chat()` with user input and session ID.
  - ✅ Displayed the AI's response to the console.

- [x] **Add chat-specific help and commands**
  - ✅ `help-chat`: Displays chat-specific commands.
  - ✅ `clear-history`: Clears the current conversation history.
  - ✅ `show-session`: Displays the current session ID and active session count.
  - ✅ `exit-chat`: Returns to the main tool-testing command prompt.

- [x] **Enhance user experience**
  - ✅ Implemented an asynchronous "thinking" indicator (`animateTypingIndicator`) while waiting for the AI response.
  - ✅ Formatted responses clearly with "You:" and "🤖 IMC Assistant:" prefixes.
  - ✅ Added response time logging in the `ChatService`.

- [x] **Update main help and welcome messages**
  - ✅ Updated `printWelcome()` to reflect the new "IMC Chatbot" branding.
  - ✅ Added a "Chat Commands" section to the help text.
  - ✅ Added an OpenAI connection status check to the welcome message.

**Acceptance Criteria:**
- ✅ `chat` command launches interactive chat mode.
- ✅ Users can have natural conversations with OpenAI.
- ✅ Chat maintains context within session.
- ✅ `exit-chat` returns to tool testing mode.
- ✅ Chat-specific help commands (`help-chat`, `clear-history`, `show-session`) work as expected.
- ✅ The asynchronous "thinking" indicator provides good user feedback.

---

### **Step 1.4: Basic Integration Testing**
**Estimated Time:** 4 hours
**Status:** ✅ **COMPLETED**

**Tasks:**
- [x] **Create integration test suite for chat**
  - ✅ Created basic application context tests to verify Spring Boot startup.
  - ✅ Added unit tests for core components (ParameterParser).
  - ✅ Verified that application components can be loaded without external dependencies.

- [x] **Test tool invocation via CLI**
  - ✅ Created tests that verify CLI components are properly wired.
  - ✅ Used mocked dependencies to avoid external API calls during testing.
  - ✅ Verified application structure and package organization.

**Acceptance Criteria:**
- ✅ Integration tests cover the basic application startup and component wiring.
- ✅ Unit tests verify core functionality without external dependencies.

---

### **Step 1.5: Tool Integration with Chat**
**Estimated Time:** 8 hours
**Status:** ✅ **COMPLETED**

**Tasks:**
- [x] **Modify ChatService to be tool-aware**
  - ✅ Injected `SyncMcpToolCallbackProvider` into ChatService.
  - ✅ Updated ChatService to check for available MCP tools.
  - ✅ Fixed Spring AI tool integration API compatibility issues.

- [x] **Update `ChatModel` call**
  - ✅ Updated tool integration to work with Spring AI 1.0.1.
  - ✅ Implemented proper tool callback handling via MCP provider.
  - ✅ Ensured tools are automatically available when MCP is enabled.

- [x] **Implement tool execution logic**
  - ✅ MCP tools are automatically executed via Spring AI's tool callback mechanism.
  - ✅ Tool results are seamlessly integrated into AI responses.
  - ✅ Added proper error handling and fallback for when tools are unavailable.

- [x] **Refine the system prompt**
  - ✅ System prompt already instructs AI on tool usage for insurance scenarios.
  - ✅ Tools are automatically available to AI when MCP profile is active.

**Acceptance Criteria:**
- ✅ ChatService integrates with MCP tools seamlessly.
- ✅ AI can access tools automatically when MCP profile is enabled.
- ✅ Tool results are properly incorporated into AI responses via Spring AI framework.

---

## Phase 2: Advanced Features

**Goal:** Enhance the chatbot with more sophisticated capabilities, improving its utility and user experience.

- **Step 2.1: Advanced RAG (Retrieval-Augmented Generation)**
  - Integrate with a vector database (e.g., Chroma, Pinecone).
  - Create a service to load and index documents (e.g., policy PDFs, knowledge base articles).
  - Modify the chat service to retrieve relevant context from the vector store before generating a response.

- **Step 2.2: Multi-modal Support**
  - Allow users to upload documents or images for analysis.
  - Integrate with a vision-capable model (e.g., GPT-4o) to describe images.

## Phase 3: Production Readiness

**Goal:** Prepare the chatbot for deployment and real-world use.

- **Step 3.1: Containerization & Deployment**
  - Create a `Dockerfile` for the application.
  - Set up a CI/CD pipeline (e.g., GitHub Actions) to build and deploy the container.

- **Step 3.2: Monitoring and Observability**
  - Integrate Micrometer for application metrics.
  - Set up structured logging for easier analysis.
  - Create a dashboard to monitor chatbot usage, performance, and error rates.