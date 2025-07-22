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
