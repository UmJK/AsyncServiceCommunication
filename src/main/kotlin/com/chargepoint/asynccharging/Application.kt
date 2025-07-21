package com.chargepoint.asynccharging

import com.chargepoint.asynccharging.config.ConfigManager
import com.chargepoint.asynccharging.config.appConfig
import com.chargepoint.asynccharging.controllers.ChargingController
import com.chargepoint.asynccharging.database.AuthorizationRepositoryInterface
import com.chargepoint.asynccharging.database.DatabaseConfig
import com.chargepoint.asynccharging.database.DatabaseDependencies
import com.chargepoint.asynccharging.plugins.*
import com.chargepoint.asynccharging.services.AuthorizationService
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Application")

// Global service instances
lateinit var authorizationRepository: AuthorizationRepositoryInterface
lateinit var authorizationService: AuthorizationService
lateinit var chargingController: ChargingController
lateinit var databaseDependencies: DatabaseDependencies

fun main() {
    logger.info("Starting Async Charging Service...")
    logger.info("Kotlin version: ${KotlinVersion.CURRENT}")
    logger.info("Java version: ${System.getProperty("java.version")}")

    embeddedServer(
        factory = Netty,
        port = 8080, // This will be overridden by config
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    logger.info("Initializing application module...")

    try {
        // Initialize configuration first
        ConfigManager.initialize(environment)
        val config = appConfig

        logger.info("Configuration loaded successfully:")
        logger.info("- Server: ${config.server.host}:${config.server.port}")
        logger.info("- Database: ${config.database.url}")
        logger.info("- Redis: ${config.redis.host}:${config.redis.port}")
        logger.info("- Queue threads: ${config.queue.processingThreads}")

        // Initialize dependencies using DatabaseConfig
        runBlocking {
            initializeDependencies(config)
        }

        // Configure Ktor plugins in the correct order
        configureHTTP()
        configureSerialization()
        configureRouting()

        // Initialize and start background services
        initializeServices(config)

        // Setup graceful shutdown
        setupShutdownHook()

        logger.info("Application module initialized successfully")

    } catch (e: Exception) {
        logger.error("Failed to initialize application", e)
        throw e
    }
}

/**
 * Initialize all application dependencies using DatabaseConfig factory
 */
private suspend fun initializeDependencies(config: com.chargepoint.asynccharging.config.AppConfig) {
    logger.info("Initializing application dependencies...")

    try {
        // Use DatabaseConfig to setup all dependencies
        databaseDependencies = DatabaseConfig.setupDependencies(config)

        // Assign to global variables for backward compatibility
        authorizationRepository = databaseDependencies.repository
        authorizationService = databaseDependencies.authorizationService
        chargingController = databaseDependencies.chargingController

        logger.info("Dependencies initialized successfully:")
        logger.info("- Repository: ${authorizationRepository::class.simpleName}")
        logger.info("- Queue: ${databaseDependencies.queue::class.simpleName}")
        logger.info("- Authorization Service: ${authorizationService::class.simpleName}")
        logger.info("- Controller: ${chargingController::class.simpleName}")

    } catch (e: Exception) {
        logger.error("Failed to initialize dependencies", e)
        throw RuntimeException("Dependency initialization failed", e)
    }
}

/**
 * Initialize and start all background services
 */
private fun Application.initializeServices(config: com.chargepoint.asynccharging.config.AppConfig) {
    logger.info("Starting background services...")

    runBlocking {
        try {
            // Start authorization processing service
            authorizationService.startProcessing()
            logger.info("Authorization processing service started with ${config.queue.processingThreads} threads")

            // Perform initial health check
            val healthStatus = performHealthCheck()
            logger.info("Initial health check: $healthStatus")

        } catch (e: Exception) {
            logger.error("Error starting background services", e)
            throw RuntimeException("Service startup failed", e)
        }
    }

    // Setup application lifecycle monitoring
    setupApplicationMonitoring(config)
}

/**
 * Setup application lifecycle monitoring and logging
 */
private fun Application.setupApplicationMonitoring(config: com.chargepoint.asynccharging.config.AppConfig) {
    environment.monitor.subscribe(ApplicationStarting) {
        logger.info("Application is starting...")
    }

    environment.monitor.subscribe(ApplicationStarted) {
        logger.info("🚀 Application started successfully!")
        logger.info("📍 Server running at: http://${config.server.host}:${config.server.port}")
        logger.info("🏥 Health check: http://${config.server.host}:${config.server.port}/health")
        logger.info("⚡ Charging API: http://${config.server.host}:${config.server.port}/api/v1/charging/start")
        logger.info("📊 Queue statistics: http://${config.server.host}:${config.server.port}/api/v1/queue/statistics")

        // Log queue configuration
        logger.info("📋 Queue Configuration:")
        logger.info("   - Processing threads: ${config.queue.processingThreads}")
        logger.info("   - Batch size: ${config.queue.batchSize}")
        logger.info("   - Poll interval: ${config.queue.pollInterval}ms")
        logger.info("   - Max retries: ${config.queue.maxRetries}")
        logger.info("   - Retry delay: ${config.queue.retryDelay}ms")
    }

    environment.monitor.subscribe(ApplicationStopping) {
        logger.info("🛑 Application is stopping...")
    }

    environment.monitor.subscribe(ApplicationStopped) {
        logger.info("✅ Application stopped gracefully")
    }
}

/**
 * Perform initial health check on all components
 */
private suspend fun performHealthCheck(): Map<String, Any> {
    return try {
        val repoHealth = authorizationRepository.healthCheck()
        val queueHealth = databaseDependencies.queue.healthCheck()
        val serviceStats = authorizationService.getStatistics()

        mapOf(
            "overall" to "healthy",
            "repository" to repoHealth,
            "queue" to queueHealth,
            "service" to serviceStats,
            "timestamp" to System.currentTimeMillis()
        )
    } catch (e: Exception) {
        logger.warn("Health check failed", e)
        mapOf(
            "overall" to "unhealthy",
            "error" to (e.message ?: "Unknown error"),
            "timestamp" to System.currentTimeMillis()
        )
    }
}

/**
 * Setup graceful shutdown with proper cleanup
 */
private fun Application.setupShutdownHook() {
    // Ktor application lifecycle hooks
    environment.monitor.subscribe(ApplicationStopping) {
        logger.info("Initiating graceful shutdown...")

        runBlocking {
            try {
                // Stop processing new requests
                authorizationService.stopProcessing()
                logger.info("Authorization service stopped")

                // Allow current requests to complete (with timeout)
                kotlinx.coroutines.delay(2000) // 2 second grace period

                logger.info("Graceful shutdown initiated")
            } catch (e: Exception) {
                logger.error("Error during graceful shutdown", e)
            }
        }
    }

    // JVM shutdown hook for emergency cleanup
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("🔧 JVM shutdown hook triggered - cleaning up resources...")

        try {
            // Cleanup database dependencies
            runBlocking {
                if (::databaseDependencies.isInitialized) {
                    DatabaseConfig.shutdown(databaseDependencies)
                    logger.info("Database dependencies cleaned up")
                }
            }

            logger.info("✅ Resource cleanup completed")
        } catch (e: Exception) {
            logger.error("❌ Error during shutdown cleanup", e)
        }
    })
}

/**
 * Extension function to get application dependencies
 * Useful for testing and other components that need access to services
 */
val Application.dependencies: DatabaseDependencies
    get() = if (::databaseDependencies.isInitialized) {
        databaseDependencies
    } else {
        throw IllegalStateException("Application dependencies not initialized")
    }

/**
 * Extension function to check if application is ready
 */
val Application.isReady: Boolean
    get() = try {
        ::authorizationRepository.isInitialized &&
                ::authorizationService.isInitialized &&
                ::chargingController.isInitialized &&
                ::databaseDependencies.isInitialized
    } catch (e: Exception) {
        false
    }