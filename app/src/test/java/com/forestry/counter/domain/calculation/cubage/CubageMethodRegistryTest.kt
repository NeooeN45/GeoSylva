package com.forestry.counter.domain.calculation.cubage

import org.junit.Assert.*
import org.junit.Test

class CubageMethodRegistryTest {

    @Test
    fun `logSegmentMethods contains the 4 geometric methods`() {
        val ids = CubageMethodRegistry.logSegmentMethods().map { it.id }.toSet()
        assertEquals(setOf("HUBER", "SMALIAN", "NEWTON", "CONE_FRUSTUM"), ids)
    }

    @Test
    fun `standingTreeMethods contains the 7 tree-level methods`() {
        val ids = CubageMethodRegistry.standingTreeMethods().map { it.id }.toSet()
        assertEquals(
            setOf("SCHAEFFER_1E", "SCHAEFFER_2E", "ALGAN", "IFN_RAPIDE", "IFN_LENT", "FGH", "COEF_FORME"),
            ids
        )
    }

    @Test
    fun `findLogSegment resolves by id`() {
        assertSame(HuberMethod, CubageMethodRegistry.findLogSegment("HUBER"))
        assertSame(SmalianMethod, CubageMethodRegistry.findLogSegment("SMALIAN"))
        assertSame(NewtonMethod, CubageMethodRegistry.findLogSegment("NEWTON"))
        assertSame(ConeFrustumMethod, CubageMethodRegistry.findLogSegment("CONE_FRUSTUM"))
    }

    @Test
    fun `findLogSegment returns null for unknown id`() {
        assertNull(CubageMethodRegistry.findLogSegment("INCONNU"))
    }

    @Test
    fun `findLogSegment filters by version when provided`() {
        assertNotNull(CubageMethodRegistry.findLogSegment("HUBER", version = "1.0.0"))
        assertNull(CubageMethodRegistry.findLogSegment("HUBER", version = "9.9.9"))
    }

    @Test
    fun `findStandingTree resolves COEF_FORME`() {
        assertSame(CoefFormeCubageMethod, CubageMethodRegistry.findStandingTree("COEF_FORME"))
    }

    @Test
    fun `Blocked case — diameter le 0 across all geometric methods`() {
        val badInput = LogSegmentMeasurement(lengthM = 5.0, diameterBaseM = 0.0, diameterMidM = 0.0, diameterTopM = 0.0)
        for (m in CubageMethodRegistry.logSegmentMethods()) {
            val result = m.compute(badInput)
            assertTrue("${m.id} should be Blocked for diameter=0", result is CubageResult.Blocked)
        }
    }

    @Test
    fun `Blocked case — length le 0 across all geometric methods`() {
        val badInput = LogSegmentMeasurement(lengthM = 0.0, diameterBaseM = 0.5, diameterMidM = 0.4, diameterTopM = 0.3)
        for (m in CubageMethodRegistry.logSegmentMethods()) {
            val result = m.compute(badInput)
            assertTrue("${m.id} should be Blocked for length=0", result is CubageResult.Blocked)
        }
    }
}
