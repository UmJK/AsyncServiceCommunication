package com.chargepoint.asynccharging.repository

import com.chargepoint.asynccharging.models.ChargingRequest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [AuthorizationRepository] which checks business logic
 * to classify driverToken as `allowed`, `not_allowed`, or `unknown`.
 */
class AuthorizationRepositoryTest {

    private val repository = AuthorizationRepository.instance

    @Test
    fun `should return allowed for token starting with test`() {
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "test-token-xyz",
            callbackUrl = "http://localhost/callback"
        )

        val decision = repository.checkACL(request)
        assertEquals("allowed", decision.status)
    }

    @Test
    fun `should return not_allowed for token starting with blocked`() {
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "blocked-abc-789",
            callbackUrl = "http://localhost/callback"
        )

        val decision = repository.checkACL(request)
        assertEquals("not_allowed", decision.status)
    }

    @Test
    fun `should return unknown for unclassified token`() {
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "neutralToken-123",
            callbackUrl = "http://localhost/callback"
        )

        val decision = repository.checkACL(request)
        assertEquals("unknown", decision.status)
    }
}
