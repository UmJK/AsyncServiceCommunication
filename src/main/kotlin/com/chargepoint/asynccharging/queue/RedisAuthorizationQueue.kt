package com.chargepoint.asynccharging.queue

import com.chargepoint.asynccharging.config.RedisConfig
import com.chargepoint.asynccharging.models.ChargingRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

class RedisAuthorizationQueue(
    private val redisConfig: RedisConfig
) : AuthorizationQueue {

    private val logger = LoggerFactory.getLogger(RedisAuthorizationQueue::class.java)
    private lateinit var jedisPool: JedisPool

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private const val AUTHORIZATION_QUEUE_KEY = "authorization:requests"
        private const val PROCESSING_QUEUE_KEY = "authorization:processing"
        private const val FAILED_QUEUE_KEY = "authorization:failed"
        private const val RETRY_QUEUE_KEY = "authorization:retry"
    }

    override suspend fun initialize() = withContext(Dispatchers.IO) {
        logger.info("Initializing Redis connection pool...")

        val poolConfig = JedisPoolConfig().apply {
            maxTotal = 20
            maxIdle = 10
            minIdle = 5
            testOnBorrow = true
            testOnReturn = true
            testWhileIdle = true
            blockWhenExhausted = true
            maxWaitMillis = redisConfig.timeout
        }

        jedisPool = if (redisConfig.password != null) {
            JedisPool(
                poolConfig,
                redisConfig.host,
                redisConfig.port,
                redisConfig.timeout.toInt(),
                redisConfig.password,
                redisConfig.database
            )
        } else {
            JedisPool(
                poolConfig,
                redisConfig.host,
                redisConfig.port,
                redisConfig.timeout.toInt(),
                null,
                redisConfig.database
            )
        }

        // Test connection
        try {
            jedisPool.resource.use { jedis ->
                jedis.ping()
                logger.info("Redis connection established successfully")
            }
        } catch (e: Exception) {
            logger.error("Failed to connect to Redis", e)
            throw e
        }
    }

    override suspend fun enqueue(authorizationId: String, request: ChargingRequest): Boolean = withContext(Dispatchers.IO) {
        try {
            val message = QueueMessage(authorizationId, request, System.currentTimeMillis())
            val messageJson = json.encodeToString(message)

            jedisPool.resource.use { jedis ->
                val result = jedis.lpush(AUTHORIZATION_QUEUE_KEY, messageJson)
                logger.debug("Enqueued authorization request: $authorizationId")
                result > 0
            }
        } catch (e: Exception) {
            logger.error("Error enqueuing authorization request: $authorizationId", e)
            false
        }
    }

    override suspend fun dequeue(): QueueMessage? = withContext(Dispatchers.IO) {
        try {
            jedisPool.resource.use { jedis ->
                // Use blocking right pop with timeout for efficient polling
                val result = jedis.brpoplpush(AUTHORIZATION_QUEUE_KEY, PROCESSING_QUEUE_KEY, 1)

                result?.let { messageJson ->
                    try {
                        json.decodeFromString<QueueMessage>(messageJson).also {
                            logger.debug("Dequeued authorization request: ${it.authorizationId}")
                        }
                    } catch (e: Exception) {
                        logger.error("Error deserializing queue message: $messageJson", e)
                        // Move malformed message to failed queue
                        jedis.lpush(FAILED_QUEUE_KEY, messageJson)
                        null
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error dequeuing authorization request", e)
            null
        }
    }

    override suspend fun acknowledgeProcessing(authorizationId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            jedisPool.resource.use { jedis ->
                // Remove from processing queue
                val processingMessage = findMessageInQueue(jedis, PROCESSING_QUEUE_KEY, authorizationId)
                val removed = if (processingMessage != null) {
                    jedis.lrem(PROCESSING_QUEUE_KEY, 1, processingMessage)
                } else {
                    0L
                }
                logger.debug("Acknowledged processing for authorization: $authorizationId")
                removed > 0
            }
        } catch (e: Exception) {
            logger.error("Error acknowledging processing for: $authorizationId", e)
            false
        }
    }

    override suspend fun requeueForRetry(authorizationId: String, retryCount: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            jedisPool.resource.use { jedis ->
                val processingMessage = findMessageInQueue(jedis, PROCESSING_QUEUE_KEY, authorizationId)

                if (processingMessage != null) {
                    // Parse message and increment retry count
                    val message = json.decodeFromString<QueueMessage>(processingMessage)
                    val retryMessage = message.copy(
                        retryCount = retryCount,
                        lastRetryAt = System.currentTimeMillis()
                    )

                    // Move to retry queue with delay
                    val retryMessageJson = json.encodeToString(retryMessage)
                    jedis.lpush(RETRY_QUEUE_KEY, retryMessageJson)

                    // Remove from processing queue
                    jedis.lrem(PROCESSING_QUEUE_KEY, 1, processingMessage)

                    logger.debug("Requeued for retry (attempt $retryCount): $authorizationId")
                    true
                } else {
                    logger.warn("Message not found in processing queue for retry: $authorizationId")
                    false
                }
            }
        } catch (e: Exception) {
            logger.error("Error requeuing for retry: $authorizationId", e)
            false
        }
    }

    override suspend fun markAsFailed(authorizationId: String, error: String): Boolean = withContext(Dispatchers.IO) {
        try {
            jedisPool.resource.use { jedis ->
                val processingMessage = findMessageInQueue(jedis, PROCESSING_QUEUE_KEY, authorizationId)

                if (processingMessage != null) {
                    // Parse message and mark as failed
                    val message = json.decodeFromString<QueueMessage>(processingMessage)
                    val failedMessage = message.copy(
                        failureReason = error,
                        failedAt = System.currentTimeMillis()
                    )

                    // Move to failed queue
                    val failedMessageJson = json.encodeToString(failedMessage)
                    jedis.lpush(FAILED_QUEUE_KEY, failedMessageJson)

                    // Remove from processing queue
                    jedis.lrem(PROCESSING_QUEUE_KEY, 1, processingMessage)

                    logger.debug("Marked as failed: $authorizationId - $error")
                    true
                } else {
                    logger.warn("Message not found in processing queue to mark as failed: $authorizationId")
                    false
                }
            }
        } catch (e: Exception) {
            logger.error("Error marking as failed: $authorizationId", e)
            false
        }
    }

    override suspend fun getQueueSize(): Long = withContext(Dispatchers.IO) {
        try {
            jedisPool.resource.use { jedis ->
                jedis.llen(AUTHORIZATION_QUEUE_KEY)
            }
        } catch (e: Exception) {
            logger.error("Error getting queue size", e)
            0L
        }
    }

    override suspend fun getProcessingQueueSize(): Long = withContext(Dispatchers.IO) {
        try {
            jedisPool.resource.use { jedis ->
                jedis.llen(PROCESSING_QUEUE_KEY)
            }
        } catch (e: Exception) {
            logger.error("Error getting processing queue size", e)
            0L
        }
    }

    override suspend fun getFailedQueueSize(): Long = withContext(Dispatchers.IO) {
        try {
            jedisPool.resource.use { jedis ->
                jedis.llen(FAILED_QUEUE_KEY)
            }
        } catch (e: Exception) {
            logger.error("Error getting failed queue size", e)
            0L
        }
    }

    override suspend fun getRetryQueueSize(): Long = withContext(Dispatchers.IO) {
        try {
            jedisPool.resource.use { jedis ->
                jedis.llen(RETRY_QUEUE_KEY)
            }
        } catch (e: Exception) {
            logger.error("Error getting retry queue size", e)
            0L
        }
    }

    override suspend fun getStatistics(): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            jedisPool.resource.use { jedis ->
                mapOf(
                    "queueSize" to jedis.llen(AUTHORIZATION_QUEUE_KEY),
                    "processingSize" to jedis.llen(PROCESSING_QUEUE_KEY),
                    "failedSize" to jedis.llen(FAILED_QUEUE_KEY),
                    "retrySize" to jedis.llen(RETRY_QUEUE_KEY),
                    "totalMessages" to (jedis.llen(AUTHORIZATION_QUEUE_KEY) + jedis.llen(PROCESSING_QUEUE_KEY) + jedis.llen(FAILED_QUEUE_KEY) + jedis.llen(RETRY_QUEUE_KEY))
                )
            }
        } catch (e: Exception) {
            logger.error("Error getting queue statistics", e)
            emptyMap()
        }
    }

    override suspend fun healthCheck(): Map<String, Any> {
        return try {
            jedisPool.resource.use { jedis ->
                val startTime = System.currentTimeMillis()
                val pong = jedis.ping()
                val responseTime = System.currentTimeMillis() - startTime

                mapOf(
                    "status" to "healthy",
                    "ping" to pong,
                    "responseTimeMs" to responseTime,
                    "host" to redisConfig.host,
                    "port" to redisConfig.port,
                    "database" to redisConfig.database
                )
            }
        } catch (e: Exception) {
            mapOf(
                "status" to "unhealthy",
                "error" to (e.message ?: "Unknown error"),
                "host" to redisConfig.host,
                "port" to redisConfig.port
            )
        }
    }

    override suspend fun clearAllQueues(): Boolean = withContext(Dispatchers.IO) {
        try {
            jedisPool.resource.use { jedis ->
                jedis.del(AUTHORIZATION_QUEUE_KEY, PROCESSING_QUEUE_KEY, FAILED_QUEUE_KEY, RETRY_QUEUE_KEY)
                logger.info("Cleared all queues")
                true
            }
        } catch (e: Exception) {
            logger.error("Error clearing all queues", e)
            false
        }
    }

    override suspend fun getFailedMessages(limit: Int): List<QueueMessage> = withContext(Dispatchers.IO) {
        try {
            jedisPool.resource.use { jedis ->
                val messages = jedis.lrange(FAILED_QUEUE_KEY, 0, (limit - 1).toLong())
                messages.mapNotNull { messageJson ->
                    try {
                        json.decodeFromString<QueueMessage>(messageJson)
                    } catch (e: Exception) {
                        logger.error("Error deserializing failed message: $messageJson", e)
                        null
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting failed messages", e)
            emptyList()
        }
    }

    override suspend fun requeueFailedMessages(authorizationIds: List<String>): Int = withContext(Dispatchers.IO) {
        var requeueCount = 0
        try {
            jedisPool.resource.use { jedis ->
                authorizationIds.forEach { authId ->
                    val failedMessage = findMessageInQueue(jedis, FAILED_QUEUE_KEY, authId)
                    if (failedMessage != null) {
                        jedis.lpush(AUTHORIZATION_QUEUE_KEY, failedMessage)
                        jedis.lrem(FAILED_QUEUE_KEY, 1, failedMessage)
                        requeueCount++
                    }
                }
            }
            logger.info("Requeued $requeueCount failed messages")
        } catch (e: Exception) {
            logger.error("Error requeuing failed messages", e)
        }
        return@withContext requeueCount
    }

    override suspend fun getStuckMessages(olderThanMinutes: Int): List<QueueMessage> = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = System.currentTimeMillis() - (olderThanMinutes * 60 * 1000L)
            jedisPool.resource.use { jedis ->
                val messages = jedis.lrange(PROCESSING_QUEUE_KEY, 0, -1)
                messages.mapNotNull { messageJson ->
                    try {
                        val message = json.decodeFromString<QueueMessage>(messageJson)
                        if (message.enqueuedAt < cutoffTime) message else null
                    } catch (e: Exception) {
                        logger.error("Error deserializing stuck message: $messageJson", e)
                        null
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting stuck messages", e)
            emptyList()
        }
    }

    override suspend fun shutdown() = withContext(Dispatchers.IO) {
        if (::jedisPool.isInitialized && !jedisPool.isClosed) {
            jedisPool.close()
            logger.info("Redis connection pool closed")
        }
    }

    private fun findMessageInQueue(jedis: redis.clients.jedis.Jedis, queueKey: String, authorizationId: String): String? {
        val queueContents = jedis.lrange(queueKey, 0, -1)
        return queueContents.find { messageJson ->
            try {
                val message = json.decodeFromString<QueueMessage>(messageJson)
                message.authorizationId == authorizationId
            } catch (e: Exception) {
                false
            }
        }
    }

    fun close() {
        if (::jedisPool.isInitialized && !jedisPool.isClosed) {
            jedisPool.close()
            logger.info("Redis connection pool closed")
        }
    }
}
