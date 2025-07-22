package com.chargepoint.asynccharging.plugins

import com.chargepoint.asynccharging.controllers.chargingSessionRoutes
import com.chargepoint.asynccharging.queue.AuthorizationQueue
import com.chargepoint.asynccharging.services.MetricsService
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting(
    authorizationQueue: AuthorizationQueue,
    metricsService: MetricsService
) {
    routing {
        route("/api/v1") {
            chargingSessionRoutes(authorizationQueue, metricsService)
        }
    }
}
