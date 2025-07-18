package com.chargepoint.asynccharging.controllers

import com.chargepoint.asynccharging.models.ChargingRequest
import com.chargepoint.asynccharging.services.ChargingService
import com.chargepoint.asynccharging.utils.Validator
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Controller for handling charging requests. Accepts a POST call and enqueues the request.
 */
fun Route.chargingRoutes(service: ChargingService) {
    route("/api/v1/charging") {
        post("/start") {
            val request = call.receive<ChargingRequest>()
            // Validate inputs using utility
            if (!Validator.isValidUUID(request.stationId) ||
                !Validator.isValidDriverToken(request.driverToken) ||
                !Validator.isValidUrl(request.callbackUrl)) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request payload"))
                return@post
            }
            service.processChargingRequest(request)
            call.respond(HttpStatusCode.Accepted, mapOf("status" to "accepted", "message" to "Processing started"))
        }
    }
}