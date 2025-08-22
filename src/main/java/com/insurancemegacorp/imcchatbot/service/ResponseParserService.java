package com.insurancemegacorp.imcchatbot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.insurancemegacorp.imcchatbot.dto.StructuredResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Service
public class ResponseParserService {
    
    private static final Logger log = LoggerFactory.getLogger(ResponseParserService.class);
    private final ObjectMapper objectMapper;
    
    public ResponseParserService() {
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Parse the LLM response and determine if it contains structured table data
     */
    public StructuredResponse parseResponse(String llmResponse) {
        if (llmResponse == null || llmResponse.trim().isEmpty()) {
            return StructuredResponse.text("I apologize, but I'm unable to generate a response at this time.");
        }
        
        try {
            // Try to parse as JSON
            JsonNode jsonNode = objectMapper.readTree(llmResponse);
            
            // Check if it's a dataTable response
            if (jsonNode.has("type") && "dataTable".equals(jsonNode.get("type").asText())) {
                if (jsonNode.has("data") && jsonNode.has("columns")) {
                    log.debug("Detected structured table response from LLM");
                    return parseDataTableResponse(jsonNode);
                }
            }
            
            // If it's valid JSON but not a dataTable, return as text
            log.debug("LLM returned valid JSON but not table data, treating as text");
            return StructuredResponse.text(llmResponse);
            
        } catch (JsonProcessingException e) {
            // Not JSON, treat as regular text
            log.debug("LLM response is not JSON, treating as text: {}", e.getMessage());
            return StructuredResponse.text(llmResponse);
        }
    }
    
    /**
     * Parse a dataTable JSON response from the LLM
     */
    private StructuredResponse parseDataTableResponse(JsonNode jsonNode) {
        try {
            JsonNode dataNode = jsonNode.get("data");
            JsonNode columnsNode = jsonNode.get("columns");
            
            if (!dataNode.isArray() || !columnsNode.isArray()) {
                log.warn("Invalid dataTable format: data or columns is not an array");
                return StructuredResponse.text("Error: Invalid table format received");
            }
            
            // Parse columns
            List<String> columns = new ArrayList<>();
            for (JsonNode column : columnsNode) {
                columns.add(column.asText());
            }
            
            // Parse data rows
            List<Map<String, Object>> data = new ArrayList<>();
            for (JsonNode row : dataNode) {
                if (row.isObject()) {
                    Map<String, Object> rowMap = objectMapper.convertValue(row, Map.class);
                    data.add(rowMap);
                }
            }
            
            log.debug("Successfully parsed table with {} columns and {} rows", columns.size(), data.size());
            return StructuredResponse.dataTable(data, columns);
            
        } catch (Exception e) {
            log.error("Error parsing dataTable response: {}", e.getMessage(), e);
            return StructuredResponse.text("Error: Could not parse table data");
        }
    }
}
