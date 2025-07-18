package com.chargepoint.asynccharging.utils

import java.util.regex.Pattern

/**
 * Utility object to validate UUIDs, driver tokens, and callback URLs.
 * Used across controllers and services to ensure request integrity.
 */
object Validator {

    /**
     * Validates if the input string is a well-formed UUID.
     * @param uuid the UUID string to validate
     * @return true if the string is a valid UUID format
     */
    fun isValidUUID(uuid: String): Boolean =
        uuid.matches(Regex("[a-fA-F0-9\\-]{36}"))

    /**
     * Validates the driver token (20–80 characters, alphanumeric and -._~)
     * @param token the driver token to validate
     * @return true if the token follows the expected format
     */
    fun isValidDriverToken(token: String): Boolean =
        token.length in 20..80 && token.matches(Regex("[A-Za-z0-9._~-]+"))

    /**
     * Validates whether the callback URL starts with http or https.
     * @param url the callback URL string
     * @return true if it begins with "http://" or "https://"
     */
    fun isValidUrl(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")
}
