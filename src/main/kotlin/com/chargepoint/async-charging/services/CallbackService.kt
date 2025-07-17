package com.chargepoint.`async-charging`.services

import com.chargepoint.`async-charging`.models.AuthorizationDecision
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.net.http.HttpClient

/**
 * Service responsible for sending async callback after processing.
 */
class CallbackService {
    private val client = HttpClient(CIO)

    suspend fun sendCallback(decision: AuthorizationDecision): HttpResponse {
        return client.post(decision.callbackUrl) {
            contentType(ContentType.Application.Json)
            setBody(decision)
        }
    }
}