package com.chargepoint.asynccharging.queue

import com.chargepoint.asynccharging.models.CallbackPayload
import com.chargepoint.asynccharging.services.AuthorizationService
import com.chargepoint.asynccharging.services.CallbackService
import kotlinx.coroutines.runBlocking

/**
 * Continuously listens to the queue and processes charging requests asynchronously.
 */
class QueueConsumer {
    private val authorizationService = AuthorizationService()
    private val callbackService = CallbackService()

    fun start() {
        while (true) {
            val request = AuthorizationQueue.instance.dequeue()
            if (request != null) {
                val decision = authorizationService.authorize(request)
                val callbackPayload = CallbackPayload(
                    callbackUrl = request.callbackUrl,
                    stationId = request.stationId,
                    driverToken = request.driverToken,
                    decision = decision.status
                )
                runBlocking {
                    callbackService.sendCallback(callbackPayload)
                }
            } else {
                Thread.sleep(100)
            }
        }
    }
}
