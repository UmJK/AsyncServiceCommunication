package com.chargepoint.asynccharging.component.monitoring

import com.chargepoint.asynccharging.monitoring.HealthCheck
import com.chargepoint.asynccharging.services.MetricsServiceImpl
import kotlin.test.*

class HealthCheckComponentTest {
    
    private lateinit var healthCheck: HealthCheck
    private lateinit var metricsService: MetricsServiceImpl
    
    @BeforeTest
    fun setup() {
        metricsService = MetricsServiceImpl()
        healthCheck = HealthCheck(metricsService)
    }
    
    @Test
    fun `should report UP status when all components are healthy`() {
        // When
        val health = healthCheck.check()
        
        // Then
        assertEquals("UP", health.status)
        assertTrue(health.components.containsKey("queue"))
        assertTrue(health.components.containsKey("authorization"))
        assertTrue(health.components.containsKey("callbacks"))
    }
    
    @Test
    fun `should report DEGRADED status when queue is large`() {
        // Given - simulate large queue
        metricsService.recordQueueSize(1500)
        
        // When
        val health = healthCheck.check()
        
        // Then
        assertEquals("DEGRADED", health.status)
        assertEquals("DEGRADED", health.components["queue"]?.status)
    }
    
    @Test
    fun `should report component details`() {
        // When
        val health = healthCheck.check()
        
        // Then
        val queueComponent = health.components["queue"]
        assertNotNull(queueComponent)
        assertNotNull(queueComponent.details)
        assertTrue(queueComponent.details!!.containsKey("currentSize"))
        assertTrue(queueComponent.details!!.containsKey("maxSize"))
    }
}
