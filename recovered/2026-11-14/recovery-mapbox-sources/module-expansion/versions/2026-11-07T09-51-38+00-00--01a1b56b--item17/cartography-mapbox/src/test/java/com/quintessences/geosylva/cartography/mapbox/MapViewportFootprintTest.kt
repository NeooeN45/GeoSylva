package com.quintessences.geosylva.cartography.mapbox

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mapbox.maps.ScreenCoordinate
import org.junit.Assert.*
import org.junit.Test

class MapViewportFootprintTest {
    @Test fun `exposed corners exclude the covered bottom and use actual physical view pixels`() {
        val corners = requireNotNull(visibleViewportCorners(720, 1560, ViewportInsetsPx(143.0,21.0,777.0,35.0)))
        assertEquals(listOf(21.0 to 143.0,685.0 to 143.0,685.0 to 783.0,21.0 to 783.0), corners.map { it.x to it.y })
    }
    @Test fun `logical start and end resolve in either direction before converting dp to pixels`() {
        val padding = PaddingValues(start = 10.dp, top = 20.dp, end = 30.dp, bottom = 40.dp)
        assertEquals(ViewportInsetsPx(40.0,20.0,80.0,60.0), viewportInsetsPx(padding,2f,LayoutDirection.Ltr))
        assertEquals(ViewportInsetsPx(40.0,60.0,80.0,20.0), viewportInsetsPx(padding,2f,LayoutDirection.Rtl))
        assertEquals(0.875, viewportInsetsPx(PaddingValues(0.5.dp),1.75f,LayoutDirection.Ltr).left,0.0)
    }
    @Test fun `unlaid out nonfinite negative or fully occluded view has no corners`() {
        val zero = ViewportInsetsPx(0.0,0.0,0.0,0.0)
        assertNull(visibleViewportCorners(0,1560,zero)); assertNull(visibleViewportCorners(720,0,zero))
        for (insets in listOf(ViewportInsetsPx(1.0,360.0,1.0,360.0), ViewportInsetsPx(1000.0,0.0,560.0,0.0),
            ViewportInsetsPx(0.0,900.0,0.0,0.0), ViewportInsetsPx(-1.0,0.0,0.0,0.0),
            ViewportInsetsPx(Double.NaN,0.0,0.0,0.0), ViewportInsetsPx(0.0,0.0,Double.POSITIVE_INFINITY,0.0))) {
            assertNull(visibleViewportCorners(720,1560,insets))
        }
    }
    @Test fun `zero insets keep corners strictly inside the native view without a full pixel shift`() {
        val corners = requireNotNull(visibleViewportCorners(720,1560,ViewportInsetsPx(0.0,0.0,0.0,0.0)))
        assertEquals(0.0,corners.first().x,0.0); assertEquals(0.0,corners.first().y,0.0)
        assertTrue(corners.all { it.x >= 0 && it.x < 720 && it.y >= 0 && it.y < 1560 })
        assertEquals(720.0,corners[1].x,1e-10); assertEquals(1560.0,corners[2].y,1e-10)
    }
    @Test fun `all four native hits are preserved in order and the ring closes exactly once`() {
        val pixels = listOf(ScreenCoordinate(0.0,0.0),ScreenCoordinate(1.0,0.0),ScreenCoordinate(1.0,1.0),ScreenCoordinate(0.0,1.0))
        val ring = requireNotNull(projectVisibleFootprint(pixels) { MapCoordinate(2.0 + it.x,45.0 + it.y) })
        assertEquals(listOf(MapCoordinate(2.0,45.0),MapCoordinate(3.0,45.0),MapCoordinate(3.0,46.0),MapCoordinate(2.0,46.0),MapCoordinate(2.0,45.0)),ring)
        assertNull(projectVisibleFootprint(pixels) { if (it.x == 1.0) null else MapCoordinate(2.0,45.0) })
        assertNull(projectVisibleFootprint(pixels) { throw IllegalStateException("TEST unavailable surface ray") })
        assertNull(projectVisibleFootprint(pixels) { MapCoordinate(2.0,45.0) })
    }
    @Test fun `existing two argument viewport constructors explicitly have no visible footprint`() {
        assertNull(MapViewport(MapBounds(1.0,44.0,3.0,46.0),MapCameraPosition(2.0,45.0,10.0)).visibleFootprint)
    }
}
