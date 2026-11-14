package com.forestry.counter.presentation.screens.forestry

import com.forestry.counter.domain.location.ParcelCoordinate
import com.forestry.counter.domain.location.ParcelGeometry
import com.forestry.counter.domain.location.SamplingFootprint
import com.forestry.counter.domain.location.PlotBoundaryDifference
import com.forestry.counter.domain.location.PlotOutsidePreview
import com.quintessences.geosylva.cartography.mapbox.MapOverlayFillPattern
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeographicSelectionFeaturesTest {
    @Test fun `only the selected candidate outside difference receives a hatch pattern`() {
        val parent = ParcelGeometry.fromWkt("POLYGON ((0 0,10 0,10 10,0 10,0 0))")
        val footprint = ParcelGeometry.fromWkt("POLYGON ((8 2,12 2,12 6,8 6,8 2))")
        val outside = (PlotBoundaryDifference.outside(parent, footprint) as PlotOutsidePreview.Available).geometry
        val center = ParcelCoordinate(9.0, 4.0)
        val result = geographicSelectionFeatures(collection(), null, listOf(center), parent,
            listOf(SamplingFootprint(center, footprint, true)), emptyList(), listOf("candidate-edge"), "candidate-edge", outside)
        val hatch = result.single { it.style.fillPattern == MapOverlayFillPattern.HATCH }
        assertEquals("selection-plot-outside", hatch.key.layerId)
        assertEquals("candidate-edge", hatch.key.featureId)
        assertEquals(outside.toGeoJson(), hatch.geometry.toJson())
        assertFalse(hatch.selectable)
        assertEquals(footprint.toGeoJson(), result.single { it.key.layerId == "selection-plot-footprints" }.geometry.toJson())
    }
    @Test
    fun `parcel identity survives selection and label anchor avoids a hole`() {
        val id = "parcelle-\"nord\""
        val polygon = polygonWithHole()
        val feature = Feature.fromGeometry(polygon).apply {
            addStringProperty("parcel_id", id)
            addStringProperty("parcel_label", "Parcelle nord")
        }
        val result = geographicSelectionFeatures(collection(feature), id, emptyList(), null, emptyList(), emptyList())
        val parcel = result.single { it.key.layerId == "selection-parcels" }
        assertEquals(id, parcel.key.featureId)
        assertEquals("#117B43", parcel.style.colorHex)
        assertEquals(polygon.toJson(), parcel.geometry.toJson())
        val label = result.single { it.key.layerId == "selection-parcel-labels" }
        val anchor = label.geometry as Point
        val domain = ParcelGeometry.fromGeoJson(polygon.toJson())
        assertTrue(domain.contains(ParcelCoordinate(anchor.longitude(), anchor.latitude())))
        assertFalse(label.selectable)
        assertEquals(0.0, label.style.pointRadius, 0.0)
        assertEquals("Parcelle nord", label.label)
    }

    @Test
    fun `duplicate parcel identity fails instead of selecting a different feature`() {
        val first = Feature.fromGeometry(polygonWithHole()).apply { addStringProperty("parcel_id", "same-id") }
        val second = Feature.fromGeometry(polygonWithHole()).apply { addStringProperty("parcel_id", "same-id") }
        assertThrows(IllegalArgumentException::class.java) {
            geographicSelectionFeatures(collection(first, second), "same-id", emptyList(), null, emptyList(), emptyList())
        }
    }

    @Test
    fun `projected coordinates are rejected without a fabricated WGS84 rectangle`() {
        val polygon = Polygon.fromLngLats(listOf(listOf(
            Point.fromLngLat(700000.0, 6600000.0), Point.fromLngLat(700100.0, 6600000.0),
            Point.fromLngLat(700100.0, 6600100.0), Point.fromLngLat(700000.0, 6600000.0),
        )))
        val feature = Feature.fromGeometry(polygon).apply { addStringProperty("parcel_id", "projected") }
        assertThrows(IllegalArgumentException::class.java) {
            geographicSelectionFeatures(collection(feature), null, emptyList(), null, emptyList(), emptyList())
        }
    }

    @Test
    fun `explicit projected CRS is rejected even if numeric values look geographic`() {
        val feature = Feature.fromGeometry(polygonWithHole()).apply { addStringProperty("parcel_id", "declared-projected") }
        val raw = collection(feature).replaceFirst("{", "{\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:2154\"}},")
        assertThrows(IllegalArgumentException::class.java) {
            geographicSelectionFeatures(raw, null, emptyList(), null, emptyList(), emptyList())
        }
    }

    @Test
    fun `planning keeps footprint warning grid and original centre without persisted identities`() {
        val geometry = ParcelGeometry.fromGeoJson(polygonWithHole().toJson())
        val centre = ParcelCoordinate(2.01, 46.01)
        val result = geographicSelectionFeatures(
            collection(), null, listOf(centre), geometry,
            listOf(SamplingFootprint(centre, geometry, crossesBoundary = true)), listOf(geometry),
        )
        val footprint = result.single { it.key.layerId == "selection-plot-footprints" }
        assertEquals("#6560A5", footprint.style.colorHex)
        assertEquals(geometry.toGeoJson(), footprint.geometry.toJson())
        val grid = result.single { it.key.layerId == "selection-grid-cells" }
        assertEquals(listOf(3.0, 2.0), grid.style.lineDashArray)
        val point = result.single { it.key.layerId == "selection-plot-points" }
        val marker = point.geometry as Point
        assertEquals(centre.longitude, marker.longitude(), 0.0)
        assertEquals(centre.latitude, marker.latitude(), 0.0)
        assertEquals("1", point.label)
        assertFalse(point.selectable)
    }

    @Test fun `planning selection uses stable candidate keys while labels follow display order`() {
        val first = ParcelCoordinate(2.01, 46.01)
        val retained = ParcelCoordinate(2.02, 46.02)
        val before = geographicSelectionFeatures(collection(), null, listOf(first, retained), null,
            emptyList(), emptyList(), listOf("candidate-a", "candidate-b"), "candidate-b")
        val after = geographicSelectionFeatures(collection(), null, listOf(retained), null,
            emptyList(), emptyList(), listOf("candidate-b"), "candidate-b")
        val original = before.single { it.key.featureId == "candidate-b" }
        val selected = after.single()
        assertEquals(original.key, selected.key)
        assertEquals(original.geometry, selected.geometry)
        assertTrue(selected.selectable)
        assertEquals("1", selected.label)
        assertEquals(10.0, selected.style.pointRadius, 0.0)
        assertThrows(IllegalArgumentException::class.java) {
            geographicSelectionFeatures(collection(), null, listOf(first, retained), null,
                emptyList(), emptyList(), listOf("same", "same"))
        }
    }

    private fun collection(vararg features: Feature): String = FeatureCollection.fromFeatures(features.toList()).toJson()

    private fun polygonWithHole(): Polygon = Polygon.fromLngLats(listOf(
        listOf(Point.fromLngLat(2.0, 46.0), Point.fromLngLat(2.1, 46.0), Point.fromLngLat(2.1, 46.1),
            Point.fromLngLat(2.0, 46.1), Point.fromLngLat(2.0, 46.0)),
        listOf(Point.fromLngLat(2.04, 46.04), Point.fromLngLat(2.04, 46.06),
            Point.fromLngLat(2.06, 46.06), Point.fromLngLat(2.06, 46.04), Point.fromLngLat(2.04, 46.04)),
    ))
}
