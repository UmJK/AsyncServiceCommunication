package com.chargepoint.`async-charging`.queue

import com.chargepoint.`async-charging`.models.ChargingRequest
import java.util.concurrent.LinkedBlockingQueue

/**
 * In-memory queue implementation using singleton pattern.
 */
class AuthorizationQueue private constructor() {
    private val queue = LinkedBlockingQueue<ChargingRequest>()

    fun enqueue(request: ChargingRequest) = queue.put(request)
    fun dequeue(): ChargingRequest? = queue.poll()

    companion object {
        val instance: AuthorizationQueue by lazy { AuthorizationQueue() }
    }
}