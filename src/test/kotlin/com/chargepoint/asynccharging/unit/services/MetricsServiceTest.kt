package com.chargepoint.asynccharging.unit.services

import com.chargepoint.asynccharging.services.MetricsServiceImpl
import kotlin.test.*

class MetricsServiceTest {
    
    private lateinit var metricsService: MetricsServiceImpl
    
    @BeforeTest
    fun setup() {
        metricsService = MetricsServiceImpl()
    }
    
    @Test
    fun `should increment request counter`() {
        // Given
        val initialCount = metricsService.getMetrics().requests_total
        
        // When
        metricsService.incrementRequestCounter()
        
        // Then
        val updatedCount = metricsService.getMetrics().requests_total
        assertEquals(initialCount + 1, updatedCount)
    }
    
    @Test
    fun `should track authorization decisions`() {
        // When
        metricsService.incrementAuthorizationCounter("allowed")
        metricsService.incrementAuthorizationCounter("not_allowed")
        metricsService.incrementAuthorizationCounter("allowed")
        
        // Then
        val metrics = metricsService.getMetrics()
        assertEquals(2L, metrics.authorization_decisions["allowed"])
        assertEquals(1L, metrics.authorization_decisions["not_allowed"])
    }
    
    @Test
    fun `should calculate average authorization time`() {
        // When
        metricsService.recordAuthorizationTime(100L)
        metricsService.recordAuthorizationTime(200L)
        metricsService.recordAuthorizationTime(300L)
        
        // Then
        val metrics = metricsService.getMetrics()
        assertEquals(200.0, metrics.authorization_time_avg_ms)
    }
    
    @Test
    fun `should track queue size`() {
        // When
        metricsService.recordQueueSize(42)
        
        // Then
        val metrics = metricsService.getMetrics()
        assertEquals(42L, metrics.queue_size_current)
    }
}
