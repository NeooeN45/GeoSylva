package com.quintessences.geosylva.cartography.mapbox

import com.mapbox.geojson.Geometry
import com.mapbox.geojson.GeometryCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.MultiPoint
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import org.junit.Assert.*
import org.junit.Test

class MapPinGeometryTest {
    private val a = Point.fromLngLat(2.0,45.0)
    private val b = Point.fromLngLat(2.1,45.1)
    private val c = Point.fromLngLat(2.0,45.1)
    private val style = MapOverlayStyle(pointSymbol = MapOverlayPointSymbol.PIN)
    @Test fun `point preset does not add pin sprites to line and area objects in a mixed import`() {
        val line = LineString.fromLngLats(listOf(a,b))
        val polygon = Polygon.fromLngLats(listOf(listOf(a,b,c,a)))
        val features = listOf<Geometry>(a,line,polygon).mapIndexed { index, geometry -> MapOverlayFeature(MapFeatureKey("TEST-mixed","f-$index"),geometry,style) }
        // Both pin decorations remain point-only; neither can turn a line/area into a marker.
        for (symbol in listOf(MapOverlayPointSymbol.PIN, MapOverlayPointSymbol.PIN_CHECKED)) {
            val decorated = features.map { it.copy(style = style.copy(pointSymbol = symbol)) }
            assertEquals(listOf(decorated.first()),decorated.filter(::needsPinSprite))
        }
        assertFalse(needsPinSprite(features.first().copy(style = style.copy(pointRadius = 0.0))))
        assertFalse(needsPinSprite(features.first().copy(style = style.copy(pointSymbol = MapOverlayPointSymbol.CIRCLE))))
    }
    @Test fun `multipoints and point children remain eligible while line only collections do not`() {
        for (symbol in listOf(MapOverlayPointSymbol.PIN, MapOverlayPointSymbol.PIN_CHECKED)) {
            fun eligible(geometry: com.mapbox.geojson.Geometry) = needsPinSprite(MapOverlayFeature(
                MapFeatureKey("TEST-mixed","f-0"),geometry,style.copy(pointSymbol = symbol)))
            assertTrue(eligible(MultiPoint.fromLngLats(listOf(a,b))))
            val line = LineString.fromLngLats(listOf(a,b))
            assertTrue(eligible(GeometryCollection.fromGeometries(listOf<Geometry>(line,a))))
            assertFalse(eligible(GeometryCollection.fromGeometries(listOf<Geometry>(line))))
        }
    }
}
