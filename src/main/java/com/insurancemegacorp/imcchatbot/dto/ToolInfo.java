package com.insurancemegacorp.imcchatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ToolInfo(
    @JsonProperty("name") String name,
    @JsonProperty("fullName") String fullName,
    @JsonProperty("description") String description,
    @JsonProperty("inputSchema") String inputSchema
) {}