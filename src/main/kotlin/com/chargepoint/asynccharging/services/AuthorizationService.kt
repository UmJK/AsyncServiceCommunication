package com.chargepoint.asynccharging.services

import com.chargepoint.asynccharging.config.AuthorizationConfig
import com.chargepoint.asynccharging.models.decisions.AuthorizationDecision
import com.chargepoint.asynccharging.models.enums.AuthorizationStatus
import com.chargepoint.asynccharging.models.requests.ChargingRequest
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

interface AuthorizationService {
    suspend fun authorize(request: ChargingRequest): AuthorizationDecision
}

class AuthorizationServiceImpl(
    private val config: AuthorizationConfig,
    private val circuitBreakerService: CircuitBreakerService,
    private val metricsService: MetricsService
) : AuthorizationService {
    
    private val acl = setOf(
        "validDriverToken123",
        "ABCD-efgh1234567890_~valid.token",
        "authorizedDriver456",
        "testDriver789"
    )
    
    override suspend fun authorize(request: ChargingRequest): AuthorizationDecision {
        val startTime = System.currentTimeMillis()
        
        return try {
            val decision = if (config.circuitBreakerEnabled) {
                circuitBreakerService.execute("authorization") {
                    performAuthorization(request)
                }
            } else {
                performAuthorization(request)
            }
            
            val processingTime = System.currentTimeMillis() - startTime
            metricsService.recordAuthorizationTime(processingTime)
            metricsService.incrementAuthorizationCounter(decision.status.value)
            
            decision.copy(processingTimeMs = processingTime)
            
        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            logger.error(e) { "Authorization failed for request ${request.toLogString()}" }
            metricsService.incrementAuthorizationCounter("error")
            
            AuthorizationDecision(
                requestId = request.requestId,
                stationId = request.stationId,
                driverToken = request.driverToken,
                status = AuthorizationStatus.UNKNOWN,
                reason = "Service error: ${e.message}",
                processingTimeMs = processingTime
            )
        }
    }
    
    private suspend fun performAuthorization(request: ChargingRequest): AuthorizationDecision {
        return withTimeout(config.timeout) {
            // Simulate ACL lookup delay
            kotlinx.coroutines.delay(kotlin.random.Random.nextLong(50, 200))
            
            val status = when {
                acl.contains(request.driverToken) -> AuthorizationStatus.ALLOWED
                else -> AuthorizationStatus.NOT_ALLOWED
            }
            
            logger.info { "Authorization completed: ${request.toLogString()} -> $status" }
            
            AuthorizationDecision(
                requestId = request.requestId,
                stationId = request.stationId,
                driverToken = request.driverToken,
                status = status,
                reason = if (status == AuthorizationStatus.NOT_ALLOWED) "Driver not in ACL" else null
            )
        }
    }
}
