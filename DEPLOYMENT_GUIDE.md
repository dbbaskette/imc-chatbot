# IMC Chatbot Deployment Guide

## Overview

The IMC Chatbot is now configured with a clean, profile-based architecture where **MCP tools are enabled by default** in all profiles. This eliminates configuration conflicts and provides clear deployment paths for different use cases.

## Configuration Structure

### 1. Base Configuration (`application.properties`)
- **MCP Enabled by Default**: All MCP tools and server connections are configured here
- **Common Settings**: Application name, ports, logging, and system prompts
- **No Profile Conflicts**: Clean base configuration that works with all profiles

### 2. Profile-Specific Configurations

#### `application-cloud.properties` - Bound Model Profile
- **Use Case**: Cloud Foundry with bound GenAI service
- **AI Model**: Uses bound service (no API key required)
- **MCP Tools**: ✅ Enabled by default
- **Manifest**: `manifest-cloud.yml`

#### `application-cloud-openai.properties` - OpenAI API Key Profile  
- **Use Case**: Cloud Foundry with OpenAI API key
- **AI Model**: GPT-5-Nano via API key
- **MCP Tools**: ✅ Enabled by default
- **Manifest**: `manifest-cloud-openai.yml`

## Deployment Options

### Option 1: Bound Model (Default)
```bash
# Deploy with bound GenAI service + MCP tools
./build-and-push.sh

# Or explicitly specify profile
./build-and-push.sh -p cloud
```

**Manifest**: `manifest-cloud.yml`
**Profile**: `cloud`
**Features**: Bound AI service + MCP tools enabled

### Option 2: OpenAI API Key
```bash
# Deploy with OpenAI API key + MCP tools
./build-and-push.sh -p cloud-openai
```

**Manifest**: `manifest-cloud-openai.yml`
**Profile**: `cloud-openai`
**Features**: OpenAI API key + MCP tools enabled

## Build and Deploy Script

The `build-and-push.sh` script provides a comprehensive build and deployment solution:

### Basic Usage
```bash
# Build and deploy with default profile (cloud)
./build-and-push.sh

# Build only, no deploy
./build-and-push.sh -b

# Deploy with specific profile
./build-and-push.sh -p cloud-openai

# Run tests before building
./build-and-push.sh -p cloud-openai -t
```

### Script Features
- ✅ **Prerequisites Check**: Validates Maven, CF CLI, and environment
- ✅ **Environment Variables**: Sources `.env` file for API keys
- ✅ **Auto-Detection**: Automatically selects correct manifest
- ✅ **Profile Management**: Handles different deployment configurations
- ✅ **Error Handling**: Clear error messages and exit codes
- ✅ **Colored Output**: Professional logging with timestamps

## Environment Configuration

### Required Files
1. **`.env`** - Your environment variables (copy from `env.example`)
2. **`manifest-*.yml`** - Cloud Foundry deployment manifests

### Environment Variables
```bash
# OpenAI API Configuration (for cloud-openai profile)
OPENAI_API_KEY=your_openai_api_key_here

# Cloud Foundry Configuration
CF_ORG=your_org_name
CF_SPACE=your_space_name
CF_API=your_cf_api_endpoint

# MCP Server Configuration (enabled by default)
MCP_POLICY_SERVER_URL=https://imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com/
MCP_ACCIDENT_SERVER_URL=http://localhost:3002
```

## MCP Tools Configuration

### Default MCP Settings
- **Tool Callbacks**: ✅ Enabled by default
- **Server URL**: `https://imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com/`
- **SSE Endpoint**: `/sse`
- **Connection Resilience**: ✅ Enabled
- **Timeout**: 30 seconds
- **SSL Validation**: Configurable per environment

### Available Tools
1. **`answerQuestion(question, customerId)`** - Retrieves policy context data
2. **`queryInformation(question)`** - Retrieves reference information
3. **`queryCustomer(customerId)`** - Retrieves customer information

## Deployment Process

### 1. Prepare Environment
```bash
# Copy environment template
cp env.example .env

# Edit .env with your values
nano .env
```

### 2. Build and Deploy
```bash
# Deploy with bound model (default)
./build-and-push.sh

# Deploy with OpenAI API key
./build-and-push.sh -p cloud-openai

# Build only for testing
./build-and-push.sh -b
```

### 3. Verify Deployment
```bash
# Check app status
cf app imc-chatbot

# View logs
cf logs imc-chatbot --recent
```

## Troubleshooting

### Common Issues

#### Health Check Failures (Most Common)
- **Symptom**: `Instance became unhealthy: Liveness check unsuccessful: failed to make HTTP request to '/api/health' on port 8080: timed out after 1.00 seconds`
- **Cause**: Application taking too long to start up, especially with MCP connections
- **Solution**: 
  1. Use the updated manifests with proper health check timeouts
  2. The `/api/health` endpoint responds quickly without external dependencies
  3. Health check timeout is set to 10 seconds with 30-second invocation timeout

#### MCP Connection Failures
- **Symptom**: 404 errors or connection timeouts
- **Cause**: MCP server unavailable or network restrictions
- **Solution**: Verify MCP server is running and accessible

#### Configuration Conflicts
- **Symptom**: Spring Boot startup failures
- **Cause**: Profile not loading correctly
- **Solution**: Ensure correct profile is set in manifest

#### Build Failures
- **Symptom**: Maven compilation errors
- **Cause**: Missing dependencies or compilation issues
- **Solution**: Run `./build-and-push.sh -b -v` for verbose output

### Debug Commands
```bash
# Verbose build
./build-and-push.sh -p cloud -b -v

# Check current CF target
cf target

# Verify manifest syntax
cf push --dry-run -f manifest-cloud.yml

# Check application logs
cf logs imc-chatbot --recent

# Check application status
cf app imc-chatbot
```

### Health Check Configuration

The application now has multiple health check endpoints:

1. **`/api/health`** - Simple health check (used by Cloud Foundry)
   - Responds quickly with "OK"
   - No external dependency checks
   - Used for liveness probes

2. **`/api/liveness`** - Liveness probe endpoint
   - Responds with "ALIVE"
   - Quick response for container orchestration

3. **`/api/status`** - Detailed status endpoint
   - Full application status including MCP tools
   - May take longer due to external checks
   - Use for monitoring, not health checks

### Startup Time Configuration

The application is configured with:
- **Health check timeout**: 10 seconds
- **Health check invocation timeout**: 30 seconds
- **Shutdown phase timeout**: 30 seconds
- **Lazy initialization**: Disabled for faster startup

## File Structure
```
imc-chatbot/
├── src/main/resources/
│   ├── application.properties              # Base config (MCP enabled)
│   ├── application-cloud.properties       # Bound model profile
│   └── application-cloud-openai.properties # OpenAI API key profile
├── manifest-cloud.yml                     # Bound model manifest
├── manifest-cloud-openai.yml             # OpenAI API key manifest
├── build-and-push.sh                     # Build & deploy script
├── env.example                           # Environment template
└── DEPLOYMENT_GUIDE.md                   # This guide
```

## Best Practices

1. **Always use the build script**: Don't manually run `cf push`
2. **Test builds first**: Use `-b` flag to verify compilation
3. **Check prerequisites**: Ensure Maven and CF CLI are available
4. **Environment isolation**: Use separate `.env` files for different environments
5. **Profile consistency**: Ensure manifest and profile match your deployment needs

## Support

For deployment issues:
1. Check the logs: `cf logs imc-chatbot --recent`
2. Verify profile configuration: Check manifest files
3. Test build process: `./build-and-push.sh -b`
4. Review this guide for common solutions
