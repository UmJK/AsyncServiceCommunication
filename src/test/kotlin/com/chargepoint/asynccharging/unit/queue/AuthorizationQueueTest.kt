package com.chargepoint.asynccharging.unit.queue

import com.chargepoint.asynccharging.config.QueueConfig
import com.chargepoint.asynccharging.models.requests.ChargingRequest
import com.chargepoint.asynccharging.queue.AuthorizationQueue
import com.chargepoint.asynccharging.services.MetricsServiceImpl
import com.chargepoint.asynccharging.exceptions.QueueException
import kotlin.test.*

class AuthorizationQueueTest {
    
    private lateinit var queue: AuthorizationQueue
    private lateinit var metricsService: MetricsServiceImpl
    
    @BeforeTest
    fun setup() {
        metricsService = MetricsServiceImpl()
        val config = QueueConfig(maxSize = 3, consumerThreads = 1)
        queue = AuthorizationQueue(config, metricsService)
    }
    
    @Test
    fun `should enqueue and dequeue items`() {
        // Given
        val request = createTestRequest()
        
        // When
        val enqueued = queue.enqueue(request)
        val dequeued = queue.dequeue(1000)
        
        // Then
        assertTrue(enqueued, "Should successfully enqueue")
        assertEquals(request.requestId, dequeued?.requestId)
    }
    
    @Test
    fun `should return null when dequeue times out`() {
        // When
        val result = queue.dequeue(100) // Short timeout
        
        // Then
        assertNull(result, "Should return null on timeout")
    }
    
    @Test
    fun `should track queue size`() {
        // Given
        assertTrue(queue.isEmpty())
        assertEquals(0, queue.size())
        
        // When
        queue.enqueue(createTestRequest())
        
        // Then
        assertFalse(queue.isEmpty())
        assertEquals(1, queue.size())
    }
    
    @Test
    fun `should reject items when queue is full`() {
        // Given - fill the queue to capacity (3 items)
        repeat(3) { queue.enqueue(createTestRequest()) }
        
        // When & Then
        assertFailsWith<QueueException> {
            queue.enqueue(createTestRequest())
        }
    }
    
    @Test
    fun `should clear queue`() {
        // Given
        queue.enqueue(createTestRequest())
        queue.enqueue(createTestRequest())
        assertEquals(2, queue.size())
        
        // When
        queue.clear()
        
        // Then
        assertEquals(0, queue.size())
        assertTrue(queue.isEmpty())
    }
    
    private fun createTestRequest() = ChargingRequest(
        stationId = "123e4567-e89b-12d3-a456-426614174000",
        driverToken = "testDriverToken123456789",
        callbackUrl = "https://example.com/callback"
    )
}
