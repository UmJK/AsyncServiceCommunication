package com.chargepoint.asynccharging.models.responses

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val status: String,
    val message: String,
    val timestamp: Long,
    val details: Map<String, String>? = null
)
