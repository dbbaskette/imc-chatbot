package com.insurancemegacorp.imcchatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record StatusResponse(
    @JsonProperty("openaiHealthy") boolean openaiHealthy,
    @JsonProperty("activeChatSessions") int activeChatSessions,
    @JsonProperty("availableTools") int availableTools,
    @JsonProperty("activeProfiles") List<String> activeProfiles,
    @JsonProperty("toolsEnabled") boolean toolsEnabled
) {}