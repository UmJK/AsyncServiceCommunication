package com.chargepoint.asynccharging.unit.services

import com.chargepoint.asynccharging.config.AuthorizationConfig
import com.chargepoint.asynccharging.models.enums.AuthorizationStatus
import com.chargepoint.asynccharging.models.requests.ChargingRequest
import com.chargepoint.asynccharging.services.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class AuthorizationServiceTest {
    
    private lateinit var authorizationService: AuthorizationService
    private lateinit var metricsService: MetricsServiceImpl
    private lateinit var circuitBreakerService: CircuitBreakerService
    
    @BeforeTest
    fun setup() {
        metricsService = MetricsServiceImpl()
        circuitBreakerService = CircuitBreakerService()
        val config = AuthorizationConfig(
            timeout = 30.seconds,
            maxRetries = 3,
            circuitBreakerEnabled = false
        )
        
        authorizationService = AuthorizationServiceImpl(
            config,
            circuitBreakerService,
            metricsService
        )
    }
    
    @Test
    fun `should allow valid driver token`() = runTest {
        // Given - use a token that's actually in the ACL (from AuthorizationServiceImpl)
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "validDriverToken123", // This is in the ACL (but we need 20+ chars)
            callbackUrl = "https://example.com/callback"
        )
        
        // We need to manually validate since the token is < 20 chars but in ACL
        try {
            request.validate()
            fail("Should fail validation for short token")
        } catch (e: Exception) {
            // Expected - token is too short
        }
        
        // Test with a longer valid token that would be in ACL if we had one
        val validRequest = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "ABCD-efgh1234567890_~valid.token", // This is in ACL and long enough
            callbackUrl = "https://example.com/callback"
        )
        
        // When
        val decision = authorizationService.authorize(validRequest)
        
        // Then
        assertEquals(AuthorizationStatus.ALLOWED, decision.status)
        assertEquals(validRequest.requestId, decision.requestId)
        assertEquals(validRequest.stationId, decision.stationId)
        assertNull(decision.reason)
    }
    
    @Test
    fun `should deny invalid driver token`() = runTest {
        // Given - use a token that's NOT in the ACL but has 20+ characters
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "invalidDriverToken12345678901", // 31 characters, NOT in ACL
            callbackUrl = "https://example.com/callback"
        )
        
        // When
        val decision = authorizationService.authorize(request)
        
        // Then
        assertEquals(AuthorizationStatus.NOT_ALLOWED, decision.status)
        assertEquals("Driver not in ACL", decision.reason)
    }
    
    @Test
    fun `should record processing time`() = runTest {
        // Given - use valid ACL token
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "ABCD-efgh1234567890_~valid.token", // In ACL and valid length
            callbackUrl = "https://example.com/callback"
        )
        
        // When
        val decision = authorizationService.authorize(request)
        
        // Then
        assertTrue(decision.processingTimeMs > 0, "Processing time should be recorded")
    }
}
