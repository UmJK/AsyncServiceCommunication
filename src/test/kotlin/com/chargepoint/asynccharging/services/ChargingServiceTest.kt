package com.chargepoint.asynccharging.services

import com.chargepoint.asynccharging.database.AuthorizationRepository
import com.chargepoint.asynccharging.models.ChargingRequest
import com.chargepoint.asynccharging.queue.RedisAuthorizationQueue
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ChargingServiceTest {

    private val repository = AuthorizationRepository()
    private val queue = RedisAuthorizationQueue()
    private val chargingService = ChargingService(queue, repository)

    @Test
    fun `processChargingRequest should save and enqueue request`() = runBlocking {
        val request = ChargingRequest(
            userId = "test_user_123",
            chargerId = "charger_456",
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            connectorId = 1,
            requestedEnergy = "25.0",
            maxDurationMinutes = 60,
            callbackUrl = "http://localhost/callback",
            metadata = mapOf("test" to "true")
        )

        chargingService.processChargingRequest(request)

        // Give some time for async processing
        kotlinx.coroutines.delay(100)

        // Check that something was queued
        val queueSize = queue.getQueueSize()
        assertTrue(queueSize > 0, "Request should be queued")
    }
}