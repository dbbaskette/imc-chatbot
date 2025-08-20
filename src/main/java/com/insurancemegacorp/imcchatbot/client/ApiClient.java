package com.insurancemegacorp.imcchatbot.client;

import com.insurancemegacorp.imcchatbot.dto.ChatRequest;
import com.insurancemegacorp.imcchatbot.dto.ChatResponse;
import com.insurancemegacorp.imcchatbot.dto.StatusResponse;
import com.insurancemegacorp.imcchatbot.dto.ToolInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class ApiClient {

    private static final Logger log = LoggerFactory.getLogger(ApiClient.class);
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    
    public ApiClient(@Value("${server.port:8080}") int serverPort) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.baseUrl = "http://localhost:" + serverPort;
    }
    
    public ChatResponse sendMessage(String message, String sessionId) throws Exception {
        ChatRequest request = new ChatRequest(message, sessionId);
        String requestJson = objectMapper.writeValueAsString(request);
        
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .timeout(Duration.ofSeconds(30))
                .build();
        
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), ChatResponse.class);
        } else {
            throw new RuntimeException("API call failed with status: " + response.statusCode());
        }
    }
    
    public List<ToolInfo> getTools() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/tools"))
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            ToolInfo[] tools = objectMapper.readValue(response.body(), ToolInfo[].class);
            return Arrays.asList(tools);
        } else {
            throw new RuntimeException("API call failed with status: " + response.statusCode());
        }
    }
    
    public ToolInfo getTool(String toolName) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/tools/" + toolName))
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), ToolInfo.class);
        } else if (response.statusCode() == 404) {
            return null;
        } else {
            throw new RuntimeException("API call failed with status: " + response.statusCode());
        }
    }
    
    public String invokeTool(String toolName, Map<String, Object> parameters) throws Exception {
        String requestJson = objectMapper.writeValueAsString(parameters);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/tools/" + toolName))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .timeout(Duration.ofSeconds(30))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new RuntimeException("Tool invocation failed with status: " + response.statusCode() + 
                                     ", body: " + response.body());
        }
    }
    
    public StatusResponse getStatus() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/status"))
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), StatusResponse.class);
        } else {
            throw new RuntimeException("API call failed with status: " + response.statusCode());
        }
    }
    
    public void clearSession(String sessionId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat/session/" + sessionId))
                .DELETE()
                .timeout(Duration.ofSeconds(10))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            log.warn("Clear session failed with status: {}", response.statusCode());
        }
    }
    
    public boolean isHealthy() {
        // Retry health check a few times to allow server startup
        for (int i = 0; i < 5; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/chat/health"))
                        .GET()
                        .timeout(Duration.ofSeconds(5))
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200;
            } catch (Exception e) {
                log.debug("Health check attempt {} failed: {}", i + 1, e.getMessage());
                if (i < 4) {
                    try {
                        Thread.sleep(1000); // Wait 1 second before retry
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        return false;
    }
    
    public <T> T get(String endpoint, Class<T> responseType) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), responseType);
        } else {
            throw new RuntimeException("Failed to get from " + endpoint + ": HTTP " + response.statusCode());
        }
    }
    
    public <T> T post(String endpoint, Class<T> responseType) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .timeout(Duration.ofSeconds(10))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), responseType);
        } else {
            throw new RuntimeException("Failed to post to " + endpoint + ": HTTP " + response.statusCode());
        }
    }
}