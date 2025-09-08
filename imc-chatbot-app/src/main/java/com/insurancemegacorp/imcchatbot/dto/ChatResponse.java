package com.insurancemegacorp.imcchatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ChatResponse(
    @JsonProperty("response") String response,
    @JsonProperty("sessionId") String sessionId,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("type") String type
) {
    public static ChatResponse text(String response, String sessionId) {
        return new ChatResponse(response, sessionId, Instant.now(), "text");
    }
    
    public static ChatResponse error(String errorMessage, String sessionId) {
        return new ChatResponse(errorMessage, sessionId, Instant.now(), "error");
    }
}