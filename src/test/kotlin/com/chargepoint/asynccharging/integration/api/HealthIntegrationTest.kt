package com.chargepoint.asynccharging.integration.api

import com.chargepoint.asynccharging.module
import com.chargepoint.asynccharging.models.responses.HealthResponse
import com.chargepoint.asynccharging.services.MetricsResponse
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class HealthIntegrationTest {
    
    @Test
    fun `GET health should return health status`() = testApplication {
        application {
            module()
        }
        
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        
        // When
        val response = client.get("/health")
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val healthResponse = response.body<HealthResponse>()
        assertNotNull(healthResponse.status)
        assertTrue(healthResponse.components.containsKey("queue"))
        assertTrue(healthResponse.components.containsKey("authorization"))
        assertTrue(healthResponse.components.containsKey("callbacks"))
    }
    
    @Test
    fun `GET metrics should return metrics data`() = testApplication {
        application {
            module()
        }
        
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        
        // When
        val response = client.get("/metrics")
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val metricsResponse = response.body<MetricsResponse>()
        assertTrue(metricsResponse.requests_total >= 0)
        assertNotNull(metricsResponse.authorization_decisions)
        assertNotNull(metricsResponse.callback_results)
        assertTrue(metricsResponse.authorization_time_avg_ms >= 0.0)
    }
}
