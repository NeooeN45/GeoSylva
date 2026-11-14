package com.quintessences.geosylva.cartography.mapbox

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class MapReticleProjectionTest {
    @Test fun viewport_pixel_is_preserved_even_when_not_at_camera_centre() {
        val pixel = requireNotNull(reticlePixelInsideView(Offset(130.5f, 217.25f), 720, 1560))
        assertEquals(130.5, pixel.x, 0.0)
        assertEquals(217.25, pixel.y, 0.0)
    }
    @Test fun absent_unlaid_out_and_outside_viewport_have_no_coordinate() {
        assertNull(reticlePixelInsideView(null, 720, 1560))
        assertNull(reticlePixelInsideView(Offset.Zero, 0, 1560))
        for (offset in listOf(Offset(-1f, 0f), Offset(0f, -1f), Offset(720f, 0f),
            Offset(0f, 1560f), Offset(Float.NaN, 1f), Offset(1f, Float.POSITIVE_INFINITY))) {
            assertNull(reticlePixelInsideView(offset, 720, 1560))
        }
    }
}
