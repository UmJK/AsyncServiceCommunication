
package com.chargepoint.asynccharging.config

import io.ktor.server.application.*
import io.ktor.server.config.*
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger("AppConfig")

/**
 * Application configuration data class
 */
data class AppConfig(
    val server: ServerConfig,
    val database: DatabaseConfig,
    val redis: RedisConfig,
    val http: HttpConfig,
    val authorization: AuthorizationConfig,
    val queue: QueueConfig
) {
    companion object {
        /**
         * Load configuration from application.conf
         */
        fun load(environment: ApplicationEnvironment): AppConfig {
            logger.info("Loading application configuration...")

            return AppConfig(
                server = ServerConfig.from(environment.config),
                database = DatabaseConfig.from(environment.config),
                redis = RedisConfig.from(environment.config),
                http = HttpConfig.from(environment.config),
                authorization = AuthorizationConfig.from(environment.config),
                queue = QueueConfig.from(environment.config)
            ).also {
                logger.info("Configuration loaded successfully")
                logger.debug("Server: ${it.server}")
                logger.debug("Queue processing threads: ${it.queue.processingThreads}")
                logger.debug("Redis host: ${it.redis.host}")
            }
        }
    }
}

/**
 * Server configuration
 */
data class ServerConfig(
    val host: String,
    val port: Int
) {
    companion object {
        fun from(config: ApplicationConfig): ServerConfig {
            return ServerConfig(
                host = config.propertyOrNull("server.host")?.getString() ?: "0.0.0.0",
                port = config.propertyOrNull("server.port")?.getString()?.toInt() ?: 8080
            )
        }
    }
}

/**
 * Database configuration
 */
data class DatabaseConfig(
    val url: String,
    val driver: String,
    val username: String,
    val password: String,
    val maxPoolSize: Int,
    val connectionTimeout: Long
) {
    companion object {
        fun from(config: ApplicationConfig): DatabaseConfig {
            return DatabaseConfig(
                url = config.propertyOrNull("database.url")?.getString()
                    ?: "jdbc:postgresql://localhost:5432/async_charging",
                driver = config.propertyOrNull("database.driver")?.getString()
                    ?: "org.postgresql.Driver",
                username = config.propertyOrNull("database.username")?.getString()
                    ?: "postgres",
                password = config.propertyOrNull("database.password")?.getString()
                    ?: "password",
                maxPoolSize = config.propertyOrNull("database.maxPoolSize")?.getString()?.toInt()
                    ?: 20,
                connectionTimeout = config.propertyOrNull("database.connectionTimeout")?.getString()?.toLong()
                    ?: 30000L
            )
        }
    }
}

/**
 * Redis configuration
 */
data class RedisConfig(
    val host: String,
    val port: Int,
    val password: String?,
    val database: Int,
    val timeout: Long
) {
    companion object {
        fun from(config: ApplicationConfig): RedisConfig {
            return RedisConfig(
                host = config.propertyOrNull("redis.host")?.getString() ?: "localhost",
                port = config.propertyOrNull("redis.port")?.getString()?.toInt() ?: 6379,
                password = config.propertyOrNull("redis.password")?.getString(),
                database = config.propertyOrNull("redis.database")?.getString()?.toInt() ?: 0,
                timeout = config.propertyOrNull("redis.timeout")?.getString()?.toLong() ?: 5000L
            )
        }
    }
}

/**
 * HTTP client configuration
 */
data class HttpConfig(
    val connectTimeout: Long,
    val readTimeout: Long,
    val writeTimeout: Long,
    val maxRetries: Int
) {
    companion object {
        fun from(config: ApplicationConfig): HttpConfig {
            return HttpConfig(
                connectTimeout = config.propertyOrNull("http.connectTimeout")?.getString()?.toLong() ?: 10000L,
                readTimeout = config.propertyOrNull("http.readTimeout")?.getString()?.toLong() ?: 30000L,
                writeTimeout = config.propertyOrNull("http.writeTimeout")?.getString()?.toLong() ?: 30000L,
                maxRetries = config.propertyOrNull("http.maxRetries")?.getString()?.toInt() ?: 3
            )
        }
    }
}

/**
 * Authorization configuration
 */
data class AuthorizationConfig(
    val groupKey: String,
    val defaultPermissions: List<String>,
    val adminGroups: List<String>
) {
    companion object {
        fun from(config: ApplicationConfig): AuthorizationConfig {
            return AuthorizationConfig(
                groupKey = config.propertyOrNull("authorization.groupKey")?.getString() ?: "charging_group",
                defaultPermissions = config.propertyOrNull("authorization.defaultPermissions")?.getList()
                    ?: listOf("read", "charge"),
                adminGroups = config.propertyOrNull("authorization.adminGroups")?.getList()
                    ?: listOf("admin", "operator")
            )
        }
    }
}

/**
 * Queue processing configuration
 */
data class QueueConfig(
    val processingThreads: Int,
    val batchSize: Int,
    val pollInterval: Long,
    val maxRetries: Int,
    val retryDelay: Long
) {
    companion object {
        fun from(config: ApplicationConfig): QueueConfig {
            return QueueConfig(
                processingThreads = config.propertyOrNull("queue.processingThreads")?.getString()?.toInt() ?: 4,
                batchSize = config.propertyOrNull("queue.batchSize")?.getString()?.toInt() ?: 10,
                pollInterval = config.propertyOrNull("queue.pollInterval")?.getString()?.toLong() ?: 1000L,
                maxRetries = config.propertyOrNull("queue.maxRetries")?.getString()?.toInt() ?: 3,
                retryDelay = config.propertyOrNull("queue.retryDelay")?.getString()?.toLong() ?: 5000L
            )
        }
    }
}

/**
 * Configuration singleton for global access
 */
object ConfigManager {
    private var _config: AppConfig? = null

    val config: AppConfig
        get() = _config ?: throw IllegalStateException("Configuration not initialized. Call initialize() first.")

    fun initialize(environment: ApplicationEnvironment) {
        if (_config == null) {
            _config = AppConfig.load(environment)
            logger.info("Configuration manager initialized")
        } else {
            logger.warn("Configuration manager already initialized")
        }
    }

    fun isInitialized(): Boolean = _config != null

    // Convenience accessors
    val serverConfig: ServerConfig get() = config.server
    val databaseConfig: DatabaseConfig get() = config.database
    val redisConfig: RedisConfig get() = config.redis
    val httpConfig: HttpConfig get() = config.http
    val authorizationConfig: AuthorizationConfig get() = config.authorization
    val queueConfig: QueueConfig get() = config.queue
}

/**
 * Extension function to get configuration from Application
 */
val Application.appConfig: AppConfig
    get() {
        if (!ConfigManager.isInitialized()) {
            ConfigManager.initialize(environment)
        }
        return ConfigManager.config
    }