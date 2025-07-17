package com.chargepoint.`async-charging`.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*

/**
 * Install content negotiation and JSON serializer.
 */
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json()
    }
}