package com.chargepoint.asynccharging.models.decisions

import com.chargepoint.asynccharging.models.enums.AuthorizationStatus
import kotlinx.serialization.Serializable

/**
 * Authorization decision result
 * Note: driverToken is not included for security reasons
 */
@Serializable
data class AuthorizationDecision(
    val requestId: String,
    val stationId: String,
    val status: AuthorizationStatus,
    val reason: String? = null,
    val processingTimeMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)
