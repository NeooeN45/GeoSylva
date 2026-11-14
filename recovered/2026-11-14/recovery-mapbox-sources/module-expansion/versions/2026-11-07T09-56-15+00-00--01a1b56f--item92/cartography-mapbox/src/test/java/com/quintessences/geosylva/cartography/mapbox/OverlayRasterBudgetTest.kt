package com.quintessences.geosylva.cartography.mapbox

import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import org.junit.Assert.*
import org.junit.Test

class OverlayRasterBudgetTest {
    @Test fun `7999 long labels preserve every geometry and budget the selected label first`() {
        val features = (0 until 7999).map { index -> MapOverlayFeature(MapFeatureKey("TEST-import","f-$index"),
            Point.fromLngLat(2.0,45.0),label = "W".repeat(95)+index) }
        val selected = features.last().key
        val scene = MapOverlayScene("TEST",1,features,selected)
        val budget = OverlayRasterBudget<MapFeatureKey>(maximumImages = 256,maximumBytes = 4L*1024*1024)
        val bytes = requireNotNull(rgbaImageBytes(1194,63)) // Physical dimensions at density 3, not dp.
        val images = imagePriority(scene).mapNotNull { feature -> if (budget.reserve(feature.key,bytes)) feature.key to "TEST-image" else null }.toMap()
        assertTrue(selected in images)
        assertTrue(images.size < 256)
        assertTrue(budget.bytes <= 4L*1024*1024)
        val rendered = requireNotNull(overlayFeatureCollection(scene,images).features())
        assertEquals(7999,rendered.size)
        assertEquals(features.map { it.key.featureId },rendered.map { it.getStringProperty(OVERLAY_FEATURE_PROPERTY) })
        assertTrue(rendered.all { it.geometry() == Point.fromLngLat(2.0,45.0) })
    }
    @Test fun `sprite count bounds tiny labels and already accepted sprites remain shareable after closure`() {
        val budget = OverlayRasterBudget<String>(maximumImages = 256,maximumBytes = 4L*1024*1024)
        assertEquals(256,(0 until 7999).count { budget.reserve("TEST-$it",64) })
        assertFalse(budget.canPrepareNewImage)
        assertTrue(budget.reserve("TEST-0",64))
        assertFalse(budget.reserve("TEST-new",64))
        assertEquals(256L*64,budget.bytes)
    }
    @Test fun `byte budget rejects oversized images before allocation and invalid dimensions cannot overflow`() {
        val budget = OverlayRasterBudget<String>()
        assertFalse(budget.reserve("TEST-too-large",4L*1024*1024+1))
        assertEquals(0,budget.bytes)
        assertNull(rgbaImageBytes(0,100)); assertNull(rgbaImageBytes(-1,100))
        assertNull(rgbaImageBytes(Int.MAX_VALUE,Int.MAX_VALUE))
        assertEquals(16L*16*4,rgbaImageBytes(16,16))
    }
    @Test fun `hatch repeats use bounded power of two physical tiles and do not change solid fill payloads`() {
        for (density in listOf(0.75f,1f,1.75f,2f,2.75f,3.5f,4f)) {
            val size = requireNotNull(hatchTilePixels(density))
            assertTrue(size in listOf(16,32)); assertEquals(0,size and (size-1))
        }
        assertNull(hatchTilePixels(Float.NaN)); assertNull(hatchTilePixels(0f))
        val a = Point.fromLngLat(2.0,45.0); val b = Point.fromLngLat(2.1,45.1); val c = Point.fromLngLat(2.0,45.1)
        val polygon = Polygon.fromLngLats(listOf(listOf(a,b,c,a)))
        val solid = MapOverlayFeature(MapFeatureKey("TEST","solid"),polygon,MapOverlayStyle(colorHex = "#6560A5",fillOpacity = .30,opacity = .5))
        val hatch = solid.copy(key = MapFeatureKey("TEST","hatch"),style = solid.style.copy(fillPattern = MapOverlayFillPattern.HATCH))
        val scene = MapOverlayScene("TEST",1,listOf(solid,hatch))
        val payload = requireNotNull(overlayFeatureCollection(scene,patternImages = mapOf(hatch.key to "TEST-hatch-image")).features())
        assertFalse(payload[0].hasProperty("gs_fill_pattern")); assertFalse(payload[0].hasProperty("gs_fill_pattern_image"))
        assertEquals("HATCH",payload[1].getStringProperty("gs_fill_pattern"))
        assertEquals("TEST-hatch-image",payload[1].getStringProperty("gs_fill_pattern_image"))
        assertEquals(.15,payload[0].getNumberProperty("gs_fill_opacity").toDouble(),1e-9)
        assertEquals(.15,payload[1].getNumberProperty("gs_fill_opacity").toDouble(),1e-9)
        assertEquals(polygon,payload[0].geometry()); assertEquals(polygon,payload[1].geometry())
        // If a texture cannot be admitted, this feature still has its full geometry and ordinary fill.
        val fallback = requireNotNull(overlayFeatureCollection(scene).features())[1]
        assertFalse(fallback.hasProperty("gs_fill_pattern")); assertEquals(polygon,fallback.geometry())
    }
}
