package com.chargepoint.asynccharging.models

import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class ChargingRequest(
    val userId: String,
    val chargerId: String,
    val stationId: String,
    val connectorId: Int,
    val requestedEnergy: String, // Will be converted to BigDecimal
    val maxDurationMinutes: Int,
    val callbackUrl: String,
    val metadata: Map<String, String> = emptyMap()
) {
    fun getRequestedEnergyAsBigDecimal(): BigDecimal {
        return requestedEnergy.toBigDecimal()
    }
}