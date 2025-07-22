package com.chargepoint.asynccharging.models.decisions

import com.chargepoint.asynccharging.models.enums.AuthorizationStatus
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class AuthorizationDecision(
    val requestId: String,
    val stationId: String,
    val driverToken: String,
    val status: AuthorizationStatus,
    val reason: String? = null,
    val processedAt: Long = Instant.now().toEpochMilli(),
    val processingTimeMs: Long = 0
) {
    fun toLogString(): String = "AuthorizationDecision(requestId=$requestId, status=$status, processingTime=${processingTimeMs}ms)"
}
