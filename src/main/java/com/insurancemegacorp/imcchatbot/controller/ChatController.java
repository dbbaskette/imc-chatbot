package com.insurancemegacorp.imcchatbot.controller;

import com.insurancemegacorp.imcchatbot.dto.ChatRequest;
import com.insurancemegacorp.imcchatbot.dto.ChatResponse;
import com.insurancemegacorp.imcchatbot.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    
    private final ChatService chatService;
    
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }
    
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        try {
            String sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();
            
            log.debug("Processing chat request for session: {}", sessionId);
            
            String response = chatService.chat(sessionId, request.message());
            ChatResponse chatResponse = ChatResponse.of(response, sessionId);
            
            return ResponseEntity.ok(chatResponse);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid chat request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Chat processing error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping(value = "/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@PathVariable String sessionId, @RequestParam String message) {
        return Mono.fromCallable(() -> {
            log.debug("Processing streaming chat request for session: {}", sessionId);
            return chatService.chat(sessionId, message);
        })
        .flux()
        .flatMap(response -> {
            // Split response into words for streaming effect
            String[] words = response.split(" ");
            return Flux.fromArray(words)
                    .delayElements(Duration.ofMillis(50))
                    .map(word -> "data: " + word + " \n\n");
        })
        .concatWithValues("data: [DONE]\n\n")
        .doOnError(error -> log.error("Streaming chat error for session {}: {}", sessionId, error.getMessage()));
    }
    
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> clearSession(@PathVariable String sessionId) {
        chatService.clearSession(sessionId);
        log.debug("Cleared session: {}", sessionId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        boolean healthy = chatService.isHealthy();
        if (healthy) {
            return ResponseEntity.ok("Chat service is healthy");
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Chat service is unhealthy");
        }
    }
}