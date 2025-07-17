# Asynchronous EV Charging Session Service
> **Kotlin / Coroutines / Redis Streams / Docker**

---

## 1. Purpose
Start an EV-charging session **without ever blocking the public API**.  
We **decouple** the REST endpoint from the internal authorization micro-service via an **asynchronous, back-pressured queue**.  
This keeps the API snappy under load and protects the auth service from overload.

---

## 2. High-Level Flow

```text
Driver App ──POST──►  API Gateway  ──►  Redis Stream
                                      (charging.requests)
                                                    │
                                                    ▼
                           ┌────────────────────────────────────┐
                           │  Authorization Worker (Kotlin coro)│
                           │  • ACL lookup                      │
                           │  • Timeout = 5 s                   │
                           │  • Decision → Redis Stream         │
                           └────────────────────────────────────┘
                                                    │
                                                    ▼
                           ┌────────────────────────────────────┐
                           │  Callback Dispatcher (WebClient)   │
                           │  • POST to driver’s callback_url   │
                           │  • Retry w/ exponential backoff    │
                           └────────────────────────────────────┘
```
---

## 3. Core Components
Table

Feature	             Spring Boot	  Ktor (Recommended)	   Custom Server (Netty/Undertow)
Complexity	         Medium	          Low	                   High
Performance	         Good	          Very Good	               Excellent
Ecosystem	         Extensive	      Growing	               Minimal
Learning             Curve	          Medium	               Low	High
Control	             Medium	          High	                   Very High

Feature	Technology              Choices
HTTP Server	                    Ktor (Netty Engine / CIO Engine)
Asynchronous Processing	        Kotlin Coroutines
Serialization (JSON)	        Kotlinx Serialization
Input Validation	            Custom Validators (Regex-based)
Logging	                        Logback
Testing	                        JUnit 5, Kotest, Ktor Test Engine, MockK
Containerization	            Docker, Docker Compose
CI/CD	                        GitHub Actions

## 4. Folder Structure Rationale:
Ktor Plugins (plugins/):
Contains setup for routing, serialization, and input validation, leveraging Ktor’s modular plugin architecture.

Controllers (controllers/):
HTTP endpoints handlers with clearly defined validation and immediate asynchronous response acknowledgment, fulfilling PDF requirements.

Services (services/):
Clearly separated services managing async queuing, authorization checks, and callback operations.

Queue (queue/):
Implements a lightweight in-memory queue (BlockingQueue) suitable for demonstration, easily swappable to external solutions like Kafka, RabbitMQ, Redis.

Repository (repository/):
In-memory authorization logic storage and decision persistence, matching PDF specifications.

Models (models/):
Strongly typed Kotlin data classes with built-in validation annotations, matching PDF format and validation rules.

Utils (utils/):
Validation helpers for common tasks (UUID, URL).

Config (resources/application.conf):
Ktor’s HOCON-based configuration for clean and readable setups.

Logging (resources/logback.xml):
Customizable and efficient logging configurations with Logback.

Testing (test/):
Comprehensive tests across all layers (controllers, services, queues), essential for ensuring quality and correct async behavior.

## 4. API Contract
POST /api/v1/charging/start
Request
{
  "station_id":   "123e4567-e89b-12d3-a456-426614174000", // UUID v4
  "driver_token": "ABCD-efgh1234567890_~valid.token",      // 20-80 chars
  "callback_url": "https://client.app/api/callbacks/charge-result"
}
Immediate Response
{
  "status": "accepted",
  "message": "Request queued for async processing."
}

Callback Payload
{
  "station_id":   "123e4567-e89b-12d3-a456-426614174000",
  "driver_token": "ABCD-efgh1234567890_~valid.token",
  "status":       "allowed"        // allowed | not_allowed | unknown | invalid
}

## 6. Quick Start
Docker (single command)
git clone https://github.com/your-org/async-charging-service.git
cd async-charging-service
docker compose up --build
API → http://localhost:8080
WireMock callback dashboard → http://localhost:8081/__admin
Grafana metrics → http://localhost:3000 (admin / admin)
Local Gradle
./gradlew bootRun --args='--spring.profiles.active=local'

## 7. Testing
./gradlew test                     # unit + integration
./gradlew koverHtmlReport          # coverage report
Sample Integration Test (Kotest)
kotlin
class AsyncFlowTest : FunSpec({
    test("happy path") {
        val callback = wiremock.post("/charge-result").willReturn(ok())

        apiClient.startSession(
            stationId = UUID.randomUUID(),
            driverToken = "A".repeat(25),
            callbackUrl = callback.url()
        ).status shouldBe HttpStatus.ACCEPTED

        eventually(5.seconds) {
            wiremock.verifyThat(
                postRequestedFor(urlEqualTo("/charge-result"))
                    .withRequestBody(containing(""""status":"allowed""""))
            )
        }
    }
})

## 8. Configuration
application.yml
yaml

spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: 6379
authorization:
  timeout: 5s
  acl:
    allowed:
      - "ABCD-efgh1234567890_~valid.token"
callback:
  retry:
    max-attempts: 3
    backoff: 1s

## 9.  Extensibility & Scaling
Table
Copy
Need	Plug-in
Real ACL micro-service	Replace AuthorizationWorker with HTTP WebClient
Kafka	Implement StreamBridge interface backed by Kafka
Persistence	Swap InMemoryDecisionStore with Spring Data R2DBC
Rate-limit	Add Bucket4j filter before controller
Observability	Enable Sleuth → Zipkin traces across coroutines
## 10.  Roadmap
[ ] Idempotency keys (deduplication via Redis)
[ ] Signed callbacks (HMAC-SHA256 header)
[ ] Dead-letter stream for failed callbacks
[ ] Chaos testing with Toxiproxy & Redis failover



