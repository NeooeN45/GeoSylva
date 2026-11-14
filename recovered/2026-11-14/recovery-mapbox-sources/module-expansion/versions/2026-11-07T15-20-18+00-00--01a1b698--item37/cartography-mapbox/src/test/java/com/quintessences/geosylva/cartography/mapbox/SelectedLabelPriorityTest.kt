package com.quintessences.geosylva.cartography.mapbox

import com.mapbox.geojson.Point
import org.junit.Assert.*
import org.junit.Test

class SelectedLabelPriorityTest {
    @Test fun `collision priority follows the complete opaque selection key even when source labels and feature ids repeat`() {
        val point=Point.fromLngLat(5.4684,45.0541)
        val features=listOf(
            MapOverlayFeature(MapFeatureKey("TEST-layer-a","same-id"),point,label="P-03"),
            MapOverlayFeature(MapFeatureKey("TEST-layer-b","same-id"),point,label="P-03"),
            MapOverlayFeature(MapFeatureKey("TEST-layer-b","other-id"),point,label="P-03"))
        val images=features.associate { it.key to "TEST-shared-label-image" }
        fun priorities(selected: MapFeatureKey?): List<Double> {
            val payload=requireNotNull(overlayFeatureCollection(MapOverlayScene("TEST",1,features,selected),images).features())
            assertEquals(features.map { it.key.layerId },payload.map { it.getStringProperty(OVERLAY_LAYER_PROPERTY) })
            assertEquals(features.map { it.key.featureId },payload.map { it.getStringProperty(OVERLAY_FEATURE_PROPERTY) })
            assertTrue(payload.all { it.geometry() == point && it.getStringProperty(MAP_OVERLAY_LABEL_PROPERTY) == "P-03" })
            assertTrue(payload.all { it.getStringProperty("gs_label_image") == "TEST-shared-label-image" })
            return payload.map { it.getNumberProperty("gs_label_priority").toDouble() }
        }
        assertEquals(listOf(1.0,0.0,1.0),priorities(features[1].key))
        assertEquals(listOf(0.0,1.0,1.0),priorities(features[0].key))
        assertEquals(listOf(1.0,1.0,1.0),priorities(null))
    }
}
