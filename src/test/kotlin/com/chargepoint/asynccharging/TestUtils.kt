package com.chargepoint.asynccharging

import com.chargepoint.asynccharging.models.requests.ChargingRequest

object TestUtils {
    
    // Tokens from the actual ACL in AuthorizationServiceImpl
    val VALID_ACL_TOKENS = listOf(
        "validDriverToken123",               // Too short for validation but in ACL
        "ABCD-efgh1234567890_~valid.token", // Perfect - in ACL and valid length
        "authorizedDriver456",              // Too short for validation but in ACL
        "testDriver789"                     // Too short for validation but in ACL
    )
    
    val VALID_LONG_ACL_TOKEN = "ABCD-efgh1234567890_~valid.token"
    
    fun createValidRequest(
        stationId: String = "123e4567-e89b-12d3-a456-426614174000",
        driverToken: String = VALID_LONG_ACL_TOKEN,
        callbackUrl: String = "https://example.com/callback"
    ) = ChargingRequest(
        stationId = stationId,
        driverToken = driverToken,
        callbackUrl = callbackUrl
    )
    
    fun createInvalidRequest() = ChargingRequest(
        stationId = "invalid-uuid",
        driverToken = "short",
        callbackUrl = "not-a-url"
    )
    
    fun createValidRequestWithInvalidToken() = ChargingRequest(
        stationId = "123e4567-e89b-12d3-a456-426614174000",
        driverToken = "invalidButLongDriverToken123456789", // Long enough but not in ACL
        callbackUrl = "https://example.com/callback"
    )
}
