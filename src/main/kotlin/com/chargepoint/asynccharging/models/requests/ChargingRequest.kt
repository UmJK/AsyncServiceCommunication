package com.chargepoint.asynccharging.models.requests

import com.chargepoint.asynccharging.utils.Validator
import com.chargepoint.asynccharging.exceptions.ValidationException
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
data class ChargingRequest(
    val stationId: String,
    val driverToken: String,
    val callbackUrl: String,
    val requestId: String = UUID.randomUUID().toString(),
    val timestamp: Long = Instant.now().toEpochMilli()
) {
    fun validate() {
        if (!Validator.isValidUUID(stationId)) {
            throw ValidationException("Invalid station_id format. Must be a valid UUID.")
        }
        
        if (!Validator.isValidDriverToken(driverToken)) {
            throw ValidationException(
                "Invalid driver_token. Must be 20-80 characters containing only " +
                "alphanumeric characters, hyphens, periods, underscores, and tildes."
            )
        }
        
        if (!Validator.isValidUrl(callbackUrl)) {
            throw ValidationException("Invalid callback_url. Must be a valid HTTP/HTTPS URL.")
        }
    }
    
    fun toLogString(): String = "ChargingRequest(requestId=$requestId, stationId=$stationId, driverToken=${driverToken.take(8)}***)"
}
