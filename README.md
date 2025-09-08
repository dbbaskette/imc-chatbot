# IMC Chatbot - Insurance AI Assistant

A modern, intelligent chatbot built for Insurance MegaCorp using Spring Boot 3.5.4, Spring AI 1.0.1, and OpenAI integration. Features a clean web interface, REST API, and optional Model Context Protocol (MCP) tool integration.

## 🚀 Quick Start

### Prerequisites
- Java 21+
- OpenAI API Key ([Get one here](https://platform.openai.com/api-keys))

### Setup
1. **Clone and navigate to project:**
   ```bash
   cd imc-chatbot
   ```

2. **Set up environment:**
   ```bash
   cp .env.example .env
   # Edit .env and add your OPENAI_API_KEY
   ```

3. **Run the application:**
   ```bash
   ./run.sh
   ```

4. **Open your browser:**
   - Web UI: http://localhost:8080
   - Health Check: http://localhost:8080/api/chat/health

## 🏗️ Architecture

This project follows a multi-module Maven structure inspired by PlumChat:

```
imc-chatbot/
├── pom.xml                     # Parent POM
├── imc-chatbot-app/           # Main application module
│   ├── src/main/java/         # Java source code
│   ├── src/main/resources/    # Configuration and web UI
│   └── pom.xml               # App dependencies
├── .env.example              # Environment template
├── run.sh                   # Startup script
└── README.md               # This file
```

### Key Components
- **ChatService**: Core AI integration with Spring AI ChatClient
- **REST API**: `/api/chat` endpoints for programmatic access
- **Web UI**: iMessage-style chat interface
- **SSE Streaming**: Real-time chat responses
- **MCP Support**: Optional tool integration (conditional)

## 🎯 Features

### ✅ Current Features
- **🤖 AI Chat**: OpenAI GPT-4o-mini integration
- **🌐 Web Interface**: Clean, responsive iMessage-style UI
- **📡 API Access**: RESTful endpoints for external integrations
- **⚡ Real-time**: Server-Sent Events for streaming responses
- **🔒 Secure**: Environment-based secrets management
- **🏥 Health Checks**: Built-in monitoring endpoints

### 🔄 Optional Features
- **🛠️ MCP Tools**: Enable with `./run.sh --mcp` when MCP servers available
- **📊 Debugging**: Verbose logging with `./run.sh --verbose`

## 📖 Usage

### Command Line Options
```bash
./run.sh [OPTIONS]

Options:
  --rebuild          Clean and rebuild before running
  --mcp              Enable MCP profile for tool integration  
  --verbose          Enable debug logging
  --port PORT        Set server port (default: 8080)
  --help             Show help message

Examples:
  ./run.sh                    # Basic usage
  ./run.sh --mcp              # With MCP tools
  ./run.sh --rebuild --verbose # Clean build with debug logging
```

### API Endpoints

**Chat API:**
```bash
# Send message
POST /api/chat
Content-Type: application/json
{
  "message": "What types of insurance do you offer?",
  "sessionId": "user-session-123"
}

# Streaming chat (SSE)
GET /api/chat/stream/{sessionId}?message=Hello

# Clear session
DELETE /api/chat/session/{sessionId}

# Health check
GET /api/chat/health
```

### Environment Variables
```bash
# Required
OPENAI_API_KEY=sk-your-api-key-here

# Optional  
SERVER_PORT=8080
OPENAI_MODEL=gpt-4o-mini
LOG_LEVEL=INFO
```

## 🔧 Development

### Build Commands
```bash
# Compile only
./mvnw compile

# Full build
./mvnw clean package

# Run tests (when added)
./mvnw test

# Spring Boot dev mode
./mvnw spring-boot:run
```

### Project Structure
```
src/main/java/com/insurancemegacorp/imcchatbot/
├── ImcChatbotApplication.java     # Main Spring Boot app
├── controller/                    # REST controllers
│   ├── ChatController.java       # Chat API endpoints
│   └── WebController.java        # Web UI controller  
├── service/                       # Business logic
│   └── ChatService.java          # AI chat service
├── dto/                          # Data transfer objects
│   ├── ChatRequest.java          
│   └── ChatResponse.java
└── config/                       # Spring configuration
    ├── ChatConfiguration.java    # ChatClient setup
    └── McpConfiguration.java     # Optional MCP config
```

## 🚦 Profiles

- **default**: Basic OpenAI chat functionality
- **mcp**: Enables Model Context Protocol tool integration

## 🛠️ MCP Integration

The application supports optional MCP tool integration:

1. **MCP Dependencies**: Included but marked as optional
2. **Conditional Configuration**: Only activates when MCP classes are present
3. **Profile-based**: Enable with `--mcp` flag or `spring.profiles.active=mcp`
4. **Tool-ready**: Enhanced prompts for insurance-specific tool usage

When MCP is enabled, the AI can use tools for:
- Policy information lookup
- Customer data retrieval  
- Insurance term definitions

## 🏥 Monitoring

- **Health Endpoint**: `/api/chat/health`
- **Connection Status**: Real-time UI status indicator
- **Session Management**: Automatic conversation history management
- **Error Handling**: Graceful degradation and user-friendly error messages

## 🔒 Security

- **Environment Secrets**: `.env` file (git-ignored)
- **CORS Support**: Configurable for different environments
- **Input Validation**: Request validation and sanitization
- **Session Isolation**: Per-session conversation management

## 📝 Insurance Domain

The AI assistant is specifically trained for Insurance MegaCorp with:
- **Insurance Expertise**: Policy, claims, and coverage information
- **Professional Tone**: Helpful and compliant responses
- **Tool Integration**: Ready for MCP-based data access
- **Regulatory Awareness**: Recommends agent consultation for binding decisions

---

**Built with ❤️ for Insurance MegaCorp**  
*Leveraging Spring Boot 3.5.4, Spring AI 1.0.1, and OpenAI GPT-4o-mini*