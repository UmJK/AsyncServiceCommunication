package com.chargepoint.asynccharging.plugins

import com.chargepoint.asynccharging.controllers.chargingRoutes
import com.chargepoint.asynccharging.services.ChargingService
import io.ktor.server.application.*
import io.ktor.server.routing.*

/**
 * Register application routes.
 */
fun Application.configureRouting() {
    routing {
        chargingRoutes(ChargingService())
    }
}