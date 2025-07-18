package com.chargepoint.asynccharging.queue

import com.chargepoint.asynccharging.models.ChargingRequest
import java.util.concurrent.LinkedBlockingQueue

/**
 * In-memory queue implementation using singleton pattern.
 */
class AuthorizationQueue private constructor() {
    private val queue = LinkedBlockingQueue<ChargingRequest>()

    fun enqueue(request: ChargingRequest) = queue.put(request)

    fun dequeue(): ChargingRequest? = queue.poll()

    fun clear() = queue.clear()

    companion object {
        val instance: AuthorizationQueue by lazy { AuthorizationQueue() }
    }
}