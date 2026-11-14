package com.quintessences.geosylva.cartography.mapbox

import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import org.junit.Assert.*
import org.junit.Test

class MapLabelBadgeTest {
    @Test fun `default pill metrics stay unchanged and circles have a density scaled outline`() {
        assertEquals(MapOverlayLabelBadge.PILL,MapOverlayStyle().labelBadge)
        val pill = overlayLabelBadgePixels(8f,15f,1f,MapOverlayLabelBadge.PILL)
        assertEquals(22,pill.width); assertEquals(21,pill.height); assertEquals(0f,pill.outlineWidth,0f)
        val circle = overlayLabelBadgePixels(8f,15f,1f,MapOverlayLabelBadge.CIRCLE)
        assertEquals(25,circle.width); assertEquals(circle.width,circle.height); assertEquals(2f,circle.outlineWidth,0f)
        val scaled = overlayLabelBadgePixels(28f,52.5f,3.5f,MapOverlayLabelBadge.CIRCLE)
        assertEquals(88,scaled.width); assertEquals(scaled.width,scaled.height); assertEquals(7f,scaled.outlineWidth,0f)
    }
    @Test fun `circular long labels are admitted with their square physical RGBA bytes before allocation`() {
        val dimensions = overlayLabelBadgePixels(384f*3,30f*3,3f,MapOverlayLabelBadge.CIRCLE)
        val bytes = requireNotNull(rgbaImageBytes(dimensions.width,dimensions.height))
        assertTrue(bytes > 4L*1024*1024)
        val budget = OverlayRasterBudget<String>()
        assertFalse(budget.reserve("TEST-circle",bytes)); assertEquals(0,budget.bytes)
        val features = (0 until 7999).map { index -> MapOverlayFeature(MapFeatureKey("TEST","$index"),
            Point.fromLngLat(2.0,45.0),MapOverlayStyle(labelBadge = MapOverlayLabelBadge.CIRCLE),label = "TEST-long") }
        assertEquals(7999,requireNotNull(overlayFeatureCollection(MapOverlayScene("TEST",1,features)).features()).size)
    }
    @Test fun `pin selection scales its icon around the same anchored coordinate and line casing stays visual`() {
        val point = Point.fromLngLat(2.0,45.0)
        val pin = MapOverlayFeature(MapFeatureKey("TEST","pin"),point,MapOverlayStyle(pointSymbol = MapOverlayPointSymbol.PIN,opacity = .4))
        val line = MapOverlayFeature(MapFeatureKey("TEST","line"),LineString.fromLngLats(listOf(point,Point.fromLngLat(2.1,45.1))),
            MapOverlayStyle(strokeWidth = 2.0))
        val payload = requireNotNull(overlayFeatureCollection(MapOverlayScene("TEST",1,listOf(pin,line),pin.key)).features())
        assertEquals(1.2,payload[0].getNumberProperty("gs_pin_scale").toDouble(),0.0)
        assertEquals(.4,payload[0].getNumberProperty("gs_opacity").toDouble(),0.0)
        assertEquals(point,payload[0].geometry()); assertEquals(line.geometry,payload[1].geometry())
        assertEquals(7.0,payload[1].getNumberProperty("gs_casing_width").toDouble(),0.0)
        val ordinary = requireNotNull(overlayFeatureCollection(MapOverlayScene("TEST",2,listOf(pin))).features()).single()
        assertEquals(1.0,ordinary.getNumberProperty("gs_pin_scale").toDouble(),0.0)
    }
}
