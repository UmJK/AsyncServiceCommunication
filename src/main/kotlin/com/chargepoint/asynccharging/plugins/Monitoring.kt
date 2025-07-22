package com.chargepoint.asynccharging.plugins

import com.chargepoint.asynccharging.services.MetricsService
import com.chargepoint.asynccharging.monitoring.HealthCheck
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureMonitoring(metricsService: MetricsService) {
    routing {
        get("/health") {
            val healthCheck = HealthCheck(metricsService)
            val health = healthCheck.check()
            call.respond(health)
        }
        
        get("/metrics") {
            val metrics = metricsService.getMetrics()
            call.respond(metrics)
        }
    }
}
