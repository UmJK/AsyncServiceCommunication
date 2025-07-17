package com.chargepoint.`async-charging`.services

import com.chargepoint.`async-charging`.models.AuthorizationDecision
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class CallbackServiceTest {

    private val callbackService = CallbackService()

    @Test
    fun `sendCallback executes without exceptions`() = runTest {
        val decision = AuthorizationDecision(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "testDriverToken123456789",
            status = "allowed",
            callbackUrl = "http://localhost/callback"
        )

        runCatching { callbackService.sendCallback(decision) }
            .onSuccess { assertTrue(true) }
            .onFailure { assertTrue(false, "Callback failed unexpectedly") }
    }
}
