package com.chargepoint.asynccharging.plugins

import com.chargepoint.asynccharging.authorizationService
import com.chargepoint.asynccharging.chargingController
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Routing")

fun Application.configureRouting() {
    routing {
        // Health check endpoint
        get("/") {
            call.respondText("🚗 Async Charging Service is running!", ContentType.Text.Plain)
        }

        get("/health") {
            chargingController.healthCheck(call)
        }

        // API routes
        route("/api/v1") {
            route("/charging") {
                post("/start") {
                    chargingController.startCharging(call)
                }

                get("/status/{authorizationId}") {
                    chargingController.getAuthorizationStatus(call)
                }

                delete("/cancel/{authorizationId}") {
                    chargingController.cancelAuthorization(call)
                }
            }

            // Statistics endpoint
            get("/statistics") {
                try {
                    val stats = authorizationService.getStatistics()
                    call.respond(HttpStatusCode.OK, mapOf(
                        "service" to "async-charging",
                        "timestamp" to System.currentTimeMillis(),
                        "statistics" to stats
                    ))
                } catch (e: Exception) {
                    logger.error("Error getting statistics", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
                }
            }
        }
    }
}