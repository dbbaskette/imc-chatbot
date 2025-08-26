#!/bin/bash

# IMC Chatbot Build and Deploy Script
# Usage: ./build-and-push.sh [OPTIONS]

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
PROFILE="cloud"
MANIFEST="manifest-cloud.yml"
BUILD_ONLY=false
SKIP_TESTS=true
VERBOSE=false

# Function to print usage
print_usage() {
    echo -e "${BLUE}IMC Chatbot Build and Deploy Script${NC}"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "OPTIONS:"
    echo "  -p, --profile PROFILE     Deployment profile (default: cloud)"
    echo "                            Options: cloud, cloud-openai"
    echo "                            Note: MCP tools are enabled by default in all profiles"
    echo "  -m, --manifest MANIFEST   Manifest file to use (default: auto-detected)"
    echo "  -b, --build-only          Build only, don't deploy"
    echo "  -t, --run-tests           Run tests before building"
    echo "  -v, --verbose             Verbose output"
    echo "  -h, --help                Show this help message"
    echo ""
    echo "EXAMPLES:"
    echo "  $0                                    # Deploy with cloud profile (bound model + MCP)"
    echo "  $0 -p cloud-openai                   # Deploy with OpenAI API key + MCP"
    echo "  $0 -b                                # Build only, no deploy"
    echo "  $0 -p cloud-openai -t               # Deploy with OpenAI API key + MCP and run tests"
    echo ""
    echo "PROFILES:"
    echo "  cloud        - Cloud Foundry with bound GenAI service + MCP tools"
    echo "  cloud-openai - Cloud Foundry with OpenAI API key + MCP tools"
    echo ""
}

# Function to print colored output
log() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check prerequisites
check_prerequisites() {
    log "Checking prerequisites..."
    
    # Check if Maven is available
    if ! command -v mvn &> /dev/null; then
        error "Maven is not installed or not in PATH"
        exit 1
    fi
    
    # Check if cf CLI is available
    if ! command -v cf &> /dev/null; then
        error "Cloud Foundry CLI is not installed or not in PATH"
        exit 1
    fi
    
    # Check if .env file exists
    if [ ! -f .env ]; then
        warn ".env file not found. Some configurations may not work properly."
    else
        log "Found .env file"
    fi
    
    log "Prerequisites check passed"
}

# Function to source environment variables
source_env() {
    if [ -f .env ]; then
        log "Sourcing .env file..."
        set -a  # automatically export all variables
        source .env
        set +a
        log "Environment variables loaded"
    fi
}

# Function to determine manifest based on profile
determine_manifest() {
    case $PROFILE in
        "cloud")
            MANIFEST="manifest-cloud.yml"
            ;;
        "cloud-openai")
            MANIFEST="manifest-cloud-openai.yml"
            ;;
        *)
            error "Unknown profile: $PROFILE"
            exit 1
            ;;
    esac
    
    if [ ! -f "$MANIFEST" ]; then
        error "Manifest file not found: $MANIFEST"
        exit 1
    fi
    
    log "Using manifest: $MANIFEST"
}

# Function to build the application
build_app() {
    log "Building application with profile: $PROFILE"
    
    # Set Maven options
    MAVEN_OPTS=""
    if [ "$SKIP_TESTS" = true ]; then
        MAVEN_OPTS="$MAVEN_OPTS -DskipTests"
    fi
    
    if [ "$VERBOSE" = true ]; then
        MAVEN_OPTS="$MAVEN_OPTS -X"
    fi
    
    # Build command
    log "Running: mvn clean package $MAVEN_OPTS"
    mvn clean package $MAVEN_OPTS
    
    if [ $? -eq 0 ]; then
        log "Build successful!"
        
        # Check if JAR was created
        if [ -f "target/imc-chatbot-*.jar" ]; then
            JAR_FILE=$(ls target/imc-chatbot-*.jar | head -1)
            JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)
            log "JAR created: $JAR_FILE ($JAR_SIZE)"
        else
            # Try alternative pattern matching
            JAR_FILES=$(find target -name "imc-chatbot-*.jar" -type f)
            if [ -n "$JAR_FILES" ]; then
                JAR_FILE=$(echo "$JAR_FILES" | head -1)
                JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)
                log "JAR created: $JAR_FILE ($JAR_SIZE)"
            else
                error "JAR file not found after build"
                exit 1
            fi
        fi
    else
        error "Build failed!"
        exit 1
    fi
}

# Function to deploy to Cloud Foundry
deploy_app() {
    log "Deploying to Cloud Foundry..."
    
    # Check if logged in to CF
    if ! cf target &> /dev/null; then
        error "Not logged in to Cloud Foundry. Please run 'cf login' first."
        exit 1
    fi
    
    # Show current target
    log "Current CF target:"
    cf target
    
    # Deploy using the manifest
    log "Deploying with manifest: $MANIFEST"
    cf push -f "$MANIFEST"
    
    if [ $? -eq 0 ]; then
        log "Deployment successful!"
        
        # Show app status
        log "Application status:"
        cf app imc-chatbot
        
        # Show app URL
        APP_URL=$(cf app imc-chatbot --guid | xargs cf curl /v2/apps/ | jq -r '.entity.routes[0].host + "." + .entity.routes[0].domain.name' 2>/dev/null || echo "URL not available")
        if [ "$APP_URL" != "URL not available" ]; then
            log "Application URL: https://$APP_URL"
        fi
    else
        error "Deployment failed!"
        exit 1
    fi
}

# Function to show deployment summary
show_summary() {
    echo ""
    echo -e "${BLUE}=== Deployment Summary ===${NC}"
    echo "Profile: $PROFILE"
    echo "Manifest: $MANIFEST"
    echo "Build Only: $BUILD_ONLY"
    echo "Tests: $([ "$SKIP_TESTS" = true ] && echo "Skipped" || echo "Run")"
    echo ""
    
    if [ "$BUILD_ONLY" = true ]; then
        log "Build completed successfully. Use 'cf push -f $MANIFEST' to deploy manually."
    else
        log "Build and deployment completed successfully!"
    fi
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -p|--profile)
            PROFILE="$2"
            shift 2
            ;;
        -m|--manifest)
            MANIFEST="$2"
            shift 2
            ;;
        -b|--build-only)
            BUILD_ONLY=true
            shift
            ;;
        -t|--run-tests)
            SKIP_TESTS=false
            shift
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -h|--help)
            print_usage
            exit 0
            ;;
        *)
            error "Unknown option: $1"
            print_usage
            exit 1
            ;;
    esac
done

# Main execution
main() {
    log "Starting IMC Chatbot build and deploy process..."
    
    # Check prerequisites
    check_prerequisites
    
    # Source environment variables
    source_env
    
    # Determine manifest if not specified
    if [ "$MANIFEST" = "manifest-cloud.yml" ]; then
        determine_manifest
    fi
    
    # Build the application
    build_app
    
    # Deploy if not build-only
    if [ "$BUILD_ONLY" = false ]; then
        deploy_app
    fi
    
    # Show summary
    show_summary
}

# Run main function
main "$@"
