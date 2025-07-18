package com.chargepoint.asynccharging.repository

import com.chargepoint.asynccharging.models.AuthorizationDecision
import com.chargepoint.asynccharging.models.ChargingRequest

/**
 * Repository simulating access control list (ACL) to evaluate authorization logic.
 */
class AuthorizationRepository private constructor() {

    fun checkACL(request: ChargingRequest): AuthorizationDecision {
        val status = when {
            request.driverToken.startsWith("test") -> "allowed"
            request.driverToken.startsWith("blocked") -> "not_allowed"
            else -> "unknown"
        }
        return AuthorizationDecision(
            stationId = request.stationId,
            driverToken = request.driverToken,
            status = status,
            callbackUrl = request.callbackUrl
        )
    }

    companion object {
        val instance: AuthorizationRepository by lazy { AuthorizationRepository() }
    }
}