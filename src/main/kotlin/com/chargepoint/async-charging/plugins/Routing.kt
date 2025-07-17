package com.chargepoint.`async-charging`.plugins

import com.chargepoint.`async-charging`.controllers.chargingRoutes
import com.chargepoint.`async-charging`.services.ChargingService
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