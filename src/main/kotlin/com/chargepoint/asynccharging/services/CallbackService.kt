package com.chargepoint.asynccharging.services

import com.chargepoint.asynccharging.models.decisions.AuthorizationDecision

/**
 * Service interface for sending callback notifications
 */
interface CallbackService {
    suspend fun sendCallback(decision: AuthorizationDecision, callbackUrl: String): Boolean
}
