package com.chargepoint.asynccharging.controllers

import com.chargepoint.asynccharging.models.*
import com.chargepoint.asynccharging.services.AuthorizationService
import com.chargepoint.asynccharging.utils.Validator
import com.chargepoint.asynccharging.database.AuthorizationRepositoryInterface
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Controller for handling charging-related HTTP requests
 */
class ChargingController(
    private val authorizationService: AuthorizationService,
    private val authorizationRepository: AuthorizationRepositoryInterface
) {

    private val logger = LoggerFactory.getLogger(ChargingController::class.java)

    /**
     * Handle charging start request
     */
    suspend fun startCharging(call: ApplicationCall) {
        try {
            val clientHost = getClientHost(call)
            logger.info("Received charging start request from $clientHost")

            val request = call.receive<ChargingRequest>()
            logger.debug("Parsed charging request: $request")

            val validationResult = Validator.validateChargingRequest(request)
            if (!validationResult.isValid) {
                logger.warn("Invalid charging request: ${validationResult.errorMessage}")
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                    error = "Validation failed",
                    message = validationResult.errorMessage,
                    requestId = generateRequestId()
                ))
                return
            }

            // Check for duplicate requests
            val existingRequests = authorizationRepository.findByUserAndStation(
                request.userId,
                request.stationId
            ).filter { it.decision == "PENDING" }

            if (existingRequests.isNotEmpty()) {
                val existingRequest = existingRequests.first()
                logger.info("Found existing pending request: ${existingRequest.authorizationId}")
                call.respond(HttpStatusCode.Conflict, ErrorResponse(
                    error = "Duplicate request",
                    message = "You already have a pending charging request for this station",
                    requestId = generateRequestId(),
                    authorizationId = existingRequest.authorizationId
                ))
                return
            }

            // Submit for async authorization
            val authorizationId = authorizationService.submitAuthorizationRequest(request)
            logger.info("Submitted authorization request: $authorizationId for user: ${request.userId}")

            // Respond immediately with authorization ID
            call.respond(HttpStatusCode.Accepted, ChargingStartResponse(
                authorizationId = authorizationId,
                message = "Charging request submitted for authorization",
                status = "pending",
                estimatedProcessingTime = "30-60 seconds",
                statusUrl = "/api/v1/charging/status/$authorizationId",
                requestId = generateRequestId(),
                timestamp = Clock.System.now().toString()
            ))

        } catch (e: ContentTransformationException) {
            logger.warn("Invalid request format: ${e.message}")
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                error = "Invalid request format",
                message = "Please check your request body format",
                requestId = generateRequestId()
            ))
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid request parameters: ${e.message}")
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                error = "Invalid parameters",
                message = e.message ?: "Invalid parameters",
                requestId = generateRequestId()
            ))
        } catch (e: Exception) {
            logger.error("Unexpected error processing charging request", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(
                error = "Internal server error",
                message = "An unexpected error occurred",
                requestId = generateRequestId()
            ))
        }
    }

    /**
     * Get authorization status
     */
    suspend fun getAuthorizationStatus(call: ApplicationCall) {
        try {
            val authorizationId = call.parameters["authorizationId"]
            if (authorizationId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                    error = "Missing authorization ID",
                    message = "Authorization ID is required",
                    requestId = generateRequestId()
                ))
                return
            }

            logger.debug("Getting authorization status for: $authorizationId")

            val record = authorizationRepository.findById(authorizationId)
            if (record == null) {
                logger.warn("Authorization not found: $authorizationId")
                call.respond(HttpStatusCode.NotFound, ErrorResponse(
                    error = "Authorization not found",
                    message = "No authorization found with the provided ID",
                    requestId = generateRequestId(),
                    authorizationId = authorizationId
                ))
                return
            }

            val now = Clock.System.now()
            val isExpired = record.expiresAt?.let { it <= now } ?: false

            call.respond(HttpStatusCode.OK, AuthorizationStatusResponse(
                authorizationId = record.authorizationId,
                userId = record.userId,
                stationId = record.stationId,
                connectorId = record.connectorId,
                status = record.decision.lowercase(),
                decision = record.decision,
                reason = record.reason,
                requestedEnergy = record.requestedEnergy.toString(),
                approvedEnergy = record.approvedEnergy?.toString(),
                maxDurationMinutes = record.maxDurationMinutes,
                submittedAt = record.timestamp.toString(),
                processedAt = record.processedAt?.toString(),
                expiresAt = record.expiresAt?.toString(),
                isExpired = isExpired,
                metadata = record.metadata,
                requestId = generateRequestId(),
                timestamp = now.toString()
            ))

        } catch (e: Exception) {
            logger.error("Error getting authorization status", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(
                error = "Internal server error",
                message = "Unable to retrieve authorization status",
                requestId = generateRequestId()
            ))
        }
    }

    /**
     * Cancel charging authorization
     */
    suspend fun cancelAuthorization(call: ApplicationCall) {
        try {
            val authorizationId = call.parameters["authorizationId"]
            if (authorizationId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                    error = "Missing authorization ID",
                    message = "Authorization ID is required",
                    requestId = generateRequestId()
                ))
                return
            }

            logger.info("Cancelling authorization: $authorizationId")

            val record = authorizationRepository.findById(authorizationId)
            if (record == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(
                    error = "Authorization not found",
                    message = "No authorization found with the provided ID",
                    requestId = generateRequestId(),
                    authorizationId = authorizationId
                ))
                return
            }

            // Check if authorization can be cancelled
            if (record.decision != "PENDING") {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                    error = "Cannot cancel authorization",
                    message = "Authorization is already ${record.decision.lowercase()}",
                    requestId = generateRequestId(),
                    authorizationId = authorizationId
                ))
                return
            }

            // Update authorization status to cancelled
            val updatedRecord = authorizationRepository.updateStatus(
                authorizationId = authorizationId,
                decision = "REJECTED",
                reason = "Cancelled by user",
                processedAt = Clock.System.now()
            )

            if (updatedRecord != null) {
                logger.info("Successfully cancelled authorization: $authorizationId")
                call.respond(HttpStatusCode.OK, CancelAuthorizationResponse(
                    message = "Authorization cancelled successfully",
                    authorizationId = authorizationId,
                    status = "cancelled",
                    requestId = generateRequestId(),
                    timestamp = Clock.System.now().toString()
                ))
            } else {
                throw Exception("Failed to update authorization status")
            }

        } catch (e: Exception) {
            logger.error("Error cancelling authorization", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(
                error = "Internal server error",
                message = "Unable to cancel authorization",
                requestId = generateRequestId()
            ))
        }
    }

    /**
     * Health check endpoint
     */
    suspend fun healthCheck(call: ApplicationCall) {
        try {
            logger.debug("Performing health check")

            val repositoryHealth = authorizationRepository.healthCheck()
            val serviceStats = authorizationService.getStatistics()

            // Determine service health based on statistics
            val serviceHealth = buildServiceHealth(serviceStats)
            val overallStatus = determineOverallStatus(repositoryHealth, serviceHealth)

            val response = HealthCheckResponse(
                status = overallStatus,
                components = HealthComponents(
                    repository = RepositoryHealth(
                        status = repositoryHealth["status"] as? String ?: "unknown",
                        type = repositoryHealth["type"] as? String ?: "unknown",
                        version = repositoryHealth["version"] as? String ?: "1.0.0",
                        timestamp = repositoryHealth["timestamp"] as? String ?: Clock.System.now().toString(),
                        healthCheckDurationMs = repositoryHealth["healthCheckDurationMs"] as? Long ?: 0L,
                        uptime = repositoryHealth["uptime"] as? String ?: "unknown",
                        metrics = HealthMetrics(
                            recordCount = (repositoryHealth["metrics"] as? Map<*, *>)?.get("recordCount") as? Int ?: 0,
                            operationCount = (repositoryHealth["metrics"] as? Map<*, *>)?.get("operationCount") as? Long ?: 0L,
                            memoryUsageMB = (repositoryHealth["metrics"] as? Map<*, *>)?.get("memoryUsageMB") as? Long ?: 0L,
                            hasRecords = (repositoryHealth["metrics"] as? Map<*, *>)?.get("hasRecords") as? Boolean ?: false
                        ),
                        issues = repositoryHealth["issues"] as? List<String> ?: emptyList(),
                        recommendations = repositoryHealth["recommendations"] as? List<String> ?: emptyList()
                    ),
                    service = ServiceHealth(
                        status = serviceHealth["status"] as? String ?: "unknown",
                        isProcessing = serviceHealth["isProcessing"] as? Boolean ?: false,
                        processedCount = serviceHealth["processedCount"] as? Long ?: 0L,
                        errorCount = serviceHealth["errorCount"] as? Long ?: 0L,
                        errorRate = serviceHealth["errorRate"] as? String ?: "0.00%",
                        statistics = ServiceStats(
                            processedCount = serviceStats["processedCount"] as? Long ?: 0L,
                            errorCount = serviceStats["errorCount"] as? Long ?: 0L,
                            isProcessing = serviceStats["isProcessing"] as? Boolean ?: false,
                            queueSize = serviceStats["queueSize"] as? Long ?: 0L,
                            averageProcessingTime = serviceStats["averageProcessingTime"] as? Double ?: 0.0,
                            errorRate = serviceStats["errorRate"] as? Double ?: 0.0
                        )
                    )
                ),
                requestId = generateRequestId(),
                timestamp = Clock.System.now().toString()
            )

            val httpStatus = when (overallStatus) {
                "unhealthy" -> HttpStatusCode.ServiceUnavailable
                "degraded" -> HttpStatusCode.OK
                else -> HttpStatusCode.OK
            }

            call.respond(httpStatus, response)

        } catch (e: Exception) {
            logger.error("Health check failed", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(
                error = "Health check failed",
                message = e.message ?: "Health check failed",
                requestId = generateRequestId()
            ))
        }
    }

    // Helper methods
    private fun getClientHost(call: ApplicationCall): String {
        return try {
            call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                ?: call.request.headers["X-Real-IP"]
                ?: call.request.local.remoteHost
                ?: "unknown"
        } catch (e: Exception) {
            logger.debug("Could not determine client host: ${e.message}")
            "unknown"
        }
    }

    private fun buildServiceHealth(stats: Map<String, Any>): Map<String, Any> {
        val processedCount = stats["processedCount"] as? Long ?: 0L
        val errorCount = stats["errorCount"] as? Long ?: 0L
        val isProcessing = stats["isProcessing"] as? Boolean ?: false

        val errorRate = if (processedCount > 0) {
            (errorCount.toDouble() / processedCount.toDouble()) * 100
        } else 0.0

        val status = when {
            !isProcessing -> "unhealthy"
            errorRate > 50.0 -> "degraded"
            errorRate > 20.0 -> "warning"
            else -> "healthy"
        }

        return mapOf(
            "status" to status,
            "isProcessing" to isProcessing,
            "processedCount" to processedCount,
            "errorCount" to errorCount,
            "errorRate" to "%.2f%%".format(errorRate),
            "statistics" to stats
        )
    }

    private fun determineOverallStatus(
        repositoryHealth: Map<String, Any>,
        serviceHealth: Map<String, Any>
    ): String {
        val repoStatus = repositoryHealth["status"]?.toString() ?: "unknown"
        val serviceStatus = serviceHealth["status"]?.toString() ?: "unknown"

        return when {
            repoStatus == "unhealthy" || serviceStatus == "unhealthy" -> "unhealthy"
            repoStatus == "degraded" || serviceStatus == "degraded" -> "degraded"
            repoStatus == "warning" || serviceStatus == "warning" -> "warning"
            else -> "healthy"
        }
    }

    private fun generateRequestId(): String {
        return UUID.randomUUID().toString().take(8)
    }
}