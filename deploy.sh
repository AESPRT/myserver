#!/bin/bash

set -e

APP_NAME="myserver"

echo "=========================================="
echo " 🚀 Deploying ${APP_NAME}"
echo "=========================================="

echo ""
echo "🐳 Checking Docker..."

if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running or current user has no permission."
    exit 1
fi

echo "✅ Docker is available."

echo ""
echo "🔧 Checking Docker Compose..."

if ! docker compose version > /dev/null 2>&1; then
    echo "❌ Docker Compose is not available."
    exit 1
fi

echo "✅ Docker Compose is available."

echo ""
echo "🔐 Checking environment configuration..."

if [ ! -f ".env" ]; then
    echo "❌ .env file not found."
    exit 1
fi

echo "✅ .env found."

echo ""
echo "🔨 Checking Gradle..."

if [ ! -f "./gradlew" ]; then
    echo "❌ Gradle wrapper not found."
    exit 1
fi

chmod +x ./gradlew

echo "✅ Gradle wrapper found."

# ==========================================
# Build application
# ==========================================

echo ""
echo "=========================================="
echo " 🏗️ Building Spring Boot application"
echo "=========================================="

./gradlew clean build -x test

echo ""
echo "✅ Spring Boot build completed."

# ==========================================
# Build Docker image
# ==========================================

echo ""
echo "=========================================="
echo " 🐳 Building Docker image"
echo "=========================================="

docker compose build springboot

echo ""
echo "✅ Docker image built."

# ==========================================
# Start / recreate API
# ==========================================

echo ""
echo "=========================================="
echo " 🚀 Starting Spring Boot"
echo "=========================================="

docker compose up -d --no-deps springboot

echo ""
echo "✅ Spring Boot container started."

# ==========================================
# Ensure supporting services are running
# ==========================================

echo ""
echo "=========================================="
echo " 🗄️ Checking PostgreSQL"
echo "=========================================="

docker compose up -d postgres

echo ""
echo "=========================================="
echo " ☁️ Checking Cloudflare Tunnel"
echo "=========================================="

docker compose up -d cloudflared

# ==========================================
# Wait
# ==========================================

echo ""
echo "⏳ Waiting for application..."

sleep 8

# ==========================================
# Status
# ==========================================

echo ""
echo "=========================================="
echo " 📦 Container Status"
echo "=========================================="

docker compose ps

# ==========================================
# Health check
# ==========================================

echo ""
echo "=========================================="
echo " ❤️ API Check"
echo "=========================================="

if curl -fsS http://localhost:8086/ > /dev/null 2>&1; then
    echo "✅ API is responding on port 8086."
else
    echo "⚠️ API root endpoint did not return a successful response."
    echo "This may be normal if '/' is not mapped."
fi

# ==========================================
# Logs
# ==========================================

echo ""
echo "=========================================="
echo " 📋 Recent Spring Boot Logs"
echo "=========================================="

docker compose logs --tail=30 springboot

echo ""
echo "=========================================="
echo " ✅ Deployment completed"
echo "=========================================="

echo ""
echo "API:"
echo "  http://localhost:8086"

echo ""
echo "Webhook:"
echo "  /paymongo-webhook"

echo ""
echo "Logs:"
echo "  docker compose logs -f springboot"

echo ""
echo "Cloudflare:"
echo "  docker compose logs -f cloudflared"

echo ""