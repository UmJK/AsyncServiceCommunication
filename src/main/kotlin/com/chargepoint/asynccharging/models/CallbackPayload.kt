package com.chargepoint.asynccharging.models

import kotlinx.serialization.Serializable

@Serializable
data class CallbackPayload(
    val authorizationId: String,
    val userId: String,
    val stationId: String,
    val connectorId: Int,
    val decision: String,
    val reason: String?,
    val approvedEnergy: String?,
    val timestamp: String
)