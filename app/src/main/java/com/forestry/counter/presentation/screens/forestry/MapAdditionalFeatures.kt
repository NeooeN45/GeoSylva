package com.forestry.counter.presentation.screens.forestry

import com.forestry.counter.domain.location.trace.TraceCaptureSnapshot
import com.forestry.counter.domain.location.trace.TraceCapturePhase
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.quintessences.geosylva.cartography.mapbox.*

/** Display projection only. Missing passages are never converted into a line. */
internal fun buildTraceMapFeatures(snapshot: TraceCaptureSnapshot?, selectedPortionId: String?): List<MapOverlayFeature> = buildList {
    snapshot?.portions.orEmpty().forEachIndexed { index, portion ->
        val points = portion.points.map { Point.fromLngLat(it.longitude, it.latitude) }
        if (points.size >= 2) add(MapOverlayFeature(MapFeatureKey("gps-trace-portions", portion.id),
            LineString.fromLngLats(points), MapOverlayStyle(colorHex = "#176BDF",
                lineWidth = if (portion.id == selectedPortionId) 5.0 else 3.0, strokeWidth = 1.0)))
        points.firstOrNull()?.let { point -> add(MapOverlayFeature(MapFeatureKey("gps-trace-starts", portion.id),
            point, MapOverlayStyle(colorHex = "#176BDF", pointRadius = 4.0), "${index + 1}")) }
    }
    if (snapshot?.phase != TraceCapturePhase.RECORDING) snapshot?.lastAcceptedFix?.let { fix ->
        add(MapOverlayFeature(MapFeatureKey("gps-trace-last", "last-accepted"), Point.fromLngLat(fix.longitude, fix.latitude),
            MapOverlayStyle(colorHex = "#77828D", pointRadius = 6.0), selectable = false))
    }
}

internal fun buildTreeGuidanceFeatures(guidance: TreeGuidanceMapOverlay?): List<MapOverlayFeature> = buildList {
    guidance ?: return@buildList
    val target = Point.fromLngLat(guidance.targetLongitude, guidance.targetLatitude)
    add(MapOverlayFeature(MapFeatureKey("tree-guidance", "target"), target,
        MapOverlayStyle(colorHex = "#176F39", pointRadius = 8.0), selectable = false))
    if (guidance.userLongitude != null && guidance.userLatitude != null) {
        val user = Point.fromLngLat(guidance.userLongitude, guidance.userLatitude)
        add(MapOverlayFeature(MapFeatureKey("tree-guidance", "position"), user,
            MapOverlayStyle(colorHex = if (guidance.userPositionIsCurrent) "#176BDF" else "#77828D", pointRadius = 7.0), selectable = false))
        if (guidance.drawIndicativeConnector && guidance.userPositionIsCurrent) add(MapOverlayFeature(
            MapFeatureKey("tree-guidance", "straight-indication"), LineString.fromLngLats(listOf(user, target)),
            MapOverlayStyle(colorHex = "#386BA5", lineWidth = 2.0, lineDashArray = listOf(2.0, 2.0)), selectable = false))
    }
}
