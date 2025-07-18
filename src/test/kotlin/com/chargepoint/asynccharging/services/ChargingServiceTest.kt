package com.chargepoint.asynccharging.services

import com.chargepoint.asynccharging.models.ChargingRequest
import com.chargepoint.asynccharging.queue.AuthorizationQueue
import kotlin.test.Test
import kotlin.test.assertEquals

class ChargingServiceTest {

    private val chargingService = ChargingService()
    private val queue = AuthorizationQueue.instance

    @Test
    fun `processChargingRequest should enqueue request`() {
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "validDriverToken123456789",
            callbackUrl = "http://localhost/callback"
        )

        chargingService.processChargingRequest(request)

        val queuedRequest = queue.dequeue()
        assertEquals(request, queuedRequest)
    }
}
