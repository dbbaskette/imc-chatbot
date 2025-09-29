package com.insurancemegacorp.imcchatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatRequest(
    @JsonProperty("message") String message,
    @JsonProperty("sessionId") String sessionId,
    @JsonProperty("customerId") String customerId
) {
    public ChatRequest {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be null or empty");
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Session ID cannot be null or empty");
        }
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
    }
}