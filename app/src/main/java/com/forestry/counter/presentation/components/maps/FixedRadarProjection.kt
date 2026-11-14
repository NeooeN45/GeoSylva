package com.forestry.counter.presentation.components.maps

import com.forestry.counter.domain.maps.FixedRadarsSnapshot
import com.mapbox.geojson.Point
import com.quintessences.geosylva.cartography.mapbox.MapFeatureKey
import com.quintessences.geosylva.cartography.mapbox.MapOverlayFeature
import com.quintessences.geosylva.cartography.mapbox.MapOverlayStyle

/** Display-only source points: no alerts, direction, live speed limit or average-speed segment is invented. */
fun buildFixedRadarMapFeatures(snapshot: FixedRadarsSnapshot): List<MapOverlayFeature> = snapshot.radars.map { radar ->
    MapOverlayFeature(MapFeatureKey("public-fixed-radars", "${snapshot.source.datasetId}:${radar.id}"),
        Point.fromLngLat(radar.point.longitude, radar.point.latitude),
        MapOverlayStyle(colorHex = "#6656A2", pointRadius = 5.0, strokeWidth = 1.2,
            strokeColorHex = "#FFFFFF", fillOpacity = .9), selectable = false)
}
