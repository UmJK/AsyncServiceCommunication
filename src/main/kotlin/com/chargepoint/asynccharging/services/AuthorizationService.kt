package com.chargepoint.asynccharging.services

import com.chargepoint.asynccharging.models.decisions.AuthorizationDecision
import com.chargepoint.asynccharging.models.requests.ChargingRequest

/**
 * Service interface for authorizing charging requests
 */
interface AuthorizationService {
    suspend fun authorize(request: ChargingRequest): AuthorizationDecision
}
