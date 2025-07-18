package com.chargepoint.asynccharging.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for input validation utilities defined in [Validator.kt]
 * Ensures StationId, driverToken, and callback URL are validated correctly.
 */
class ValidatorTest {

    @Test
    fun `valid UUID format should pass`() {
        assertTrue(Validator.isValidUUID("123e4567-e89b-12d3-a456-426614174000"))
    }

    @Test
    fun `invalid UUID format should fail`() {
        assertFalse(Validator.isValidUUID("not-a-valid-uuid"))
    }

    @Test
    fun `valid driver token should pass`() {
        assertTrue(Validator.isValidDriverToken("token-valid-1234567890"))
    }

    @Test
    fun `driver token too short should fail`() {
        assertFalse(Validator.isValidDriverToken("short"))
    }

    @Test
    fun `driver token with invalid characters should fail`() {
        assertFalse(Validator.isValidDriverToken("invalid token!*@#"))
    }

    @Test
    fun `valid callback URL should pass`() {
        assertTrue(Validator.isValidUrl("https://valid-url.com"))
    }

    @Test
    fun `invalid callback URL should fail`() {
        assertFalse(Validator.isValidUrl("ftp://invalid-url"))
    }
}
