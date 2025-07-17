package com.chargepoint.`async-charging`.services

import com.chargepoint.`async-charging`.models.AuthorizationDecision
import com.chargepoint.`async-charging`.models.ChargingRequest
import com.chargepoint.`async-charging`.repository.AuthorizationRepository

/**
 * Service responsible for handling authorization logic.
 */
class AuthorizationService {
    fun authorize(request: ChargingRequest): AuthorizationDecision {
        return AuthorizationRepository.instance.checkACL(request)
    }
}