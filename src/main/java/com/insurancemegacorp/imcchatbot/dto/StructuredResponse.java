package com.insurancemegacorp.imcchatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record StructuredResponse(
    @JsonProperty("type") String type,
    @JsonProperty("content") Object content
) {
    // For text responses
    public static StructuredResponse text(String text) {
        return new StructuredResponse("text", text);
    }
    
    // For table data
    public static StructuredResponse dataTable(List<Map<String, Object>> data, List<String> columns) {
        DataTable table = new DataTable(data, columns);
        return new StructuredResponse("dataTable", table);
    }
    
    // Inner class for table data
    public record DataTable(
        @JsonProperty("data") List<Map<String, Object>> data,
        @JsonProperty("columns") List<String> columns
    ) {}
}
