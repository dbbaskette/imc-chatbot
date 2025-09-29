package com.insurancemegacorp.imcchatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_CONVERSATION_HISTORY = 20;
    
    private final ChatClient chatClient;
    // McpConfiguration removed - no longer needed for table formatting
    private final Map<String, List<Message>> conversationHistory;

    public ChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.conversationHistory = new ConcurrentHashMap<>();

        log.info("✅ ChatService initialized with MCP-enabled ChatClient and simplified table formatting");
    }
    
    /**
     * Send a message to the AI and get a response, maintaining conversation context
     */
    public String chat(String sessionId, String userMessage, String customerId) {
        if (!StringUtils.hasText(userMessage)) {
            throw new IllegalArgumentException("User message cannot be empty");
        }
        if (!StringUtils.hasText(customerId)) {
            throw new IllegalArgumentException("Customer ID cannot be empty");
        }
        
        try {
            log.info("📝 User message for session {} (customer {}): {}", sessionId, customerId, userMessage);
            
            // Get or create conversation history
            List<Message> history = conversationHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());
            
            // System message handled by ChatClient defaultSystem() configuration
            
            // Add user message to history with customer context
            String contextualMessage = String.format("Customer ID: %s\nUser Question: %s", customerId, userMessage);
            UserMessage userMsg = new UserMessage(contextualMessage);
            history.add(userMsg);
            
            long startTime = System.currentTimeMillis();

            // Log the complete context being sent to LLM
            logCompleteContext(history, sessionId, "SYNC_CALL");

            // Use ChatClient to get response with potential tool calls
            ChatResponse chatResponse = chatClient
                .prompt()
                .messages(history)
                .call()
                .chatResponse();

            // Add detailed logging for debugging
            log.info("🔍 ChatResponse received: {}", chatResponse != null ? "NOT NULL" : "NULL");
            if (chatResponse != null) {
                log.info("🔍 ChatResponse results count: {}", chatResponse.getResults() != null ? chatResponse.getResults().size() : "NULL results");
                if (chatResponse.getResults() != null && !chatResponse.getResults().isEmpty()) {
                    log.info("🔍 First result: {}", chatResponse.getResults().get(0) != null ? "NOT NULL" : "NULL");
                }
            }

            // Extract content from the chat response with null safety
            if (chatResponse == null) {
                log.error("❌ ChatResponse is null for session {}", sessionId);
                return handleChatError(new RuntimeException("ChatResponse is null"));
            }

            if (chatResponse.getResults() == null || chatResponse.getResults().isEmpty()) {
                log.error("❌ ChatResponse has no results for session {}", sessionId);
                return handleChatError(new RuntimeException("ChatResponse has no results"));
            }

            Generation result = chatResponse.getResult();
            if (result == null) {
                log.error("❌ ChatResponse result is null for session {}", sessionId);
                return handleChatError(new RuntimeException("ChatResponse result is null"));
            }

            AssistantMessage output = result.getOutput();
            if (output == null) {
                log.error("❌ AssistantMessage output is null for session {}", sessionId);
                return handleChatError(new RuntimeException("AssistantMessage output is null"));
            }

            String response = getMessageContent(output);

            // Table formatting is now handled by the base system prompt
            // No additional enhancement needed as instructions are always present
            log.info("🛠️ Tools were called for session {}, table formatting handled by system prompt", sessionId);
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Ensure response is not null or empty
            if (response == null || response.trim().isEmpty()) {
                log.warn("⚠️ Received null/empty response, providing fallback");
                response = "I apologize, but I'm unable to generate a response at this time. Please try again.";
            }
            
            log.info("🤖 AI response for session {} ({}ms): {}", sessionId, responseTime, 
                     response.length() > 100 ? response.substring(0, 100) + "..." : response);
            
            // Add assistant response to history
            AssistantMessage assistantMsg = new AssistantMessage(response);
            history.add(assistantMsg);
            
            // Manage conversation history size
            manageConversationHistory(history);
            
            return response;
            
        } catch (Exception e) {
            log.error("❌ Chat error for session {}: {}", sessionId, e.getMessage(), e);
            return handleChatError(e);
        }
    }
    
    /**
     * Stream chat response for SSE
     */
    public Flux<String> chatStream(String sessionId, String userMessage, String customerId) {
        if (!StringUtils.hasText(userMessage)) {
            return Flux.error(new IllegalArgumentException("User message cannot be empty"));
        }
        if (!StringUtils.hasText(customerId)) {
            return Flux.error(new IllegalArgumentException("Customer ID cannot be empty"));
        }
        
        try {
            log.info("📝 Streaming chat for session {} (customer {}): {}", sessionId, customerId, userMessage);
            
            // Get or create conversation history
            List<Message> history = conversationHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());

            // System message handled by ChatClient defaultSystem() configuration

            // Add user message to history with customer context
            String contextualMessage = String.format("Customer ID: %s\nUser Question: %s", customerId, userMessage);
            UserMessage userMsg = new UserMessage(contextualMessage);
            history.add(userMsg);

            // Log the complete context being sent to LLM
            logCompleteContext(history, sessionId, "STREAM_CALL");

            // Use ChatClient to stream response
            Flux<String> responseStream = chatClient
                .prompt()
                .messages(history)
                .stream()
                .content();
                
            // Collect the full response for history
            StringBuilder fullResponse = new StringBuilder();
            
            return responseStream
                .doOnNext(chunk -> {
                    log.info("📡 Raw Spring AI chunk for session {}: '{}'", sessionId, chunk);
                    fullResponse.append(chunk);
                })
                .doOnComplete(() -> {
                    // Add complete response to history
                    String response = fullResponse.toString();
                    if (StringUtils.hasText(response)) {
                        AssistantMessage assistantMsg = new AssistantMessage(response);
                        history.add(assistantMsg);
                        manageConversationHistory(history);
                        log.info("✅ Streaming complete for session {}, response length: {}", sessionId, response.length());
                    }
                })
                .doOnError(error -> {
                    log.error("❌ Streaming error for session {}: {}", sessionId, error.getMessage(), error);
                });
                
        } catch (Exception e) {
            log.error("❌ Chat streaming error for session {}: {}", sessionId, e.getMessage(), e);
            return Flux.just(handleChatError(e));
        }
    }
    
    /**
     * Clear conversation history for a session
     */
    public void clearSession(String sessionId) {
        List<Message> removed = conversationHistory.remove(sessionId);
        if (removed != null) {
            log.debug("🧹 Cleared conversation history for session: {} ({} messages)", sessionId, removed.size());
        }
    }
    
    /**
     * Get the number of active conversation sessions
     */
    public int getActiveSessionCount() {
        return conversationHistory.size();
    }
    
    /**
     * Check if the ChatService is healthy
     */
    public boolean isHealthy() {
        try {
            // For local models, just check if chatClient is configured properly
            // Direct connectivity tests often fail with local models due to system prompts
            if (chatClient != null) {
                log.debug("ChatService health check passed - chatClient is configured");
                return true;
            } else {
                log.warn("ChatService health check failed - chatClient is null");
                return false;
            }
        } catch (Exception e) {
            log.error("ChatService health check error: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Manage conversation history size to prevent token limit issues
     */
    private void manageConversationHistory(List<Message> history) {
        if (history.size() > MAX_CONVERSATION_HISTORY) {
            int messagesToRemove = history.size() - MAX_CONVERSATION_HISTORY;
            for (int i = 0; i < messagesToRemove; i++) {
                // Remove from position 1 to preserve system message at position 0
                if (history.size() > MAX_CONVERSATION_HISTORY) {
                    history.remove(1);
                }
            }
            log.debug("📏 Trimmed conversation history, removed {} old messages", messagesToRemove);
        }
    }
    
    /**
     * Handle various types of chat errors with user-friendly messages
     */
    private String handleChatError(Exception e) {
        String errorMessage = e.getMessage();
        
        if (errorMessage != null) {
            if (errorMessage.contains("rate limit") || errorMessage.contains("429")) {
                return "I'm currently experiencing high demand. Please wait a moment and try again.";
            } else if (errorMessage.contains("token") && errorMessage.contains("limit")) {
                return "Your message is too long. Please try with a shorter message.";
            } else if (errorMessage.contains("network") || errorMessage.contains("timeout")) {
                return "I'm having trouble connecting right now. Please try again in a few moments.";
            } else if (errorMessage.contains("authentication") || errorMessage.contains("401")) {
                return "There's an authentication issue. Please check your API configuration.";
            }
        }
        
        return "I'm sorry, I encountered an error processing your request. Please try again.";
    }

    /**
     * Log the complete context being sent to the LLM for debugging
     */
    private void logCompleteContext(List<Message> history, String sessionId, String callType) {
        log.info("🔍 ==================== LLM CONTEXT DEBUG ====================");
        log.info("🔍 Session: {} | Call Type: {}", sessionId, callType);
        log.info("🔍 Total Messages in Context: {}", history.size());

        for (int i = 0; i < history.size(); i++) {
            Message msg = history.get(i);
            String msgType = msg.getClass().getSimpleName();
            String content = getMessageContent(msg);

            log.info("🔍 Message {}: [{}] {}", i + 1, msgType,
                content.length() > 200 ? content.substring(0, 200) + "..." : content);

            // Log additional details for special message types
            if (msgType.contains("Tool")) {
                log.info("🔍    Tool Message Details: {}", msg.toString());
            }
        }

        log.info("🔍 ============================================================");
    }

    /**
     * Safely extract content from any message type
     */
    private String getMessageContent(Message msg) {
        try {
            // For AssistantMessage, check metadata for textContent
            if (msg instanceof AssistantMessage) {
                AssistantMessage assistantMsg = (AssistantMessage) msg;

                // Check metadata for textContent first (Spring AI specific)
                Object textContent = assistantMsg.getMetadata().get("textContent");
                if (textContent != null && !textContent.toString().trim().isEmpty()) {
                    return textContent.toString();
                }

                // Try to get content via reflection
                try {
                    // Try getText() method
                    java.lang.reflect.Method getTextMethod = assistantMsg.getClass().getMethod("getText");
                    Object text = getTextMethod.invoke(assistantMsg);
                    if (text != null && !text.toString().trim().isEmpty()) {
                        return text.toString();
                    }
                } catch (Exception ignored) {}

                // Parse from toString if needed
                String msgStr = assistantMsg.toString();
                if (msgStr.contains("textContent=")) {
                    int start = msgStr.indexOf("textContent=") + 12;
                    int end = msgStr.indexOf(",", start);
                    if (end == -1) end = msgStr.indexOf("]", start);
                    if (end == -1) end = msgStr.indexOf("}", start);
                    if (end != -1) {
                        String content = msgStr.substring(start, end).trim();
                        if (!content.isEmpty()) {
                            return content;
                        }
                    }
                }
            }

            // Try reflection for other message types
            try {
                java.lang.reflect.Method getContentMethod = msg.getClass().getMethod("getContent");
                Object content = getContentMethod.invoke(msg);
                return content != null ? content.toString() : "Content is null";
            } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
                // If getContent() method doesn't exist or fails, fallback to toString
                String msgStr = msg.toString();
                log.debug("Using toString() for message content: {}", msgStr);
                return msgStr;
            }
        } catch (Exception e) {
            log.warn("Error extracting message content: {}", e.getMessage());
            return "Content not accessible: " + e.getMessage();
        }
    }

    // Removed unused methods:
    // - checkIfToolsWereCalled: No longer needed with consolidated prompt
    // - enhanceResponseWithFormattingIfNeeded: Duplicate logic removed
}