package com.chargepoint.asynccharging.queue

import com.chargepoint.asynccharging.models.ChargingRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthorizationQueueTest {

    private val queue = InMemoryAuthorizationQueue()

    @BeforeTest
    fun setup() {
        queue.clear() // Ensure the queue is empty before each test
    }

    @Test
    fun `enqueue and dequeue request`() = runBlocking {
        val request = ChargingRequest(
            userId = "user-123",
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            chargerId = "charger-123",
            connectorId = 1,
            requestedEnergy = "50.0",
            maxDurationMinutes = 60,
            callbackUrl = "http://localhost/callback"
        )

        val authId = "auth-123"
        queue.enqueue(authId, request)
        val dequeued = queue.dequeue()

        assertEquals(authId, dequeued?.authorizationId)
        assertEquals(request, dequeued?.request)
    }

    @Test
    fun `dequeue returns null on empty queue`() = runBlocking {
        assertNull(queue.dequeue())
    }
}

/**
 * Simple in-memory implementation of AuthorizationQueue for testing
 */
class InMemoryAuthorizationQueue : AuthorizationQueue {
    private val queue = mutableListOf<QueueMessage>()

    override suspend fun initialize() {}

    override suspend fun enqueue(authorizationId: String, request: ChargingRequest): Boolean {
        queue.add(QueueMessage(authorizationId, request, System.currentTimeMillis()))
        return true
    }

    override suspend fun dequeue(): QueueMessage? {
        return if (queue.isNotEmpty()) queue.removeAt(0) else null
    }

    override suspend fun acknowledgeProcessing(authorizationId: String): Boolean = true
    override suspend fun requeueForRetry(authorizationId: String, retryCount: Int): Boolean = true
    override suspend fun markAsFailed(authorizationId: String, error: String): Boolean = true
    override suspend fun getQueueSize(): Long = queue.size.toLong()
    override suspend fun getProcessingQueueSize(): Long = 0
    override suspend fun getFailedQueueSize(): Long = 0
    override suspend fun getRetryQueueSize(): Long = 0
    override suspend fun getStatistics(): Map<String, Any> = emptyMap()
    override suspend fun healthCheck(): Map<String, Any> = mapOf("status" to "healthy")
    override suspend fun clearAllQueues(): Boolean = true
    override suspend fun getFailedMessages(limit: Int): List<QueueMessage> = emptyList()
    override suspend fun requeueFailedMessages(authorizationIds: List<String>): Int = 0
    override suspend fun getStuckMessages(olderThanMinutes: Int): List<QueueMessage> = emptyList()
    override suspend fun shutdown() {}

    // Helper method for tests
    fun clear() {
        queue.clear()
    }
}
