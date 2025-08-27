#!/bin/bash

# IMC Chatbot Build and Deploy Script
# Standard interface: ./imc-chatbot.sh --profile <profile> [--deploy] [--help]
# Profiles: cloud (bound services), cloud-ai (OpenAI API keys)

set -e

# Default values
PROFILE=""
DEPLOY=false
BUILD=true
VERBOSE=false
HELP=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --profile)
            PROFILE="$2"
            shift 2
            ;;
        --deploy)
            DEPLOY=true
            shift
            ;;
        --build)
            BUILD=true
            shift
            ;;
        --no-build)
            BUILD=false
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --help|-h)
            HELP=true
            shift
            ;;
        *)
            echo "❌ Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Help message
if [ "$HELP" = true ]; then
    cat << 'EOF'
IMC Chatbot Build and Deploy Script

Usage: ./imc-chatbot.sh --profile <profile> [options]

Required:
  --profile <profile>     Deployment profile (cloud | cloud-ai)

Options:
  --deploy               Deploy to Cloud Foundry after build
  --no-build             Skip build, only deploy  
  --verbose              Enable verbose logging
  --help, -h             Show this help message

Profiles:
  cloud                  CF bound services (Qwen Ultra, etc.) - no API keys needed
  cloud-ai               OpenAI API keys - requires OPENAI_API_KEY environment variable

Examples:
  ./imc-chatbot.sh --profile cloud --deploy
  ./imc-chatbot.sh --profile cloud-ai --deploy --verbose
  ./imc-chatbot.sh --profile cloud --no-build --deploy

Environment Variables (for cloud-ai profile):
  OPENAI_API_KEY         Required for cloud-ai profile
EOF
    exit 0
fi

# Validate required arguments
if [ -z "$PROFILE" ]; then
    echo "❌ Error: --profile is required"
    echo "Available profiles: cloud, cloud-ai"
    echo "Use --help for full usage information"
    exit 1
fi

# Validate profile
case $PROFILE in
    cloud|cloud-ai)
        # Valid profiles
        ;;
    *)
        echo "❌ Error: Invalid profile '$PROFILE'"
        echo "Available profiles: cloud, cloud-ai"
        exit 1
        ;;
esac

# Validate cloud-ai specific requirements
if [ "$PROFILE" = "cloud-ai" ]; then
    if [ -z "$OPENAI_API_KEY" ]; then
        echo "❌ Error: OPENAI_API_KEY environment variable is required for cloud-ai profile"
        echo "Set it with: export OPENAI_API_KEY=your-api-key"
        exit 1
    fi
fi

echo "🚀 IMC Chatbot Build and Deploy"
echo "Profile: $PROFILE"
echo "Build: $BUILD"
echo "Deploy: $DEPLOY"
echo

# Build if requested
if [ "$BUILD" = true ]; then
    echo "🔨 Building application..."
    if [ "$VERBOSE" = true ]; then
        ./mvnw clean package
    else
        ./mvnw clean package -q
    fi
    
    if [ $? -eq 0 ]; then
        echo "✅ Build completed successfully"
    else
        echo "❌ Build failed"
        exit 1
    fi
    echo
fi

# Deploy if requested  
if [ "$DEPLOY" = true ]; then
    echo "☁️  Deploying to Cloud Foundry with profile: $PROFILE"
    
    # Select the appropriate manifest
    MANIFEST="manifest-${PROFILE}.yml"
    
    if [ ! -f "$MANIFEST" ]; then
        echo "❌ Error: Manifest file $MANIFEST not found"
        exit 1
    fi
    
    echo "Using manifest: $MANIFEST"
    
    # Deploy with appropriate manifest
    if [ "$VERBOSE" = true ]; then
        cf push imc-chatbot -f "$MANIFEST"
    else
        cf push imc-chatbot -f "$MANIFEST" --no-start
        cf start imc-chatbot
    fi
    
    if [ $? -eq 0 ]; then
        echo "✅ Deployment completed successfully"
        echo "🌐 Application URL: https://imc-chatbot.apps.tas-ndc.kuhn-labs.com"
        echo "🏥 Health Check: https://imc-chatbot.apps.tas-ndc.kuhn-labs.com/api/health"
    else
        echo "❌ Deployment failed"
        exit 1
    fi
    echo
fi

echo "🎉 Script completed successfully!"

# Show next steps
if [ "$DEPLOY" = true ]; then
    echo
    echo "Next steps:"
    echo "- Test the application: curl https://imc-chatbot.apps.tas-ndc.kuhn-labs.com/api/health"
    echo "- View logs: cf logs imc-chatbot --recent"
    echo "- Check status: cf app imc-chatbot"
fi