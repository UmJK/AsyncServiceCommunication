#!/bin/bash
echo "🚀 Starting development environment..."
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up --build
