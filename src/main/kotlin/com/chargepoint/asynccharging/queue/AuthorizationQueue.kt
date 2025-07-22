package com.chargepoint.asynccharging.queue

import com.chargepoint.asynccharging.config.QueueConfig
import com.chargepoint.asynccharging.models.requests.ChargingRequest
import com.chargepoint.asynccharging.services.MetricsService
import com.chargepoint.asynccharging.exceptions.QueueException
import mu.KotlinLogging
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

interface MessageQueue<T> {
    fun enqueue(item: T): Boolean
    fun dequeue(timeoutMs: Long = 5000): T?
    fun size(): Int
    fun isEmpty(): Boolean
    fun clear()
}

class AuthorizationQueue(
    private val config: QueueConfig,
    private val metricsService: MetricsService
) : MessageQueue<ChargingRequest> {
    
    private val queue: BlockingQueue<ChargingRequest> = ArrayBlockingQueue(config.maxSize)
    
    override fun enqueue(item: ChargingRequest): Boolean {
        return try {
            val result = queue.offer(item, 1, TimeUnit.SECONDS)
            if (result) {
                metricsService.recordQueueSize(queue.size)
                logger.debug { "Enqueued request ${item.toLogString()}, queue size: ${queue.size}" }
            } else {
                logger.warn { "Queue is full, rejected request ${item.toLogString()}" }
                throw QueueException("Queue is full (size: ${queue.size}, max: ${config.maxSize})")
            }
            result
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.error(e) { "Interrupted while enqueuing request ${item.toLogString()}" }
            false
        }
    }
    
    override fun dequeue(timeoutMs: Long): ChargingRequest? {
        return try {
            val item = queue.poll(timeoutMs, TimeUnit.MILLISECONDS)
            if (item != null) {
                metricsService.recordQueueSize(queue.size)
                logger.debug { "Dequeued request ${item.toLogString()}, queue size: ${queue.size}" }
            }
            item
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.debug { "Interrupted while waiting for queue item" }
            null
        }
    }
    
    override fun size(): Int = queue.size
    
    override fun isEmpty(): Boolean = queue.isEmpty()
    
    override fun clear() {
        queue.clear()
        metricsService.recordQueueSize(0)
        logger.info { "Queue cleared" }
    }
}
