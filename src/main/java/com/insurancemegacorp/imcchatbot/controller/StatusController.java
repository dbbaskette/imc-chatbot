package com.insurancemegacorp.imcchatbot.controller;

import com.insurancemegacorp.imcchatbot.dto.StatusResponse;
import com.insurancemegacorp.imcchatbot.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class StatusController {

    private static final Logger log = LoggerFactory.getLogger(StatusController.class);
    
    private final ChatService chatService;
    private final SyncMcpToolCallbackProvider toolCallbackProvider;
    private final Environment environment;
    
    public StatusController(ChatService chatService,
                           @Autowired(required = false) SyncMcpToolCallbackProvider toolCallbackProvider,
                           Environment environment) {
        this.chatService = chatService;
        this.toolCallbackProvider = toolCallbackProvider;
        this.environment = environment;
    }
    
    @GetMapping("/status")
    public ResponseEntity<StatusResponse> getStatus() {
        try {
            boolean openaiHealthy = chatService.isHealthy();
            int activeSessions = chatService.getActiveSessionCount();
            boolean toolsEnabled = toolCallbackProvider != null;
            int availableTools = toolsEnabled ? toolCallbackProvider.getToolCallbacks().length : 0;
            List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
            
            StatusResponse status = new StatusResponse(
                openaiHealthy,
                activeSessions,
                availableTools,
                activeProfiles,
                toolsEnabled
            );
            
            log.debug("Status check: OpenAI={}, Sessions={}, Tools={}", 
                     openaiHealthy, activeSessions, availableTools);
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("Error getting status: {}", e.getMessage(), e);
            StatusResponse errorStatus = new StatusResponse(false, 0, 0, List.of(), false);
            return ResponseEntity.ok(errorStatus);
        }
    }
}