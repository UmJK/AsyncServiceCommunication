
package com.chargepoint.asynccharging.plugins

import com.chargepoint.asynccharging.models.ErrorResponse
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.http.*
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.util.*
import org.slf4j.event.Level
import java.util.UUID

fun Application.configureHTTP() {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
        header("X-API-Version", "1.0")
    }

    install(CallLogging) {
        level = Level.INFO
        mdc("request-id") { call ->
            call.request.headers["X-Request-ID"] ?: UUID.randomUUID().toString().take(8)
        }
        format { call ->
            val status = call.response.status()
            val method = call.request.httpMethod.value
            val path = call.request.path()
            val userAgent = call.request.headers["User-Agent"]
            val requestId = call.attributes.getOrNull(AttributeKey("request-id")) ?: "unknown"
            "$status: $method $path - $userAgent [RequestID: $requestId]"
        }
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.AccessControlAllowHeaders)
        allowHeader(HttpHeaders.AccessControlAllowOrigin)
        allowHeader("X-Request-ID")
        allowCredentials = true
        anyHost() // For development - restrict this in production
    }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = "Bad Request",
                    message = cause.message ?: "Invalid request parameters",
                    requestId = generateRequestId()
                )
            )
        }

        exception<kotlinx.serialization.SerializationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = "Invalid JSON",
                    message = "Request body contains invalid JSON format: ${cause.message}",
                    requestId = generateRequestId()
                )
            )
        }

        exception<io.ktor.server.plugins.BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = "Bad Request",
                    message = cause.message ?: "Invalid request",
                    requestId = generateRequestId()
                )
            )
        }

        exception<io.ktor.server.plugins.NotFoundException> { call, _cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(
                    error = "Not Found",
                    message = "The requested resource was not found",
                    requestId = generateRequestId()
                )
            )
        }

        exception<Throwable> { call, cause ->
            this@configureHTTP.environment.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    error = "Internal Server Error",
                    message = "An unexpected error occurred",
                    requestId = generateRequestId()
                )
            )
        }
    }
}

private fun generateRequestId(): String {
    return UUID.randomUUID().toString().take(8)
}