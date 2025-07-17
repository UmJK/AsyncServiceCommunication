package com.chargepoint.`async-charging`.queue

import com.chargepoint.`async-charging`.services.AuthorizationService
import com.chargepoint.`async-charging`.services.CallbackService
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
                runBlocking {
                    callbackService.sendCallback(decision)
                }
            } else {
                Thread.sleep(100)
            }
        }
    }
}