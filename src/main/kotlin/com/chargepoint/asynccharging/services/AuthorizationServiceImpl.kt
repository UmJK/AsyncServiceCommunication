package com.chargepoint.asynccharging.services

import com.chargepoint.asynccharging.config.AuthorizationConfig
import com.chargepoint.asynccharging.models.decisions.AuthorizationDecision
import com.chargepoint.asynccharging.models.enums.AuthorizationStatus
import com.chargepoint.asynccharging.models.requests.ChargingRequest
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {}

class AuthorizationServiceImpl(
    private val config: AuthorizationConfig,
    private val circuitBreakerService: CircuitBreakerService,
    private val metricsService: MetricsService
) : AuthorizationService {
    
    // Simple ACL - In production, this would be externalized
    private val authorizedDrivers = setOf(
        "validDriverToken123",
        "ABCD-efgh1234567890_~valid.token", 
        "authorizedDriver456",
        "testDriver789"
    )
    
    override suspend fun authorize(request: ChargingRequest): AuthorizationDecision {
        return try {
            // SPECIFICATION REQUIREMENT: Handle timeout with UNKNOWN status
            withTimeout(config.timeout) {
                performAuthorization(request)
            }
        } catch (e: TimeoutCancellationException) {
            logger.warn { "Authorization timeout for request ${request.requestId}" }
            
            // SPECIFICATION REQUIREMENT: Default to "unknown" on timeout
            val decision = AuthorizationDecision(
                requestId = request.requestId,
                stationId = request.stationId,
                status = AuthorizationStatus.UNKNOWN,
                reason = "Authorization service timeout",
                processingTimeMs = config.timeout.inWholeMilliseconds
            )
            
            metricsService.incrementAuthorizationCounter("timeout")
            metricsService.recordAuthorizationTime(config.timeout.inWholeMilliseconds)
            
            decision
        } catch (e: Exception) {
            logger.error(e) { "Authorization error for request ${request.requestId}: ${e.message}" }
            
            val decision = AuthorizationDecision(
                requestId = request.requestId,
                stationId = request.stationId,
                status = AuthorizationStatus.UNKNOWN,
                reason = "Authorization service error: ${e.message}",
                processingTimeMs = 0L
            )
            
            metricsService.incrementAuthorizationCounter("error")
            decision
        }
    }
    
    private suspend fun performAuthorization(request: ChargingRequest): AuthorizationDecision {
        val processingTime = measureTimeMillis {
            // Simulate authorization processing time
            kotlinx.coroutines.delay(kotlin.random.Random.nextLong(50, 200))
        }
        
        val isAuthorized = authorizedDrivers.contains(request.driverToken)
        val status = if (isAuthorized) AuthorizationStatus.ALLOWED else AuthorizationStatus.NOT_ALLOWED
        val reason = if (!isAuthorized) "Driver not in ACL" else null
        
        val decision = AuthorizationDecision(
            requestId = request.requestId,
            stationId = request.stationId,
            status = status,
            reason = reason,
            processingTimeMs = processingTime
        )
        
        // Record metrics
        metricsService.incrementAuthorizationCounter(status.name.lowercase())
        metricsService.recordAuthorizationTime(processingTime)
        
        logger.debug { "Authorization decision for ${request.requestId}: $status" }
        
        return decision
    }
}
