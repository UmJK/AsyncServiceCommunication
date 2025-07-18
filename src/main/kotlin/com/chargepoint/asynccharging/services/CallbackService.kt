package com.chargepoint.asynccharging.services

import com.chargepoint.asynccharging.models.CallbackPayload
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class CallbackService(private val client: HttpClient = defaultHttpClient) {

    companion object {
        private val logger = LoggerFactory.getLogger(CallbackService::class.java)

        private val defaultHttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
        }
    }

    suspend fun sendCallback(payload: CallbackPayload) {
        try {
            val response: HttpResponse = client.post(payload.callbackUrl) {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }

            if (response.status == HttpStatusCode.OK) {
                logger.info("Callback sent successfully to ${payload.callbackUrl}")
            } else {
                logger.error("Callback failed with status ${response.status}")
            }
        } catch (e: Exception) {
            logger.error("Callback failed: ${e.message}", e)
        }
    }
}
