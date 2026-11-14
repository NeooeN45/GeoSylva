package com.forestry.counter.presentation.screens.forestry.layers

import com.forestry.counter.domain.model.cartography.LayerGeometryFamily
import com.forestry.counter.domain.model.cartography.LayerStyle
import com.mapbox.geojson.Point
import com.quintessences.geosylva.cartography.mapbox.MapFeatureKey
import com.quintessences.geosylva.cartography.mapbox.MapOverlayFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayerSelectionProjectionTest {
    private fun layer(id: String = "L1", visible: Boolean = true) = LayerRenderSnapshot(
        id, """{"type":"FeatureCollection","features":[]}""", LayerStyle(visible = visible),
        LayerGeometryFamily.POINT, "f-1", preview = false,
    )
    private fun feature(layer: String = "L1") = MapOverlayFeature(
        MapFeatureKey("imported-layer:$layer", "f-1"), Point.fromLngLat(2.0, 47.0),
    )

    @Test fun selectedObjectIsResolvedInTheDrawnScene() {
        assertEquals(feature().key, selectedLayerMapKey(listOf(layer()), listOf(feature())))
    }

    @Test fun filteredOrUnlocatedSelectionCannotHighlightAnAbsentFeature() {
        assertNull(selectedLayerMapKey(listOf(layer()), emptyList()))
    }

    @Test fun identicalFeatureIdsInOtherLayersCannotAdoptTheSelection() {
        assertNull(selectedLayerMapKey(listOf(layer()), listOf(feature("L2"))))
    }

    @Test fun hiddenLayerCannotRetainSelectionEvenDuringSceneReplacement() {
        assertNull(selectedLayerMapKey(listOf(layer(visible = false)), listOf(feature())))
    }
}
