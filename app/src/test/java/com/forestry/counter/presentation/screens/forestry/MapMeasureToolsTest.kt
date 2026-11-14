package com.forestry.counter.presentation.screens.forestry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protect SDK migration against latitude/longitude inversion and accidental line closure. */
class MapMeasureToolsTest {
    @Test
    fun `compatibility wrappers keep empty zero but never hide invalid geometry behind zero`() {
        assertEquals(0.0, measurePolylineM(emptyList()), 0.0)
        assertEquals(0.0, measureAreaM2(emptyList()), 0.0)
        assertTrue(measurePolylineM(listOf(MeasurePoint(0.0, 0.0))).isNaN())
        assertTrue(measurePolylineM(listOf(MeasurePoint(0.0, 0.0), MeasurePoint(0.0, 0.0))).isNaN())
        assertTrue(measureAreaM2(listOf(MeasurePoint(0.0, 0.0), MeasurePoint(0.001, 0.001),
            MeasurePoint(0.0, 0.001), MeasurePoint(0.001, 0.0))).isNaN())
    }

    @Test
    fun `two manual vertices measure an open line without a return leg`() {
        val result = measurePolylineM(listOf(MeasurePoint(0.0, 0.0), MeasurePoint(0.0, 0.001)))
        assertEquals(111.195, result, 0.01)
    }

    @Test
    fun `latitude then longitude constructor preserves a short French latitude itinerary`() {
        val points = listOf(MeasurePoint(48.0, 2.0), MeasurePoint(48.0, 2.001), MeasurePoint(48.001, 2.001))
        // At48°N a0.001° eastward leg is about74m, followed by111m northwards.
        // Swapping coordinates would give about222m; closure would exceed300m.
        assertTrue(measurePolylineM(points) in 185.0..186.0)
    }

    @Test
    fun `existing local area remains invariant to polygon traversal direction`() {
        val square = listOf(MeasurePoint(0.0, 0.0), MeasurePoint(0.0, 0.001),
            MeasurePoint(0.001, 0.001), MeasurePoint(0.001, 0.0))
        assertEquals(12_364.31, measureAreaM2(square), 0.1)
        assertEquals(measureAreaM2(square), measureAreaM2(square.reversed()), 0.01)
    }
}
