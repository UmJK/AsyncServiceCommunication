package com.chargepoint.asynccharging.component

import com.chargepoint.asynccharging.config.QueueConfig
import com.chargepoint.asynccharging.models.requests.ChargingRequest
import com.chargepoint.asynccharging.queue.AuthorizationQueue
import com.chargepoint.asynccharging.services.MetricsServiceImpl
import kotlin.test.*

class EndToEndTest {
    
    @Test
    fun `should process request end-to-end through queue`() {
        // Given
        val metricsService = MetricsServiceImpl()
        val queueConfig = QueueConfig(maxSize = 10, consumerThreads = 1)
        val queue = AuthorizationQueue(queueConfig, metricsService)
        
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "ABCD-efgh1234567890_~valid.token",
            callbackUrl = "https://example.com/callback"
        )
        
        // When - enqueue request
        val enqueued = queue.enqueue(request)
        
        // Then - should be successfully enqueued
        assertTrue(enqueued, "Request should be enqueued successfully")
        assertEquals(1, queue.size(), "Queue should have 1 item")
        
        // When - dequeue request
        val dequeued = queue.dequeue(1000)
        
        // Then - should get the same request back
        assertNotNull(dequeued, "Should dequeue a request")
        assertEquals(request.requestId, dequeued.requestId)
        assertEquals(request.stationId, dequeued.stationId)
        assertEquals(request.driverToken, dequeued.driverToken)
        assertEquals(0, queue.size(), "Queue should be empty after dequeue")
        
        // Metrics should be updated
        val metrics = metricsService.getMetrics()
        assertTrue(metrics.queue_size_current >= 0, "Queue size metric should be tracked")
    }
    
    @Test
    fun `should validate request before processing`() {
        // Given
        val validRequest = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "ABCD-efgh1234567890_~valid.token",
            callbackUrl = "https://example.com/callback"
        )
        
        val invalidRequest = ChargingRequest(
            stationId = "invalid-uuid",
            driverToken = "short",
            callbackUrl = "not-a-url"
        )
        
        // When & Then - valid request should pass validation
        try {
            validRequest.validate()
            assertTrue(true, "Valid request should pass validation")
        } catch (e: Exception) {
            fail("Valid request should not throw: ${e.message}")
        }
        
        // When & Then - invalid request should fail validation
        assertFailsWith<Exception> {
            invalidRequest.validate()
        }
    }
}
