package com.chargepoint.asynccharging.queue

import com.chargepoint.asynccharging.models.ChargingRequest
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Interface for authorization queue operations
 */
interface AuthorizationQueue {
    suspend fun initialize()
    suspend fun enqueue(authorizationId: String, request: ChargingRequest): Boolean
    suspend fun dequeue(): QueueMessage?
    suspend fun acknowledgeProcessing(authorizationId: String): Boolean
    suspend fun requeueForRetry(authorizationId: String, retryCount: Int): Boolean
    suspend fun markAsFailed(authorizationId: String, error: String): Boolean
    suspend fun getQueueSize(): Long
    suspend fun getProcessingQueueSize(): Long
    suspend fun getFailedQueueSize(): Long
    suspend fun getRetryQueueSize(): Long
    suspend fun getStatistics(): Map<String, @Contextual Any>
    suspend fun healthCheck(): Map<String, @Contextual Any>
    suspend fun clearAllQueues(): Boolean
    suspend fun getFailedMessages(limit: Int): List<QueueMessage>
    suspend fun requeueFailedMessages(authorizationIds: List<String>): Int
    suspend fun getStuckMessages(olderThanMinutes: Int): List<QueueMessage>
    suspend fun shutdown()
}

/**
 * Health status of the queue system
 */
@Serializable
data class QueueHealth(
    val status: String, // "healthy", "warning", "unhealthy"
    val isOperational: Boolean,
    val lastHealthCheck: Long,
    val issues: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val metrics: Map<String, String> = emptyMap() // Using String instead of Any for serialization
)

@Serializable
data class QueueMessage(
    val authorizationId: String,
    val request: ChargingRequest,
    val enqueuedAt: Long,
    val retryCount: Int = 0,
    val lastRetryAt: Long? = null,
    val failureReason: String? = null,
    val failedAt: Long? = null
)