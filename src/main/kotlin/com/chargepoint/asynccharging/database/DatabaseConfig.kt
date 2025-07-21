
package com.chargepoint.asynccharging.database

import com.chargepoint.asynccharging.config.AppConfig
import com.chargepoint.asynccharging.controllers.ChargingController
import com.chargepoint.asynccharging.queue.AuthorizationQueue
import com.chargepoint.asynccharging.queue.RedisAuthorizationQueue
import com.chargepoint.asynccharging.services.AuthorizationService
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("DatabaseConfig")

/**
 * Data class to hold all database-related dependencies
 */
data class DatabaseDependencies(
    val repository: AuthorizationRepositoryInterface,
    val queue: AuthorizationQueue,
    val authorizationService: AuthorizationService,
    val chargingController: ChargingController
)

/**
 * Factory object for creating and managing database-related dependencies
 */
object DatabaseConfig {

    /**
     * Setup all dependencies based on configuration
     */
    suspend fun setupDependencies(config: AppConfig): DatabaseDependencies {
        logger.info("Setting up database dependencies...")

        // Create repository
        val repository = createAuthorizationRepository(config)

        // Create queue
        val queue = createAuthorizationQueue(config)

        // Create authorization service
        val authorizationService = createAuthorizationService(repository, queue)

        // Create controller
        val chargingController = createChargingController(authorizationService, repository)

        logger.info("All dependencies created successfully")

        return DatabaseDependencies(
            repository = repository,
            queue = queue,
            authorizationService = authorizationService,
            chargingController = chargingController
        )
    }

    /**
     * Create and initialize the authorization repository based on configuration
     */
    private suspend fun createAuthorizationRepository(config: AppConfig): AuthorizationRepositoryInterface {
        logger.info("Creating authorization repository...")

        val repository: AuthorizationRepositoryInterface = if (config.database.url.contains("postgresql")) {
            // Use PostgreSQL implementation
            logger.info("Using PostgreSQL repository")
            val postgresRepo = PostgresAuthorizationRepository(config.database)
            postgresRepo.initialize()
            postgresRepo
        } else {
            // Use in-memory implementation
            logger.info("Using in-memory repository")
            AuthorizationRepository()
        }

        logger.info("Authorization repository created successfully: ${repository::class.simpleName}")
        return repository
    }

    /**
     * Create and initialize the authorization queue based on configuration
     */
    private suspend fun createAuthorizationQueue(config: AppConfig): AuthorizationQueue {
        logger.info("Creating authorization queue...")

        // Always use Redis implementation
        logger.info("Using Redis queue")
        val redisQueue = RedisAuthorizationQueue(config.redis)
        redisQueue.initialize()

        logger.info("Authorization queue created successfully: ${redisQueue::class.simpleName}")
        return redisQueue
    }

    /**
     * Create the authorization service with all dependencies
     */
    private fun createAuthorizationService(
        repository: AuthorizationRepositoryInterface,
        queue: AuthorizationQueue
    ): AuthorizationService {
        logger.info("Creating authorization service...")

        val service = AuthorizationService(
            repository = repository,
            queue = queue
        )

        logger.info("Authorization service created successfully")
        return service
    }

    /**
     * Create the charging controller with all dependencies
     */
    private fun createChargingController(
        authorizationService: AuthorizationService,
        authorizationRepository: AuthorizationRepositoryInterface
    ): ChargingController {
        logger.info("Creating charging controller...")

        val controller = ChargingController(
            authorizationService = authorizationService,
            authorizationRepository = authorizationRepository
        )

        logger.info("Charging controller created successfully")
        return controller
    }

    /**
     * Gracefully shutdown all dependencies
     */
    suspend fun shutdown(dependencies: DatabaseDependencies) {
        logger.info("Shutting down database dependencies...")

        try {
            // Stop the authorization service
            dependencies.authorizationService.stopProcessing()
            logger.info("Authorization service stopped")

            // Shutdown the queue
            dependencies.queue.shutdown()
            logger.info("Authorization queue shutdown")

            // Close repository if it's PostgreSQL
            if (dependencies.repository is PostgresAuthorizationRepository) {
                dependencies.repository.close()
                logger.info("PostgreSQL repository closed")
            }

            // Close Redis queue connection
            if (dependencies.queue is RedisAuthorizationQueue) {
                dependencies.queue.close()
                logger.info("Redis queue connection closed")
            }

            logger.info("All dependencies shutdown successfully")
        } catch (e: Exception) {
            logger.error("Error during dependency shutdown", e)
            throw e
        }
    }

    /**
     * Perform health check on all dependencies
     */
    suspend fun healthCheck(dependencies: DatabaseDependencies): Map<String, Any> {
        logger.debug("Performing health check on all dependencies...")

        return try {
            val repoHealth = dependencies.repository.healthCheck()
            val queueHealth = dependencies.queue.healthCheck()
            val serviceStats = dependencies.authorizationService.getStatistics()

            val overallStatus = when {
                repoHealth["status"] == "healthy" &&
                        queueHealth["status"] == "healthy" -> "healthy"
                repoHealth["status"] == "unhealthy" ||
                        queueHealth["status"] == "unhealthy" -> "unhealthy"
                else -> "degraded"
            }

            mapOf(
                "status" to overallStatus,
                "repository" to repoHealth,
                "queue" to queueHealth,
                "service" to serviceStats,
                "timestamp" to System.currentTimeMillis()
            )
        } catch (e: Exception) {
            logger.error("Health check failed", e)
            mapOf(
                "status" to "unhealthy",
                "error" to (e.message ?: "Unknown error"),
                "timestamp" to System.currentTimeMillis()
            )
        }
    }

    /**
     * Get statistics from all dependencies
     */
    suspend fun getStatistics(dependencies: DatabaseDependencies): Map<String, Any> {
        return try {
            val repoStats = dependencies.repository.getStatistics()
            val queueStats = dependencies.queue.getStatistics()
            val serviceStats = dependencies.authorizationService.getStatistics()

            mapOf(
                "repository" to repoStats,
                "queue" to queueStats,
                "service" to serviceStats,
                "timestamp" to System.currentTimeMillis()
            )
        } catch (e: Exception) {
            logger.error("Error getting statistics", e)
            mapOf(
                "error" to (e.message ?: "Unknown error"),
                "timestamp" to System.currentTimeMillis()
            )
        }
    }
}