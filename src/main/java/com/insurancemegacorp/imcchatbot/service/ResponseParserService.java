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
            // Not JSON, check if it's a markdown table that we can convert
            if (isMarkdownTable(llmResponse)) {
                log.debug("Detected markdown table, attempting to convert to structured format");
                return convertMarkdownTableToStructured(llmResponse);
            }
            
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
    
    /**
     * Check if the response contains a markdown table
     */
    private boolean isMarkdownTable(String response) {
        // Look for markdown table pattern with | symbols
        String[] lines = response.split("\n");
        int tableLines = 0;
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.contains("|")) {
                tableLines++;
                if (tableLines >= 2) { // Need at least header + separator or header + data
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Convert markdown table to structured JSON format
     */
    private StructuredResponse convertMarkdownTableToStructured(String response) {
        try {
            String[] lines = response.split("\n");
            List<String> tableLines = new ArrayList<>();
            
            // Extract table lines
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                    tableLines.add(trimmed);
                }
            }
            
            if (tableLines.size() < 2) {
                log.warn("Insufficient table lines found");
                return StructuredResponse.text(response);
            }
            
            // Parse header (first line)
            String headerLine = tableLines.get(0);
            List<String> columns = parseMarkdownTableRow(headerLine);
            
            // Skip separator line (usually line 1) and parse data rows
            List<Map<String, Object>> data = new ArrayList<>();
            for (int i = 1; i < tableLines.size(); i++) {
                String line = tableLines.get(i);
                // Skip separator lines (contain only |, -, :, and spaces)
                if (line.matches("^\\|[\\s\\-:]+\\|$")) {
                    continue;
                }
                
                List<String> rowValues = parseMarkdownTableRow(line);
                if (rowValues.size() == columns.size()) {
                    Map<String, Object> row = new java.util.HashMap<>();
                    for (int j = 0; j < columns.size(); j++) {
                        row.put(columns.get(j), rowValues.get(j));
                    }
                    data.add(row);
                }
            }
            
            if (!data.isEmpty()) {
                log.debug("Successfully converted markdown table with {} columns and {} rows", columns.size(), data.size());
                return StructuredResponse.dataTable(data, columns);
            }
            
        } catch (Exception e) {
            log.error("Error converting markdown table: {}", e.getMessage(), e);
        }
        
        // Fallback to text if conversion fails
        return StructuredResponse.text(response);
    }
    
    /**
     * Parse a single markdown table row
     */
    private List<String> parseMarkdownTableRow(String line) {
        List<String> values = new ArrayList<>();
        
        // Remove leading and trailing |
        String content = line.trim();
        if (content.startsWith("|")) {
            content = content.substring(1);
        }
        if (content.endsWith("|")) {
            content = content.substring(0, content.length() - 1);
        }
        
        // Split by | and clean up
        String[] parts = content.split("\\|");
        for (String part : parts) {
            values.add(part.trim());
        }
        
        return values;
    }
}
