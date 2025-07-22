package com.chargepoint.asynccharging.models.responses

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: Long = System.currentTimeMillis(),
    val components: Map<String, ComponentHealth>
)

@Serializable
data class ComponentHealth(
    val status: String,
    val details: Map<String, String>? = null
)
