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
        // Given
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "validDriverToken123", // This is in the ACL
            callbackUrl = "https://example.com/callback"
        )
        
        // When
        val decision = authorizationService.authorize(request)
        
        // Then
        assertEquals(AuthorizationStatus.ALLOWED, decision.status)
        assertEquals(request.requestId, decision.requestId)
        assertEquals(request.stationId, decision.stationId)
        assertNull(decision.reason)
    }
    
    @Test
    fun `should deny invalid driver token`() = runTest {
        // Given
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "invalidDriverToken12345678",
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
        // Given
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "validDriverToken123",
            callbackUrl = "https://example.com/callback"
        )
        
        // When
        val decision = authorizationService.authorize(request)
        
        // Then
        assertTrue(decision.processingTimeMs > 0, "Processing time should be recorded")
    }
}
