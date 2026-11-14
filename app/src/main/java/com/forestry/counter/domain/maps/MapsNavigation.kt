package com.forestry.counter.domain.maps

import kotlin.math.*

/** Provider instructions, never inferred from the shape of the polyline. */
data class MapsStep(
    val instruction: String,
    val point: MapsPoint,
    val distanceMetres: Double,
    val durationSeconds: Double,
    val geometry: List<MapsPoint>,
) {
    init {
        require(instruction.isNotBlank() && instruction.length <= 1_000)
        require(distanceMetres.isFinite() && distanceMetres >= 0)
        require(durationSeconds.isFinite() && durationSeconds >= 0)
        require(geometry.isNotEmpty())
    }
}

enum class MapsRouteSource { IGN, MAPBOX }
enum class MapsNavigationStatus { IDLE, ACQUIRING, ACTIVE, LOST, OFF_ROUTE, ORIGIN_MISMATCH, PAUSED, NETWORK_END, END_REACHED, ARRIVED }
data class MapsLocationFix(val point: MapsPoint, val accuracyMetres: Double, val elapsedRealtimeNanos: Long) {
    fun qualified(nowNanos: Long, sinceNanos: Long): Boolean = accuracyMetres.isFinite() &&
        accuracyMetres in 0.0..40.0 && elapsedRealtimeNanos >= sinceNanos &&
        elapsedRealtimeNanos > 0 && nowNanos >= elapsedRealtimeNanos && nowNanos - elapsedRealtimeNanos <= FRESH_NANOS
    companion object { const val FRESH_NANOS = 15_000_000_000L }
}
data class MapsRouteProgress(val alongMetres: Double, val remainingMetres: Double, val distanceFromRouteMetres: Double, val stepIndex: Int?, val distanceToStepMetres: Double? = null)
data class MapsNavigation(
    val session: Long = 0,
    val status: MapsNavigationStatus = MapsNavigationStatus.IDLE,
    val startedAtNanos: Long = 0,
    val fix: MapsLocationFix? = null,
    val progress: MapsRouteProgress? = null,
    val recovered: Boolean = false,
    val sound: Boolean = false,
    val originMatched: Boolean = false,
) {
    val observing: Boolean get() = status in setOf(MapsNavigationStatus.ACQUIRING, MapsNavigationStatus.ACTIVE,
        MapsNavigationStatus.LOST, MapsNavigationStatus.OFF_ROUTE, MapsNavigationStatus.ORIGIN_MISMATCH, MapsNavigationStatus.NETWORK_END, MapsNavigationStatus.END_REACHED)
}

/** Foreground geometric aid, not Mapbox Navigation SDK / map matching / traffic ETA. */
object MapsRouteTracking {
    private const val EARTH_METRES = 6_371_000.0
    private const val ROUTE_CORRIDOR_METRES = 60.0
    const val ORIGIN_TOLERANCE_METRES = 100.0
    const val END_TOLERANCE_METRES = 30.0
    fun progress(route: MapsRoute, point: MapsPoint): MapsRouteProgress {
        val lengths = route.points.zipWithNext { a, b -> a.distanceTo(b) }
        var before = 0.0
        var bestAlong = 0.0
        var bestDistance = Double.POSITIVE_INFINITY
        route.points.zipWithNext().forEachIndexed { index, (a, b) ->
            val cosLat = cos(Math.toRadians(point.latitude)).coerceAtLeast(0.000001)
            fun x(p: MapsPoint) = Math.toRadians((p.longitude - point.longitude + 540.0) % 360.0 - 180.0) * EARTH_METRES * cosLat
            fun y(p: MapsPoint) = Math.toRadians(p.latitude - point.latitude) * EARTH_METRES
            val ax = x(a); val ay = y(a); val dx = x(b) - ax; val dy = y(b) - ay
            val denominator = dx * dx + dy * dy
            val fraction = if (denominator <= 0) 0.0 else (-(ax * dx + ay * dy) / denominator).coerceIn(0.0, 1.0)
            val distance = hypot(ax + fraction * dx, ay + fraction * dy)
            if (distance < bestDistance) { bestDistance = distance; bestAlong = before + fraction * lengths[index] }
            before += lengths[index]
        }
        var offset = 0.0
        var distanceToStep: Double? = null
        var stepIndex: Int? = null
        route.steps.forEachIndexed { index, step ->
            val end = offset + step.geometry.zipWithNext { a, b -> a.distanceTo(b) }.sum()
            if (stepIndex == null && (bestAlong < end || index == route.steps.lastIndex)) {
                // The next maneuver starts at the end of the segment currently travelled.
                stepIndex = (index + 1).coerceAtMost(route.steps.lastIndex)
                distanceToStep = (end - bestAlong).coerceAtLeast(0.0)
            }
            offset = end
        }
        return MapsRouteProgress(bestAlong, (before - bestAlong).coerceAtLeast(0.0), bestDistance, stepIndex, distanceToStep)
    }
    fun compatible(progress: MapsRouteProgress, accuracy: Double): Boolean =
        progress.distanceFromRouteMetres <= max(ROUTE_CORRIDOR_METRES, accuracy * 1.5)
    fun endReached(route: MapsRoute, requestedEnd: MapsPoint, fix: MapsLocationFix, progress: MapsRouteProgress): Boolean =
        progress.remainingMetres <= END_TOLERANCE_METRES &&
            fix.point.distanceTo(route.points.last()) <= END_TOLERANCE_METRES &&
            fix.point.distanceTo(requestedEnd) <= END_TOLERANCE_METRES &&
            route.points.last().distanceTo(requestedEnd) <= END_TOLERANCE_METRES
}
