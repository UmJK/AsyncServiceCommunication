#!/bin/bash
echo "🔨 Building project..."
./gradlew clean build

echo ""
echo "🐳 Building Docker image..."
docker build -t asyncservicecommunication:latest .

echo ""
echo "✅ Build completed successfully!"
