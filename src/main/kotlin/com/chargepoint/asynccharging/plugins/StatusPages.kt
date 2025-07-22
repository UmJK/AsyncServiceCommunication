package com.chargepoint.asynccharging.plugins

import com.chargepoint.asynccharging.exceptions.*
import com.chargepoint.asynccharging.models.responses.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            logger.warn(cause) { "Validation error: ${cause.message}" }
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    status = "validation_error",
                    message = cause.message ?: "Invalid request",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        
        exception<QueueException> { call, cause ->
            logger.warn(cause) { "Queue error: ${cause.message}" }
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse(
                    status = "service_unavailable",
                    message = "Service temporarily unavailable. Please try again later.",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unexpected error: ${cause.message}" }
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    status = "internal_error",
                    message = "An unexpected error occurred",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }
}
