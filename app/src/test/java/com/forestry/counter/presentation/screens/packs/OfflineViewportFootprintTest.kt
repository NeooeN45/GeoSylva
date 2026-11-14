package com.forestry.counter.presentation.screens.packs

import com.forestry.counter.presentation.screens.packs.offline.downloadableViewportRing
import com.quintessences.geosylva.cartography.mapbox.MapCoordinate
import org.junit.Assert.*
import org.junit.Test

class OfflineViewportFootprintTest {
    private fun ring(west: Double, east: Double, north: Double = 46.0) = listOf(
        MapCoordinate(west, north), MapCoordinate(east, north), MapCoordinate(east, 45.0),
        MapCoordinate(west, 45.0), MapCoordinate(west, north))
    @Test fun preserves_the_four_surface_rays_instead_of_an_axis_aligned_envelope() {
        val input = listOf(MapCoordinate(2.0,46.0),MapCoordinate(3.0,45.8),MapCoordinate(2.7,45.0),MapCoordinate(1.9,45.1),MapCoordinate(2.0,46.0))
        val output = requireNotNull(downloadableViewportRing(input))
        assertEquals(input.map { it.longitude to it.latitude }, output.map { it.longitude() to it.latitude() })
    }
    @Test fun refuses_sky_unclosed_degenerate_wrapped_or_outside_mercator_footprints() {
        assertNull(downloadableViewportRing(null))
        assertNull(downloadableViewportRing(ring(2.0,3.0).dropLast(1)))
        assertNull(downloadableViewportRing(ring(2.0,2.0)))
        assertNull(downloadableViewportRing(ring(179.0,-179.0)))
        assertNull(downloadableViewportRing(ring(-100.0,100.0)))
        assertNull(downloadableViewportRing(ring(2.0,3.0,86.0)))
    }
}
