package com.chargepoint.asynccharging.services

import com.chargepoint.asynccharging.models.AuthorizationDecision
import com.chargepoint.asynccharging.models.ChargingRequest
import com.chargepoint.asynccharging.repository.AuthorizationRepository

/**
 * Service responsible for handling authorization logic.
 */
class AuthorizationService {
    fun authorize(request: ChargingRequest): AuthorizationDecision {
        return AuthorizationRepository.instance.checkACL(request)
    }
}