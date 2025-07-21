
package com.chargepoint.asynccharging.utils

import com.chargepoint.asynccharging.models.ChargingRequest
import java.math.BigDecimal

data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String = "" // Changed from String? to String with default empty string
) {
    companion object {
        fun valid() = ValidationResult(true)
        fun invalid(message: String) = ValidationResult(false, message)
    }
}

object Validator {

    fun validateChargingRequest(request: ChargingRequest): ValidationResult {
        // User ID validation
        if (request.userId.isBlank()) {
            return ValidationResult.invalid("User ID cannot be empty")
        }
        if (request.userId.length > 100) {
            return ValidationResult.invalid("User ID cannot exceed 100 characters")
        }

        // Station ID validation
        if (request.stationId.isBlank()) {
            return ValidationResult.invalid("Station ID cannot be empty")
        }
        if (request.stationId.length > 50) {
            return ValidationResult.invalid("Station ID cannot exceed 50 characters")
        }

        // Connector ID validation
        if (request.connectorId < 1 || request.connectorId > 10) {
            return ValidationResult.invalid("Connector ID must be between 1 and 10")
        }

        // Energy validation
        try {
            val energy = request.getRequestedEnergyAsBigDecimal()
            if (energy <= BigDecimal.ZERO) {
                return ValidationResult.invalid("Requested energy must be greater than zero")
            }
            if (energy > BigDecimal("100.0")) {
                return ValidationResult.invalid("Requested energy cannot exceed 100 kWh")
            }
        } catch (e: NumberFormatException) {
            return ValidationResult.invalid("Invalid energy format: ${request.requestedEnergy}")
        }

        // Duration validation
        if (request.maxDurationMinutes <= 0) {
            return ValidationResult.invalid("Maximum duration must be greater than zero")
        }
        if (request.maxDurationMinutes > 1440) { // 24 hours
            return ValidationResult.invalid("Maximum duration cannot exceed 24 hours (1440 minutes)")
        }

        // Callback URL validation
        if (request.callbackUrl.isBlank()) {
            return ValidationResult.invalid("Callback URL cannot be empty")
        }
        if (!isValidUrl(request.callbackUrl)) {
            return ValidationResult.invalid("Invalid callback URL format")
        }

        // Metadata validation
        if (request.metadata.size > 20) {
            return ValidationResult.invalid("Metadata cannot contain more than 20 entries")
        }

        for ((key, value) in request.metadata) {
            if (key.length > 50) {
                return ValidationResult.invalid("Metadata key cannot exceed 50 characters: $key")
            }
            if (value.length > 200) {
                return ValidationResult.invalid("Metadata value cannot exceed 200 characters for key: $key")
            }
        }

        return ValidationResult.valid()
    }

    fun validateChargingRequestWithBusinessRules(request: ChargingRequest): ValidationResult {
        // First run basic validation
        val basicValidation = validateChargingRequest(request)
        if (!basicValidation.isValid) {
            return basicValidation
        }

        // Additional business rule validations
        val energy = request.getRequestedEnergyAsBigDecimal()

        // Peak hours energy limit (simulate business rule)
        val currentHour = java.time.LocalTime.now().hour
        val isPeakHours = currentHour in 17..20 // 5 PM to 8 PM
        if (isPeakHours && energy > BigDecimal("30.0")) {
            return ValidationResult.invalid("During peak hours (5 PM - 8 PM), energy cannot exceed 30 kWh")
        }

        // Weekend energy boost (simulate business rule)
        val isWeekend = java.time.LocalDate.now().dayOfWeek.value in 6..7
        val maxWeekendEnergy = if (isWeekend) BigDecimal("150.0") else BigDecimal("100.0")
        if (energy > maxWeekendEnergy) {
            val limit = if (isWeekend) "150 kWh (weekend)" else "100 kWh"
            return ValidationResult.invalid("Requested energy exceeds limit: $limit")
        }

        // Station-specific rules (simulate)
        if (request.stationId.startsWith("FAST_") && energy < BigDecimal("10.0")) {
            return ValidationResult.invalid("Fast charging stations require minimum 10 kWh")
        }

        return ValidationResult.valid()
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            val regex = Regex(
                "^https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]",
                RegexOption.IGNORE_CASE
            )
            regex.matches(url)
        } catch (e: Exception) {
            false
        }
    }
}