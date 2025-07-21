package com.chargepoint.asynccharging.controllers

import com.chargepoint.asynccharging.models.ChargingRequest
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
            userId = "user-123",
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            chargerId = "charger-123",
            connectorId = 1,
            requestedEnergy = "50.0",
            maxDurationMinutes = 60,
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
            userId = "user-123",
            stationId = "invalid-uuid",
            chargerId = "charger-123",
            connectorId = 1,
            requestedEnergy = "50.0",
            maxDurationMinutes = 60,
            callbackUrl = "http://localhost/callback"
        )

        val response = client.post("/api/v1/charging/start") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
