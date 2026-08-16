#!/bin/bash

set -e

APP_NAME="myserver"

echo "=========================================="
echo " 🚀 Deploying ${APP_NAME}"
echo "=========================================="

# ==========================================
# Check current directory
# ==========================================

if [ ! -f "compose.yaml" ] && [ ! -f "compose.yml" ]; then
    echo "❌ compose.yaml / compose.yml not found."
    echo "Please run this script from the project directory."
    exit 1
fi

# ==========================================
# Environment check
# ==========================================

echo ""
echo "=========================================="
echo " 🔐 Checking environment configuration"
echo "=========================================="

if [ ! -f ".env" ]; then
    echo "❌ .env file not found."
    exit 1
fi

echo "✅ .env found."

# ==========================================
# Git check
# ==========================================

echo ""
echo "=========================================="
echo " 📥 Updating source code"
echo "=========================================="

if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "❌ Local Git changes detected."
    echo ""
    echo "Commit/stash your changes before deploying."
    git status --short
    exit 1
fi

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
# Validate Compose configuration
# ==========================================

echo ""
echo "=========================================="
echo " 🔍 Validating Docker Compose"
echo "=========================================="

docker compose config > /dev/null

echo "✅ Docker Compose configuration is valid."

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
# Wait for PostgreSQL
# ==========================================

echo ""
echo "=========================================="
echo " 🗄️ Waiting for PostgreSQL"
echo "=========================================="

POSTGRES_READY=false

for i in {1..30}; do
    if docker compose exec -T postgres \
        pg_isready \
        -U "${POSTGRES_USER}" \
        -d "${POSTGRES_DB}" > /dev/null 2>&1; then

        POSTGRES_READY=true
        echo "✅ PostgreSQL is ready."
        break
    fi

    echo "⏳ PostgreSQL is not ready yet... ($i/30)"
    sleep 2
done

if [ "$POSTGRES_READY" = false ]; then
    echo "❌ PostgreSQL did not become ready."
    echo ""
    echo "PostgreSQL logs:"
    docker compose logs --tail=50 postgres
    exit 1
fi

# ==========================================
# Wait for Spring Boot
# ==========================================

echo ""
echo "=========================================="
echo " ❤️ Waiting for Spring Boot"
echo "=========================================="

SPRINGBOOT_READY=false

for i in {1..30}; do

    if docker compose exec -T springboot \
        sh -c 'echo > /dev/tcp/127.0.0.1/8086' \
        > /dev/null 2>&1; then

        SPRINGBOOT_READY=true
        echo "✅ Spring Boot is listening on port 8086."
        break
    fi

    echo "⏳ Spring Boot is not ready yet... ($i/30)"
    sleep 2
done

if [ "$SPRINGBOOT_READY" = false ]; then
    echo "❌ Spring Boot did not become ready."
    echo ""
    echo "Spring Boot logs:"
    docker compose logs --tail=100 springboot
    exit 1
fi

# ==========================================
# Container Status
# ==========================================

echo ""
echo "=========================================="
echo " 📦 Container Status"
echo "=========================================="

docker compose ps

# ==========================================
# Spring Boot Logs
# ==========================================

echo ""
echo "=========================================="
echo " 📋 Recent Spring Boot Logs"
echo "=========================================="

docker compose logs --tail=30 springboot

# ==========================================
# Cloudflare Tunnel
# ==========================================

echo ""
echo "=========================================="
echo " ☁️ Cloudflare Tunnel Status"
echo "=========================================="

docker compose logs --tail=20 cloudflared

# ==========================================
# Deployment completed
# ==========================================

echo ""
echo "=========================================="
echo " ✅ Deployment completed successfully"
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
echo "PostgreSQL logs:"
echo "  docker compose logs -f postgres"

echo ""
echo "Cloudflare logs:"
echo "  docker compose logs -f cloudflared"

echo ""