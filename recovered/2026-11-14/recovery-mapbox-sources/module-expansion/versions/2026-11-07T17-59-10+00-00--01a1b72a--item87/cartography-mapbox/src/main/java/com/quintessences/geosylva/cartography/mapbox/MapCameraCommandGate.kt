package com.quintessences.geosylva.cartography.mapbox

import kotlin.math.abs

enum class MapCameraCommandKind { POINT, FIT }
enum class MapCameraCommandStage { REQUESTED, OPTIONS_CALCULATED, OBSERVED, CANCELED_BY_GESTURE, REJECTED_OPTIONS }

/** Optional observations of native commands; no SDK strings, URLs or credentials. */
data class MapCameraCommandEvent(
    val id: String,
    val kind: MapCameraCommandKind,
    val stage: MapCameraCommandStage,
    val requestedPadding: MapCameraPadding,
    val camera: MapCameraPosition? = null,
)

/** A setter/async calculation cannot acknowledge a camera. Only matching native read-back can. */
internal class MapCameraCommandGate {
    internal class Ticket(val id: String, val kind: MapCameraCommandKind, val padding: MapCameraPadding) {
        var target: MapCameraPosition? = null
            internal set
    }
    private var latest: Ticket? = null

    fun submit(id: String, kind: MapCameraCommandKind, padding: MapCameraPadding): Ticket =
        Ticket(id, kind, padding).also { latest = it }

    fun isCurrent(ticket: Ticket): Boolean = latest === ticket

    fun resolve(ticket: Ticket, camera: MapCameraPosition): Boolean {
        if (!isCurrent(ticket)) return false
        ticket.target = camera
        return true
    }

    fun observe(camera: MapCameraPosition): Ticket? {
        val ticket = latest ?: return null
        val target = ticket.target ?: return null
        if (!cameraMatches(target, camera)) return null
        latest = null
        return ticket
    }

    fun invalidate(): Ticket? = latest.also { latest = null }
}

/** Native floating point read-back tolerance; padding comparison stays below one physical pixel. */
internal fun cameraMatches(expected: MapCameraPosition, actual: MapCameraPosition): Boolean {
    fun angle(a: Double, b: Double): Double = abs((((a - b) % 360.0 + 540.0) % 360.0) - 180.0)
    fun close(a: Double, b: Double, tolerance: Double) = abs(a - b) <= tolerance
    return angle(expected.longitude, actual.longitude) <= 0.0000001 &&
        close(expected.latitude, actual.latitude, 0.0000001) &&
        close(expected.zoom, actual.zoom, 0.00001) && angle(expected.bearing, actual.bearing) <= 0.001 &&
        close(expected.pitch, actual.pitch, 0.001) &&
        close(expected.padding.top, actual.padding.top, 0.5) &&
        close(expected.padding.left, actual.padding.left, 0.5) &&
        close(expected.padding.bottom, actual.padding.bottom, 0.5) &&
        close(expected.padding.right, actual.padding.right, 0.5)
}
