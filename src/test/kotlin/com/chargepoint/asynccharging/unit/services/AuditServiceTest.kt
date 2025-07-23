package com.chargepoint.asynccharging.unit.services

import com.chargepoint.asynccharging.models.decisions.AuthorizationDecision
import com.chargepoint.asynccharging.models.enums.AuthorizationStatus
import com.chargepoint.asynccharging.models.requests.ChargingRequest
import com.chargepoint.asynccharging.services.AuditService
import java.io.File
import kotlin.test.*

class AuditServiceTest {
    
    private lateinit var auditService: AuditService
    private val testAuditFile = File("test_authorization_audit.log")
    
    @BeforeTest
    fun setup() {
        // Clean up any existing test file
        if (testAuditFile.exists()) {
            testAuditFile.delete()
        }
        auditService = AuditService()
    }
    
    @AfterTest
    fun cleanup() {
        // Clean up test file
        testAuditFile.delete()
        // Also clean up the main audit file created during tests
        File("authorization_audit.log").delete()
    }
    
    @Test
    fun `should create audit log file on initialization`() {
        // When - AuditService is created, it should create the log file
        val auditFilePath = auditService.getAuditFilePath()
        val auditFile = File(auditFilePath)
        
        // Then
        assertTrue(auditFile.exists(), "Audit log file should be created")
    }
    
    @Test
    fun `should log authorization decision`() {
        // Given
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "testDriverToken123456789",
            callbackUrl = "https://example.com/callback"
        )
        
        val decision = AuthorizationDecision(
            requestId = request.requestId,
            stationId = request.stationId,
            status = AuthorizationStatus.ALLOWED,
            reason = null,
            processingTimeMs = 150L
        )
        
        // When
        auditService.logDecision(request, decision)
        
        // Then
        val auditFile = File(auditService.getAuditFilePath())
        assertTrue(auditFile.exists(), "Audit file should exist")
        
        val content = auditFile.readText()
        println("DEBUG: Audit file content: '$content'") // Debug output
        
        // More flexible assertions - check for the actual content format
        val lines = content.lines().filter { it.isNotBlank() }
        assertTrue(lines.size >= 2, "Should have header and at least one data line, but got: $lines")
        
        // Check header line exists
        val headerLine = lines.first()
        assertTrue(headerLine.contains("timestamp"), "Header should contain 'timestamp'")
        assertTrue(headerLine.contains("request_id"), "Header should contain 'request_id'")
        
        // Check data line (should be the second line)
        val dataLines = lines.drop(1) // Skip header
        assertTrue(dataLines.isNotEmpty(), "Should have at least one data line")
        
        val dataLine = dataLines.last() // Get the last data line
        assertTrue(dataLine.contains(request.requestId), "Should contain request ID: ${request.requestId}")
        assertTrue(dataLine.contains(request.stationId), "Should contain station ID: ${request.stationId}")
        
        // Check for status - could be "ALLOWED" or "allowed"
        assertTrue(
            dataLine.contains("ALLOWED") || dataLine.contains("allowed"),
            "Should contain status 'ALLOWED' or 'allowed', but dataLine is: '$dataLine'"
        )
    }
    
    @Test
    fun `should hash driver token in audit log`() {
        // Given
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "sensitiveDriverToken123456789",
            callbackUrl = "https://example.com/callback"
        )
        
        val decision = AuthorizationDecision(
            requestId = request.requestId,
            stationId = request.stationId,
            status = AuthorizationStatus.ALLOWED,
            reason = null,
            processingTimeMs = 150L
        )
        
        // When
        auditService.logDecision(request, decision)
        
        // Then
        val auditFile = File(auditService.getAuditFilePath())
        val content = auditFile.readText()
        
        // Should NOT contain the actual driver token
        assertFalse(content.contains("sensitiveDriverToken123456789"), 
                   "Should not contain actual driver token")
        
        // Should contain a hash (numeric representation)
        val expectedHash = request.driverToken.hashCode().toString()
        assertTrue(content.contains(expectedHash),
                  "Should contain hashed driver token: $expectedHash, but content is: '$content'")
    }
    
    @Test
    fun `should handle CSV format correctly`() {
        // Given
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "testDriverToken123456789",
            callbackUrl = "https://example.com/callback"
        )
        
        val decision = AuthorizationDecision(
            requestId = request.requestId,
            stationId = request.stationId,
            status = AuthorizationStatus.NOT_ALLOWED,
            reason = "Driver not in ACL",
            processingTimeMs = 75L
        )
        
        // When
        auditService.logDecision(request, decision)
        
        // Then
        val auditFile = File(auditService.getAuditFilePath())
        val content = auditFile.readText()
        val lines = content.lines().filter { it.isNotBlank() }
        
        // Should have proper CSV format
        val dataLine = lines.last()
        val fields = dataLine.split(",")
        
        assertTrue(fields.size >= 6, "Should have at least 6 CSV fields, but got: ${fields.size} in '$dataLine'")
        
        // Verify specific fields
        assertTrue(fields[1] == request.requestId, "Field 2 should be request ID")
        assertTrue(fields[2] == request.stationId, "Field 3 should be station ID")
        assertTrue(fields[4].contains("NOT_ALLOWED") || fields[4].contains("not_allowed"), 
                  "Field 5 should contain status")
        assertTrue(fields[5] == "Driver not in ACL", "Field 6 should be reason")
        assertTrue(fields[6] == "75", "Field 7 should be processing time")
    }
}
