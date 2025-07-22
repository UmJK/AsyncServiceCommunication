package com.chargepoint.asynccharging.unit.models

import com.chargepoint.asynccharging.exceptions.ValidationException
import com.chargepoint.asynccharging.models.requests.ChargingRequest
import kotlin.test.*

class ChargingRequestTest {
    
    @Test
    fun `should create valid charging request`() {
        // Given
        val stationId = "123e4567-e89b-12d3-a456-426614174000"
        val driverToken = "validDriverToken123456789"
        val callbackUrl = "https://example.com/callback"
        
        // When
        val request = ChargingRequest(stationId, driverToken, callbackUrl)
        
        // Then
        assertDoesNotThrow { request.validate() }
        assertEquals(stationId, request.stationId)
        assertEquals(driverToken, request.driverToken)
        assertEquals(callbackUrl, request.callbackUrl)
        assertNotNull(request.requestId)
        assertTrue(request.timestamp > 0)
    }
    
    @Test
    fun `should fail validation for invalid station ID`() {
        // Given
        val request = ChargingRequest(
            stationId = "invalid-uuid",
            driverToken = "validDriverToken123456789",
            callbackUrl = "https://example.com/callback"
        )
        
        // When & Then
        assertFailsWith<ValidationException> {
            request.validate()
        }
    }
    
    @Test
    fun `should fail validation for short driver token`() {
        // Given
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "short", // Less than 20 characters
            callbackUrl = "https://example.com/callback"
        )
        
        // When & Then
        val exception = assertFailsWith<ValidationException> {
            request.validate()
        }
        assertTrue(exception.message!!.contains("driver_token"))
    }
    
    @Test
    fun `should fail validation for invalid callback URL`() {
        // Given
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "validDriverToken123456789",
            callbackUrl = "not-a-url"
        )
        
        // When & Then
        val exception = assertFailsWith<ValidationException> {
            request.validate()
        }
        assertTrue(exception.message!!.contains("callback_url"))
    }
    
    @Test
    fun `should mask driver token in log string`() {
        // Given
        val request = ChargingRequest(
            stationId = "123e4567-e89b-12d3-a456-426614174000",
            driverToken = "validDriverToken123456789",
            callbackUrl = "https://example.com/callback"
        )
        
        // When
        val logString = request.toLogString()
        
        // Then
        assertTrue(logString.contains("validDri***"))
        assertFalse(logString.contains("validDriverToken123456789"))
    }
}
