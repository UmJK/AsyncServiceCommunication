package com.chargepoint.asynccharging.controllers

import com.chargepoint.asynccharging.models.requests.ChargingRequest
import com.chargepoint.asynccharging.models.responses.ApiResponse
import com.chargepoint.asynccharging.queue.AuthorizationQueue
import com.chargepoint.asynccharging.services.MetricsService
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun Route.chargingSessionRoutes(
    authorizationQueue: AuthorizationQueue,
    metricsService: MetricsService
) {
    post("/charging-session") {
        try {
            // Record incoming request
            metricsService.incrementRequestCounter()
            
            // Parse and validate request
            val request = call.receive<ChargingRequest>()
            logger.info { "Received charging session request: ${request.toLogString()}" }
            
            // Enqueue for async processing
            val enqueued = authorizationQueue.enqueue(request)
            
            if (enqueued) {
                logger.info { "Request queued successfully: ${request.requestId}" }
                call.respond(
                    ApiResponse(
                        status = "accepted",
                        message = "Request is being processed asynchronously. The result will be sent to the provided callback URL.",
                        requestId = request.requestId
                    )
                )
            } else {
                logger.warn { "Failed to queue request: ${request.requestId} - queue may be full" }
                throw com.chargepoint.asynccharging.exceptions.QueueException(
                    "Unable to process request at this time. Please try again later."
                )
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Error processing charging session request" }
            throw e // Let StatusPages plugin handle it
        }
    }
}
