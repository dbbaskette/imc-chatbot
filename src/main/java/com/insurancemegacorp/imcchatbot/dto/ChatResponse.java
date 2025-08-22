package com.insurancemegacorp.imcchatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatResponse(
    @JsonProperty("response") Object response,
    @JsonProperty("sessionId") String sessionId,
    @JsonProperty("timestamp") long timestamp
) {
    // For backward compatibility - text responses
    public static ChatResponse of(String response, String sessionId) {
        return new ChatResponse(response, sessionId, System.currentTimeMillis());
    }
    
    // For structured responses
    public static ChatResponse of(StructuredResponse response, String sessionId) {
        return new ChatResponse(response, sessionId, System.currentTimeMillis());
    }
}