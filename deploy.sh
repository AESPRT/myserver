#!/bin/bash

set -e

APP_NAME="myserver"

echo "=========================================="
echo " 🚀 Deploying ${APP_NAME}"
echo "=========================================="


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
    git status --short
    echo ""
    echo "Commit or stash your changes before deploying."
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
# Validate Compose
# ==========================================

echo ""
echo "=========================================="
echo " 🔍 Validating Docker Compose"
echo "=========================================="

docker compose config > /dev/null

echo "✅ Docker Compose configuration is valid."


# ==========================================
# Build and start
# ==========================================

echo ""
echo "=========================================="
echo " 🏗️ Building and starting containers"
echo "=========================================="

docker compose up -d --build

echo ""
echo "✅ Docker Compose started."


# ==========================================
# Wait for services
# ==========================================

echo ""
echo "=========================================="
echo " ⏳ Waiting for services"
echo "=========================================="

MAX_ATTEMPTS=30
ATTEMPT=1

while [ $ATTEMPT -le $MAX_ATTEMPTS ]; do

    API_STATUS=$(docker inspect \
        --format='{{.State.Health.Status}}' \
        myserver-api 2>/dev/null || echo "missing")

    POSTGRES_STATUS=$(docker inspect \
        --format='{{.State.Health.Status}}' \
        myserver-postgres 2>/dev/null || echo "missing")

    echo "Attempt ${ATTEMPT}/${MAX_ATTEMPTS}"
    echo "  PostgreSQL: ${POSTGRES_STATUS}"
    echo "  Spring Boot: ${API_STATUS}"

    if [ "$POSTGRES_STATUS" = "healthy" ] && \
       [ "$API_STATUS" = "healthy" ]; then

        echo ""
        echo "✅ PostgreSQL is healthy."
        echo "✅ Spring Boot is healthy."
        break
    fi

    if [ "$API_STATUS" = "unhealthy" ]; then
        echo ""
        echo "❌ Spring Boot became unhealthy."
        echo ""
        docker compose logs --tail=100 springboot
        exit 1
    fi

    if [ "$POSTGRES_STATUS" = "unhealthy" ]; then
        echo ""
        echo "❌ PostgreSQL became unhealthy."
        echo ""
        docker compose logs --tail=100 postgres
        exit 1
    fi

    sleep 3

    ATTEMPT=$((ATTEMPT + 1))
done


if [ $ATTEMPT -gt $MAX_ATTEMPTS ]; then
    echo ""
    echo "❌ Services did not become healthy in time."
    echo ""
    docker compose ps
    echo ""
    echo "Spring Boot logs:"
    docker compose logs --tail=100 springboot
    exit 1
fi


# ==========================================
# Container status
# ==========================================

echo ""
echo "=========================================="
echo " 📦 Container Status"
echo "=========================================="

docker compose ps


# ==========================================
# Spring Boot logs
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
echo " ☁️ Cloudflare Tunnel"
echo "=========================================="

docker compose logs --tail=20 cloudflared


# ==========================================
# Complete
# ==========================================

echo ""
echo "=========================================="
echo " ✅ Deployment completed successfully"
echo "=========================================="

echo ""
echo "API:"
echo "  https://aeserver.aesprt.com"

echo ""
echo "Webhook:"
echo "  https://aeserver.aesprt.com/paymongo-webhook"

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