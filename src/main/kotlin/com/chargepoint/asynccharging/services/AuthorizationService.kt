package com.chargepoint.asynccharging.services

import com.chargepoint.asynccharging.database.AuthorizationRepository
import com.chargepoint.asynccharging.models.*
import com.chargepoint.asynccharging.queue.AuthorizationQueue
import com.chargepoint.asynccharging.utils.Validator
import com.chargepoint.asynccharging.database.AuthorizationRepositoryInterface

import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

/**
 * Service responsible for processing charging authorization requests asynchronously
 */
class AuthorizationService(
    private val repository: AuthorizationRepositoryInterface,
    private val queue: AuthorizationQueue
) {

    private val logger = LoggerFactory.getLogger(AuthorizationService::class.java)

    // Service state management
    private val isProcessing = AtomicBoolean(false)
    private val processedCount = AtomicLong(0)
    private val errorCount = AtomicLong(0)
    private var processingJob: Job? = null
    private val startTime = Clock.System.now()

    // Active authorization tracking
    private val activeAuthorizations = ConcurrentHashMap<String, AuthorizationRecord>()

    // Processing metrics
    private val processingTimes = mutableListOf<Long>()
    private val maxMetricsSize = 100

    // Create a dedicated coroutine scope for processing
    private val processingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val PROCESSING_DELAY_MS = 1000L
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 5000L
        private const val BATCH_SIZE = 10
        private const val POLL_INTERVAL_MS = 500L
    }

    /**
     * Submit a new authorization request for async processing
     */
    suspend fun submitAuthorizationRequest(request: ChargingRequest): String {
        logger.info("Submitting authorization request for user: ${request.userId}, station: ${request.stationId}")

        // Validate request first
        val validation = Validator.validateChargingRequestWithBusinessRules(request)
        if (!validation.isValid) {
            logger.warn("Invalid charging request: ${validation.errorMessage}")
            throw IllegalArgumentException("Invalid request: ${validation.errorMessage}")
        }

        // Generate authorization ID
        val authorizationId = generateAuthorizationId()

        // Create authorization record
        val record = AuthorizationRecord(
            authorizationId = authorizationId,
            userId = request.userId,
            stationId = request.stationId,
            connectorId = request.connectorId,
            requestedEnergy = request.getRequestedEnergyAsBigDecimal(),
            maxDurationMinutes = request.maxDurationMinutes,
            callbackUrl = request.callbackUrl,
            decision = "PENDING",
            timestamp = Clock.System.now(),
            metadata = request.metadata
        )

        try {
            // Save to database
            repository.save(record)
            logger.debug("Authorization record saved: $authorizationId")

            // Add to active tracking
            activeAuthorizations[authorizationId] = record

            // Add to processing queue with authorization ID in metadata
            val requestWithId = request.copy(
                metadata = request.metadata + ("authorizationId" to authorizationId)
            )
            queue.enqueue(authorizationId, requestWithId)

            logger.info("Authorization request submitted successfully: $authorizationId")
            return authorizationId

        } catch (e: Exception) {
            logger.error("Error submitting authorization request: $authorizationId", e)
            throw e
        }
    }

    /**
     * Start background processing of authorization requests
     */
    suspend fun startProcessing() {
        if (isProcessing.compareAndSet(false, true)) {
            logger.info("Starting authorization processing...")

            processingJob = processingScope.launch {
                while (isProcessing.get()) {
                    try {
                        processNextBatch(BATCH_SIZE)
                        delay(POLL_INTERVAL_MS)
                    } catch (e: CancellationException) {
                        logger.info("Authorization processing cancelled")
                        break
                    } catch (e: Exception) {
                        logger.error("Error in authorization processing loop", e)
                        errorCount.incrementAndGet()
                        delay(RETRY_DELAY_MS) // Back off on error
                    }
                }
            }

            logger.info("Authorization processing started")
        } else {
            logger.warn("Authorization processing already running")
        }
    }

    /**
     * Stop background processing
     */
    fun stopProcessing() {
        if (isProcessing.compareAndSet(true, false)) {
            logger.info("Stopping authorization processing...")

            processingJob?.cancel()
            processingJob = null

            logger.info("Authorization processing stopped. Processed: ${processedCount.get()}, Errors: ${errorCount.get()}")
        }
    }

    /**
     * Process next batch of requests from the queue
     */
    private suspend fun processNextBatch(batchSize: Int) {
        val messages = mutableListOf<com.chargepoint.asynccharging.queue.QueueMessage>()

        // Collect batch
        repeat(batchSize) {
            val message = queue.dequeue()
            if (message != null) {
                messages.add(message)
            }
        }

        if (messages.isEmpty()) {
            return
        }

        logger.debug("Processing batch of ${messages.size} authorization requests")

        // Process requests concurrently within the batch
        val jobs = messages.map { message ->
            processingScope.async {
                processAuthorizationRequest(message)
            }
        }

        // Wait for all to complete
        jobs.awaitAll()

        logger.debug("Batch processing completed")
    }

    /**
     * Process a single authorization request
     */
    suspend fun processAuthorizationRequest(message: com.chargepoint.asynccharging.queue.QueueMessage): Boolean {
        val authorizationId = message.authorizationId
        val request = message.request
        val startTime = System.currentTimeMillis()

        return try {
            logger.debug("Processing authorization request: $authorizationId")

            // Simulate authorization logic
            val decision = performAuthorization(request)
            val record = activeAuthorizations[authorizationId]

            if (record != null) {
                // Update record with decision using repository method
                repository.updateStatus(
                    authorizationId = authorizationId,
                    decision = decision.name,
                    reason = getDecisionReason(decision),
                    processedAt = Clock.System.now()
                )

                // Update active tracking
                val updatedRecord = record.copy(
                    decision = decision.name,
                    reason = getDecisionReason(decision),
                    processedAt = Clock.System.now(),
                    approvedEnergy = if (decision == AuthorizationDecision.APPROVED) record.requestedEnergy else null,
                    expiresAt = if (decision == AuthorizationDecision.APPROVED) {
                        Clock.System.now().plus(record.maxDurationMinutes.minutes)
                    } else null
                )

                activeAuthorizations[authorizationId] = updatedRecord

                // Send callback (simulate)
                sendAuthorizationCallback(updatedRecord)

                // Remove from active tracking if completed
                if (decision != AuthorizationDecision.PENDING) {
                    activeAuthorizations.remove(authorizationId)
                }

                // Acknowledge processing
                queue.acknowledgeProcessing(authorizationId)

                // Record processing time
                val processingTime = System.currentTimeMillis() - startTime
                recordProcessingTime(processingTime)

                processedCount.incrementAndGet()
                logger.info("Authorization processed: $authorizationId -> ${decision.name} (${processingTime}ms)")
                true

            } else {
                logger.warn("Authorization record not found: $authorizationId")
                queue.markAsFailed(authorizationId, "Authorization record not found")
                false
            }

        } catch (e: Exception) {
            logger.error("Error processing authorization: $authorizationId", e)
            errorCount.incrementAndGet()

            // Mark as failed in queue
            queue.markAsFailed(authorizationId, "Processing error: ${e.message}")

            // Update record with error
            try {
                val record = activeAuthorizations[authorizationId]
                if (record != null) {
                    repository.updateStatus(
                        authorizationId = authorizationId,
                        decision = AuthorizationDecision.REJECTED.name,
                        reason = "Processing error: ${e.message}",
                        processedAt = Clock.System.now()
                    )

                    val errorRecord = record.copy(
                        decision = AuthorizationDecision.REJECTED.name,
                        reason = "Processing error: ${e.message}",
                        processedAt = Clock.System.now()
                    )

                    sendAuthorizationCallback(errorRecord)
                    activeAuthorizations.remove(authorizationId)
                }
            } catch (callbackError: Exception) {
                logger.error("Error sending error callback for: $authorizationId", callbackError)
            }
            false
        }
    }


    /**
     * Perform the actual authorization logic
     */
    private suspend fun performAuthorization(request: ChargingRequest): AuthorizationDecision {
        // Check access control (simulate)
        val hasAccess = checkUserAccess(request.userId, request.stationId)
        if (!hasAccess) {
            logger.info("Access denied for user ${request.userId} to station ${request.stationId}")
            return AuthorizationDecision.REJECTED
        }

        // Check station availability (simulate)
        val isStationAvailable = checkStationAvailability(request.stationId, request.connectorId)
        if (!isStationAvailable) {
            logger.info("Station ${request.stationId} connector ${request.connectorId} not available")
            return AuthorizationDecision.REJECTED
        }

        // Check user limits (simulate)
        val withinLimits = checkUserLimits(request.userId, request.getRequestedEnergyAsBigDecimal())
        if (!withinLimits) {
            logger.info("User ${request.userId} exceeds energy limits")
            return AuthorizationDecision.REJECTED
        }

        // Check business rules (simulate peak hours, special conditions, etc.)
        val businessRulesPassed = checkBusinessRules(request)
        if (!businessRulesPassed) {
            logger.info("Business rules check failed for request: ${request.userId}")
            return AuthorizationDecision.REJECTED
        }

        // Simulate processing time (realistic authorization delay)
        delay(Random.nextLong(500, 2000))

        // 85% approval rate for simulation (more realistic)
        return if (Random.nextDouble() < 0.85) {
            AuthorizationDecision.APPROVED
        } else {
            AuthorizationDecision.REJECTED
        }
    }

    /**
     * Send authorization result callback (simulated)
     */
    private suspend fun sendAuthorizationCallback(record: AuthorizationRecord) {
        try {
            logger.debug("Sending callback for authorization: ${record.authorizationId}")

            // Simulate HTTP callback with delay
            delay(Random.nextLong(100, 500))

            // Simulate callback success rate (95% success)
            val callbackSuccess = Random.nextDouble() < 0.95

            if (callbackSuccess) {
                logger.debug("Callback sent successfully for: ${record.authorizationId}")
            } else {
                logger.warn("Failed to send callback for: ${record.authorizationId}")
                // In real implementation, you might want to retry or add to dead letter queue
            }

        } catch (e: Exception) {
            logger.error("Error sending callback for: ${record.authorizationId}", e)
        }
    }

    /**
     * Get status of an authorization
     */
    suspend fun getAuthorizationStatus(authorizationId: String): AuthorizationRecord? {
        return try {
            // Check active authorizations first (faster)
            activeAuthorizations[authorizationId] ?: repository.findById(authorizationId)
        } catch (e: Exception) {
            logger.error("Error getting authorization status: $authorizationId", e)
            null
        }
    }

    /**
     * Get service statistics
     */
    fun getStatistics(): Map<String, Any> {
        val now = Clock.System.now()
        val uptime = (now - startTime).inWholeSeconds

        val avgProcessingTime = if (processingTimes.isNotEmpty()) {
            processingTimes.average()
        } else 0.0

        val errorRate = if (processedCount.get() > 0) {
            (errorCount.get().toDouble() / processedCount.get().toDouble()) * 100
        } else 0.0

        return mapOf(
            "isProcessing" to isProcessing.get(),
            "processedCount" to processedCount.get(),
            "errorCount" to errorCount.get(),
            "activeAuthorizations" to activeAuthorizations.size.toLong(),
            "queueSize" to runBlocking { queue.getQueueSize() },
            "averageProcessingTime" to avgProcessingTime,
            "errorRate" to errorRate,
            "uptime" to uptime,
            "startTime" to startTime.toString()
        )
    }

    // Helper methods
    private fun generateAuthorizationId(): String {
        return "auth_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
    }

    private fun getDecisionReason(decision: AuthorizationDecision): String {
        return when (decision) {
            AuthorizationDecision.APPROVED -> "Authorization approved - all checks passed"
            AuthorizationDecision.REJECTED -> "Authorization rejected - failed business rules or availability check"
            AuthorizationDecision.PENDING -> "Authorization pending processing"
        }
    }

    private suspend fun checkUserAccess(userId: String, stationId: String): Boolean {
        // Simulate user access check with database lookup
        delay(50)

        // Simulate some users being blocked
        val blockedUsers = listOf("blocked_user_1", "blocked_user_2")
        if (userId in blockedUsers) {
            return false
        }

        // Simulate station-specific access (premium stations)
        if (stationId.startsWith("PREMIUM_") && !userId.startsWith("premium_")) {
            return false
        }

        return true
    }

    private suspend fun checkStationAvailability(stationId: String, connectorId: Int): Boolean {
        // Simulate station availability check
        delay(100)

        // Simulate some stations being offline
        if (stationId.endsWith("_OFFLINE")) {
            return false
        }

        // Simulate connector availability (80% available)
        return Random.nextDouble() < 0.8
    }

    private suspend fun checkUserLimits(userId: String, requestedEnergy: java.math.BigDecimal): Boolean {
        // Simulate user limit check
        delay(50)

        // Different limits based on user type
        val maxEnergy = when {
            userId.startsWith("premium_") -> java.math.BigDecimal("100.0")
            userId.startsWith("basic_") -> java.math.BigDecimal("30.0")
            else -> java.math.BigDecimal("50.0") // Standard limit
        }

        return requestedEnergy <= maxEnergy
    }

    private suspend fun checkBusinessRules(request: ChargingRequest): Boolean {
        // Simulate business rules check
        delay(25)

        val currentHour = java.time.LocalTime.now().hour
        val requestedEnergy = request.getRequestedEnergyAsBigDecimal()

        // Peak hours restrictions (5 PM - 8 PM)
        if (currentHour in 17..20 && requestedEnergy > java.math.BigDecimal("25.0")) {
            return false
        }

        // Maintenance window restrictions (2 AM - 4 AM)
        if (currentHour in 2..4) {
            return false
        }

        return true
    }

    private fun recordProcessingTime(processingTime: Long) {
        synchronized(processingTimes) {
            processingTimes.add(processingTime)
            if (processingTimes.size > maxMetricsSize) {
                processingTimes.removeAt(0) // Remove oldest entry
            }
        }
    }

    /**
     * Get detailed service health information
     */
    fun getHealthStatus(): Map<String, Any> {
        val stats = getStatistics()
        val errorRate = stats["errorRate"] as Double
        val queueSize = stats["queueSize"] as Long
        val avgProcessingTime = stats["averageProcessingTime"] as Double

        val status = when {
            !isProcessing.get() -> "unhealthy"
            errorRate > 25.0 -> "degraded"
            queueSize > 100 -> "warning"
            avgProcessingTime > 5000 -> "warning"
            else -> "healthy"
        }

        val issues = mutableListOf<String>()
        if (!isProcessing.get()) {
            issues.add("Service is not processing requests")
        }
        if (errorRate > 25.0) {
            issues.add("High error rate: ${String.format("%.2f", errorRate)}%")
        }
        if (queueSize > 100) {
            issues.add("Queue size is high: $queueSize")
        }
        if (avgProcessingTime > 5000) {
            issues.add("Average processing time is high: ${avgProcessingTime.toLong()}ms")
        }

        return mapOf(
            "status" to status,
            "issues" to issues,
            "metrics" to stats
        )
    }
}