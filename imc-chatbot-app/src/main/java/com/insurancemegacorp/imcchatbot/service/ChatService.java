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
    private final Map<String, List<Message>> conversationHistory;
    private final SystemMessage systemMessage;
    
    public ChatService(ChatClient chatClient, 
                      @Value("${imc.chatbot.system-prompt}") String systemPrompt) {
        this.chatClient = chatClient;
        this.conversationHistory = new ConcurrentHashMap<>();
        this.systemMessage = new SystemMessage(systemPrompt);
        
        log.info("✅ ChatService initialized with Spring AI ChatClient");
        log.debug("System prompt loaded: {} characters", systemPrompt.length());
    }
    
    /**
     * Send a message to the AI and get a response, maintaining conversation context
     */
    public String chat(String sessionId, String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
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
            
            // Use ChatClient to get response
            String response = chatClient
                .prompt()
                .messages(history)
                .call()
                .content();
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Ensure response is not null or empty
            if (!StringUtils.hasText(response)) {
                log.warn("⚠️ Received empty response, providing fallback");
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
    public Flux<String> chatStream(String sessionId, String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return Flux.error(new IllegalArgumentException("User message cannot be empty"));
        }
        
        try {
            log.info("📝 Streaming chat for session {}: {}", sessionId, userMessage);
            
            // Get or create conversation history
            List<Message> history = conversationHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());
            
            // Add system message if this is the first message in conversation
            if (history.isEmpty()) {
                history.add(systemMessage);
            }
            
            // Add user message to history
            UserMessage userMsg = new UserMessage(userMessage);
            history.add(userMsg);
            
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
            String testResponse = chatClient
                .prompt("Say 'OK' if you can respond")
                .call()
                .content();
            return StringUtils.hasText(testResponse);
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
}