package com.chargepoint.asynccharging

import com.chargepoint.asynccharging.config.AppConfig
import com.chargepoint.asynccharging.queue.AuthorizationQueue
import com.chargepoint.asynccharging.services.*
import com.chargepoint.asynccharging.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.launch
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun main() {
    embeddedServer(
        Netty, 
        port = 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    val appConfig = AppConfig.from(environment.config)
    
    val metricsService = MetricsServiceImpl()
    val circuitBreakerService = CircuitBreakerService()
    val authorizationQueue = AuthorizationQueue(appConfig.queue, metricsService)
    val authorizationService = AuthorizationServiceImpl(
        appConfig.authorization, 
        circuitBreakerService, 
        metricsService
    )
    val callbackService = CallbackServiceImpl(appConfig.callback, metricsService)
    
    launch {
        val processor = AuthorizationProcessor(
            authorizationQueue,
            authorizationService,
            callbackService,
            metricsService
        )
        processor.start()
    }
    
    configureHTTP()
    configureSerialization()
    configureMonitoring(metricsService)
    configureRouting(authorizationQueue, metricsService)
    configureStatusPages()
    configureCORS()
    
    logger.info { "Application started successfully" }
}
