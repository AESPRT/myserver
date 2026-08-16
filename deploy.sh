#!/bin/bash

set -e

APP_NAME="myserver"

echo "=========================================="
echo " 🚀 Deploying ${APP_NAME}"
echo "=========================================="

# ==========================================
# Update source code
# ==========================================

echo ""
echo "=========================================="
echo " 📥 Pulling latest code from Git"
echo "=========================================="

git pull --ff-only

echo ""
echo "✅ Latest code pulled."

# ==========================================
# Docker check
# ==========================================

echo ""
echo "🐳 Checking Docker..."

if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running or current user has no permission."
    exit 1
fi

echo "✅ Docker is available."

# ==========================================
# Docker Compose check
# ==========================================

echo ""
echo "🔧 Checking Docker Compose..."

if ! docker compose version > /dev/null 2>&1; then
    echo "❌ Docker Compose is not available."
    exit 1
fi

echo "✅ Docker Compose is available."

# ==========================================
# Environment check
# ==========================================

echo ""
echo "🔐 Checking environment configuration..."

if [ ! -f ".env" ]; then
    echo "❌ .env file not found."
    exit 1
fi

echo "✅ .env found."

# ==========================================
# Build and deploy
# ==========================================

echo ""
echo "=========================================="
echo " 🏗️ Building and starting containers"
echo "=========================================="

docker compose up -d --build

echo ""
echo "✅ Containers started."

# ==========================================
# Status
# ==========================================

echo ""
echo "=========================================="
echo " 📦 Container Status"
echo "=========================================="

docker compose ps

# ==========================================
# Wait for application
# ==========================================

echo ""
echo "⏳ Waiting for Spring Boot..."

sleep 10

# ==========================================
# API health check
# ==========================================

echo ""
echo "=========================================="
echo " ❤️ API Check"
echo "=========================================="

if curl -fsS http://localhost:8086/ > /dev/null 2>&1; then
    echo "✅ API is responding on port 8086."
else
    echo "⚠️ API root endpoint did not return a successful response."
    echo "Check the Spring Boot logs."
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
echo " ☁️ Cloudflare Tunnel Status"
echo "=========================================="

docker compose logs --tail=20 cloudflared

echo ""
echo "=========================================="
echo " ✅ Deployment completed"
echo "=========================================="

echo ""
echo "API:"
echo "  https://YOUR_API_DOMAIN"

echo ""
echo "Webhook:"
echo "  /paymongo-webhook"

echo ""
echo "Spring Boot logs:"
echo "  docker compose logs -f springboot"

echo ""
echo "Cloudflare logs:"
echo "  docker compose logs -f cloudflared"

echo ""