#!/usr/bin/env bash
set -Eeuo pipefail

# Smart Pantry — one-command startup
# Requires: Docker Desktop (https://www.docker.com/products/docker-desktop/)
# Usage:    ./start.sh

echo ""
echo "  Smart Pantry Manager"
echo "  ─────────────────────"
echo ""

# Check Docker is installed and running
if ! command -v docker &>/dev/null; then
    echo "  Docker is not installed."
    echo "  Download it from https://www.docker.com/products/docker-desktop/ and try again."
    exit 1
fi

if ! docker info &>/dev/null; then
    echo "  Docker is installed but not running."
    echo "  Open Docker Desktop and wait for it to start, then run ./start.sh again."
    exit 1
fi

echo "  Starting the database and API..."
docker compose up -d --build

echo ""
echo "  Waiting for the API to be ready..."
for i in $(seq 1 30); do
    if curl --fail --silent --max-time 2 http://127.0.0.1:8080/api/recipes >/dev/null; then
        break
    fi
    sleep 2
done

if ! curl --fail --silent --max-time 2 http://127.0.0.1:8080/api/recipes >/dev/null; then
    echo ""
    echo "  The API did not start in time. Check the logs with:"
    echo "    docker compose logs api"
    exit 1
fi

echo ""
echo "  ✓ API is running at http://localhost:8080"
echo ""
echo "  Now open Android Studio, connect an emulator, and run the app."
echo "  The app connects to the API automatically via 10.0.2.2:8080."
echo ""
echo "  To stop everything:  docker compose down"
echo "  To view logs:        docker compose logs -f"
echo ""
