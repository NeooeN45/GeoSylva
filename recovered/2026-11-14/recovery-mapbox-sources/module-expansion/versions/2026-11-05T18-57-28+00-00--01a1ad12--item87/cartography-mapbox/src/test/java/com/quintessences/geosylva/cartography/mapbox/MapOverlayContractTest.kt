package com.quintessences.geosylva.cartography.mapbox

import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.MultiLineString
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapOverlayContractTest {
    private val key = MapFeatureKey("trees", "tree-stable-id")
    private val feature = MapOverlayFeature(key, Point.fromLngLat(2.4, 46.6))

    @Test fun `a delayed hit is rejected after snapshot or authorization scope replacement`() {
        val queried = MapOverlayScene("account-a", 1, listOf(feature))
        assertEquals(key, resolveOverlayHit(queried, queried, key))
        assertNull(resolveOverlayHit(queried, MapOverlayScene("account-b", 1, listOf(feature)), key))
        assertNull(resolveOverlayHit(queried, MapOverlayScene("account-a", 2, emptyList()), key))
        // A host accidentally reusing its revision cannot make an old query valid.
        assertNull(resolveOverlayHit(queried, MapOverlayScene("account-a", 1, listOf(feature)), key))
    }

    @Test fun `display labels cannot become selectable object identities`() {
        val label = feature.copy(key = MapFeatureKey("labels", "same-display-name"), label = "Oak", selectable = false,
            style = MapOverlayStyle(pointRadius = 0.0))
        val scene = MapOverlayScene("scope", 0, listOf(label))
        assertNull(resolveOverlayHit(scene, scene, label.key))
        assertNull(resolveOverlayHit(scene, scene, MapFeatureKey("trees", "Oak")))
        val payload = overlayFeatureCollection(scene).features()!!.single()
        assertEquals(0, payload.getNumberProperty("gs_point_stroke").toInt())
        assertEquals(0.0, payload.getNumberProperty("gs_radius").toDouble(), 0.0)
    }

    @Test fun `a multipart trace stays separated instead of bridging a GPS interruption`() {
        val first = listOf(Point.fromLngLat(2.0, 46.0), Point.fromLngLat(2.1, 46.1))
        val second = listOf(Point.fromLngLat(2.5, 46.5), Point.fromLngLat(2.6, 46.6))
        val trace = MultiLineString.fromLngLats(listOf(first, second))
        val scene = MapOverlayScene("scope", 1, listOf(feature.copy(geometry = trace)))
        val roundTrip = FeatureCollection.fromJson(overlayFeatureCollection(scene).toJson()).features()!!.single()
        assertEquals(listOf(first, second), (roundTrip.geometry() as MultiLineString).coordinates())
        assertEquals(2, (roundTrip.geometry() as MultiLineString).coordinates().size)
    }

    @Test fun `polygon holes are retained and not replaced by bounding boxes`() {
        val outer = listOf(Point.fromLngLat(1.0, 45.0), Point.fromLngLat(3.0, 45.0),
            Point.fromLngLat(3.0, 47.0), Point.fromLngLat(1.0, 45.0))
        val inner = listOf(Point.fromLngLat(1.5, 45.5), Point.fromLngLat(1.7, 45.5),
            Point.fromLngLat(1.7, 45.7), Point.fromLngLat(1.5, 45.5))
        val polygon = Polygon.fromLngLats(listOf(outer, inner))
        assertTrue(validOverlayGeometry(polygon))
        val scene = MapOverlayScene("scope", 0, listOf(feature.copy(geometry = polygon)))
        assertEquals(listOf(outer, inner),
            (overlayFeatureCollection(scene).features()!!.single().geometry() as Polygon).coordinates())
    }

    @Test fun `invalid coordinates and open polygon rings are rejected before SDK submission`() {
        assertFalse(validOverlayGeometry(Point.fromLngLat(200.0, 46.0)))
        assertFalse(validOverlayGeometry(Point.fromLngLat(2.0, Double.NaN)))
        assertFalse(validOverlayGeometry(Polygon.fromLngLats(listOf(listOf(
            Point.fromLngLat(1.0, 45.0), Point.fromLngLat(2.0, 45.0),
            Point.fromLngLat(2.0, 46.0), Point.fromLngLat(1.0, 46.0))))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate business identities are never silently collapsed`() {
        MapOverlayScene("scope", 1, listOf(feature, feature.copy(label = "Different label")))
    }

    @Test fun `source identities survive escaping and properties do not include arbitrary business payload`() {
        val quoted = MapFeatureKey("layer\"\\", "tree\"\\\n")
        val scene = MapOverlayScene("scope", 0, listOf(feature.copy(key = quoted)))
        val stored = FeatureCollection.fromJson(overlayFeatureCollection(scene).toJson()).features()!!.single()
        assertEquals(quoted.layerId, stored.getStringProperty(OVERLAY_LAYER_PROPERTY))
        assertEquals(quoted.featureId, stored.getStringProperty(OVERLAY_FEATURE_PROPERTY))
        assertFalse(stored.hasProperty("scopeId"))
    }

    @Test fun `camera bounds permit antimeridian wrapping without swapping longitude and latitude`() {
        val bounds = MapBounds(175.0, -10.0, -175.0, 10.0)
        assertEquals(175.0, bounds.west, 0.0)
        assertEquals(-175.0, bounds.east, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `nonfinite camera padding cannot enter native fit operation`() {
        MapCameraFitCommand("fit", listOf(MapCoordinate(2.0, 46.0)), paddingTopPx = Double.NaN)
    }

    @Test fun `inline local raster style has no URI but remains distinct from remote styles`() {
        val inline = MapboxStyleSpec("offline", "{\"version\":8,\"sources\":{},\"layers\":[]}", demSourceUrl = null)
        assertTrue(inline.isInlineJson)
        assertTrue(inline.matchesNativeStyleUri(""))
        assertFalse(inline.matchesNativeStyleUri(MapboxStyles.Satellite.uri))
        assertTrue(MapboxStyles.Satellite.matchesNativeStyleUri(MapboxStyles.Satellite.uri))
        assertFalse(MapboxStyles.Satellite.matchesNativeStyleUri(""))
    }
}
