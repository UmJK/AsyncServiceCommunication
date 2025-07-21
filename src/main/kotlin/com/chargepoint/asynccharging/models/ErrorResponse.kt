package com.chargepoint.asynccharging.models

import kotlinx.serialization.Serializable

/**
 * Standard error response model
 */
@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
    val requestId: String,
    val authorizationId: String? = null,
    val timestamp: String = kotlinx.datetime.Clock.System.now().toString(),
    val details: Map<String, String> = emptyMap()
)
