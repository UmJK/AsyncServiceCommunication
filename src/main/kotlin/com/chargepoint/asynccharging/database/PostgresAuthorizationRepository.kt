
package com.chargepoint.asynccharging.database

import com.chargepoint.asynccharging.config.DatabaseConfig as DbConfig
import com.chargepoint.asynccharging.models.AuthorizationRecord
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.*
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.util.concurrent.atomic.AtomicLong

class PostgresAuthorizationRepository(
    private val databaseConfig: DbConfig
) : AuthorizationRepositoryInterface {

    private val logger = LoggerFactory.getLogger(PostgresAuthorizationRepository::class.java)
    private lateinit var dataSource: HikariDataSource
    private val startTime = Clock.System.now()
    private val operationCount = AtomicLong(0)

    suspend fun initialize() = withContext(Dispatchers.IO) {
        logger.info("Initializing PostgreSQL connection pool...")

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = databaseConfig.url
            driverClassName = databaseConfig.driver
            username = databaseConfig.username
            password = databaseConfig.password
            maximumPoolSize = databaseConfig.maxPoolSize
            connectionTimeout = databaseConfig.connectionTimeout

            // Additional connection pool settings
            isAutoCommit = true // Changed to true for simpler transaction handling
            transactionIsolation = "TRANSACTION_READ_COMMITTED"

            // Connection validation
            connectionTestQuery = "SELECT 1"
            validationTimeout = 3000

            // Pool settings
            minimumIdle = 5
            maxLifetime = 600000 // 10 minutes
            idleTimeout = 300000  // 5 minutes

            // Additional properties for stability
            leakDetectionThreshold = 60000
        }

        dataSource = HikariDataSource(hikariConfig)

        // Create tables if they don't exist
        createTables()

        logger.info("PostgreSQL repository initialized successfully")
    }

    private suspend fun createTables() = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                val createTableSql = """
                    CREATE TABLE IF NOT EXISTS authorization_records (
                        authorization_id VARCHAR(255) PRIMARY KEY,
                        user_id VARCHAR(255) NOT NULL,
                        station_id VARCHAR(255) NOT NULL,
                        connector_id INT NOT NULL,
                        requested_energy DECIMAL(10,2),
                        approved_energy DECIMAL(10,2),
                        max_duration_minutes INT NOT NULL,
                        callback_url VARCHAR(500),
                        decision VARCHAR(50) NOT NULL DEFAULT 'PENDING',
                        reason TEXT,
                        timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        processed_at TIMESTAMP,
                        expires_at TIMESTAMP,
                        metadata TEXT,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    );
                    
                    CREATE INDEX IF NOT EXISTS idx_authorization_user_id ON authorization_records(user_id);
                    CREATE INDEX IF NOT EXISTS idx_authorization_station_id ON authorization_records(station_id);
                    CREATE INDEX IF NOT EXISTS idx_authorization_decision ON authorization_records(decision);
                    CREATE INDEX IF NOT EXISTS idx_authorization_timestamp ON authorization_records(timestamp);
                """.trimIndent()

                statement.execute(createTableSql)
                logger.info("Database tables created/verified successfully")
            }
        }
    }

    override suspend fun save(record: AuthorizationRecord): AuthorizationRecord = withContext(Dispatchers.IO) {
        operationCount.incrementAndGet()

        try {
            val sql = """
                INSERT INTO authorization_records 
                (authorization_id, user_id, station_id, connector_id, requested_energy, 
                 approved_energy, max_duration_minutes, callback_url, decision, reason, 
                 timestamp, processed_at, expires_at, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (authorization_id) DO UPDATE SET
                    decision = EXCLUDED.decision,
                    reason = EXCLUDED.reason,
                    processed_at = EXCLUDED.processed_at,
                    expires_at = EXCLUDED.expires_at,
                    approved_energy = EXCLUDED.approved_energy,
                    updated_at = CURRENT_TIMESTAMP
                RETURNING *
            """.trimIndent()

            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, record.authorizationId)
                    statement.setString(2, record.userId)
                    statement.setString(3, record.stationId)
                    statement.setInt(4, record.connectorId)
                    statement.setBigDecimal(5, record.requestedEnergy)
                    statement.setBigDecimal(6, record.approvedEnergy)
                    statement.setInt(7, record.maxDurationMinutes)
                    statement.setString(8, record.callbackUrl)
                    statement.setString(9, record.decision)
                    statement.setString(10, record.reason)
                    statement.setTimestamp(11, Timestamp.from(record.timestamp.toJavaInstant()))
                    statement.setTimestamp(12, record.processedAt?.let { Timestamp.from(it.toJavaInstant()) })
                    statement.setTimestamp(13, record.expiresAt?.let { Timestamp.from(it.toJavaInstant()) })
                    statement.setString(14, record.metadata.toString()) // Convert Map to JSON string

                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            mapResultSetToRecord(resultSet)
                        } else {
                            record
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error saving authorization record: ${record.authorizationId}", e)
            throw e
        }
    }

    override suspend fun findById(authorizationId: String): AuthorizationRecord? = withContext(Dispatchers.IO) {
        operationCount.incrementAndGet()

        try {
            val sql = "SELECT * FROM authorization_records WHERE authorization_id = ?"

            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, authorizationId)

                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            mapResultSetToRecord(resultSet)
                        } else {
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error retrieving authorization record: $authorizationId", e)
            null
        }
    }

    override suspend fun findByUserId(userId: String, limit: Int): List<AuthorizationRecord> = withContext(Dispatchers.IO) {
        operationCount.incrementAndGet()

        try {
            val sql = "SELECT * FROM authorization_records WHERE user_id = ? ORDER BY timestamp DESC"

            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, userId)
                    statement.setInt(2, limit)

                    statement.executeQuery().use { resultSet ->
                        val records = mutableListOf<AuthorizationRecord>()
                        while (resultSet.next()) {
                            records.add(mapResultSetToRecord(resultSet))
                        }
                        records
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error retrieving records by user ID: $userId", e)
            emptyList()
        }
    }

    override suspend fun findByStationId(stationId: String, limit: Int): List<AuthorizationRecord> = withContext(Dispatchers.IO) {
        operationCount.incrementAndGet()

        try {
            val sql = "SELECT * FROM authorization_records WHERE station_id = ? ORDER BY timestamp DESC"

            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, stationId)
                    statement.setInt(2, limit)

                    statement.executeQuery().use { resultSet ->
                        val records = mutableListOf<AuthorizationRecord>()
                        while (resultSet.next()) {
                            records.add(mapResultSetToRecord(resultSet))
                        }
                        records
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error retrieving records by station ID: $stationId", e)
            emptyList()
        }
    }

    override suspend fun findByUserAndStation(userId: String, stationId: String): List<AuthorizationRecord> = withContext(Dispatchers.IO) {
        operationCount.incrementAndGet()

        try {
            val sql = "SELECT * FROM authorization_records WHERE user_id = ? AND station_id = ? ORDER BY timestamp DESC"

            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, userId)
                    statement.setString(2, stationId)

                    statement.executeQuery().use { resultSet ->
                        val records = mutableListOf<AuthorizationRecord>()
                        while (resultSet.next()) {
                            records.add(mapResultSetToRecord(resultSet))
                        }
                        records
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error retrieving records by user and station: $userId, $stationId", e)
            emptyList()
        }
    }

    override suspend fun findActiveRecords(): List<AuthorizationRecord> = withContext(Dispatchers.IO) {
        operationCount.incrementAndGet()

        try {
            val sql = """
                SELECT * FROM authorization_records 
                WHERE decision = 'APPROVED' 
                AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                ORDER BY timestamp DESC
            """.trimIndent()

            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.executeQuery().use { resultSet ->
                        val records = mutableListOf<AuthorizationRecord>()
                        while (resultSet.next()) {
                            records.add(mapResultSetToRecord(resultSet))
                        }
                        records
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error retrieving active records", e)
            emptyList()
        }
    }

    override suspend fun updateStatus(
        authorizationId: String,
        decision: String,
        reason: String?,
        processedAt: Instant?
    ): AuthorizationRecord? = withContext(Dispatchers.IO) {
        operationCount.incrementAndGet()

        try {
            val sql = """
                UPDATE authorization_records 
                SET decision = ?, reason = ?, processed_at = ?, updated_at = CURRENT_TIMESTAMP
                WHERE authorization_id = ?
                RETURNING *
            """.trimIndent()

            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, decision)
                    statement.setString(2, reason)
                    if (processedAt != null) {
                        statement.setTimestamp(3, Timestamp.from(processedAt.toJavaInstant()))
                    } else {
                        statement.setNull(3, Types.TIMESTAMP)
                    }
                    statement.setString(4, authorizationId)

                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            mapResultSetToRecord(resultSet)
                        } else {
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error updating authorization status: $authorizationId", e)
            null
        }
    }

    override suspend fun getStatistics(): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            val sql = """
                SELECT 
                    COUNT(*) as total_records,
                    SUM(CASE WHEN decision = 'APPROVED' THEN 1 ELSE 0 END) as approved_count,
                    SUM(CASE WHEN decision = 'REJECTED' THEN 1 ELSE 0 END) as rejected_count,
                    SUM(CASE WHEN decision = 'PENDING' THEN 1 ELSE 0 END) as pending_count
                FROM authorization_records
            """.trimIndent()

            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            mapOf(
                                "totalRecords" to resultSet.getLong("total_records"),
                                "approvedCount" to resultSet.getLong("approved_count"),
                                "rejectedCount" to resultSet.getLong("rejected_count"),
                                "pendingCount" to resultSet.getLong("pending_count"),
                                "operationCount" to operationCount.get(),
                                "uptime" to getUptime(),
                                "timestamp" to Clock.System.now().toString()
                            )
                        } else {
                            emptyMap()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting statistics", e)
            emptyMap()
        }
    }

    override suspend fun healthCheck(): Map<String, Any> {
        val startTime = Clock.System.now()

        return try {
            val stats = getStatistics()
            val endTime = Clock.System.now()
            val healthCheckDuration = (endTime - startTime).inWholeMilliseconds

            // Test connection
            dataSource.connection.use { connection ->
                connection.prepareStatement("SELECT 1").use { statement ->
                    statement.executeQuery().use { resultSet ->
                        resultSet.next()
                    }
                }
            }

            mapOf(
                "status" to "healthy",
                "type" to "postgresql",
                "version" to "1.0.0",
                "timestamp" to endTime.toString(),
                "healthCheckDurationMs" to healthCheckDuration,
                "uptime" to getUptime(),
                "connectionPoolActive" to dataSource.hikariPoolMXBean.activeConnections,
                "connectionPoolIdle" to dataSource.hikariPoolMXBean.idleConnections,
                "connectionPoolTotal" to dataSource.hikariPoolMXBean.totalConnections,
                "metrics" to stats
            )
        } catch (e: Exception) {
            logger.error("Health check failed", e)
            mapOf(
                "status" to "unhealthy",
                "error" to (e.message ?: "Unknown error"),
                "timestamp" to Clock.System.now().toString()
            )
        }
    }

    private fun mapResultSetToRecord(resultSet: ResultSet): AuthorizationRecord {
        return AuthorizationRecord(
            authorizationId = resultSet.getString("authorization_id"),
            userId = resultSet.getString("user_id"),
            stationId = resultSet.getString("station_id"),
            connectorId = resultSet.getInt("connector_id"),
            requestedEnergy = resultSet.getBigDecimal("requested_energy"),
            approvedEnergy = resultSet.getBigDecimal("approved_energy"),
            maxDurationMinutes = resultSet.getInt("max_duration_minutes"),
            callbackUrl = resultSet.getString("callback_url"),
            decision = resultSet.getString("decision"),
            reason = resultSet.getString("reason"),
            timestamp = resultSet.getTimestamp("timestamp").toInstant().toKotlinInstant(),
            processedAt = resultSet.getTimestamp("processed_at")?.toInstant()?.toKotlinInstant(),
            expiresAt = resultSet.getTimestamp("expires_at")?.toInstant()?.toKotlinInstant(),
            metadata = parseMetadata(resultSet.getString("metadata"))
        )
    }

    private fun parseMetadata(metadataJson: String?): Map<String, String> {
        return try {
            if (metadataJson.isNullOrBlank()) {
                emptyMap()
            } else {
                // Simple parsing for now - in production you might want to use a JSON library
                emptyMap()
            }
        } catch (e: Exception) {
            logger.warn("Error parsing metadata: $metadataJson", e)
            emptyMap()
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

    fun close() {
        if (::dataSource.isInitialized && !dataSource.isClosed) {
            dataSource.close()
            logger.info("Database connection pool closed")
        }
    }
}