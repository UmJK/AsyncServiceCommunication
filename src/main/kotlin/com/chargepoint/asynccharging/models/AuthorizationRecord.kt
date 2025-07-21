package com.chargepoint.asynccharging.models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant
import java.math.BigDecimal

/**
 * Data class representing an authorization record in the database
 */
@Serializable
data class AuthorizationRecord(
    val authorizationId: String,
    val userId: String,
    val stationId: String,
    val connectorId: Int,
    @Contextual val requestedEnergy: BigDecimal,
    val maxDurationMinutes: Int,
    val callbackUrl: String,
    val decision: String, // PENDING, APPROVED, REJECTED
    val reason: String? = null,
    @Contextual val approvedEnergy: BigDecimal? = null,
    val metadata: Map<String, String> = emptyMap(),
    @Contextual val timestamp: Instant,
    @Contextual val processedAt: Instant? = null,
    @Contextual val expiresAt: Instant? = null
)