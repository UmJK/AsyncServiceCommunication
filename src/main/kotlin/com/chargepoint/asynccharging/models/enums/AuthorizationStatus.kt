package com.chargepoint.asynccharging.models.enums

import kotlinx.serialization.Serializable

@Serializable
enum class AuthorizationStatus(val value: String) {
    ALLOWED("allowed"),
    NOT_ALLOWED("not_allowed"),
    UNKNOWN("unknown"),
    INVALID("invalid");
    
    override fun toString(): String = value
}
