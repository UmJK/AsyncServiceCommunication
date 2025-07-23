package com.chargepoint.asynccharging.models.callbacks

import kotlinx.serialization.Serializable

/**
 * Callback payload as defined by ChargePoint specification
 */
@Serializable
data class CallbackPayload(
    val station_id: String,
    val driver_token: String,
    val status: String // "allowed", "not_allowed", "unknown", "invalid"
)
