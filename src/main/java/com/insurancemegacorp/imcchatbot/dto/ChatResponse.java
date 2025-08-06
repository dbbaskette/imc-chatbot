package com.insurancemegacorp.imcchatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatResponse(
    @JsonProperty("response") String response,
    @JsonProperty("sessionId") String sessionId,
    @JsonProperty("timestamp") long timestamp
) {
    public static ChatResponse of(String response, String sessionId) {
        return new ChatResponse(response, sessionId, System.currentTimeMillis());
    }
}