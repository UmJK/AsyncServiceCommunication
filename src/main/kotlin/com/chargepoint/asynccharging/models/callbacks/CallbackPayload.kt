package com.chargepoint.asynccharging.models.callbacks

import kotlinx.serialization.Serializable

@Serializable
data class CallbackPayload(
    val station_id: String,
    val driver_token: String,
    val status: String,
    val timestamp: Long = System.currentTimeMillis()
)
