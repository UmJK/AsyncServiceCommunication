package com.chargepoint.asynccharging.services

import kotlinx.coroutines.delay

object RetryUtils {
    suspend fun <T> executeWithRetry(retries: Int, delayMillis: Long, block: suspend () -> T): T {
        var lastError: Throwable? = null
        repeat(retries) {
            try {
                return block()
            } catch (ex: Exception) {
                lastError = ex
                delay(delayMillis)
            }
        }
        throw lastError ?: IllegalStateException("Failed after retries")
    }
}
