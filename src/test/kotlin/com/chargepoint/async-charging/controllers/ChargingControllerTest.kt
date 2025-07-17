package com.chargepoint.`async-charging`.controllers

import com.chargepoint.`async-charging`.models.ChargingRequest
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ChargingControllerTest {

    @Test
    fun `should accept valid charging request`() = testApplication {
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "validDriverToken123456789",
            callbackUrl = "http://localhost/callback"
        )

        val response = client.post("/api/v1/charging/start") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
    }

    @Test
    fun `should reject invalid UUID`() = testApplication {
        val request = ChargingRequest(
            stationId = "invalid-uuid",
            driverToken = "validDriverToken123456789",
            callbackUrl = "http://localhost/callback"
        )

        val response = client.post("/api/v1/charging/start") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
