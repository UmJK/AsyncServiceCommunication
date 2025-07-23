package com.chargepoint.asynccharging.services

import com.chargepoint.asynccharging.models.requests.ChargingRequest
import com.chargepoint.asynccharging.models.decisions.AuthorizationDecision
import java.io.File
import java.time.Instant
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * AuditService - Implements ChargePoint specification requirement:
 * "The decision is persisted for debugging purposes"
 */
class AuditService {
    private val auditFile = File("authorization_audit.log")
    
    init {
        if (!auditFile.exists()) {
            try {
                auditFile.createNewFile()
                auditFile.appendText("timestamp,request_id,station_id,driver_token_hash,status,reason,processing_time_ms\n")
                logger.info { "Created audit log file: ${auditFile.absolutePath}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to create audit log file" }
            }
        } else {
            logger.info { "Using existing audit log file: ${auditFile.absolutePath}" }
        }
    }
    
    /**
     * Log authorization decision for debugging purposes (ChargePoint spec requirement)
     */
    fun logDecision(request: ChargingRequest, decision: AuthorizationDecision) {
        try {
            // Hash driver token for security (don't log actual token)
            val tokenHash = request.driverToken.hashCode().toString()
            val auditEntry = "${Instant.now()},${request.requestId},${request.stationId},$tokenHash,${decision.status},${decision.reason ?: ""},${decision.processingTimeMs}\n"
            auditFile.appendText(auditEntry)
            logger.debug { "Logged decision for request ${request.requestId}: ${decision.status}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to log decision for request ${request.requestId}" }
        }
    }
    
    /**
     * Get audit file path for monitoring/debugging
     */
    fun getAuditFilePath(): String = auditFile.absolutePath
}
