package com.chargepoint.`async-charging`.models

import kotlinx.serialization.Serializable

/**
 * Authorization decision model that will be returned as callback.
 */
@Serializable
data class AuthorizationDecision(
    val stationId: String,
    val driverToken: String,
    val status: String,
    val callbackUrl: String
)
