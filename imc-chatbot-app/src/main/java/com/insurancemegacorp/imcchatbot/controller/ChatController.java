package com.insurancemegacorp.imcchatbot.controller;

import com.insurancemegacorp.imcchatbot.dto.ChatRequest;
import com.insurancemegacorp.imcchatbot.dto.ChatResponse;
import com.insurancemegacorp.imcchatbot.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    
    private final ChatService chatService;
    
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }
    
    /**
     * Send a chat message and get a response
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        try {
            log.info("📨 Chat request from session: {}", request.sessionId());
            
            String response = chatService.chat(request.sessionId(), request.message());
            
            ChatResponse chatResponse = ChatResponse.text(response, request.sessionId());
            return ResponseEntity.ok(chatResponse);
            
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Invalid chat request: {}", e.getMessage());
            ChatResponse errorResponse = ChatResponse.error("Invalid request: " + e.getMessage(), 
                                                           request != null ? request.sessionId() : "unknown");
            return ResponseEntity.badRequest().body(errorResponse);
            
        } catch (Exception e) {
            log.error("❌ Chat error: {}", e.getMessage(), e);
            ChatResponse errorResponse = ChatResponse.error("An error occurred processing your request", 
                                                           request != null ? request.sessionId() : "unknown");
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Stream chat response using Server-Sent Events
     */
    @GetMapping(value = "/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @PathVariable String sessionId,
            @RequestParam String message) {
        
        try {
            log.info("📡 Stream chat request from session: {}", sessionId);
            
            return chatService.chatStream(sessionId, message)
                .filter(chunk -> StringUtils.hasText(chunk.trim())) // Filter out empty chunks first
                .map(chunk -> {
                    // Spring AI sends clean content, format correctly for SSE
                    String cleanChunk = chunk.trim();
                    log.info("📡 Processing chunk: '{}'", cleanChunk);
                    return "data: " + cleanChunk + "\n\n";
                })
                .concatWith(Flux.just("data: [DONE]\n\n"))
                .delayElements(Duration.ofMillis(30))
                .doOnNext(formattedChunk -> log.info("📡 Sending formatted SSE: {}", formattedChunk.replace("\n", "\\n")))
                .doOnError(error -> log.error("❌ Stream error for session {}: {}", sessionId, error.getMessage()))
                .onErrorReturn("data: [ERROR]\n\n");
                
        } catch (Exception e) {
            log.error("❌ Stream setup error for session {}: {}", sessionId, e.getMessage(), e);
            return Flux.just("data: An error occurred\n\n", "data: [DONE]\n\n");
        }
    }
    
    /**
     * Clear conversation history for a session
     */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> clearSession(@PathVariable String sessionId) {
        try {
            log.info("🧹 Clear session request: {}", sessionId);
            chatService.clearSession(sessionId);
            return ResponseEntity.ok().build();
            
        } catch (Exception e) {
            log.error("❌ Error clearing session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get chat service health status
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        try {
            boolean healthy = chatService.isHealthy();
            int activeSessions = chatService.getActiveSessionCount();
            
            String status = String.format("{\"healthy\": %s, \"activeSessions\": %d}", 
                                        healthy, activeSessions);
            
            return healthy ? ResponseEntity.ok(status) : ResponseEntity.status(503).body(status);
            
        } catch (Exception e) {
            log.error("❌ Health check error: {}", e.getMessage(), e);
            return ResponseEntity.status(503).body("{\"healthy\": false, \"error\": \"Health check failed\"}");
        }
    }
}