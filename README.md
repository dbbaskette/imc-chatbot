# IMC Chatbot - Insurance Assistant

AI-powered insurance chatbot with MCP (Model Context Protocol) tool integration, built with Spring Boot 3.3.2 and Spring AI 1.0.0. Features OpenAI ChatGPT integration with transparent tool calling capabilities.

## 🏗️ Architecture

**API-First Design**: All interfaces (CLI, Web UI, external clients) consume the same REST API layer, ensuring consistency and scalability.

```
┌─────────────┐    ┌─────────────┐    ┌──────────────┐
│   Web UI    │    │     CLI     │    │ External API │
│ (Browser)   │    │  (Terminal) │    │   Clients    │
└─────────────┘    └─────────────┘    └──────────────┘
       │                   │                   │
       └───────────────────┼───────────────────┘
                           │
                    ┌─────────────┐
                    │  REST API   │
                    │ Controllers │
                    └─────────────┘
                           │
                    ┌─────────────┐
                    │ ChatService │
                    │ (OpenAI +   │
                    │ MCP Tools)  │
                    └─────────────┘
```

## 🚀 Quick Start

### 1. Environment Setup

First, set up your environment variables:

```bash
./setup-env.sh
```

This will:
- Create a `.env` file from the template
- Prompt for your OpenAI API key
- Guide you through configuration

### 2. Run the Application

```bash
./imc-chatbot.sh --profile local
```

### 3. Access the Chatbot

**CLI Interface**: Use the `chat` command in the terminal
```
imc-chatbot> chat
🤖 === IMC Chatbot - Chat Mode === 🤖
You: What types of insurance do you offer?
```

**Web Interface**: Open http://localhost:8080
- iMessage-style chat interface
- Real-time connection status
- Mobile-responsive design
- Error handling and typing indicators

## 📋 Available Commands

### Chat Commands
- `chat` - Enter interactive chat mode with AI assistant
- `exit-chat` - Return to tool mode (from within chat)
- `clear-history` - Clear conversation history
- `show-session` - Display session information

### Tool Testing Commands
- `list-tools` - List all available tools from MCP servers
- `describe-tool <name>` - Show detailed tool information
- `tool <name> <params>` - Execute a tool with parameters
- `status` - Show system and connection status

### General Commands
- `help` - Show help message
- `exit` - Exit the application

## 🔧 Configuration

### Environment Variables (.env file)

```bash
# Required
OPENAI_API_KEY=your_openai_api_key_here

# Optional
SERVER_PORT=8080
LOG_LEVEL=INFO
```

### Profiles

**Local Development** (`--profile local`):
- Uses OpenAI API with your API key
- Connects to local MCP servers (if available)
- Debug logging enabled
- Web server on port 8080

**Cloud Foundry** (`--profile cloud`):
- Uses bound "chat-model" service
- Production logging
- Environment-provided configuration

## 🌐 REST API Endpoints

### Chat Endpoints
- `POST /api/chat` - Send message and receive response
- `GET /api/chat/stream/{sessionId}?message=` - SSE streaming chat
- `DELETE /api/chat/session/{sessionId}` - Clear session history
- `GET /api/chat/health` - Chat service health check

### Tool Endpoints
- `GET /api/tools` - List all available MCP tools
- `GET /api/tools/{toolName}` - Get tool details
- `POST /api/tools/{toolName}` - Invoke tool with parameters

### Administrative Endpoints
- `GET /api/status` - System status and connection health

### Example API Usage

```javascript
// Send a chat message
const response = await fetch('/api/chat', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    message: 'What is my policy coverage?',
    sessionId: 'optional-session-id'
  })
});

const data = await response.json();
console.log(data.response);
```

## 🛠️ Development

### Build Commands

```bash
# Build the application
./mvnw clean package

# Run with specific profile
./imc-chatbot.sh --profile local

# Rebuild and run
./imc-chatbot.sh --rebuild --profile local

# Enable verbose logging
./imc-chatbot.sh --verbose --profile local
```

### Project Structure

```
src/main/java/com/insurancemegacorp/imcchatbot/
├── cli/              # Command-line interface
├── client/           # API client for internal communication
├── config/           # Spring configuration classes
├── controller/       # REST API controllers
├── dto/              # Data transfer objects
├── service/          # Business logic services
└── util/             # Utility classes

src/main/resources/
├── static/           # Web UI assets
├── application*.properties  # Configuration files
└── ...
```

## 🔌 MCP Integration

The chatbot integrates with Model Context Protocol (MCP) servers for extended functionality:

- **Policy Server**: Insurance policy queries and management
- **Accident Server**: Claims processing and accident reporting
- **Tool Discovery**: Automatic discovery and registration of available tools
- **Transparent Invocation**: AI automatically calls tools when needed

## 🔒 Security

- ✅ Environment variables for API keys (never committed)
- ✅ `.env` files are git-ignored
- ✅ Input validation and sanitization
- ✅ Error handling without information disclosure
- ✅ CORS configuration for web API

## 🐛 Troubleshooting

### Connection Issues

If you see `❌ Chat error: Chat request failed or timed out`:

1. **Check your `.env` file**: Ensure `OPENAI_API_KEY` is set
2. **Verify API key**: Test with OpenAI's API directly
3. **Check network**: Ensure internet connectivity
4. **Wait for startup**: The web server needs 2-3 seconds to start

### API Key Issues

```bash
# Check if your API key is loaded
echo $OPENAI_API_KEY

# Recreate your .env file
./setup-env.sh
```

### Port Conflicts

```bash
# Use a different port
echo "SERVER_PORT=8081" >> .env
./imc-chatbot.sh --profile local
```

## 📊 Monitoring

- **Health Checks**: Built-in health endpoints for monitoring
- **Logging**: Configurable logging levels for debugging
- **Session Management**: Track active chat sessions
- **Connection Status**: Real-time MCP server connection monitoring

## 🚀 Deployment

### Local Development
```bash
./imc-chatbot.sh --profile local
```

### Cloud Foundry
```bash
# Deploy to CF with bound chat-model service
cf push imc-chatbot --profile cloud
```

### Docker (Future)
```bash
# Build and run with Docker
docker build -t imc-chatbot .
docker run -p 8080:8080 --env-file .env imc-chatbot
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test with `./imc-chatbot.sh --profile local`
5. Submit a pull request

## 📄 License

Copyright © 2024 Insurance MegaCorp. All rights reserved.

---

🤖 **IMC Chatbot** - Your AI-powered insurance assistant, ready to help with policies, claims, and more!