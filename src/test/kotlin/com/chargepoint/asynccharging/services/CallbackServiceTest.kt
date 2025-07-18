package com.chargepoint.asynccharging.services

import com.chargepoint.asynccharging.models.CallbackPayload
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Unit tests for CallbackService to ensure HTTP callbacks are sent correctly.
 */
class CallbackServiceTest {

    @Test
    fun `sendCallback executes without exceptions`() = runTest {
        // Arrange: Create a mock HTTP client that simulates a 200 OK response
        val mockClient = HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            engine {
                addHandler { _ ->
                    respond(
                        content = "OK",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type" to listOf(ContentType.Text.Plain.toString()))
                    )
                }
            }
        }

        val callbackService = CallbackService(mockClient)

        // Create a dummy payload
        val payload = CallbackPayload(
            callbackUrl = "http://localhost:8080/callback",
            stationId = "station-123",
            decision = "APPROVED",
            driverToken = "sampleDriverToken1234567890",
        )

        // Act + Assert: Send callback and ensure no exception occurs
        try {
            callbackService.sendCallback(payload)
            assertTrue(true, "Callback sent successfully without exception.")
        } catch (e: Exception) {
            assertTrue(false, "Callback failed with exception: ${e.message}")
        }
    }
}
