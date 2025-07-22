#!/bin/bash
# fix_docker_compose.sh - Fix Docker Compose YAML parsing issue

set -e

echo " Fixing Docker Compose YAML parsing issue..."

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Backup existing file if it exists
if [ -f "docker-compose.yml" ]; then
    print_warning "Backing up existing docker-compose.yml to docker-compose.yml.backup"
    cp docker-compose.yml docker-compose.yml.backup
fi

print_status "Creating corrected docker-compose.yml..."

# Create the main docker-compose.yml
cat > docker-compose.yml << 'EOF'
version: '3.8'

services:
  # Main application service
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - PORT=8080
      - AUTHORIZATION_TIMEOUT_MS=30000
      - CALLBACK_TIMEOUT_MS=10000
      - LOG_LEVEL=INFO
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 30s
    depends_on:
      - callback-mock
    networks:
      - async-charging-network

  # Mock callback server for testing
  callback-mock:
    image: kennethreitz/httpbin:latest
    ports:
      - "3000:80"
    networks:
      - async-charging-network

networks:
  async-charging-network:
    driver: bridge
EOF

print_status "Creating docker-compose.dev.yml for development..."

# Create the development override file
cat > docker-compose.dev.yml << 'EOF'
version: '3.8'

services:
  app:
    build:
      context: .
      dockerfile: Dockerfile.dev
    volumes:
      - .:/app
      - gradle_cache:/root/.gradle
    environment:
      - LOG_LEVEL=DEBUG
      - KTOR_DEVELOPMENT=true
    command: ["./gradlew", "run", "--continuous"]

volumes:
  gradle_cache:
EOF

print_status "Creating docker-compose.test.yml for testing..."

# Create the test configuration file
cat > docker-compose.test.yml << 'EOF'
version: '3.8'

services:
  app-test:
    build:
      context: .
      target: build
    command: ["./gradlew", "test", "jacocoTestReport"]
    environment:
      - LOG_LEVEL=DEBUG
    volumes:
      - test_reports:/app/build/reports

  callback-mock-test:
    image: kennethreitz/httpbin:latest
    ports:
      - "3000:80"

volumes:
  test_reports:
EOF

print_status "Creating enhanced Dockerfile.dev for development..."

# Create development Dockerfile
cat > Dockerfile.dev << 'EOF'
FROM gradle:8.5-jdk17

WORKDIR /app

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy gradle wrapper and build files
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./

# Download dependencies
RUN gradle dependencies --no-daemon

# Expose port
EXPOSE 8080

# Default command for development
CMD ["./gradlew", "run", "--continuous"]
EOF

print_status "Validating Docker Compose files..."

# Validate the files
if command -v docker-compose &> /dev/null; then
    docker-compose config > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        print_status " docker-compose.yml is valid"
    else
        print_error " docker-compose.yml validation failed"
        exit 1
    fi

    docker-compose -f docker-compose.yml -f docker-compose.dev.yml config > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        print_status " docker-compose.dev.yml override is valid"
    else
        print_error " docker-compose.dev.yml validation failed"
        exit 1
    fi
else
    print_warning "Docker Compose not installed, skipping validation"
fi

print_status "Creating usage instructions..."

cat > DOCKER_USAGE.md << 'EOF'
# Docker Usage Guide

## Quick Start

### Production Mode
```bash
# Build and start services
docker-compose up --build

# Run in background
docker-compose up -d --build

# View logs
docker-compose logs -f app
```

### Development Mode
```bash
# Start with development overrides (hot reload)
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up --build

# Or use the script
./scripts/dev.sh
```

### Testing Mode
```bash
# Run tests in Docker
docker-compose -f docker-compose.test.yml up --build --abort-on-container-exit

# Run tests and get reports
docker-compose -f docker-compose.test.yml up --build
docker cp $(docker-compose -f docker-compose.test.yml ps -q app-test):/app/build/reports ./test-reports
```

## Service URLs

- **Main Application**: http://localhost:8080
- **Health Check**: http://localhost:8080/health
- **Metrics**: http://localhost:8080/metrics
- **Mock Callback Server**: http://localhost:3000

## API Testing

```bash
# Test the charging session endpoint
curl -X POST http://localhost:8080/api/v1/charging-session \
  -H "Content-Type: application/json" \
  -d '{
    "station_id": "123e4567-e89b-12d3-a456-426614174000",
    "driver_token": "validDriverToken123",
    "callback_url": "http://callback-mock/post"
  }'
```

## Cleanup

```bash
# Stop services
docker-compose down

# Stop and remove volumes
docker-compose down -v

# Remove images
docker-compose down --rmi all
```
EOF

echo ""
print_status " Docker Compose files fixed successfully!"
echo ""
echo -e "${GREEN}Created files:${NC}"
echo "  - docker-compose.yml (main configuration)"
echo "  - docker-compose.dev.yml (development overrides)"
echo "  - docker-compose.test.yml (testing configuration)"
echo "  - Dockerfile.dev (development container)"
echo "  - DOCKER_USAGE.md (usage guide)"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "1. Test the configuration: docker-compose config"
echo "2. Build and run: docker-compose up --build"
echo "3. For development: docker-compose -f docker-compose.yml -f docker-compose.dev.yml up --build"
echo ""
print_status " Ready to run with Docker!"







