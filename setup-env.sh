#!/bin/bash

# IMC Chatbot Environment Setup Script
# This script helps you set up your environment variables for the chatbot

set -e

ENV_FILE=".env"
TEMPLATE_FILE=".env.template"

echo "🤖 === IMC Chatbot Environment Setup === 🤖"
echo ""

# Check if .env already exists
if [ -f "$ENV_FILE" ]; then
    echo "⚠️  .env file already exists!"
    read -p "Do you want to overwrite it? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Setup cancelled. Existing .env file preserved."
        exit 0
    fi
fi

# Copy template to .env
if [ ! -f "$TEMPLATE_FILE" ]; then
    echo "❌ Template file $TEMPLATE_FILE not found!"
    exit 1
fi

cp "$TEMPLATE_FILE" "$ENV_FILE"
echo "✅ Created .env file from template"

# Make it clear this file should not be committed
echo ""
echo "📝 Now edit the .env file and fill in your actual values:"
echo "   - OPENAI_API_KEY: Your OpenAI API key (required)"
echo "   - SERVER_PORT: Port to run on (optional, default: 8080)"
echo "   - Other variables as needed"
echo ""

# Prompt for OpenAI API key
echo "🔑 OpenAI API Key Setup:"
echo "You can get your API key from: https://platform.openai.com/api-keys"
echo ""
read -p "Enter your OpenAI API key (or press Enter to skip): " -r openai_key

if [ ! -z "$openai_key" ]; then
    # Escape any special characters for sed
    escaped_key=$(printf '%s\n' "$openai_key" | sed 's/[[\.*^$()+?{|]/\\&/g')
    
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        sed -i '' "s/your_openai_api_key_here/$escaped_key/" "$ENV_FILE"
    else
        # Linux
        sed -i "s/your_openai_api_key_here/$escaped_key/" "$ENV_FILE"
    fi
    echo "✅ OpenAI API key added to .env file"
else
    echo "⏭️  Skipped OpenAI API key - you'll need to edit .env manually"
fi

echo ""
echo "🎉 Environment setup complete!"
echo ""
echo "📋 Next steps:"
echo "   1. Review and edit .env file if needed: nano .env"
echo "   2. Run the chatbot: ./imc-chatbot.sh --profile local"
echo "   3. Open web UI: http://localhost:8080"
echo ""
echo "⚠️  IMPORTANT: .env file is git-ignored and contains secrets"
echo "   Never commit .env files to version control!"
echo ""