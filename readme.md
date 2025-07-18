# Asynchronous EV Charging Session Service
> **Kotlin / Coroutines / Docker / Ktor**

---

## 1. Purpose
Start an EV-charging session **without ever blocking the public API**.  
We **decouple** the REST endpoint from the internal authorization micro-service via an **asynchronous, in-memory queue**.  
This keeps the API snappy under load and protects the auth service from overload.

---

## 2. High-Level Flow

```text
Driver App ──POST──►  API Endpoint ──►  AuthorizationQueue (BlockingQueue)
                                                │
                                                ▼
                       ┌────────────────────────────────────────┐
                       │  Queue Consumer (Kotlin Coroutine)     │
                       │  • ACL Lookup                          │
                       │  • Timeout Configured                 │
                       │  • CallbackPayload → CallbackService   │
                       └────────────────────────────────────────┘
                                                │
                                                ▼
                       ┌────────────────────────────────────────┐
                       │  CallbackService (Ktor WebClient)       │
                       │  • POST to client callback_url          │
                       │  • Retry/backoff logic (to be added)    │
                       └────────────────────────────────────────┘
```

---

## 3. Core Components

| Feature                    | Technology Choices                               |
|---------------------------|--------------------------------------------------|
| HTTP Server               | Ktor (Netty Engine)                              |
| Async Processing          | Kotlin Coroutines                                |
| Serialization             | kotlinx.serialization                            |
| Input Validation          | Custom Regex Validators                          |
| Logging                   | Logback                                          |
| Testing                   | JUnit5, kotest, MockK, Ktor Test Engine          |
| Containerization          | Docker                                           |
| Queue Implementation      | `LinkedBlockingQueue` (in-memory demo)           |

---

## 4. Folder Structure Rationale

- **`plugins/`**: Ktor setup (routing, content negotiation)
- **`controllers/`**: REST endpoints (non-blocking)
- **`services/`**: Auth logic, callback logic
- **`queue/`**: `AuthorizationQueue` as singleton in-memory queue
- **`models/`**: `ChargingRequest`, `AuthorizationDecision`, `CallbackPayload`
- **`utils/`**: `Validator` (UUID, driver token, URL)
- **`test/`**: Unit tests (`CallbackServiceTest`, `AuthorizationQueueTest`)
- **`resources/application.conf`**: Ktor config (`port`, `timeouts`, etc.)

---

## 5. API Contract

### `POST /api/v1/charging/start`

**Request**
```json
{
  "station_id": "123e4567-e89b-12d3-a456-426614174000",
  "driver_token": "ABCD-efgh1234567890_~valid.token",
  "callback_url": "https://client.app/api/callbacks/charge-result"
}
```

**Response**
```json
{
  "status": "accepted",
  "message": "Request queued for async processing."
}
```

**Callback Payload**
```json
{
  "station_id": "123e4567-e89b-12d3-a456-426614174000",
  "driver_token": "ABCD-efgh1234567890_~valid.token",
  "status": "allowed"
}
```

---

## 6. Quick Start

```bash
git clone https://github.com/UmJK/async-charging-service.git
cd async-charging-service
./gradlew clean run
```

If port `8080` is in use:
```bash
lsof -i :8080
kill -9 <PID>
```
Or update `application.conf`:
```hocon
ktor.deployment.port = 9090
```

---

## 7. Testing

```bash
./gradlew test
```
Includes:
- Enqueue/dequeue validation
- Callback service correctness
- Input validation logic

---

## 8. Configuration (`application.conf`)

```hocon
ktor {
  deployment {
    port = 8080
    development = true
  }
  application {
    modules = [ com.chargepoint.asynccharging.ApplicationKt.module ]
  }
}

authorization {
  timeoutMillis = 30000
}

callback {
  timeoutMillis = 10000
}

logging {
  level = INFO
}
```

---

## 9. Extensibility Plan

| Goal                          | Strategy                                   |
|------------------------------|--------------------------------------------|
| Real ACL microservice        | Replace inline ACL check with HTTP call    |
| Replace in-memory queue      | Plug in Kafka, Redis Stream, RabbitMQ      |
| Retry Callback with Backoff  | Implement retry with exponential strategy  |
| Observability                | Add metrics, tracing (Micrometer/Zipkin)   |
| Idempotency Support          | Add Redis-backed deduplication store       |

---

## 10. Roadmap

- [ ] Retry + backoff for callbacks
- [ ] Authorization decision caching
- [ ] Auth and callback stats dashboard
- [ ] Dead-letter queue for failed callbacks
- [ ] Chaos testing (failover/timeout simulations)






