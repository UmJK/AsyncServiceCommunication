package com.chargepoint.`async-charging`.models

import kotlinx.serialization.Serializable

@Serializable
data class CallbackPayload(
    val stationId: String,
    val driverToken: String,
    val status: String
)
