package com.quintessences.geosylva.cartography.mapbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapResourceCycleTest {
    @Test fun `fast local style completion survives later terrain readback confirming no change`() {
        val generation = Any()
        val cycle = MapResourceCycle()
        cycle.beginStyleLoad(generation, 100)
        // The style is already flat. Do not move the barrier past its real queued Idle event.
        cycle.recordTerrainApplication(generation, completedAtMillis = 150, changed = false)
        assertTrue(cycle.acceptNativeResourceEvent(generation, occurredAtMillis = 120))
        assertTrue(cycle.isComplete(generation))
    }

    @Test fun `real terrain changes invalidate previous completion and reject older queued events`() {
        val generation = Any()
        val cycle = MapResourceCycle()
        cycle.beginStyleLoad(generation, 100)
        assertTrue(cycle.acceptNativeResourceEvent(generation, 120))
        cycle.recordTerrainApplication(generation, completedAtMillis = 200, changed = true)
        assertFalse(cycle.isComplete(generation))
        assertFalse(cycle.acceptNativeResourceEvent(generation, 199))
        assertFalse(cycle.acceptNativeResourceEvent(generation, 200))
        assertTrue(cycle.acceptNativeResourceEvent(generation, 201))
    }

    @Test fun `a prior generation cannot complete or reset the current generation`() {
        val first = Any()
        val second = Any()
        val cycle = MapResourceCycle()
        cycle.beginStyleLoad(first, 100)
        assertTrue(cycle.acceptNativeResourceEvent(first, 120))
        cycle.beginStyleLoad(second, 200)
        assertFalse(cycle.isComplete(second))
        assertFalse(cycle.acceptNativeResourceEvent(first, 1_000))
        cycle.recordTerrainApplication(first, 2_000, changed = true)
        assertTrue(cycle.acceptNativeResourceEvent(second, 210))
        assertFalse(cycle.isComplete(first))
    }

    @Test fun `no style parse or no-op can complete a cycle without native resource evidence`() {
        val generation = Any()
        val cycle = MapResourceCycle()
        cycle.beginStyleLoad(generation, 100)
        cycle.recordTerrainApplication(generation, 150, changed = false)
        assertFalse(cycle.isComplete(generation))
        assertFalse(cycle.acceptNativeResourceEvent(generation, 90))
        assertFalse(cycle.isComplete(generation))
    }
}
