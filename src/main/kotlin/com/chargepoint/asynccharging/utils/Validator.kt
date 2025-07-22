package com.chargepoint.asynccharging.utils

import java.util.UUID
import java.net.URL

object Validator {
    private val driverTokenRegex = Regex("^[A-Za-z0-9._~-]{20,80}$")
    
    fun isValidUUID(uuid: String): Boolean {
        return try {
            UUID.fromString(uuid)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }
    
    fun isValidDriverToken(token: String): Boolean {
        return driverTokenRegex.matches(token)
    }
    
    fun isValidUrl(url: String): Boolean {
        return try {
            val urlObj = URL(url)
            urlObj.protocol in listOf("http", "https")
        } catch (e: Exception) {
            false
        }
    }
}
