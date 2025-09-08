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
PROFILE="default"
VERBOSE=false
MCP_ENABLED=false
KILL_PREVIOUS=true

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

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --rebuild          Clean and rebuild the project before running"
    echo "  --mcp              Enable MCP profile for tool integration"
    echo "  --verbose          Enable verbose logging"
    echo "  --port PORT        Set server port (default: 8080)"
    echo "  --no-kill          Don't kill previous instances (for multiple instances)"
    echo "  --help             Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                 # Run with default settings"
    echo "  $0 --mcp           # Run with MCP tools enabled"
    echo "  $0 --rebuild       # Clean build and run"
    echo "  $0 --mcp --verbose # Run with MCP and debug logging"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --rebuild)
            REBUILD=true
            shift
            ;;
        --mcp)
            MCP_ENABLED=true
            PROFILE="mcp"
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
    export $(cat .env | grep -v '^#' | xargs)
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

# Build profiles
SPRING_PROFILES="default"
if [ "$MCP_ENABLED" = true ]; then
    SPRING_PROFILES="$SPRING_PROFILES,mcp"
    print_info "MCP profile enabled - tools will be available when MCP servers are connected"
fi

# Build the application if needed
if [ "$REBUILD" = true ]; then
    print_info "Rebuilding application..."
    ./mvnw clean package -DskipTests
fi

# Check if JAR exists, build if not
JAR_FILE="imc-chatbot-app/target/imc-chatbot-app-1.0.0.jar"
if [ ! -f "$JAR_FILE" ]; then
    print_info "JAR file not found, building application..."
    ./mvnw clean package -DskipTests
fi

if [ ! -f "$JAR_FILE" ]; then
    print_error "Failed to find or build JAR file: $JAR_FILE"
    exit 1
fi

# Export server port if specified
if [ -n "$SERVER_PORT" ]; then
    export SERVER_PORT
fi

print_success "Environment configured successfully"
print_info "Profiles: $SPRING_PROFILES"
print_info "Server port: ${SERVER_PORT:-8080}"
print_info "MCP enabled: $MCP_ENABLED"

print_info "Starting IMC Chatbot application..."

# Run the application
exec java $JAVA_OPTS \
    -Dspring.profiles.active="$SPRING_PROFILES" \
    -jar "$JAR_FILE" \
    "$@"