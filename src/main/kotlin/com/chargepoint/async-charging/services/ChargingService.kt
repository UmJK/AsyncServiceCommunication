package com.chargepoint.`async-charging`.services

import com.chargepoint.`async-charging`.models.ChargingRequest
import com.chargepoint.`async-charging`.queue.AuthorizationQueue

/**
 * Service responsible for handling business logic of charging request.
 */
class ChargingService {
    fun processChargingRequest(request: ChargingRequest) {
        AuthorizationQueue.instance.enqueue(request)
    }
}