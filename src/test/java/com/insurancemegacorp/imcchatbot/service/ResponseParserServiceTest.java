package com.insurancemegacorp.imcchatbot.service;

import com.insurancemegacorp.imcchatbot.dto.StructuredResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class ResponseParserServiceTest {

    private ResponseParserService responseParserService;

    @BeforeEach
    void setUp() {
        responseParserService = new ResponseParserService();
    }

    @Test
    void testParseTextResponse() {
        String textResponse = "Hello, how can I help you today?";
        StructuredResponse result = responseParserService.parseResponse(textResponse);
        
        assertEquals("text", result.type());
        assertEquals(textResponse, result.content());
    }

    @Test
    void testParseDataTableResponse() {
        String tableResponse = """
            {
              "type": "dataTable",
              "data": [
                {"Policy": "AUTO-001", "Status": "Active", "Premium": "$150/month"},
                {"Policy": "HOME-002", "Status": "Active", "Premium": "$75/month"}
              ],
              "columns": ["Policy", "Status", "Premium"]
            }
            """;
        
        StructuredResponse result = responseParserService.parseResponse(tableResponse);
        
        assertEquals("dataTable", result.type());
        assertNotNull(result.content());
        
        // Verify the content is a DataTable object
        assertTrue(result.content() instanceof StructuredResponse.DataTable);
    }

    @Test
    void testParseInvalidJson() {
        String invalidJson = "{ invalid json }";
        StructuredResponse result = responseParserService.parseResponse(invalidJson);
        
        assertEquals("text", result.type());
        assertEquals(invalidJson, result.content());
    }

    @Test
    void testParseNullResponse() {
        StructuredResponse result = responseParserService.parseResponse(null);
        
        assertEquals("text", result.type());
        assertEquals("I apologize, but I'm unable to generate a response at this time.", result.content());
    }

    @Test
    void testParseEmptyResponse() {
        StructuredResponse result = responseParserService.parseResponse("");
        
        assertEquals("text", result.type());
        assertEquals("I apologize, but I'm unable to generate a response at this time.", result.content());
    }
}
