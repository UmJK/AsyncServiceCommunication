package com.chargepoint.asynccharging.models

import kotlinx.serialization.Serializable

/**
 * Charging request input model.
 */
@Serializable
data class ChargingRequest(
    val stationId: String,
    val driverToken: String,
    val callbackUrl: String
)
