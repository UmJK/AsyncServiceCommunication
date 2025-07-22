package com.chargepoint.asynccharging.services

import com.chargepoint.asynccharging.config.CallbackConfig
import com.chargepoint.asynccharging.models.callbacks.CallbackPayload
import com.chargepoint.asynccharging.models.decisions.AuthorizationDecision
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

interface CallbackService {
    suspend fun sendCallback(decision: AuthorizationDecision, callbackUrl: String): Boolean
}

class CallbackServiceImpl(
    private val config: CallbackConfig,
    private val metricsService: MetricsService
) : CallbackService {
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
        
        install(HttpTimeout) {
            requestTimeoutMillis = config.timeout.inWholeMilliseconds
            connectTimeoutMillis = 5000
        }
        
        defaultRequest {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.UserAgent, "AsyncChargingService/1.0")
        }
    }
    
    override suspend fun sendCallback(decision: AuthorizationDecision, callbackUrl: String): Boolean {
        val payload = CallbackPayload(
            station_id = decision.stationId,
            driver_token = decision.driverToken,
            status = decision.status.value
        )
        
        return retryWithBackoff(config.maxRetries) { attempt ->
            try {
                val response = httpClient.post(callbackUrl) {
                    setBody(payload)
                }
                
                when (response.status) {
                    HttpStatusCode.OK, HttpStatusCode.Created, HttpStatusCode.Accepted -> {
                        logger.info { 
                            "Callback sent successfully to $callbackUrl for request ${decision.requestId} " +
                            "(attempt $attempt, status=${response.status})"
                        }
                        metricsService.incrementCallbackCounter("success")
                        true
                    }
                    else -> {
                        val errorBody = try { response.body<String>() } catch (e: Exception) { "Unknown error" }
                        logger.warn { 
                            "Callback failed with status ${response.status} for request ${decision.requestId}: $errorBody" 
                        }
                        metricsService.incrementCallbackCounter("http_error")
                        false
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Callback attempt $attempt failed for request ${decision.requestId} to $callbackUrl" }
                metricsService.incrementCallbackCounter("network_error")
                throw e
            }
        }
    }
    
    private suspend fun <T> retryWithBackoff(
        maxRetries: Int,
        operation: suspend (Int) -> T
    ): T {
        repeat(maxRetries) { attempt ->
            try {
                return operation(attempt + 1)
            } catch (e: Exception) {
                if (attempt == maxRetries - 1) throw e
                
                val delayMs = config.retryDelay.inWholeMilliseconds * (1L shl attempt)
                logger.debug { "Retrying after ${delayMs}ms (attempt ${attempt + 1}/$maxRetries)" }
                delay(delayMs)
            }
        }
        throw RuntimeException("Should not reach here")
    }
    
    fun close() {
        httpClient.close()
    }
}
