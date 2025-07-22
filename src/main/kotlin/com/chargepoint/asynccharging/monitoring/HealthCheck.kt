package com.chargepoint.asynccharging.monitoring

import com.chargepoint.asynccharging.models.responses.HealthResponse
import com.chargepoint.asynccharging.models.responses.ComponentHealth
import com.chargepoint.asynccharging.services.MetricsService

class HealthCheck(private val metricsService: MetricsService) {
    
    fun check(): HealthResponse {
        val components = mutableMapOf<String, ComponentHealth>()
        
        // Get metrics from the service
        val metrics = metricsService.getMetrics()
        
        // Check queue health
        val queueSize = metrics.queue_size_current
        
        components["queue"] = ComponentHealth(
            status = if (queueSize < 1000) "UP" else "DEGRADED",
            details = mapOf(
                "currentSize" to queueSize.toString(),
                "maxSize" to "10000"
            )
        )
        
        // Check processing health
        val authDecisions = metrics.authorization_decisions
        val totalDecisions = authDecisions.values.sum()
        val errorRate = (authDecisions["error"] ?: 0L).toDouble() / maxOf(totalDecisions, 1L)
        
        components["authorization"] = ComponentHealth(
            status = if (errorRate < 0.05) "UP" else "DEGRADED",
            details = mapOf(
                "errorRate" to errorRate.toString(),
                "totalDecisions" to totalDecisions.toString()
            )
        )
        
        // Check callback health
        val callbackResults = metrics.callback_results
        val totalCallbacks = callbackResults.values.sum()
        val callbackErrorRate = ((callbackResults["http_error"] ?: 0L) + (callbackResults["network_error"] ?: 0L)).toDouble() / maxOf(totalCallbacks, 1L)
        
        components["callbacks"] = ComponentHealth(
            status = if (callbackErrorRate < 0.1) "UP" else "DEGRADED",
            details = mapOf(
                "errorRate" to callbackErrorRate.toString(),
                "totalCallbacks" to totalCallbacks.toString()
            )
        )
        
        // Overall status
        val overallStatus = when {
            components.values.all { it.status == "UP" } -> "UP"
            components.values.any { it.status == "DEGRADED" } -> "DEGRADED"
            else -> "DOWN"
        }
        
        return HealthResponse(
            status = overallStatus,
            components = components
        )
    }
}
