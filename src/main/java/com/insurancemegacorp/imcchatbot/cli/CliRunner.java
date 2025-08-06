package com.insurancemegacorp.imcchatbot.cli;

import com.insurancemegacorp.imcchatbot.client.ApiClient;
import com.insurancemegacorp.imcchatbot.dto.ChatResponse;
import com.insurancemegacorp.imcchatbot.dto.StatusResponse;
import com.insurancemegacorp.imcchatbot.dto.ToolInfo;
import com.insurancemegacorp.imcchatbot.util.ParameterParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CliRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(CliRunner.class);

    private final ApiClient apiClient;
    private final ParameterParser parameterParser;
    private final Environment environment;
    private final Scanner scanner = new Scanner(System.in);
    private final ConfigurableApplicationContext applicationContext;
    private volatile boolean running = true;

    public CliRunner(ApiClient apiClient,
                     ParameterParser parameterParser, Environment environment, 
                     ConfigurableApplicationContext applicationContext) {
        this.apiClient = apiClient;
        this.parameterParser = parameterParser;
        this.environment = environment;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) {
        // Wait a moment for the web server to start
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        printWelcome();
        startInteractiveMode();
    }

    private void printWelcome() {
        System.out.println("\n🤖 === IMC Chatbot - Insurance Assistant === 🤖");
        System.out.println("AI-powered insurance chatbot with MCP tool integration.");
        System.out.println("Active profile(s): " + String.join(", ", environment.getActiveProfiles()));
        
        // Show OpenAI connection status
        boolean openAiHealthy = apiClient.isHealthy();
        System.out.println("OpenAI Status: " + (openAiHealthy ? "✅ Connected" : "❌ Disconnected"));
        
        System.out.println("\n🗨️ Chat Commands:");
        System.out.println("  chat                             - Enter interactive chat mode with AI assistant");
        
        System.out.println("\n🔧 Tool Testing Commands:");
        System.out.println("  list-tools                       - List all available tools from MCP servers");
        System.out.println("  describe-tool <name>             - Show detailed information about a specific tool");
        System.out.println("  tool <name> <json-params>        - Execute a tool with JSON parameters");
        System.out.println("  status                           - Show the status of all connected servers");
        
        System.out.println("\n📋 General Commands:");
        System.out.println("  help                             - Show this help message");
        System.out.println("  exit                             - Exit the application");
        System.out.println();
    }

    private void startInteractiveMode() {
        try {
            while (running) {
                System.out.print("imc-chatbot> ");
                
                // Check if scanner has input available
                if (!scanner.hasNextLine()) {
                    logger.warn("No input available from System.in - exiting interactive mode");
                    break;
                }
                
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) {
                    continue;
                }

                try {
                    processCommand(input);
                } catch (Exception e) {
                    System.err.println("❌ Error: " + e.getMessage());
                    logger.error("Command processing error", e);
                }
            }
        } catch (java.util.NoSuchElementException e) {
            logger.warn("Scanner input not available - likely running in non-interactive environment");
            System.out.println("✅ IMC Chatbot started successfully. Use Ctrl+C to stop.");
        } catch (Exception e) {
            logger.error("Unexpected error in interactive mode", e);
            System.err.println("❌ Interactive mode error: " + e.getMessage());
        }
    }

    private void processCommand(String input) {
        String[] parts = input.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "chat" -> handleChat();
            case "list-tools" -> handleListTools();
            case "describe-tool" -> handleDescribeTool(args);
            case "tool" -> handleToolInvocation(args);
            case "status" -> handleStatus();
            case "help" -> printWelcome();
            case "exit", "quit" -> handleExit();
            default -> System.out.println("Unknown command. Type 'help' for available commands.");
        }
    }

    private void handleChat() {
        System.out.println("\n🤖 === IMC Chatbot - Chat Mode === 🤖");
        System.out.println("Type 'exit-chat' to return to tool mode, 'help-chat' for commands");
        System.out.println("Connected to: OpenAI GPT-4 (via REST API)");
        System.out.println();
        
        String sessionId = UUID.randomUUID().toString();
        boolean chatActive = true;
        
        while (chatActive && running) {
            System.out.print("You: ");
            String input = scanner.nextLine().trim();
            
            if (input.equalsIgnoreCase("exit-chat")) {
                chatActive = false;
                continue;
            }
            
            if (input.equalsIgnoreCase("help-chat")) {
                showChatHelp();
                continue;
            }
            
            if (input.equalsIgnoreCase("clear-history")) {
                try {
                    apiClient.clearSession(sessionId);
                    System.out.println("🧹 Conversation history cleared.");
                } catch (Exception e) {
                    System.err.println("❌ Error clearing history: " + e.getMessage());
                }
                continue;
            }
            
            if (input.equalsIgnoreCase("show-session")) {
                try {
                    StatusResponse status = apiClient.getStatus();
                    System.out.println("📊 Session ID: " + sessionId);
                    System.out.println("📊 Active sessions: " + status.activeChatSessions());
                } catch (Exception e) {
                    System.err.println("❌ Error getting session info: " + e.getMessage());
                }
                continue;
            }
            
            if (input.isEmpty()) {
                continue;
            }
            
            try {
                // Show typing indicator and get response asynchronously
                CompletableFuture<ChatResponse> chatFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        return apiClient.sendMessage(input, sessionId);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                
                // Animate typing indicator
                animateTypingIndicator(chatFuture);
                
                ChatResponse response = chatFuture.get();
                System.out.println("\n🤖 IMC Assistant: " + response.response() + "\n");
                
            } catch (Exception e) {
                System.err.println("❌ Chat error: " + e.getMessage());
                logger.error("Chat interaction error", e);
            }
        }
        
        // Clean up session
        try {
            apiClient.clearSession(sessionId);
        } catch (Exception e) {
            logger.debug("Error clearing session: {}", e.getMessage());
        }
        System.out.println("📋 Exited chat mode. Back to tool testing mode.\n");
    }
    
    private void showChatHelp() {
        System.out.println("\n🗨️ Chat Mode Commands:");
        System.out.println("  exit-chat        - Return to tool testing mode");
        System.out.println("  help-chat        - Show this help message");
        System.out.println("  clear-history    - Clear conversation history for this session");
        System.out.println("  show-session     - Display session information");
        System.out.println("\n💡 Just type naturally to chat with IMC Assistant!");
        System.out.println("💡 The AI will automatically use tools when needed to help you.\n");
    }
    
    private void animateTypingIndicator(CompletableFuture<ChatResponse> chatFuture) {
        System.out.print("🤖 IMC Assistant is thinking");
        
        // Simple typing animation
        CompletableFuture<Void> animation = CompletableFuture.runAsync(() -> {
            String[] dots = {"", ".", "..", "..."};
            int dotIndex = 0;
            
            try {
                while (!chatFuture.isDone()) {
                    System.out.print("\r🤖 IMC Assistant is thinking" + dots[dotIndex % dots.length] + "   ");
                    dotIndex++;
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        try {
            // Wait for either the chat response or a timeout
            chatFuture.get(30, TimeUnit.SECONDS);
            animation.cancel(true);
            System.out.print("\r                                        \r"); // Clear typing indicator
        } catch (Exception e) {
            animation.cancel(true);
            System.out.print("\r                                        \r"); // Clear typing indicator
            throw new RuntimeException("Chat request failed or timed out", e);
        }
    }

    private void handleListTools() {
        try {
            List<ToolInfo> tools = apiClient.getTools();
            System.out.println("=== Available Tools ===");
            
            if (tools.isEmpty()) {
                System.out.println("No tools available from connected MCP servers.");
                System.out.println();
                return;
            }
            
            System.out.println("Found " + tools.size() + " tool(s):\n");
            
            for (int i = 0; i < tools.size(); i++) {
                ToolInfo tool = tools.get(i);
                System.out.printf("%d. %s\n", i + 1, tool.name());
                System.out.printf("   Description: %s\n", tool.description());
                System.out.printf("   Usage: tool %s key=value [key2=value2...] or tool %s '{\"json\":\"object\"}'\n", 
                                 tool.name(), tool.name());
                System.out.println();
            }
            
            System.out.println("💡 Tip: Use 'describe-tool <name>' for detailed parameter information");
        } catch (Exception e) {
            System.err.println("❌ Error listing tools: " + e.getMessage());
            logger.error("Tool listing error", e);
        }
    }

    private void handleStatus() {
        try {
            StatusResponse status = apiClient.getStatus();
            System.out.println("=== IMC Chatbot Status ===");
            
            // OpenAI Integration Status
            System.out.println("🤖 OpenAI Integration: " + (status.openaiHealthy() ? "✅ Connected" : "❌ Disconnected"));
            System.out.println("📊 Active Chat Sessions: " + status.activeChatSessions());
            
            // MCP Server Status
            System.out.println("\n🔧 MCP Server Status:");
            if (!status.toolsEnabled()) {
                System.out.println("Tool integration is disabled.");
            } else {
                if (status.availableTools() == 0) {
                    System.out.println("No tools available from connected MCP servers.");
                } else {
                    System.out.println("Found " + status.availableTools() + " available tool(s) from profile(s): " + 
                                     String.join(", ", status.activeProfiles()));
                }
            }
            System.out.println();
        } catch (Exception e) {
            System.err.println("❌ Error getting status: " + e.getMessage());
            logger.error("Status check error", e);
        }
    }

    private void handleDescribeTool(String toolName) {
        if (toolName.isBlank()) {
            System.out.println("Usage: describe-tool <tool-name>");
            return;
        }

        try {
            ToolInfo tool = apiClient.getTool(toolName);
            if (tool == null) {
                System.out.println("❌ Tool not found: " + toolName);
                List<ToolInfo> tools = apiClient.getTools();
                if (!tools.isEmpty()) {
                    System.out.println("Available tools:");
                    for (ToolInfo availableTool : tools) {
                        System.out.println("  - " + availableTool.name());
                    }
                }
                return;
            }

            System.out.println("\n=== Tool Details ===");
            System.out.println("Name: " + tool.name());
            System.out.println("Full name: " + tool.fullName());
            System.out.println("Description: " + tool.description());
            System.out.println("Usage: tool " + tool.name() + " <json-params>");
            
            String inputSchema = tool.inputSchema();
            if (inputSchema != null && !inputSchema.trim().isEmpty()) {
                System.out.println("Parameters Schema:");
                System.out.println(formatJsonSchema(inputSchema));
            } else {
                System.out.println("Parameters: No parameters required");
            }
            System.out.println();
        } catch (Exception e) {
            System.err.println("❌ Error describing tool: " + e.getMessage());
            logger.error("Tool description error", e);
        }
    }

    private void handleToolInvocation(String args) {
        if (args.isBlank()) {
            System.out.println("Usage: tool <tool-name> <parameters>");
            System.out.println("Examples:");
            System.out.println("  tool capitalizeText text=\"hello world\"");
            System.out.println("  tool capitalizeText '{\"text\": \"hello world\"}'");
            System.out.println("Tip: Use 'describe-tool <name>' for detailed parameter information");
            return;
        }

        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2) {
            System.out.println("❌ Missing parameters. Usage: tool <tool-name> <parameters>");
            return;
        }

        String toolName = parts[0];
        String parameterString = parts[1];

        try {
            String jsonParameters = convertToJson(parameterString);
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = mapper.readValue(jsonParameters, Map.class);
            
            System.out.println("🔧 Invoking tool: " + toolName);
            System.out.println("📥 Parameters: " + jsonParameters);
            
            String result = apiClient.invokeTool(toolName, parameters);
            System.out.println("✅ Result:");
            System.out.println(result);
            System.out.println();
            
        } catch (Exception e) {
            System.err.println("❌ Tool invocation failed: " + e.getMessage());
            logger.error("Tool invocation error", e);
        }
    }

    private void handleExit() {
        System.out.println("Shutting down IMC Chatbot... Goodbye!");
        System.exit(0);
    }

    private String formatJsonSchema(String schema) {
        try {
            // Pretty format the JSON schema for better readability
            var mapper = new ObjectMapper();
            var jsonNode = mapper.readTree(schema);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
        } catch (Exception e) {
            // If parsing fails, return the original schema
            return schema;
        }
    }

    private String convertToJson(String parameterString) throws Exception {
        // If it's already JSON (starts with { and ends with }), return as-is
        String trimmed = parameterString.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return parameterString;
        }
        
        // Parse key=value pairs and convert to JSON
        String[] paramPairs = parseArguments(parameterString);
        var parsedParams = parameterParser.parseParameters(paramPairs);
        
        // Convert to JSON
        var mapper = new ObjectMapper();
        return mapper.writeValueAsString(parsedParams);
    }

    private String[] parseArguments(String input) {
        List<String> args = new ArrayList<>();
        // Regex to match key=value pairs, handling quoted values
        Pattern pattern = Pattern.compile("(\\w+)=(\"[^\"]*\"|'[^']*'|\\S+)");
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            args.add(matcher.group(0));
        }
        return args.toArray(new String[0]);
    }
}