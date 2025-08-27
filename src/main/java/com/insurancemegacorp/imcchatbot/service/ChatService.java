package com.insurancemegacorp.imcchatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.insurancemegacorp.imcchatbot.dto.StructuredResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clean, simplified ChatService using official Spring AI MCP patterns
 * Removes all custom heartbeat/reconnection logic in favor of Spring AI built-ins
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_CONVERSATION_HISTORY = 20;
    
    private final ChatClient chatClient;
    private final Map<String, List<Message>> conversationHistory;
    private final SystemMessage systemMessage;
    private final ResponseParserService responseParserService;
    
    public ChatService(ChatClient chatClient, 
                      @Value("${imc.chatbot.system-prompt}") String systemPrompt,
                      ResponseParserService responseParserService) {
        this.chatClient = chatClient;
        this.conversationHistory = new ConcurrentHashMap<>();
        this.systemMessage = new SystemMessage(systemPrompt);
        this.responseParserService = responseParserService;
        
        log.info("✅ ChatService initialized with Spring AI ChatClient (MCP tools auto-configured)");
        log.debug("System prompt loaded: {}", systemPrompt.length() > 100 ? 
            systemPrompt.substring(0, 100) + "..." : systemPrompt);
    }
    
    /**
     * Send a message to the AI and get a response, maintaining conversation context
     * Uses Spring AI ChatClient which automatically handles MCP tools
     */
    public StructuredResponse chat(String sessionId, String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("User message cannot be empty");
        }
        
        try {
            log.info("📝 User message for session {}: {}", sessionId, userMessage);
            
            // Get or create conversation history
            List<Message> history = conversationHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());
            
            // Add system message if this is the first message in conversation
            if (history.isEmpty()) {
                history.add(systemMessage);
            }
            
            // Add user message to history
            UserMessage userMsg = new UserMessage(userMessage);
            history.add(userMsg);
            
            long startTime = System.currentTimeMillis();
            
            // Use ChatClient - Spring AI automatically handles MCP tools
            log.info("🤖 Using Spring AI ChatClient with auto-configured MCP tools");
            String response = chatClient
                .prompt()
                .messages(history)
                .call()
                .content();
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Ensure response is not null
            if (response == null || response.trim().isEmpty()) {
                log.warn("⚠️ Received empty response, providing fallback");
                response = "I apologize, but I'm unable to generate a response at this time. Please try again.";
            }
            
            // Filter out thinking process for models that expose it (like Qwen)
            response = filterThinkingProcess(response);
            
            log.info("🤖 AI response for session {} ({}ms): {}", sessionId, responseTime, 
                     response.length() > 100 ? response.substring(0, 100) + "..." : response);
            
            // Parse the response to detect structured data
            StructuredResponse structuredResponse = responseParserService.parseResponse(response);
            
            // Add assistant response to history
            AssistantMessage assistantMsg = new AssistantMessage(response);
            history.add(assistantMsg);
            
            // Manage conversation history size
            manageConversationHistory(history);
            
            log.debug("✅ Chat response generated for session: {} in {}ms, response length: {}, structured type: {}", 
                     sessionId, responseTime, response.length(), structuredResponse.type());
            
            return structuredResponse;
            
        } catch (Exception e) {
            log.error("❌ Chat error for session {}: {}", sessionId, e.getMessage(), e);
            return StructuredResponse.text(handleChatError(e));
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
     * Check if the ChatService is healthy (simplified - ChatClient handles MCP health)
     */
    public boolean isHealthy() {
        try {
            // Simple test using ChatClient
            String testResponse = chatClient
                .prompt("Say 'OK' if you can respond")
                .call()
                .content();
            return testResponse != null && !testResponse.trim().isEmpty();
        } catch (Exception e) {
            log.warn("Health check failed: {}", e.getMessage());
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
     * Filter out thinking process from AI responses (e.g., <think>...</think> tags)
     */
    private String filterThinkingProcess(String response) {
        if (response == null) {
            return response;
        }
        
        // Remove <think>...</think> blocks (case insensitive, multiline)
        String filtered = response.replaceAll("(?i)(?s)<think[^>]*>.*?</think>", "");
        
        // Remove any remaining thinking patterns that might not be properly closed
        filtered = filtered.replaceAll("(?i)(?s)<think[^>]*>.*", "");
        
        // Clean up extra whitespace
        filtered = filtered.replaceAll("\\n\\s*\\n\\s*\\n", "\n\n");
        filtered = filtered.trim();
        
        // If filtering removed everything, return a default message
        if (filtered.isEmpty()) {
            log.warn("⚠️ Response filtering removed all content, returning default message");
            return "I apologize, but I'm unable to provide a clear response at this time. Please try rephrasing your question.";
        }
        
        return filtered;
    }
    
    /**
     * Handle various types of chat errors with user-friendly messages
     */
    private String handleChatError(Exception e) {
        String errorMessage = e.getMessage();
        
        if (errorMessage != null) {
            // Handle specific errors
            if (errorMessage.contains("rate limit") || errorMessage.contains("429")) {
                return "I'm currently experiencing high demand. Please wait a moment and try again.";
            } else if (errorMessage.contains("token") && errorMessage.contains("limit")) {
                return "Your message is too long. Please try with a shorter message.";
            } else if (errorMessage.contains("network") || errorMessage.contains("timeout")) {
                return "I'm having trouble connecting right now. Please try again in a few moments.";
            } else if (errorMessage.contains("authentication") || errorMessage.contains("401")) {
                return "There's an authentication issue. Please contact support.";
            }
        }
        
        return "I'm sorry, I encountered an error processing your request. Please try again.";
    }
}