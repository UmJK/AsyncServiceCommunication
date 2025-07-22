package com.chargepoint.asynccharging.performance

import com.chargepoint.asynccharging.config.QueueConfig
import com.chargepoint.asynccharging.models.requests.ChargingRequest
import com.chargepoint.asynccharging.queue.AuthorizationQueue
import com.chargepoint.asynccharging.services.MetricsServiceImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.system.measureTimeMillis
import kotlin.test.*

class LoadTest {
    
    @Test
    fun `queue should handle concurrent operations`() = runTest {
        // Given
        val metricsService = MetricsServiceImpl()
        val config = QueueConfig(maxSize = 1000, consumerThreads = 1)
        val queue = AuthorizationQueue(config, metricsService)
        
        val numberOfRequests = 100
        val requests = (1..numberOfRequests).map { createTestRequest() }
        
        // When - concurrent enqueue operations
        val enqueueTime = measureTimeMillis {
            runBlocking {
                requests.map { request ->
                    async { queue.enqueue(request) }
                }.awaitAll()
            }
        }
        
        // Then
        assertEquals(numberOfRequests, queue.size())
        println("Enqueued $numberOfRequests requests in ${enqueueTime}ms")
        
        // When - concurrent dequeue operations
        val dequeueTime = measureTimeMillis {
            runBlocking {
                (1..numberOfRequests).map {
                    async { queue.dequeue(1000) }
                }.awaitAll()
            }
        }
        
        // Then
        assertEquals(0, queue.size())
        println("Dequeued $numberOfRequests requests in ${dequeueTime}ms")
    }
    
    private fun createTestRequest() = ChargingRequest(
        stationId = "123e4567-e89b-12d3-a456-426614174000",
        driverToken = "testDriverToken123456789",
        callbackUrl = "https://example.com/callback"
    )
}
