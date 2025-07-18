package com.chargepoint.asynccharging.services

import com.chargepoint.asynccharging.models.ChargingRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthorizationServiceTest {

    private val authorizationService = AuthorizationService()

    @Test
    fun `authorize returns allowed for test token`() {
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "testDriverToken123456789",
            callbackUrl = "http://localhost/callback"
        )

        val decision = authorizationService.authorize(request)

        assertEquals("allowed", decision.status)
    }

    @Test
    fun `authorize returns not_allowed for blocked token`() {
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "blockedDriverToken1234567",
            callbackUrl = "http://localhost/callback"
        )

        val decision = authorizationService.authorize(request)

        assertEquals("not_allowed", decision.status)
    }

    @Test
    fun `authorize returns unknown for other tokens`() {
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "randomDriverToken12345678",
            callbackUrl = "http://localhost/callback"
        )

        val decision = authorizationService.authorize(request)

        assertEquals("unknown", decision.status)
    }
}
