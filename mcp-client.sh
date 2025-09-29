#!/bin/bash

# IMC Chatbot Startup Script
# This script handles environment setup and application startup

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
REBUILD=false
VERBOSE=false
KILL_PREVIOUS=true
CF_DEPLOY=false
MCP_ENABLED=false

# New logical flags
RUN_LOCAL=true        # --local or --cf
USE_LOCAL_MCP=true    # --local-mcp or --remote-mcp (default: local MCP for --local)
USE_LOCAL_MODEL=true  # --public-model overrides to use OpenAI API instead

PROFILE="default"

# Function to print colored output
print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Function to kill previous instances
kill_previous_instances() {
    print_info "Checking for previous IMC Chatbot instances..."
    
    # Find processes running our specific JAR file or main class
    PIDS=$(ps aux | grep -E "(imc-chatbot-app.*\.jar|ImcChatbotApplication)" | grep -v grep | awk '{print $2}')
    
    if [ -n "$PIDS" ]; then
        print_warning "Found running instances, stopping them..."
        for PID in $PIDS; do
            print_info "Killing process $PID"
            kill -TERM $PID 2>/dev/null || true
        done
        
        # Wait a moment for graceful shutdown
        sleep 2
        
        # Force kill if still running
        REMAINING_PIDS=$(ps aux | grep -E "(imc-chatbot-app.*\.jar|ImcChatbotApplication)" | grep -v grep | awk '{print $2}')
        if [ -n "$REMAINING_PIDS" ]; then
            print_warning "Force killing remaining processes..."
            for PID in $REMAINING_PIDS; do
                kill -KILL $PID 2>/dev/null || true
            done
        fi
        
        print_success "Previous instances stopped"
    else
        print_info "No previous instances found"
    fi
    
    # Also check for processes using the target port
    PORT=${SERVER_PORT:-8080}
    PORT_PID=$(lsof -ti :$PORT 2>/dev/null || true)
    if [ -n "$PORT_PID" ]; then
        print_warning "Found process using port $PORT (PID: $PORT_PID), stopping it..."
        kill -TERM $PORT_PID 2>/dev/null || true
        sleep 1
        # Force kill if still running
        if lsof -ti :$PORT >/dev/null 2>&1; then
            kill -KILL $PORT_PID 2>/dev/null || true
        fi
        print_success "Port $PORT freed"
    fi
}

# Function to deploy to Cloud Foundry
deploy_to_cf() {
    print_info "Deploying to Cloud Foundry..."

    # Check if cf CLI is available
    if ! command -v cf &> /dev/null; then
        print_error "Cloud Foundry CLI (cf) not found. Please install it first."
        print_info "Download from: https://github.com/cloudfoundry/cli/releases"
        exit 1
    fi

    # Check if logged in to CF
    if ! cf target &> /dev/null; then
        print_error "Not logged in to Cloud Foundry. Please run 'cf login' first."
        exit 1
    fi

    # Show current CF target
    print_info "Current CF target:"
    cf target

    # Find the latest JAR file
    JAR_FILE=$(find imc-chatbot-app/target -name "imc-chatbot-app-*.jar" -not -name "*-sources.jar" -not -name "*-javadoc.jar" | sort -V | tail -1)

    # Ensure JAR is built
    if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
        print_info "Building application for CF deployment..."
        ./mvnw clean package -DskipTests

        # Try to find JAR again after build
        JAR_FILE=$(find imc-chatbot-app/target -name "imc-chatbot-app-*.jar" -not -name "*-sources.jar" -not -name "*-javadoc.jar" | sort -V | tail -1)
    fi

    if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
        print_error "Failed to find or build JAR file for CF deployment"
        exit 1
    fi

    print_info "Using JAR file: $JAR_FILE"

    # Deploy using manifest
    print_info "Pushing application to Cloud Foundry..."
    cf push

    # Show app status
    print_success "Application deployed successfully!"
    print_info "App status:"
    cf app imc-chatbot

    # Show routes
    print_info "App routes:"
    cf routes | grep imc-chatbot || print_info "No routes found for imc-chatbot"
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Where to run:"
    echo "  --local            Run locally (default)"
    echo "  --cf               Deploy to Cloud Foundry"
    echo ""
    echo "Which MCP server:"
    echo "  --local-mcp        Use local MCP server (localhost:8082)"
    echo "  --remote-mcp       Use remote MCP server (CF-hosted)"
    echo ""
    echo "Model override:"
    echo "  --public-model     Use OpenAI API instead of local model"
    echo ""
    echo "Other options:"
    echo "  --rebuild          Clean and rebuild the project before running"
    echo "  --verbose          Enable verbose logging"
    echo "  --port PORT        Set server port (default: 8080)"
    echo "  --no-kill          Don't kill previous instances (for multiple instances)"
    echo "  --help             Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                          # Run locally with local model + local MCP (default)"
    echo "  $0 --local --remote-mcp     # Local model + remote MCP"
    echo "  $0 --local --local-mcp      # Everything local (same as default)"
    echo "  $0 --local --remote-mcp --public-model # OpenAI API + remote MCP"
    echo "  $0 --cf --public-model      # Deploy to CF with OpenAI API + remote MCP"
    echo "  $0 --cf                     # Deploy to CF with bound models (no MCP)"
    echo "  $0 --rebuild --verbose      # Clean build with debug logging"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --local)
            RUN_LOCAL=true
            CF_DEPLOY=false
            shift
            ;;
        --cf)
            RUN_LOCAL=false
            CF_DEPLOY=true
            shift
            ;;
        --local-mcp)
            USE_LOCAL_MCP=true
            MCP_ENABLED=true
            shift
            ;;
        --remote-mcp)
            USE_LOCAL_MCP=false
            MCP_ENABLED=true
            shift
            ;;
        --public-model)
            USE_LOCAL_MODEL=false
            shift
            ;;
        --rebuild)
            REBUILD=true
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --port)
            SERVER_PORT="$2"
            shift 2
            ;;
        --no-kill)
            KILL_PREVIOUS=false
            shift
            ;;
        --help)
            show_usage
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

print_info "Starting IMC Chatbot..."

# Determine the Spring profiles based on flags (using composable profiles)
PROFILES="default"

if [ "$CF_DEPLOY" = true ]; then
    # Cloud Foundry deployment
    PROFILES="$PROFILES,cf"

    if [ "$MCP_ENABLED" = true ]; then
        if [ "$USE_LOCAL_MCP" = true ]; then
            print_error "Cannot use local MCP server with CF deployment"
            exit 1
        fi
        if [ "$USE_LOCAL_MODEL" = true ]; then
            print_error "Cannot use local model with CF deployment (use --public-model for CF)"
            exit 1
        fi
        PROFILES="$PROFILES,remote-mcp"
        print_info "CF deployment with remote MCP server + public model"
    else
        print_info "CF deployment with bound models, no MCP"
    fi

    deploy_to_cf
    exit 0
else
    # Local deployment
    PROFILES="$PROFILES,local"

    if [ "$MCP_ENABLED" = true ]; then
        if [ "$USE_LOCAL_MCP" = true ]; then
            PROFILES="$PROFILES,local-mcp"
            if [ "$USE_LOCAL_MODEL" = true ]; then
                print_info "Local run: local model + local MCP server"
            else
                print_info "Local run: OpenAI API + local MCP server"
            fi
        else
            # Remote MCP
            PROFILES="$PROFILES,remote-mcp"
            if [ "$USE_LOCAL_MODEL" = true ]; then
                print_info "Local run: local model + remote MCP server"
            else
                print_info "Local run: OpenAI API + remote MCP server"
            fi
        fi
    else
        # No MCP - for default behavior, enable local MCP when using local model
        if [ "$USE_LOCAL_MODEL" = true ]; then
            USE_LOCAL_MCP=true
            MCP_ENABLED=true
            PROFILES="$PROFILES,local-mcp"
            print_info "Local run: local model + local MCP server (default behavior)"
        else
            print_info "Local run: bound models, no MCP"
        fi
    fi
fi

# Kill any previous instances (unless disabled)
if [ "$KILL_PREVIOUS" = true ]; then
    kill_previous_instances
else
    print_info "Skipping previous instance cleanup (--no-kill specified)"
fi

# Check if .env file exists
if [ ! -f .env ]; then
    print_warning ".env file not found"
    print_info "Creating .env file from template..."
    
    if [ -f .env.example ]; then
        cp .env.example .env
        print_warning "Please edit .env file and add your OpenAI API key"
        print_info "Get your API key from: https://platform.openai.com/api-keys"
        exit 1
    else
        print_error ".env.example file not found"
        exit 1
    fi
fi

# Load environment variables from .env file
if [ -f .env ]; then
    print_info "Loading environment variables from .env"
    # Use a more compatible approach for macOS
    while IFS= read -r line; do
        # Skip empty lines and comments
        [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
        # Export the line if it contains an equals sign
        if [[ "$line" =~ = ]]; then
            export "$line"
        fi
    done < .env

    # Debug: show that the key is loaded
    if [ -n "$OPENAI_API_KEY" ]; then
        print_info "OPENAI_API_KEY loaded (${#OPENAI_API_KEY} characters)"
    fi
fi

# Check for required environment variables
if [ -z "$OPENAI_API_KEY" ]; then
    print_error "OPENAI_API_KEY not set in .env file"
    print_info "Please add your OpenAI API key to the .env file"
    exit 1
fi

# Set up Java options
JAVA_OPTS=""
if [ "$VERBOSE" = true ]; then
    JAVA_OPTS="$JAVA_OPTS -Dlogging.level.com.insurancemegacorp.imcchatbot=DEBUG"
    JAVA_OPTS="$JAVA_OPTS -Dlogging.level.org.springframework.ai=DEBUG"
fi

# Use the profiles determined earlier
SPRING_PROFILES="$PROFILES"

# Build the application if needed
if [ "$REBUILD" = true ]; then
    print_info "Rebuilding application..."
    ./mvnw clean package -DskipTests
fi

# Find the latest JAR file
JAR_FILE=$(find imc-chatbot-app/target -name "imc-chatbot-app-*.jar" -not -name "*-sources.jar" -not -name "*-javadoc.jar" | sort -V | tail -1)

if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
    print_info "JAR file not found, building application..."
    ./mvnw clean package -DskipTests
    
    # Try to find JAR again after build
    JAR_FILE=$(find imc-chatbot-app/target -name "imc-chatbot-app-*.jar" -not -name "*-sources.jar" -not -name "*-javadoc.jar" | sort -V | tail -1)
fi

if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
    print_error "Failed to find or build JAR file in imc-chatbot-app/target/"
    print_info "Looking for files matching: imc-chatbot-app-*.jar"
    ls -la imc-chatbot-app/target/*.jar 2>/dev/null || print_info "No JAR files found in target directory"
    exit 1
fi

print_info "Using JAR file: $JAR_FILE"

# Export server port if specified
if [ -n "$SERVER_PORT" ]; then
    export SERVER_PORT
fi

print_success "Environment configured successfully"
print_info "Profiles: $SPRING_PROFILES"
print_info "Server port: ${SERVER_PORT:-8080}"
# Show configuration status
if [ "$MCP_ENABLED" = true ]; then
    if [ "$USE_LOCAL_MCP" = true ]; then
        print_info "MCP server: LOCAL (localhost:8082)"
    else
        print_info "MCP server: REMOTE (https://imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com)"
    fi
else
    print_info "MCP: DISABLED"
fi

if [ "$USE_LOCAL_MODEL" = true ]; then
    print_info "Model: LOCAL (http://127.0.0.1:1234)"
else
    print_info "Model: PUBLIC (OpenAI API)"
fi

print_info "Starting IMC Chatbot application..."

# Run the application
# Pass OpenAI environment variables as system properties for profiles that need them
OPENAI_SYSTEM_PROPS=""
if [ -n "$OPENAI_API_KEY" ]; then
    # For profiles that use OpenAI API (when local profile is active)
    if [[ "$PROFILES" == *"local"* ]]; then
        OPENAI_SYSTEM_PROPS="-Dspring.ai.openai.api-key=$OPENAI_API_KEY"

        # Add base URL for local models
        if [ "$USE_LOCAL_MODEL" = true ] && [ -n "$OPENAI_BASE_URL" ]; then
            OPENAI_SYSTEM_PROPS="$OPENAI_SYSTEM_PROPS -Dspring.ai.openai.base-url=$OPENAI_BASE_URL"
        fi

        # Override base URL for --public-model with local profile
        if [ "$USE_LOCAL_MODEL" = false ]; then
            OPENAI_SYSTEM_PROPS="$OPENAI_SYSTEM_PROPS -Dspring.ai.openai.base-url=https://api.openai.com"
        fi

        # Add model specification
        if [ -n "$OPENAI_MODEL" ]; then
            OPENAI_SYSTEM_PROPS="$OPENAI_SYSTEM_PROPS -Dspring.ai.openai.chat.options.model=$OPENAI_MODEL"
        fi

        # Override model for --public-model
        if [ "$USE_LOCAL_MODEL" = false ]; then
            OPENAI_SYSTEM_PROPS="$OPENAI_SYSTEM_PROPS -Dspring.ai.openai.chat.options.model=${OPENAI_MODEL:-gpt-4o-mini}"
        fi
    fi
fi

exec java $JAVA_OPTS \
    -Dspring.profiles.active="$SPRING_PROFILES" \
    $OPENAI_SYSTEM_PROPS \
    -jar "$JAR_FILE" \
    "$@"