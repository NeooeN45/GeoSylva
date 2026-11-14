package com.quintessences.geosylva.cartography.mapbox

import com.mapbox.geojson.Point
import org.junit.Assert.*
import org.junit.Test

class MapCheckedPinContractTest {
    @Test fun `arrival decoration preserves coordinate identity source generation selection and nonselectable status`() {
        val point = Point.fromLngLat(5.5517,45.0718)
        val key = MapFeatureKey("maps-destination","destination")
        val ordinary = MapOverlayFeature(key,point,
            MapOverlayStyle(colorHex="#0964DC",pointRadius=10.0,pointSymbol=MapOverlayPointSymbol.PIN),
            label="Destination TEST",selectable=false)
        val checked = ordinary.copy(style=ordinary.style.copy(pointSymbol=MapOverlayPointSymbol.PIN_CHECKED))
        val scene = MapOverlayScene("TEST-arrival",8,listOf(checked),key)
        val result = requireNotNull(overlayFeatureCollection(scene,
            pinImages=mapOf(key to "TEST-checked-sprite"),dataId="TEST-native-generation",renderDensity=2f).features()).single()
        assertEquals(ordinary.geometry,result.geometry())
        assertEquals(key.layerId,result.getStringProperty(OVERLAY_LAYER_PROPERTY))
        assertEquals(key.featureId,result.getStringProperty(OVERLAY_FEATURE_PROPERTY))
        assertEquals("8",result.getStringProperty(MAP_OVERLAY_SCENE_REVISION_PROPERTY))
        assertEquals("TEST-native-generation",result.getStringProperty(MAP_OVERLAY_DATA_ID_PROPERTY))
        assertFalse(result.getBooleanProperty("gs_selectable"))
        assertEquals("Destination TEST",result.getStringProperty(MAP_OVERLAY_LABEL_PROPERTY))
        assertEquals("PIN_CHECKED",result.getStringProperty("gs_point_symbol"))
        assertEquals("TEST-checked-sprite",result.getStringProperty("gs_pin_image"))
        assertEquals(1.2,result.getNumberProperty("gs_pin_scale").toDouble(),0.0)
        assertEquals("bottom",result.getStringProperty("gs_label_point_anchor"))
        // The checked pin clears the same complete sprite height as its undecorated counterpart.
        val ordinaryPayload=requireNotNull(overlayFeatureCollection(MapOverlayScene("TEST-arrival",7,listOf(ordinary),key),
            pinImages=mapOf(key to "TEST-ordinary-sprite"),renderDensity=2f).features()).single()
        assertEquals(ordinaryPayload.getProperty("gs_label_point_offset"),result.getProperty("gs_label_point_offset"))
    }
}
