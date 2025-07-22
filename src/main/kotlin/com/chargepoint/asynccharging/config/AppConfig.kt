package com.chargepoint.asynccharging.config

import io.ktor.server.config.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class AppConfig(
    val server: ServerConfig,
    val authorization: AuthorizationConfig,
    val callback: CallbackConfig,
    val queue: QueueConfig,
    val monitoring: MonitoringConfig
) {
    companion object {
        fun from(config: ApplicationConfig): AppConfig = AppConfig(
            server = ServerConfig(
                port = config.propertyOrNull("ktor.deployment.port")?.getString()?.toInt() ?: 8080,
                development = config.propertyOrNull("ktor.deployment.development")?.getString()?.toBoolean() ?: false
            ),
            authorization = AuthorizationConfig(
                timeout = config.propertyOrNull("authorization.timeoutMillis")?.getString()?.toLong()?.milliseconds ?: 30.seconds,
                maxRetries = config.propertyOrNull("authorization.maxRetries")?.getString()?.toInt() ?: 3,
                circuitBreakerEnabled = config.propertyOrNull("authorization.circuitBreaker.enabled")?.getString()?.toBoolean() ?: true
            ),
            callback = CallbackConfig(
                timeout = config.propertyOrNull("callback.timeoutMillis")?.getString()?.toLong()?.milliseconds ?: 10.seconds,
                maxRetries = config.propertyOrNull("callback.maxRetries")?.getString()?.toInt() ?: 3,
                retryDelay = config.propertyOrNull("callback.retryDelayMillis")?.getString()?.toLong()?.milliseconds ?: 1.seconds
            ),
            queue = QueueConfig(
                maxSize = config.propertyOrNull("queue.maxSize")?.getString()?.toInt() ?: 10000,
                consumerThreads = config.propertyOrNull("queue.consumerThreads")?.getString()?.toInt() ?: 1
            ),
            monitoring = MonitoringConfig(
                metricsEnabled = config.propertyOrNull("monitoring.metrics.enabled")?.getString()?.toBoolean() ?: true,
                healthCheckEnabled = config.propertyOrNull("monitoring.healthCheck.enabled")?.getString()?.toBoolean() ?: true
            )
        )
    }
}

data class ServerConfig(
    val port: Int,
    val development: Boolean
)

data class AuthorizationConfig(
    val timeout: Duration,
    val maxRetries: Int,
    val circuitBreakerEnabled: Boolean
)

data class CallbackConfig(
    val timeout: Duration,
    val maxRetries: Int,
    val retryDelay: Duration
)

data class QueueConfig(
    val maxSize: Int,
    val consumerThreads: Int
)

data class MonitoringConfig(
    val metricsEnabled: Boolean,
    val healthCheckEnabled: Boolean
)
