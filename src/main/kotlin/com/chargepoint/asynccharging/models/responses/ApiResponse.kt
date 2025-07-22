package com.chargepoint.asynccharging.models.responses

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse(
    val status: String,
    val message: String,
    val requestId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
