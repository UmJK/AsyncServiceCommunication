package com.chargepoint.asynccharging.unit.services

import com.chargepoint.asynccharging.services.CircuitBreakerService
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class CircuitBreakerServiceTest {
    
    private lateinit var circuitBreakerService: CircuitBreakerService
    
    @BeforeTest
    fun setup() {
        circuitBreakerService = CircuitBreakerService()
    }
    
    @Test
    fun `should execute successful operation`() = runTest {
        // Given
        val expectedResult = "success"
        
        // When
        val result = circuitBreakerService.execute("test") {
            expectedResult
        }
        
        // Then
        assertEquals(expectedResult, result)
    }
    
    @Test
    fun `should propagate exceptions`() = runTest {
        // Given
        val expectedException = RuntimeException("Test exception")
        
        // When & Then
        assertFailsWith<RuntimeException> {
            circuitBreakerService.execute("test") {
                throw expectedException
            }
        }
    }
    
    @Test
    fun `should open circuit after threshold failures`() = runTest {
        // Given
        val failingOperation: suspend () -> String = {
            throw RuntimeException("Always fails")
        }
        
        // When - trigger failures to open circuit
        repeat(5) {
            assertFailsWith<RuntimeException> {
                circuitBreakerService.execute("failing-service", failingOperation)
            }
        }
        
        // Then - circuit should be open
        assertFailsWith<RuntimeException> {
            circuitBreakerService.execute("failing-service") { "should not execute" }
        }
    }
}
