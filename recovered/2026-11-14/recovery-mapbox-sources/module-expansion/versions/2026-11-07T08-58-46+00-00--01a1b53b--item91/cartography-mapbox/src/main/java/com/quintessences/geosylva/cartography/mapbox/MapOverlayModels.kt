package com.quintessences.geosylva.cartography.mapbox

import com.mapbox.geojson.Geometry
import com.mapbox.geojson.GeometryCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.MultiLineString
import com.mapbox.geojson.MultiPoint
import com.mapbox.geojson.MultiPolygon
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon

/** Geographic coordinate, never the historical latitude/longitude storage order. */
data class MapCoordinate(val longitude: Double, val latitude: Double) {
    init {
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(latitude.isFinite() && latitude in -90.0..90.0)
    }
}

/** Opaque host identities; labels and array positions are never object identities. */
data class MapFeatureKey(val layerId: String, val featureId: String) {
    init { require(layerId.isNotBlank() && featureId.isNotBlank()) }
}

data class MapOverlayStyle(
    val colorHex: String = "#176BDF",
    val lineWidth: Double = 3.0,
    val pointRadius: Double = 6.0,
    val fillOpacity: Double = 0.16,
    val lineDashArray: List<Double> = emptyList(),
    val labelColorHex: String = "#12285B",
    val labelSize: Double = 13.0,
    /** Optional casing per side of a line; zero preserves the existing uncased line. */
    val strokeWidth: Double = 0.0,
    val strokeColorHex: String = "#FFFFFF",
) {
    init {
        require(colorHex.matches(Regex("#[0-9a-fA-F]{6}")))
        require(labelColorHex.matches(Regex("#[0-9a-fA-F]{6}")))
        require(strokeColorHex.matches(Regex("#[0-9a-fA-F]{6}")))
        require(strokeWidth.isFinite() && strokeWidth in 0.0..16.0)
        require(lineWidth.isFinite() && lineWidth in 0.0..32.0)
        require(pointRadius.isFinite() && pointRadius in 0.0..64.0)
        require(fillOpacity.isFinite() && fillOpacity in 0.0..1.0)
        require(labelSize.isFinite() && labelSize in 8.0..36.0)
        require(lineDashArray.isEmpty() || (lineDashArray.size in 2..8 &&
            lineDashArray.size % 2 == 0 && lineDashArray.all { it.isFinite() && it > 0.0 }))
    }
}

/** Host supplies authorized immutable geometry. No parsing of business names or permissions here. */
data class MapOverlayFeature(
    val key: MapFeatureKey,
    val geometry: Geometry,
    val style: MapOverlayStyle = MapOverlayStyle(),
    val label: String? = null,
    val selectable: Boolean = true,
) {
    init { require(validOverlayGeometry(geometry)) { "Invalid geographic overlay geometry." } }
}

/** Replace the snapshot when data, filters, scope or selection change. Never mutate its geometries. */
class MapOverlayScene(
    val scopeId: String,
    val revision: Long,
    features: List<MapOverlayFeature>,
    val selectedKey: MapFeatureKey? = null,
) {
    val features: List<MapOverlayFeature> = features.toList()
    init {
        require(scopeId.isNotBlank() && revision >= 0)
        require(this.features.map { it.key }.distinct().size == this.features.size) { "Duplicate overlay identity." }
        require(selectedKey == null || this.features.any { it.key == selectedKey })
    }

    companion object { val Empty = MapOverlayScene("empty", 0, emptyList()) }
}

data class MapOverlayTap(val coordinate: MapCoordinate, val featureKey: MapFeatureKey?)

/** May wrap the antimeridian (west > east). Bounds describe the visible camera, not a parcel. */
data class MapBounds(val west: Double, val south: Double, val east: Double, val north: Double) {
    init {
        MapCoordinate(west, south)
        MapCoordinate(east, north)
        require(south <= north)
    }
}
data class MapViewport(val bounds: MapBounds, val camera: MapCameraPosition)

/** Pixel padding is supplied by the host from its actual visible panels and density. */
data class MapCameraFitCommand(
    val id: String,
    val coordinates: List<MapCoordinate>,
    val paddingTopPx: Double = 0.0,
    val paddingLeftPx: Double = 0.0,
    val paddingBottomPx: Double = 0.0,
    val paddingRightPx: Double = 0.0,
) {
    init {
        require(id.isNotBlank() && coordinates.isNotEmpty())
        require(listOf(paddingTopPx, paddingLeftPx, paddingBottomPx, paddingRightPx).all { it.isFinite() && it >= 0.0 })
    }
}

enum class MapOverlayFailure { SOURCE, LAYER, LABEL, QUERY }
sealed interface MapOverlayStatus {
    data class Applied(val scopeId: String, val revision: Long) : MapOverlayStatus
    data class Failed(val reason: MapOverlayFailure) : MapOverlayStatus
}

/** A delayed rendered hit is useful only in the exact still-current authorized snapshot. */
internal fun resolveOverlayHit(
    queried: MapOverlayScene,
    current: MapOverlayScene,
    key: MapFeatureKey,
): MapFeatureKey? = key.takeIf {
    queried === current && current.features.any { feature -> feature.key == key && feature.selectable }
}

/** Coordinate/ring shape checks only, not topology, survey accuracy or access authorization. */
internal fun validOverlayGeometry(geometry: Geometry, depth: Int = 0): Boolean {
    if (depth > 32) return false
    fun point(p: Point): Boolean = p.longitude().isFinite() && p.longitude() in -180.0..180.0 &&
        p.latitude().isFinite() && p.latitude() in -90.0..90.0 && p.coordinates().all { it.isFinite() }
    fun line(points: List<Point>) = points.size >= 2 && points.all(::point)
    fun polygon(rings: List<List<Point>>) = rings.isNotEmpty() && rings.all {
        it.size >= 4 && it.first() == it.last() && it.all(::point) &&
            it.dropLast(1).map { p -> p.longitude() to p.latitude() }.distinct().size >= 3
    }
    return when (geometry) {
        is Point -> point(geometry)
        is MultiPoint -> geometry.coordinates().isNotEmpty() && geometry.coordinates().all(::point)
        is LineString -> line(geometry.coordinates())
        is MultiLineString -> geometry.coordinates().isNotEmpty() && geometry.coordinates().all(::line)
        is Polygon -> polygon(geometry.coordinates())
        is MultiPolygon -> geometry.coordinates().isNotEmpty() && geometry.coordinates().all(::polygon)
        is GeometryCollection -> geometry.geometries().isNotEmpty() && geometry.geometries().all { validOverlayGeometry(it, depth + 1) }
        else -> false
    }
}
