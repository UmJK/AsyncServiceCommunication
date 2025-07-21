package com.chargepoint.asynccharging.models
package com.chargepoint.asynccharging.models

import kotlinx.serialization.Serializable

/**
 * Authorization decision model
 */
@Serializable
data class AuthorizationDecision(
    val status: String, // "allowed", "not_allowed", "unknown"
    val reason: String? = null,
    val metadata: Map<String, String> = emptyMap()
)
import kotlinx.serialization.Serializable

@Serializable
enum class AuthorizationDecision {
    PENDING,
    APPROVED,
    REJECTED
}