package com.forestry.counter.domain.calculation.cubage

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class NewtonMethodTest {

    // Vecteur "newton_log" — docs/recherche/02_marche_prix/vecteurs_tests_cubage.json
    @Test
    fun `Newton — L=5 Db=0,5 Dmid=0,4 Dt=0,3 matches reference vector`() {
        val result = NewtonMethod.compute(
            LogSegmentMeasurement(lengthM = 5.0, diameterBaseM = 0.5, diameterMidM = 0.4, diameterTopM = 0.3)
        )
        assertTrue(result is CubageResult.Valid)
        val volume = (result as CubageResult.Valid).volumeM3
        assertTrue("expected ≈0.6414085001 but was $volume", abs(volume - 0.6414085001) <= 1e-7)
    }

    @Test
    fun `Newton — missing mid diameter is Blocked`() {
        val result = NewtonMethod.compute(
            LogSegmentMeasurement(lengthM = 5.0, diameterBaseM = 0.5, diameterTopM = 0.3)
        )
        assertTrue(result is CubageResult.Blocked)
    }

    @Test
    fun `Newton — method metadata`() {
        assertEquals("NEWTON", NewtonMethod.id)
        assertEquals(MethodQualification.QUALIFIED, NewtonMethod.qualification)
    }
}
