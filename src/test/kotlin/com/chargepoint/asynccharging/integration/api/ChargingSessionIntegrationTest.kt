package com.chargepoint.asynccharging.integration.api

import com.chargepoint.asynccharging.module
import com.chargepoint.asynccharging.models.requests.ChargingRequest
import com.chargepoint.asynccharging.models.responses.ApiResponse
import com.chargepoint.asynccharging.models.responses.ErrorResponse
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class ChargingSessionIntegrationTest {
    
    @Test
    fun `POST charging-session should accept valid request`() = testApplication {
        application {
            module()
        }
        
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        
        // When
        val response = client.post("/api/v1/charging-session") {
            contentType(ContentType.Application.Json)
            setBody(ChargingRequest(
                stationId = "123e4567-e89b-12d3-a456-426614174000",
                driverToken = "validDriverToken123456789",
                callbackUrl = "https://httpbin.org/post"
            ))
        }
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val apiResponse = response.body<ApiResponse>()
        assertEquals("accepted", apiResponse.status)
        assertEquals("Request queued for async processing.", apiResponse.message)
        assertNotNull(apiResponse.requestId)
    }
    
    @Test
    fun `POST charging-session should reject invalid request`() = testApplication {
        application {
            module()
        }
        
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        
        // When
        val response = client.post("/api/v1/charging-session") {
            contentType(ContentType.Application.Json)
            setBody(ChargingRequest(
                stationId = "invalid-uuid",
                driverToken = "validDriverToken123456789",
                callbackUrl = "https://httpbin.org/post"
            ))
        }
        
        // Then
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val errorResponse = response.body<ErrorResponse>()
        assertEquals("validation_error", errorResponse.status)
        assertTrue(errorResponse.message.contains("station_id"))
    }
}
