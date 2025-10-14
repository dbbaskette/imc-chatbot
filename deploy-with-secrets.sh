#!/bin/bash

# Deploy script using CF CLI --vars-file for secrets
# This keeps secrets out of version control while allowing easy deployment
#
# Usage:
#   ./deploy-with-secrets.sh           # Deploy without building
#   ./deploy-with-secrets.sh --build   # Build then deploy
#   ./deploy-with-secrets.sh -b        # Build then deploy (short form)

set -e  # Exit on error

# Parse command line arguments
BUILD=false
if [[ "$1" == "--build" ]] || [[ "$1" == "-b" ]]; then
    BUILD=true
fi

echo "🚀 IMC Chatbot Deployment with Secrets"
echo "======================================="

# Build if requested
if [ "$BUILD" = true ]; then
    echo "🔨 Building application..."
    echo ""

    if [ ! -f "mvnw" ]; then
        echo "❌ Error: mvnw not found!"
        exit 1
    fi

    ./mvnw clean package -DskipTests

    echo ""
    echo "✅ Build complete!"
    echo ""
fi

# Check if secrets.yml exists
if [ ! -f "secrets.yml" ]; then
    echo "❌ Error: secrets.yml not found!"
    echo "📝 Please copy secrets.yml.template to secrets.yml and fill in your values:"
    echo "   cp secrets.yml.template secrets.yml"
    exit 1
fi

# Check if manifest.yml exists
if [ ! -f "manifest.yml" ]; then
    echo "❌ Error: manifest.yml not found!"
    exit 1
fi

echo "📦 Using CF CLI --vars-file for secret substitution"
echo ""

# Show deployment info (with redacted secrets)
echo "📄 Deployment Configuration:"
echo "================================================"
echo "Manifest: manifest.yml"
echo "Vars file: secrets.yml"
echo "Variables will be substituted: ((OPENAI_API_KEY)), etc."
echo ""

# Confirm deployment
read -p "🤔 Deploy to Cloud Foundry? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "❌ Deployment cancelled"
    exit 0
fi

# Deploy using CF CLI --vars-file
echo "🚢 Deploying to Cloud Foundry..."
cf push --vars-file secrets.yml

echo ""
echo "✅ Deployment complete!"
echo "📊 Check app status: cf app imc-chatbot"
echo "📝 View logs: cf logs imc-chatbot --recent"
