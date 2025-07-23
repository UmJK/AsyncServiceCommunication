package com.chargepoint.asynccharging.models.enums

import kotlinx.serialization.Serializable

/**
 * Authorization status values as defined by ChargePoint specification
 */
@Serializable
enum class AuthorizationStatus {
    ALLOWED,        // Driver is authorized to charge
    NOT_ALLOWED,    // Driver is not in ACL or denied
    UNKNOWN,        // Authorization service timeout or error - SPECIFICATION REQUIREMENT
    INVALID         // Invalid request format or validation failure
}
