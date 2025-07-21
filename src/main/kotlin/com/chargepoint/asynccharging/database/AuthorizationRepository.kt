
package com.chargepoint.asynccharging.database

import com.chargepoint.asynccharging.models.AuthorizationRecord
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Repository interface for authorization operations
 */
interface AuthorizationRepositoryInterface {
    /**
     * Save a new authorization record
     */
    suspend fun save(record: AuthorizationRecord): AuthorizationRecord

    /**
     * Find authorization record by ID
     */
    suspend fun findById(authorizationId: String): AuthorizationRecord?

    /**
     * Find authorizations by user ID
     */
    suspend fun findByUserId(userId: String, limit: Int = 100): List<AuthorizationRecord>

    /**
     * Find authorizations by station ID
     */
    suspend fun findByStationId(stationId: String, limit: Int = 100): List<AuthorizationRecord>

    /**
     * Find authorizations by user and station
     */
    suspend fun findByUserAndStation(userId: String, stationId: String): List<AuthorizationRecord>

    /**
     * Find active authorization records
     */
    suspend fun findActiveRecords(): List<AuthorizationRecord>

    /**
     * Update status of an authorization record
     */
    suspend fun updateStatus(
        authorizationId: String,
        decision: String,
        reason: String? = null,
        processedAt: Instant? = null
    ): AuthorizationRecord?

    /**
     * Get repository statistics
     */
    suspend fun getStatistics(): Map<String, Any>

    /**
     * Health check for repository
     */
    suspend fun healthCheck(): Map<String, Any>
}

/**
 * In-memory implementation of AuthorizationRepository
 * For production, this should be replaced with a database implementation
 */
class AuthorizationRepository : AuthorizationRepositoryInterface {

    private val logger = LoggerFactory.getLogger(AuthorizationRepository::class.java)
    private val records = ConcurrentHashMap<String, AuthorizationRecord>()
    private val startTime = Clock.System.now()
    private val operationCount = AtomicLong(0)

    override suspend fun save(record: AuthorizationRecord): AuthorizationRecord {
        operationCount.incrementAndGet()
        records[record.authorizationId] = record
        logger.debug("Saved authorization record: ${record.authorizationId}")
        return record
    }

    override suspend fun findById(authorizationId: String): AuthorizationRecord? {
        operationCount.incrementAndGet()
        val record = records[authorizationId]
        logger.debug("Found authorization record: $authorizationId -> ${record != null}")
        return record
    }

    override suspend fun findByUserId(userId: String, limit: Int): List<AuthorizationRecord> {
        operationCount.incrementAndGet()
        val userRecords = records.values.filter { it.userId == userId }
            .sortedByDescending { it.timestamp }
            .take(limit)
        logger.debug("Found ${userRecords.size} records for user: $userId")
        return userRecords
    }

            override suspend fun findByStationId(stationId: String, limit: Int): List<AuthorizationRecord> {
        operationCount.incrementAndGet()
        val stationRecords = records.values.filter { it.stationId == stationId }
            .sortedByDescending { it.timestamp }
            .take(limit)
        logger.debug("Found ${stationRecords.size} records for station: $stationId")
        return stationRecords
    }

    override suspend fun findByUserAndStation(userId: String, stationId: String): List<AuthorizationRecord> {
        operationCount.incrementAndGet()
        val userStationRecords = records.values.filter {
            it.userId == userId && it.stationId == stationId
        }.sortedByDescending { it.timestamp }
        logger.debug("Found ${userStationRecords.size} records for user: $userId at station: $stationId")
        return userStationRecords
    }

    override suspend fun findActiveRecords(): List<AuthorizationRecord> {
        operationCount.incrementAndGet()
        val now = Clock.System.now()
        val activeRecords = records.values.filter { record ->
            record.decision == "APPROVED" &&
                    (record.expiresAt?.let { it > now } ?: true)
        }
        logger.debug("Found ${activeRecords.size} active records")
        return activeRecords
    }

    override suspend fun updateStatus(
        authorizationId: String,
        decision: String,
        reason: String?,
        processedAt: Instant?
    ): AuthorizationRecord? {
        operationCount.incrementAndGet()
        val existingRecord = records[authorizationId]

        return if (existingRecord != null) {
            val updatedRecord = existingRecord.copy(
                decision = decision,
                reason = reason,
                processedAt = processedAt
            )
            records[authorizationId] = updatedRecord
            logger.debug("Updated authorization status: $authorizationId -> $decision")
            updatedRecord
        } else {
            logger.warn("Cannot update non-existent authorization: $authorizationId")
            null
        }
    }

    override suspend fun getStatistics(): Map<String, Any> {
        val now = Clock.System.now()
        val totalRecords = records.size.toLong()
        val approvedCount = records.values.count { it.decision == "APPROVED" }.toLong()
        val rejectedCount = records.values.count { it.decision == "REJECTED" }.toLong()
        val pendingCount = records.values.count { it.decision == "PENDING" }.toLong()

        return mapOf(
            "totalRecords" to totalRecords,
            "approvedCount" to approvedCount,
            "rejectedCount" to rejectedCount,
            "pendingCount" to pendingCount,
            "operationCount" to operationCount.get(),
            "uptime" to getUptime(),
            "timestamp" to now.toString()
        )
    }

    override suspend fun healthCheck(): Map<String, Any> {
        val startTime = Clock.System.now()

        try {
            // Perform basic operations to test functionality
            val stats = getStatistics()
            val sampleRecordExists = records.isNotEmpty()

            val endTime = Clock.System.now()
            val healthCheckDuration = (endTime - startTime).inWholeMilliseconds

            val issues = mutableListOf<String>()

            // Check for potential issues
            if (records.size > 10000) {
                issues.add("Large number of records in memory (${records.size}), consider database persistence")
            }

            if (operationCount.get() > 100000) {
                issues.add("High operation count (${operationCount.get()}), monitor performance")
            }

            val status = when {
                issues.isNotEmpty() -> "warning"
                else -> "healthy"
            }

            val metrics = mapOf(
                "recordCount" to records.size,
                "operationCount" to operationCount.get(),
                "memoryUsageMB" to getMemoryUsage(),
                "hasRecords" to sampleRecordExists
            )

            return mapOf(
                "status" to status,
                "type" to "in-memory",
                "version" to "1.0.0",
                "timestamp" to endTime.toString(),
                "healthCheckDurationMs" to healthCheckDuration,
                "uptime" to getUptime(),
                "metrics" to metrics,
                "issues" to (issues.takeIf { it.isNotEmpty() } ?: emptyList<String>()),
                "recommendations" to (getRecommendations(stats, issues) ?: emptyList<String>())
            )

        } catch (e: Exception) {
            logger.error("Health check failed", e)
            return mapOf(
                "status" to "unhealthy",
                "error" to (e.message ?: "Unknown error"),  // Handle null message
                "timestamp" to Clock.System.now().toString()
            )
        }
    }

    private fun getUptime(): String {
        val now = Clock.System.now()
        val uptime = (now - startTime).inWholeSeconds
        val hours = uptime / 3600
        val minutes = (uptime % 3600) / 60
        val seconds = uptime % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun getMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        return usedMemory / (1024 * 1024) // Convert to MB
    }

    private fun getRecommendations(stats: Map<String, Any>, issues: List<String>): List<String>? {
        val recommendations = mutableListOf<String>()

        val totalRecords = stats["totalRecords"] as Long
        val operationCount = stats["operationCount"] as Long

        if (totalRecords > 5000) {
            recommendations.add("Consider implementing data archiving or using a persistent database")
        }

        if (operationCount > 50000) {
            recommendations.add("Monitor memory usage and consider implementing connection pooling")
        }

        if (issues.isNotEmpty()) {
            recommendations.add("Review system resources and consider scaling options")
        }

        return recommendations.takeIf { it.isNotEmpty() }
    }
}