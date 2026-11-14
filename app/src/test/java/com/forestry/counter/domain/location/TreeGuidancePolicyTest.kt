package com.forestry.counter.domain.location

import org.junit.Assert.*
import org.junit.Test

class TreeGuidancePolicyTest {
    private val now = 100_000_000_000L

    @Test fun `fix lifetime uses monotonic observation time and never the display clock`() {
        assertEquals(TreePositionStatus.FRESH, position(now - 15_000_000_000))
        assertEquals(TreePositionStatus.STALE, position(now - 15_000_000_001))
        assertEquals(TreePositionStatus.STALE, position(now + 1))
        assertEquals(TreePositionStatus.STALE, position(0))
        assertEquals(TreePositionStatus.WAITING, position(null))
    }

    @Test fun `permission provider and pause reasons remain distinct with a recent fix`() {
        assertEquals(TreePositionStatus.PERMISSION_REQUIRED, TreeGuidancePolicy.positionStatus(now, now - 1, false, false, true))
        assertEquals(TreePositionStatus.UNAVAILABLE, TreeGuidancePolicy.positionStatus(now, now - 1, false, true, false))
        assertEquals(TreePositionStatus.PAUSED, TreeGuidancePolicy.positionStatus(now, now - 1, true, true, true))
    }

    @Test fun `orientation quality absence staleness and disturbance do not invent a north heading`() {
        assertEquals(TreeOrientationStatus.UNAVAILABLE, heading(null, false, null))
        assertEquals(TreeOrientationStatus.WAITING, heading(null, true, null))
        assertEquals(TreeOrientationStatus.UNKNOWN, heading(now - 1, true, null))
        assertEquals(TreeOrientationStatus.UNRELIABLE, heading(now - 1, true, 0))
        assertEquals(TreeOrientationStatus.UNRELIABLE, heading(now - 1, true, 1))
        assertEquals(TreeOrientationStatus.READY, heading(now - 1, true, 2))
        assertEquals(TreeOrientationStatus.STALE, heading(now - 5_000_000_001, true, 3))
    }

    @Test fun `relative direction requires fresh position and qualified heading independently`() {
        assertEquals(20f, TreeGuidancePolicy.relativeBearing(10f, 350f, TreePositionStatus.FRESH, TreeOrientationStatus.READY))
        assertNull(TreeGuidancePolicy.relativeBearing(10f, null, TreePositionStatus.FRESH, TreeOrientationStatus.READY))
        assertNull(TreeGuidancePolicy.relativeBearing(10f, 350f, TreePositionStatus.STALE, TreeOrientationStatus.READY))
        assertNull(TreeGuidancePolicy.relativeBearing(10f, 350f, TreePositionStatus.FRESH, TreeOrientationStatus.UNRELIABLE))
        // Compass status does not participate in the shared horizontal distance calculation.
        assertEquals(111.195, TreeGuidancePolicy.distanceM(0.0, 0.0, 0.001, 0.0) ?: 0.0, 0.01)
    }

    @Test fun `true bearing handles cardinal directions date line and coincident points`() {
        assertEquals(0f, TreeGuidancePolicy.trueBearingDeg(0.0, 0.0, 1.0, 0.0) ?: -1f, 0.01f)
        assertEquals(90f, TreeGuidancePolicy.trueBearingDeg(0.0, 0.0, 0.0, 1.0) ?: -1f, 0.01f)
        assertEquals(90f, TreeGuidancePolicy.trueBearingDeg(0.0, 179.9, 0.0, -179.9) ?: -1f, 0.01f)
        assertNull(TreeGuidancePolicy.trueBearingDeg(45.0, 5.0, 45.0, 5.0))
        assertNull(TreeGuidancePolicy.trueBearingDeg(91.0, 5.0, 45.0, 5.0))
        assertNull(TreeGuidancePolicy.distanceM(91.0, 5.0, 45.0, 5.0))
    }

    private fun position(fix: Long?) = TreeGuidancePolicy.positionStatus(now, fix, false, true, true)
    private fun heading(fix: Long?, available: Boolean, accuracy: Int?) = TreeGuidancePolicy.orientationStatus(now, fix, available, accuracy)
}
