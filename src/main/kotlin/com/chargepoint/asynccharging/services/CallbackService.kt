package com.chargepoint.asynccharging.services

import com.chargepoint.asynccharging.config.ConfigManager
import com.chargepoint.asynccharging.models.CallbackPayload
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

/**
 * Service for sending HTTP callbacks to client applications
 */
class CallbackService {

    private val logger = LoggerFactory.getLogger(CallbackService::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                prettyPrint = true
            })
        }
    }

    /**
     * Send authorization callback to the specified URL
     */
    suspend fun sendCallback(callbackUrl: String, payload: com.chargepoint.asynccharging.models.CallbackPayload): Boolean {
        return try {
            logger.debug("Sending callback to: $callbackUrl with payload: $payload")

            val config = ConfigManager.httpConfig

            withTimeout(config.readTimeout.seconds) {
                val response = httpClient.post(callbackUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }

                val success = response.status.isSuccess()
                if (success) {
                    logger.info("Callback sent successfully to: $callbackUrl, response: ${response.status}")
                } else {
                    logger.warn("Callback failed to: $callbackUrl, response: ${response.status}")
                }

                success
            }

        } catch (e: Exception) {
            logger.error("Error sending callback to: $callbackUrl", e)
            false
        }
    }

    /**
     * Test if a callback URL is reachable
     */
    suspend fun testCallback(callbackUrl: String): Boolean {
        return try {
            logger.debug("Testing callback URL: $callbackUrl")

            val testPayload = CallbackPayload(
                authorizationId = "test-callback",
                userId = "test-user",
                stationId = "test-station",
                connectorId = 1,
                decision = com.chargepoint.asynccharging.models.AuthorizationDecision.APPROVED.name,
                reason = "Test callback",
                approvedEnergy = "10.0",
                timestamp = java.time.Instant.now().toString()
            )

            sendCallback(callbackUrl, testPayload)

        } catch (e: Exception) {
            logger.error("Error testing callback URL: $callbackUrl", e)
            false
        }
    }

    fun close() {
        httpClient.close()
    }
}