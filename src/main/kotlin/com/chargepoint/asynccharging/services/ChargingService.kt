package com.chargepoint.asynccharging.services

import com.chargepoint.asynccharging.models.ChargingRequest
import com.chargepoint.asynccharging.queue.AuthorizationQueue

/**
 * Service responsible for handling business logic of charging request.
 */
class ChargingService {
    fun processChargingRequest(request: ChargingRequest) {
        AuthorizationQueue.instance.enqueue(request)
    }
}