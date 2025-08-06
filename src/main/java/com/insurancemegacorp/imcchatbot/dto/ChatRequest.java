package com.insurancemegacorp.imcchatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatRequest(
    @JsonProperty("message") String message,
    @JsonProperty("sessionId") String sessionId
) {
    public ChatRequest {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be empty");
        }
    }
    
    public ChatRequest(String message) {
        this(message, null);
    }
}