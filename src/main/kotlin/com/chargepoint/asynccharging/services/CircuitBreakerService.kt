package com.chargepoint.asynccharging.services

import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

enum class CircuitBreakerState { CLOSED, OPEN, HALF_OPEN }

data class CircuitBreakerConfig(
    val failureThreshold: Int = 5,
    val recoveryTimeout: Duration = 30.seconds,
    val samplingWindow: Duration = 60.seconds
)

class CircuitBreakerService(
    private val config: CircuitBreakerConfig = CircuitBreakerConfig()
) {
    private val circuitBreakers = ConcurrentHashMap<String, CircuitBreaker>()
    
    suspend fun <T> execute(name: String, operation: suspend () -> T): T {
        val circuitBreaker = circuitBreakers.computeIfAbsent(name) { CircuitBreaker(config) }
        return circuitBreaker.execute(operation)
    }
    
    private class CircuitBreaker(private val config: CircuitBreakerConfig) {
        private var state = CircuitBreakerState.CLOSED
        private val failureCount = AtomicInteger(0)
        private val lastFailureTime = AtomicLong(0)
        
        suspend fun <T> execute(operation: suspend () -> T): T {
            when (state) {
                CircuitBreakerState.OPEN -> {
                    if (shouldAttemptReset()) {
                        state = CircuitBreakerState.HALF_OPEN
                        logger.info { "Circuit breaker transitioning to HALF_OPEN" }
                    } else {
                        throw RuntimeException("Circuit breaker is OPEN")
                    }
                }
                CircuitBreakerState.HALF_OPEN -> {
                    // Allow one request through
                }
                CircuitBreakerState.CLOSED -> {
                    // Normal operation
                }
            }
            
            return try {
                val result = operation()
                onSuccess()
                result
            } catch (e: Exception) {
                onFailure()
                throw e
            }
        }
        
        private fun onSuccess() {
            failureCount.set(0)
            if (state == CircuitBreakerState.HALF_OPEN) {
                state = CircuitBreakerState.CLOSED
                logger.info { "Circuit breaker reset to CLOSED" }
            }
        }
        
        private fun onFailure() {
            val failures = failureCount.incrementAndGet()
            lastFailureTime.set(System.currentTimeMillis())
            
            if (failures >= config.failureThreshold && state == CircuitBreakerState.CLOSED) {
                state = CircuitBreakerState.OPEN
                logger.warn { "Circuit breaker opened due to $failures failures" }
            } else if (state == CircuitBreakerState.HALF_OPEN) {
                state = CircuitBreakerState.OPEN
                logger.warn { "Circuit breaker reopened after failed recovery attempt" }
            }
        }
        
        private fun shouldAttemptReset(): Boolean {
            val timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime.get()
            return timeSinceLastFailure >= config.recoveryTimeout.inWholeMilliseconds
        }
    }
}
