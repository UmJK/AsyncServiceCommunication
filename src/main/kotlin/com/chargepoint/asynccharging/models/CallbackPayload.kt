package com.chargepoint.asynccharging.models

import kotlinx.serialization.Serializable

@Serializable
data class CallbackPayload(
    val callbackUrl: String,
    val stationId: String,
    val driverToken: String,
    val decision: String
)
