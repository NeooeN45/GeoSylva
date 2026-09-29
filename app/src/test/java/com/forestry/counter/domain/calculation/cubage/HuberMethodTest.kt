package com.forestry.counter.domain.calculation.cubage

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class HuberMethodTest {

    // Vecteur "huber_cylindrical_log" — docs/recherche/02_marche_prix/vecteurs_tests_cubage.json
    @Test
    fun `Huber — L=5 Dmid=0,4 matches reference vector`() {
        val result = HuberMethod.compute(LogSegmentMeasurement(lengthM = 5.0, diameterMidM = 0.4))
        assertTrue(result is CubageResult.Valid)
        val volume = (result as CubageResult.Valid).volumeM3
        assertTrue("expected ≈0.6283185307 but was $volume", abs(volume - 0.6283185307) <= 1e-7)
    }

    @Test
    fun `Huber — missing mid diameter is Blocked`() {
        val result = HuberMethod.compute(LogSegmentMeasurement(lengthM = 5.0))
        assertTrue(result is CubageResult.Blocked)
    }

    @Test
    fun `Huber — zero length is Blocked`() {
        val result = HuberMethod.compute(LogSegmentMeasurement(lengthM = 0.0, diameterMidM = 0.4))
        assertTrue(result is CubageResult.Blocked)
    }

    @Test
    fun `Huber — negative diameter is Blocked`() {
        val result = HuberMethod.compute(LogSegmentMeasurement(lengthM = 5.0, diameterMidM = -0.1))
        assertTrue(result is CubageResult.Blocked)
    }

    @Test
    fun `Huber — method metadata`() {
        assertEquals("HUBER", HuberMethod.id)
        assertEquals(MethodQualification.QUALIFIED, HuberMethod.qualification)
    }
}
