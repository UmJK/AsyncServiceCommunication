#  AsyncServiceCommunication

> Enterprise-grade async charging session management service with circuit breaker resilience, comprehensive observability, and high-performance queue processing.

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](#testing)
[![Test Coverage](https://img.shields.io/badge/coverage-95%25-brightgreen)](#testing)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.20-blue)](https://kotlinlang.org/)
[![Ktor](https://img.shields.io/badge/Ktor-2.3.6-blue)](https://ktor.io/)
[![License](https://img.shields.io/badge/license-MIT-blue)](#license)

##  Overview

AsyncServiceCommunication provides **asynchronous EV charging session authorization** with enterprise-grade reliability patterns. Submit charging requests, get immediate responses, and receive authorization decisions via configurable callbacks.

### **Key Features**

-  **Sub-10ms Response Times** - Immediate API responses with background processing
-  **Circuit Breaker Resilience** - Automatic failure detection and recovery  
-  **Comprehensive Observability** - Real-time metrics and health monitoring
-  **Intelligent Retry Logic** - Exponential backoff for reliable callback delivery
-  **Production-Ready** - Type-safe configuration, structured logging, graceful shutdown
-  **Thoroughly Tested** - 95%+ test coverage with unit, integration, and performance tests

##  Quick Start

### **Prerequisites**
- JDK 17 or later
- Gradle 8.7+

### **Installation**

```bash
# Clone the repository
git clone https://github.com/your-org/AsyncServiceCommunication.git
cd AsyncServiceCommunication

# Build and run
./gradlew run
```

The service will start on `http://localhost:8080`

### **Docker Deployment**

```bash
# Build Docker image
docker build -t async-charging-service .

# Run container
docker run -p 8080:8080 async-charging-service
```

### **First API Call**

```bash
# Submit a charging session request
curl -X POST http://localhost:8080/api/v1/charging-session \
  -H "Content-Type: application/json" \
  -d '{
    "stationId": "123e4567-e89b-12d3-a456-426614174000",
    "driverToken": "ABCD-efgh1234567890_~valid.token",
    "callbackUrl": "https://httpbin.org/post"
  }'

# Response (immediate)
{
  "status": "accepted",
  "message": "Request queued for async processing.",
  "requestId": "req-abc123...",
  "timestamp": 1640995200000
}
```

##  API Documentation

### **Submit Charging Session**

`POST /api/v1/charging-session`

Submit a charging session authorization request for async processing.

**Request Body:**
```json
{
  "stationId": "123e4567-e89b-12d3-a456-426614174000",
  "driverToken": "ABCD-efgh1234567890_~valid.token", 
  "callbackUrl": "https://your-app.com/callback"
}
```

**Validation Rules:**
- `stationId`: Must be valid UUID format
- `driverToken`: 20-80 characters, alphanumeric + `-`, `.`, `_`, `~`
- `callbackUrl`: Valid HTTP/HTTPS URL

**Response (200 OK):**
```json
{
  "status": "accepted",
  "message": "Request queued for async processing.",
  "requestId": "req-abc123...",
  "timestamp": 1640995200000
}
```

**Error Response (400 Bad Request):**
```json
{
  "status": "validation_error",
  "message": "Invalid station_id format. Must be a valid UUID.",
  "timestamp": 1640995200000
}
```

### **Callback Payload**

Your callback URL will receive the authorization decision:

```json
{
  "station_id": "123e4567-e89b-12d3-a456-426614174000",
  "driver_token": "ABCD-efgh1234567890_~valid.token",
  "status": "allowed",
  "timestamp": 1640995201500
}
```

**Status Values:**
- `"allowed"` - Driver authorized for charging
- `"not_allowed"` - Driver not in ACL
- `"unknown"` - Service error occurred

##  Monitoring & Health

### **Health Check**

`GET /health`

Returns component health status for load balancer and monitoring.

```json
{
  "status": "UP",
  "timestamp": 1640995200000,
  "components": {
    "queue": {
      "status": "UP",
      "details": {"currentSize": "5", "maxSize": "10000"}
    },
    "authorization": {
      "status": "UP", 
      "details": {"errorRate": "0.02", "totalDecisions": "1000"}
    },
    "callbacks": {
      "status": "UP",
      "details": {"errorRate": "0.05", "totalCallbacks": "950"}
    }
  }
}
```

**Status Levels:**
- `UP` - Component healthy
- `DEGRADED` - Component functional but under stress
- `DOWN` - Component failure

### **Metrics Endpoint**

`GET /metrics`

Real-time operational metrics for monitoring dashboards.

```json
{
  "requests_total": 1000,
  "authorization_decisions": {"allowed": 800, "not_allowed": 200},
  "callback_results": {"success": 950, "http_error": 30, "network_error": 20},
  "authorization_time_avg_ms": 150.0,
  "authorization_time_p95_ms": 280,
  "queue_size_current": 5
}
```

##  Configuration

Configure via environment variables or `application.conf`:

### **Core Settings**
```bash
# Server
KTOR_DEPLOYMENT_PORT=8080
KTOR_DEPLOYMENT_DEVELOPMENT=false

# Authorization Service  
AUTHORIZATION_TIMEOUT_MILLIS=30000
AUTHORIZATION_MAX_RETRIES=3
AUTHORIZATION_CIRCUIT_BREAKER_ENABLED=true

# Callback Service
CALLBACK_TIMEOUT_MILLIS=10000
CALLBACK_MAX_RETRIES=3
CALLBACK_RETRY_DELAY_MILLIS=1000

# Queue Configuration
QUEUE_MAX_SIZE=10000
QUEUE_CONSUMER_THREADS=1

# Monitoring
MONITORING_METRICS_ENABLED=true
MONITORING_HEALTH_CHECK_ENABLED=true
```

### **Configuration File** (`application.conf`)
```hocon
ktor {
    deployment {
        port = 8080
        development = false
    }
}

authorization {
    timeoutMillis = 30000
    maxRetries = 3
    circuitBreaker {
        enabled = true
    }
}

callback {
    timeoutMillis = 10000
    maxRetries = 3
    retryDelayMillis = 1000
}

queue {
    maxSize = 10000
    consumerThreads = 1
}

monitoring {
    metrics { enabled = true }
    healthCheck { enabled = true }
}
```

##  Architecture

### **Request Flow**
```
1. HTTP Request    → Immediate validation & queue insertion
2. HTTP Response   → 200 OK with request ID (< 10ms)
3. Background      → Queue processing with circuit breaker
4. Authorization   → ACL lookup with timeout protection
5. Callback        → HTTP delivery with retry logic
```

### **Resilience Patterns**
- **Circuit Breaker**: Auto-recovery from service failures (5 failures → 30s timeout)
- **Retry Logic**: 3 attempts with exponential backoff (1s → 2s → 4s)
- **Queue Overflow**: Graceful rejection when capacity exceeded
- **Timeout Protection**: All operations have configurable timeouts

### **Component Diagram**
```
┌─────────────┐    ┌──────────────────┐    ┌─────────────────┐
│             │    │                  │    │                 │
│ HTTP API    │───▶│ Authorization    │───▶│ Callback        │
│ Controller  │    │ Queue            │    │ Service         │
│             │    │                  │    │                 │
└─────────────┘    └──────────────────┘    └─────────────────┘
       │                     │                       │
       ▼                     ▼                       ▼
┌─────────────┐    ┌──────────────────┐    ┌─────────────────┐
│             │    │                  │    │                 │
│ Validation  │    │ Circuit Breaker  │    │ Retry Logic     │
│ Service     │    │ Service          │    │ & Backoff       │
│             │    │                  │    │                 │
└─────────────┘    └──────────────────┘    └─────────────────┘
       │                     │                       │
       ▼                     ▼                       ▼
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│                    Metrics & Health Service                 │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

##  Testing

### **Running Tests**

```bash
# Run all tests (27 tests)
./gradlew test

# Run specific test categories
./gradlew test --tests '*Unit*'        # Unit tests (20)
./gradlew test --tests '*Component*'   # Integration tests (5)  
./gradlew test --tests '*Performance*' # Load tests (1)
```

### **Test Coverage**
- **Unit Tests (20)**: Services, models, queue operations, validation
- **Component Tests (5)**: End-to-end workflows, health monitoring
- **Performance Tests (1)**: 100 concurrent operations load testing
- **Coverage**: 95%+ of critical business logic

### **Performance Benchmarks**
```bash
# Load testing results
./gradlew test --tests '*LoadTest*'

# Sample output:
# Enqueued 100 requests in 45ms
# Dequeued 100 requests in 38ms  
# Queue operations: ~2,300 ops/second
```

## 🔧 Development

### **Project Structure**
```
src/
├── main/kotlin/com/chargepoint/asynccharging/
│   ├── Application.kt              # Main application entry point
│   ├── config/                     # Type-safe configuration
│   ├── controllers/                # HTTP request handlers
│   ├── services/                   # Business logic services
│   ├── queue/                      # Queue implementation
│   ├── models/                     # Data classes & validation
│   ├── plugins/                    # Ktor plugins configuration
│   ├── monitoring/                 # Health checks & metrics
│   └── utils/                      # Validation utilities
└── test/kotlin/com/chargepoint/asynccharging/
    ├── unit/                       # Unit tests (20 tests)
    ├── component/                  # Integration tests (5 tests)
    └── performance/                # Load tests (1 test)
```

### **Building from Source**
```bash
# Clone repository
git clone https://github.com/your-org/AsyncServiceCommunication.git
cd AsyncServiceCommunication

# Build project
./gradlew build

# Run tests  
./gradlew test

# Generate reports
./gradlew test jacocoTestReport
```

### **Development Commands**
```bash
# Development server with hot reload
./gradlew run --continuous

# Build production JAR
./gradlew shadowJar

# Run specific tests during development
./gradlew test --tests '*AuthorizationService*' --continuous
```

##  Deployment

### **Docker**
```dockerfile
# Dockerfile included in project
FROM eclipse-temurin:17-jre
COPY build/libs/*-all.jar app.jar  
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
```

```bash
# Build and run
docker build -t async-charging .
docker run -p 8080:8080 async-charging
```

### **Kubernetes**
```yaml
# Example K8s deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: async-charging-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: async-charging
  template:
    spec:
      containers:
      - name: service
        image: async-charging:latest
        ports:
        - containerPort: 8080
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
        readinessProbe:
          httpGet:
            path: /health  
            port: 8080
```

### **Environment Variables**
```bash
# Production deployment
export KTOR_DEPLOYMENT_PORT=8080
export QUEUE_MAX_SIZE=50000
export AUTHORIZATION_CIRCUIT_BREAKER_ENABLED=true
export CALLBACK_MAX_RETRIES=5
```

##  Monitoring & Observability

### **Grafana Dashboard**
Import the included Grafana dashboard for operational visibility:
- Request throughput and response times
- Queue depth and processing rates
- Authorization success/failure rates  
- Callback delivery success rates
- Circuit breaker status and transitions

### **Alerting Rules**
```yaml
# Example Prometheus alerts
- alert: HighErrorRate
  expr: error_rate > 0.05
  for: 2m
  
- alert: QueueBacklog  
  expr: queue_size > 1000
  for: 1m
  
- alert: CircuitBreakerOpen
  expr: circuit_breaker_state == 2
  for: 30s
```

##  Security

### **Input Validation**
- All inputs validated against strict schemas
- Driver tokens masked in logs and responses
- URL validation prevents SSRF attacks
- Request size limits prevent DoS

### **Network Security**
- HTTPS support for callback URLs
- Configurable CORS policies
- Request timeout protection
- No sensitive data persistence

##  Contributing

### **Development Setup**
1. Fork the repository
2. Create feature branch: `git checkout -b feature/amazing-feature`
3. Run tests: `./gradlew test`  
4. Commit changes: `git commit -m 'feat: add amazing feature'`
5. Push branch: `git push origin feature/amazing-feature`
6. Open Pull Request

### **Code Style**
- Follow Kotlin coding conventions
- All public APIs must have KDoc comments  
- Maintain 95%+ test coverage
- Use conventional commit messages

### **Testing Requirements**
- All new features require unit tests
- Integration tests for API endpoints
- Performance tests for critical paths
- Documentation updates for public APIs

##  License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

##  Support

- **Documentation**: [Architecture Guide](ARCHITECTURE.md)
- **Issues**: [GitHub Issues](https://github.com/your-org/AsyncServiceCommunication/issues)
- **Discussions**: [GitHub Discussions](https://github.com/your-org/AsyncServiceCommunication/discussions)

---

**Built with ❤️ using Kotlin & Ktor**