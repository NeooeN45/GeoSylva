package com.forestry.counter.presentation.screens.packs.offline

import com.mapbox.geojson.Point
import com.quintessences.geosylva.cartography.mapbox.MapCoordinate

/** No bounding-box substitute: only a closed native surface footprint in one longitude span. */
internal fun downloadableViewportRing(footprint: List<MapCoordinate>?): List<Point>? {
    if (footprint == null || footprint.size != 5 || footprint.first() != footprint.last() ||
        footprint.dropLast(1).distinct().size != 4) return null
    // The offline tile service uses Web Mercator. A wrapped polygon must first
    // be split; reject it rather than quietly requesting most of the planet.
    if (footprint.any { kotlin.math.abs(it.latitude) > 85.05112878 } ||
        footprint.zipWithNext().any { (a, b) -> kotlin.math.abs(a.longitude - b.longitude) >= 180.0 } ||
        footprint.maxOf { it.longitude } - footprint.minOf { it.longitude } >= 180.0) return null
    return footprint.map { Point.fromLngLat(it.longitude, it.latitude) }
}
