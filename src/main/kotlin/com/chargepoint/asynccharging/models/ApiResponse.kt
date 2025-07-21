package com.chargepoint.asynccharging.models

import kotlinx.serialization.Serializable

// ErrorResponse moved to ErrorResponse.kt

@Serializable
data class AuthorizationStatusResponse(
    val authorizationId: String,
    val userId: String,
    val stationId: String,
    val connectorId: Int,
    val status: String,
    val decision: String,
    val reason: String?,
    val requestedEnergy: String,
    val approvedEnergy: String?,
    val maxDurationMinutes: Int,
    val submittedAt: String,
    val processedAt: String?,
    val expiresAt: String?,
    val isExpired: Boolean,
    val metadata: Map<String, String>,
    val requestId: String,
    val timestamp: String
)

@Serializable
data class CancelAuthorizationResponse(
    val message: String,
    val authorizationId: String,
    val status: String,
    val requestId: String,
    val timestamp: String
)

@Serializable
data class HealthCheckResponse(
    val status: String,
    val components: HealthComponents,
    val requestId: String,
    val timestamp: String
)

@Serializable
data class HealthComponents(
    val repository: RepositoryHealth,
    val service: ServiceHealth
)

@Serializable
data class RepositoryHealth(
    val status: String,
    val type: String,
    val version: String,
    val timestamp: String,
    val healthCheckDurationMs: Long,
    val uptime: String,
    val metrics: HealthMetrics,
    val issues: List<String>,
    val recommendations: List<String>
)

@Serializable
data class ServiceHealth(
    val status: String,
    val isProcessing: Boolean,
    val processedCount: Long,
    val errorCount: Long,
    val errorRate: String,
    val statistics: ServiceStats
)

@Serializable
data class HealthMetrics(
    val recordCount: Int,
    val operationCount: Long,
    val memoryUsageMB: Long,
    val hasRecords: Boolean
)

@Serializable
data class ServiceStats(
    val processedCount: Long,
    val errorCount: Long,
    val isProcessing: Boolean,
    val queueSize: Long,
    val averageProcessingTime: Double,
    val errorRate: Double
)