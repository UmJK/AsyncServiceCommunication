package com.chargepoint.asynccharging.services

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

interface MetricsService {
    fun incrementRequestCounter()
    fun incrementAuthorizationCounter(status: String)
    fun incrementCallbackCounter(status: String)
    fun recordAuthorizationTime(timeMs: Long)
    fun recordQueueSize(size: Int)
    fun getMetrics(): MetricsResponse
}

@Serializable
data class MetricsResponse(
    val requests_total: Long,
    val authorization_decisions: Map<String, Long>,
    val callback_results: Map<String, Long>,
    val authorization_time_avg_ms: Double,
    val authorization_time_p95_ms: Long,
    val queue_size_current: Long
)

class MetricsServiceImpl : MetricsService {
    private val requestCounter = LongAdder()
    private val authorizationCounters = ConcurrentHashMap<String, LongAdder>()
    private val callbackCounters = ConcurrentHashMap<String, LongAdder>()
    private val authorizationTimes = mutableListOf<Long>()
    private val currentQueueSize = AtomicLong(0)
    
    override fun incrementRequestCounter() {
        requestCounter.increment()
    }
    
    override fun incrementAuthorizationCounter(status: String) {
        authorizationCounters.computeIfAbsent(status) { LongAdder() }.increment()
    }
    
    override fun incrementCallbackCounter(status: String) {
        callbackCounters.computeIfAbsent(status) { LongAdder() }.increment()
    }
    
    override fun recordAuthorizationTime(timeMs: Long) {
        synchronized(authorizationTimes) {
            authorizationTimes.add(timeMs)
            if (authorizationTimes.size > 1000) {
                authorizationTimes.removeAt(0)
            }
        }
    }
    
    override fun recordQueueSize(size: Int) {
        currentQueueSize.set(size.toLong())
    }
    
    override fun getMetrics(): MetricsResponse {
        val authTimes = synchronized(authorizationTimes) { authorizationTimes.toList() }
        
        return MetricsResponse(
            requests_total = requestCounter.sum(),
            authorization_decisions = authorizationCounters.mapValues { it.value.sum() },
            callback_results = callbackCounters.mapValues { it.value.sum() },
            authorization_time_avg_ms = if (authTimes.isNotEmpty()) authTimes.average() else 0.0,
            authorization_time_p95_ms = if (authTimes.isNotEmpty()) authTimes.sorted().let { sorted ->
                sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)]
            } else 0L,
            queue_size_current = currentQueueSize.get()
        )
    }
}
