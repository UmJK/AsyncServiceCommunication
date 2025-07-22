# High Level Overview

## System Architecture

The Asynchronous Service Communication system is designed to handle EV charging session requests without blocking the public API, using a producer-consumer pattern with asynchronous processing.

## Core Components

### 1. API Layer (Ktor REST Server)
- **Purpose**: Accepts HTTP requests from driver applications
- **Technology**: Ktor with Netty engine
- **Behavior**: Non-blocking, immediate response
- **Validation**: Input sanitization for UUID, driver tokens, and callback URLs

### 2. Authorization Queue (In-Memory Buffer)
- **Implementation**: `LinkedBlockingQueue<ChargingRequest>`
- **Purpose**: Decouples API from authorization processing
- **Thread Safety**: Built-in thread-safe operations
- **Backpressure**: Natural queuing mechanism prevents overload

### 3. Queue Consumer (Background Processor)
- **Technology**: Kotlin Coroutines
- **Pattern**: Single consumer polling queue continuously
- **Processing**: Sequential request handling with timeout management
- **Error Handling**: Graceful degradation on service failures

### 4. Authorization Service
- **Current**: Simulated ACL (Access Control List) checking
- **Future**: External microservice integration
- **Decision Logic**: Returns `allowed`, `not_allowed`, `unknown`, or `invalid`
- **Timeout**: 30-second configurable timeout

### 5. Callback Service
- **Implementation**: Ktor WebClient
- **Purpose**: Delivers authorization decisions to client callback URLs
- **Timeout**: 10-second configurable timeout
- **Error Handling**: Logs failures, planned retry mechanism

## Request Flow

```
Driver App → API Controller → Authorization Queue → Queue Consumer → Authorization Service
                    ↓                                                        ↓
            Immediate Response                                    Callback Service
                                                                       ↓
                                                               Client Callback URL
```

## Data Models

### ChargingRequest
```kotlin
data class ChargingRequest(
    val stationId: String,      // UUID format
    val driverToken: String,    // 20-80 chars, alphanumeric + special chars
    val callbackUrl: String     // Valid HTTP/HTTPS endpoint
)
```

### AuthorizationDecision
```kotlin
data class AuthorizationDecision(
    val stationId: String,
    val driverToken: String,
    val status: AuthorizationStatus  // ALLOWED, NOT_ALLOWED, UNKNOWN, INVALID
)
```

### CallbackPayload
```kotlin
data class CallbackPayload(
    val station_id: String,
    val driver_token: String,
    val status: String
)
```

## Key Design Decisions

### Asynchronous Processing
- **Benefit**: API remains responsive under load
- **Trade-off**: Eventual consistency model
- **Implementation**: Kotlin coroutines for lightweight concurrency

### In-Memory Queue
- **Current**: Simple `LinkedBlockingQueue` for demonstration
- **Production**: Ready for external message brokers (Kafka, Redis, RabbitMQ)
- **Consideration**: Data loss risk on application restart

### Timeout Strategy
- **Authorization**: 30-second timeout prevents hanging requests
- **Callback**: 10-second timeout for client notification
- **Default Behavior**: Unknown status on timeout

### Error Handling
- **Service Failures**: Graceful degradation with meaningful status codes
- **Callback Failures**: Logged for monitoring, no retry currently
- **Validation Errors**: Immediate rejection with clear error messages

## Configuration

The system uses `application.conf` for environment-specific settings:

```hocon
ktor {
    deployment.port = 8080
    application.modules = [ com.chargepoint.asynccharging.ApplicationKt.module ]
}

authorization {
    timeoutMillis = 30000
}

callback {
    timeoutMillis = 10000
}
```

## Testing Strategy

### Unit Tests
- **Authorization Queue**: Enqueue/dequeue operations
- **Callback Service**: HTTP client behavior
- **Validators**: Input validation logic
- **Controllers**: Request/response handling

### Integration Tests
- **End-to-End**: Full request flow simulation
- **Timeout Scenarios**: Service failure handling
- **Error Cases**: Invalid input processing

## Monitoring & Observability

### Current Logging
- Request processing stages
- Authorization decisions
- Callback delivery results
- Error conditions with stack traces

### Future Enhancements
- Metrics collection (request rates, processing times)
- Distributed tracing (request correlation)
- Health checks and service monitoring
- Dashboard for operational visibility

## Security Considerations

### Input Validation
- UUID format validation for station identifiers
- Regex validation for driver tokens
- URL format validation for callbacks
- Sanitization to prevent injection attacks

### Network Security
- HTTPS enforcement for production callbacks
- Timeout limits to prevent resource exhaustion
- Input size limits to prevent memory attacks

## Performance Characteristics

### Throughput
- **API Response**: Sub-millisecond response times
- **Queue Processing**: Single-threaded sequential processing
- **Bottleneck**: Authorization service response time

### Scalability Limits
- **Memory**: Queue size limited by available heap
- **Processing**: Single consumer thread
- **Network**: Callback delivery concurrency

### Resource Usage
- **Memory**: Lightweight coroutines vs heavyweight threads
- **CPU**: Non-blocking I/O operations
- **Network**: Efficient HTTP client connection pooling