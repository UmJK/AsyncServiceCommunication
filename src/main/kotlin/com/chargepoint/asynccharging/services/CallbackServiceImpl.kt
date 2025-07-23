package com.chargepoint.asynccharging.services

import com.chargepoint.asynccharging.config.CallbackConfig
import com.chargepoint.asynccharging.models.callbacks.CallbackPayload
import com.chargepoint.asynccharging.models.decisions.AuthorizationDecision
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class CallbackServiceImpl(
    private val config: CallbackConfig,
    private val metricsService: MetricsService
) : CallbackService {
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = config.timeout.inWholeMilliseconds
            connectTimeoutMillis = 5000
            socketTimeoutMillis = config.timeout.inWholeMilliseconds
        }
    }
    
    override suspend fun sendCallback(decision: AuthorizationDecision, callbackUrl: String): Boolean {
        // This version won't work properly since we don't have the driver token
        logger.warn { "Driver token not available in decision object - callback will have placeholder" }
        return sendCallbackWithToken(decision, callbackUrl, "TOKEN_NOT_AVAILABLE")
    }
    
    // Main method that should be used - with driver token passed separately
    suspend fun sendCallback(decision: AuthorizationDecision, callbackUrl: String, driverToken: String): Boolean {
        return retryWithBackoff(config.maxRetries) { attempt ->
            try {
                sendCallbackWithToken(decision, callbackUrl, driverToken)
            } catch (e: Exception) {
                logger.warn(e) { "Callback attempt $attempt failed for ${decision.requestId}: ${e.message}" }
                throw e
            }
        }
    }
    
    private suspend fun sendCallbackWithToken(
        decision: AuthorizationDecision, 
        callbackUrl: String, 
        driverToken: String
    ): Boolean {
        val payload = CallbackPayload(
            station_id = decision.stationId,
            driver_token = driverToken,
            status = decision.status.name.lowercase()
        )
        
        logger.debug { "Sending callback for ${decision.requestId} to $callbackUrl" }
        
        val response = httpClient.post(callbackUrl) {
            contentType(ContentType.Application.Json)
            setBody(payload)
            // Timeout is configured at client level, no need to set per request
        }
        
        val success = response.status.isSuccess()
        
        if (success) {
            logger.info { "Callback sent successfully for ${decision.requestId}" }
            metricsService.incrementCallbackCounter("success")
        } else {
            logger.warn { "Callback failed for ${decision.requestId}: ${response.status}" }
            metricsService.incrementCallbackCounter("http_error")
        }
        
        return success
    }
    
    private suspend fun <T> retryWithBackoff(maxRetries: Int, operation: suspend (Int) -> T): T {
        var lastException: Exception? = null
        
        for (attempt in 1..maxRetries) {
            try {
                return operation(attempt)
            } catch (e: Exception) {
                lastException = e
                
                if (attempt < maxRetries) {
                    val delayMs = config.retryDelay.inWholeMilliseconds * (1L shl (attempt - 1))
                    logger.debug { "Retrying in ${delayMs}ms (attempt $attempt/$maxRetries)" }
                    delay(delayMs)
                } else {
                    logger.error(e) { "All callback attempts failed after $maxRetries tries" }
                    metricsService.incrementCallbackCounter("max_retries_exceeded")
                }
            }
        }
        
        throw lastException ?: Exception("All retry attempts failed")
    }
}
