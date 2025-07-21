package com.chargepoint.asynccharging

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        application {
            module() // Load your application module
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("🚗 Async Charging Service is running!", response.bodyAsText())
    }

    @Test
    fun testChargingStartEndpoint() = testApplication {
        application {
            module() // Load your application module
        }
        val response = client.post("/api/v1/charging/start") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "userId": "test_user_123",
                    "chargerId": "charger_456",
                    "stationId": "123e4567-e89b-12d3-a456-426614174000",
                    "connectorId": 1,
                    "requestedEnergy": "25.0",
                    "maxDurationMinutes": 60,
                    "callbackUrl": "http://localhost:8080/fake-callback",
                    "metadata": {}
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Accepted, response.status)
    }

    @Test
    fun testHealthEndpoint() = testApplication {
        application {
            module()
        }
        val response = client.get("/health")
        assertTrue(response.status.isSuccess(), "Health check should succeed")
    }
}