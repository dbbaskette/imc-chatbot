package com.insurancemegacorp.imcchatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import org.springframework.ai.chat.prompt.Prompt;
import com.insurancemegacorp.imcchatbot.dto.StructuredResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_CONVERSATION_HISTORY = 20; // Maximum messages to keep in history
    
    private final ChatModel chatModel;
    private final ChatClient chatClient;
    private final Map<String, List<Message>> conversationHistory;
    private final SystemMessage systemMessage;
    private final ResponseParserService responseParserService;
    
    public ChatService(ChatModel chatModel,
                      ChatClient chatClient,
                      @Value("${imc.chatbot.system-prompt}") String systemPrompt,
                      ResponseParserService responseParserService) {
        this.chatModel = chatModel;
        this.chatClient = chatClient;
        this.conversationHistory = new ConcurrentHashMap<>();
        this.systemMessage = new SystemMessage(systemPrompt);
        this.responseParserService = responseParserService;
        
        log.info("✅ ChatService initialized with ChatClient (MCP tools auto-configured)");
        log.debug("System prompt loaded: {} characters", systemPrompt.length() > 100 ? 
            systemPrompt.substring(0, 100) + "..." : systemPrompt);
        
        // Log which AI model configuration is being used
        try {
            if (chatModel != null) {
                String modelClassName = chatModel.getClass().getName();
                log.info("🤖 AI Model Class: {}", modelClassName);
                
                // Determine if it's bound service or API key
                if (modelClassName.contains("OpenAiChatModel")) {
                    if (System.getenv("OPENAI_API_KEY") != null) {
                        log.info("🔑 Using OpenAI API Key configuration");
                    } else {
                        log.info("🔗 Using OpenAI Bound Service configuration");
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not determine model configuration: {}", e.getMessage());
        }
        
        // Log active profiles for debugging
        try {
            String[] activeProfiles = System.getProperty("spring.profiles.active", "").split(",");
            if (activeProfiles.length > 0 && !activeProfiles[0].isEmpty()) {
                log.info("📋 Active Spring profiles: {}", String.join(", ", activeProfiles));
            }
        } catch (Exception e) {
            log.debug("Could not determine active profiles: {}", e.getMessage());
        }
    }
    
    /**
     * Send a message to the AI and get a response, maintaining conversation context
     */
    public StructuredResponse chat(String sessionId, String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("User message cannot be empty");
        }
        
        try {
            log.debug("Processing chat request for session: {}, message length: {}", sessionId, userMessage.length());
            
            // Get or create conversation history
            List<Message> history = conversationHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());
            
            // Add system message if this is the first message in conversation
            if (history.isEmpty()) {
                history.add(systemMessage);
            }
            
            // Add user message to history
            UserMessage userMsg = new UserMessage(userMessage);
            history.add(userMsg);
            
            // Build prompt with conversation context and available tools
            // Call OpenAI with tools available using ChatClient
            long startTime = System.currentTimeMillis();
            
            // Log the complete prompt being sent to OpenAI (for debugging)
            logPromptDetails(sessionId, history, userMessage);
            
            String response;
            
            log.info("🔄 Starting OpenAI request for session: {}", sessionId);
            
            // Use the configured ChatClient which automatically handles MCP tools
            // The ChatClient bean is configured with system prompt and tool callbacks
            try {
                log.info("🤖 Using configured ChatClient (MCP tools auto-registered)");
                
                // Use the configured ChatClient - it will automatically use available MCP tools
                var chatResponse = chatClient
                    .prompt()
                    .messages(history)
                    .call()
                    .chatResponse();
                
                log.info("🔍 ChatResponse metadata: {}", chatResponse.getMetadata());
                log.info("🔍 ChatResponse results count: {}", chatResponse.getResults().size());
                
                if (!chatResponse.getResults().isEmpty()) {
                    var result = chatResponse.getResults().get(0);
                    response = result.getOutput().getText();
                    log.info("🔍 Raw result output: '{}'", response);
                    log.info("🔍 Result metadata: {}", result.getMetadata());
                } else {
                    log.error("❌ ChatResponse has no results!");
                    response = null;
                }
                
                log.info("✅ ChatClient completed successfully");
            } catch (Exception e) {
                log.warn("⚠️ ChatClient failed, falling back to basic ChatModel: {}", e.getMessage());
                log.error("Full exception details:", e);
                
                log.info("🔄 Calling fallback basic ChatModel...");
                response = chatModel.call(new Prompt(history)).getResult().getOutput().getText();
                log.info("✅ Fallback ChatModel completed successfully");
            }
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Enhanced debugging for null/empty responses
            if (response == null) {
                log.error("❌ ChatClient returned NULL response after {}ms for session: {}", responseTime, sessionId);
                response = "I apologize, but I'm unable to generate a response at this time. Please try again.";
            } else if (response.trim().isEmpty()) {
                log.error("❌ ChatClient returned EMPTY response after {}ms for session: {}", responseTime, sessionId);
                response = "I apologize, but I received an empty response. Please try again.";
            } else {
                log.info("✅ ChatClient returned valid response after {}ms for session: {}", responseTime, sessionId);
            }
            
            // Log the complete response received from OpenAI (for debugging)
            logResponseDetails(sessionId, response, responseTime);
            
            // Filter out thinking process for models that expose it (like Qwen)
            // Temporarily disabled to debug GPT-4.1 response filtering issues
            // response = filterThinkingProcess(response);
            
            // Parse the response to detect structured data
            StructuredResponse structuredResponse = responseParserService.parseResponse(response);
            
            // Add assistant response to history (use original text for conversation context)
            AssistantMessage assistantMsg = new AssistantMessage(response);
            history.add(assistantMsg);
            
            // Manage conversation history size
            manageConversationHistory(history);
            
            log.debug("Chat response generated for session: {} in {}ms, response length: {}, structured type: {}", 
                     sessionId, responseTime, response.length(), structuredResponse.type());
            
            return structuredResponse;
            
        } catch (Exception e) {
            log.error("Chat error for session {}: {}", sessionId, e.getMessage(), e);
            return StructuredResponse.text(handleChatError(e));
        }
    }
    
    /**
     * Clear conversation history for a session
     */
    public void clearSession(String sessionId) {
        List<Message> removed = conversationHistory.remove(sessionId);
        if (removed != null) {
            log.debug("Cleared conversation history for session: {} ({} messages)", sessionId, removed.size());
        }
    }
    
    /**
     * Get the number of active conversation sessions
     */
    public int getActiveSessionCount() {
        return conversationHistory.size();
    }
    
    /**
     * Check if the ChatService is healthy (can make basic requests)
     */
    public boolean isHealthy() {
        try {
            // Make a simple test request
            Prompt testPrompt = new Prompt("Say 'OK' if you can respond");
            ChatResponse response = chatModel.call(testPrompt);
            String testResponse = response.getResult().getOutput().getText();
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
            // Remove oldest messages (keep system message if present)
            int messagesToRemove = history.size() - MAX_CONVERSATION_HISTORY;
            for (int i = 0; i < messagesToRemove; i++) {
                // Remove from position 1 to preserve system message at position 0 if it exists
                if (history.size() > MAX_CONVERSATION_HISTORY) {
                    history.remove(1);
                }
            }
            log.debug("Trimmed conversation history, removed {} old messages", messagesToRemove);
        }
    }
    
    /**
     * Filter out thinking process from AI responses (e.g., <think>...</think> tags)
     * This is useful for models like Qwen that expose their reasoning process
     */
    private String filterThinkingProcess(String response) {
        if (response == null) {
            return response;
        }
        
        // Remove <think>...</think> blocks (case insensitive, multiline with DOTALL flag)
        String filtered = response.replaceAll("(?i)(?s)<think[^>]*>.*?</think>", "");
        
        // Remove any remaining thinking patterns that might not be properly closed
        filtered = filtered.replaceAll("(?i)(?s)<think[^>]*>.*", "");
        
        // Clean up extra whitespace and newlines that might be left behind
        filtered = filtered.replaceAll("\\n\\s*\\n\\s*\\n", "\n\n"); // Replace multiple newlines with double newline
        filtered = filtered.trim();
        
        // If filtering removed everything, return a default message
        if (filtered.isEmpty()) {
            log.warn("Response filtering removed all content, returning default message");
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
            // Handle specific OpenAI API errors
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
        
        // Generic error message
        return "I'm sorry, I encountered an error processing your request. Please try again.";
    }
    
    /**
     * Log detailed prompt information for debugging
     */
    private void logPromptDetails(String sessionId, List<Message> history, String userMessage) {
        if (log.isDebugEnabled()) {
            log.debug("=== PROMPT DEBUG for session {} ===", sessionId);
            log.debug("Total messages in history: {}", history.size());
            
            for (int i = 0; i < history.size(); i++) {
                Message msg = history.get(i);
                String role = "UNKNOWN";
                String content = "";
                
                if (msg instanceof SystemMessage) {
                    role = "SYSTEM";
                    content = ((SystemMessage) msg).getText();
                } else if (msg instanceof UserMessage) {
                    role = "USER";
                    content = ((UserMessage) msg).getText();
                } else if (msg instanceof AssistantMessage) {
                    role = "ASSISTANT";
                    content = ((AssistantMessage) msg).getText();
                }
                
                // Truncate very long content for readability
                String truncatedContent = content != null && content.length() > 200 ? content.substring(0, 200) + "..." : 
                                        (content != null ? content : "[null content]");
                log.debug("Message {}: [{}] {}", i, role, truncatedContent);
            }
            
            log.debug("=== END PROMPT DEBUG ===");
        }
        
        // Always log the current user message for basic debugging
        log.info("📝 User message for session {}: {}", sessionId, 
                userMessage.length() > 100 ? userMessage.substring(0, 100) + "..." : userMessage);
    }
    
    /**
     * Log detailed response information for debugging
     */
    private void logResponseDetails(String sessionId, String response, long responseTime) {
        // Always log basic response info
        log.info("🤖 AI response for session {} ({}ms): {}", sessionId, responseTime,
                response != null && response.length() > 150 ? response.substring(0, 150) + "..." : response);
        
        if (log.isDebugEnabled() && response != null) {
            log.debug("=== RESPONSE DEBUG for session {} ===", sessionId);
            log.debug("Response time: {}ms", responseTime);
            log.debug("Response length: {} characters", response.length());
            log.debug("Full response: {}", response);
            log.debug("=== END RESPONSE DEBUG ===");
        }
    }
}