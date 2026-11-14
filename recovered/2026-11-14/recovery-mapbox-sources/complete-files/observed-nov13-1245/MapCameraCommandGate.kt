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
        internal var requestedTarget: MapCameraPosition? = null
        internal var fitSubmitted = false
        internal var fitIdleObserved = false
        internal var idleBeforeApplication = 0L
        internal var submittedAtMs = 0L
        internal var contextStillCurrent: (() -> Boolean)? = null
        internal var applicationKey: Any? = null
    }
    private data class Application(val ticket: Ticket, val before: MapCameraPosition)
    private data class FitProof(val key: Any, val camera: MapCameraPosition)
    private var latest: Ticket? = null
    private var application: Application? = null
    private var fitProof: FitProof? = null
    private var idleSequence = 0L

    fun submit(id: String, kind: MapCameraCommandKind, padding: MapCameraPadding): Ticket =
        Ticket(id, kind, padding).also {
            latest = it
            if (kind == MapCameraCommandKind.POINT) fitProof = null
        }

    fun isCurrent(ticket: Ticket): Boolean = latest === ticket

    fun resolve(ticket: Ticket, camera: MapCameraPosition): Boolean {
        if (!isCurrent(ticket)) return false
        ticket.requestedTarget = camera
        ticket.target = camera
        return true
    }

    /** The key includes the full SDK options and map/style/layout identity, never just the 2D DTO. */
    fun beginNativeFitApplication(ticket: Ticket, before: MapCameraPosition, key: Any,
        submittedAtMs: Long, contextStillCurrent: () -> Boolean): Boolean {
        if (!isCurrent(ticket) || ticket.kind != MapCameraCommandKind.FIT || ticket.target == null ||
            application != null || !contextStillCurrent()) return false
        ticket.fitSubmitted = false
        ticket.fitIdleObserved = false
        ticket.idleBeforeApplication = idleSequence
        ticket.submittedAtMs = submittedAtMs
        ticket.contextStillCurrent = contextStillCurrent
        ticket.applicationKey = key
        // A repeat may be a native no-op. Reuse only an earlier complete, geometrically
        // verified application of these exact options, with an unchanged native read-back.
        fitProof?.takeIf { it.key == key && cameraMatches(it.camera, before) }?.let { ticket.target = it.camera }
        application = Application(ticket, before)
        return true
    }

    /** Called INLINE by the non-coalesced CameraChanged callback; never by a delayed observer. */
    fun captureNativeCamera(actual: MapCameraPosition) {
        val active = application
        if (active == null) {
            // A later native movement also expires any previously accepted Idle. An
            // Idle already captured before this movement cannot revalidate on dispatch.
            latest?.takeIf { it.kind == MapCameraCommandKind.FIT }?.let {
                it.fitIdleObserved = false
                it.idleBeforeApplication = idleSequence
            }
            if (fitProof?.let { cameraMatches(it.camera, actual) } == false) fitProof = null
            return
        }
        val ticket = active.ticket
        val requested = ticket.requestedTarget ?: return
        if (!isCurrent(ticket) || ticket.contextStillCurrent?.invoke() != true) return
        if (!cameraMatches(requested.copy(zoom = actual.zoom), actual)) return
        if (cameraMatches(active.before, actual) && !cameraMatches(requested, actual)) return
        // Only zoom constrained by this specific native setter is adopted. The centre,
        // angles and padding retain their original strict tolerances.
        ticket.target = actual
    }

    fun endNativeFitApplication(ticket: Ticket, returnedNormally: Boolean) {
        if (application?.ticket !== ticket) return
        application = null
        if (!isCurrent(ticket)) return
        if (!returnedNormally || ticket.contextStillCurrent?.invoke() != true) {
            latest = null
            fitProof = null
        } else ticket.fitSubmitted = true
    }

    /** Capture at the native callback boundary, before any coroutine dispatch. */
    fun nextNativeIdleSequence(): Long = ++idleSequence

    fun acceptNativeIdle(sequence: Long, timestampMs: Long) {
        latest?.takeIf { it.kind == MapCameraCommandKind.FIT && it.fitSubmitted &&
            sequence > it.idleBeforeApplication && timestampMs >= it.submittedAtMs &&
            it.contextStillCurrent?.invoke() == true
        }?.fitIdleObserved = true
    }

    fun fitObservationCandidate(): Ticket? = latest?.takeIf {
        it.kind == MapCameraCommandKind.FIT && application == null && it.fitSubmitted &&
            it.fitIdleObserved && it.contextStillCurrent?.invoke() == true
    }

    fun observe(camera: MapCameraPosition, fitProjectionVerified: Boolean = false): Ticket? {
        if (application != null) return null
        val ticket = latest ?: return null
        val target = ticket.target ?: return null
        if (ticket.kind == MapCameraCommandKind.FIT &&
            (!fitProjectionVerified || fitObservationCandidate() !== ticket)) return null
        if (!cameraMatches(target, camera)) return null
        if (ticket.kind == MapCameraCommandKind.FIT) {
            ticket.applicationKey?.let { fitProof = FitProof(it, camera) }
        }
        latest = null
        return ticket
    }

    fun invalidate(): Ticket? = latest.also { latest = null; application = null; fitProof = null }
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
