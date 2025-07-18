package com.chargepoint.asynccharging.queue

import com.chargepoint.asynccharging.models.ChargingRequest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthorizationQueueTest {

    private val queue = AuthorizationQueue.instance

    @BeforeTest
    fun setup() {
        queue.clear() // Ensure the queue is empty before each test
    }

    @Test
    fun `enqueue and dequeue request`() {
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "validDriverToken123456789",
            callbackUrl = "http://localhost/callback"
        )

        queue.enqueue(request)
        val dequeued = queue.dequeue()

        assertEquals(request, dequeued)
    }

    @Test
    fun `dequeue returns null on empty queue`() {
        assertNull(queue.dequeue())
    }
}
