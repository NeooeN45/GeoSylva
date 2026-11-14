package com.quintessences.geosylva.cartography.mapbox

import kotlin.math.max
import kotlin.math.min

internal data class MapFitPixel(val x: Double, val y: Double)

internal data class MapFitAttempt(
    val nonce: Int,
    val coordinatesPadding: MapCameraPadding = MapCameraPadding(),
    val maxZoom: Double? = null,
)

internal enum class MapFitFailure {
    BUDGET_EXHAUSTED, NO_PROGRESS, MIN_ZOOM, INVALID_PANE, INVALID_MEASUREMENT, RESOURCE_FAILURE,
}

internal sealed interface MapFitDecision {
    data object Waiting : MapFitDecision
    data object Verified : MapFitDecision
    data class Retry(val attempt: MapFitAttempt) : MapFitDecision
    data class Failed(val reason: MapFitFailure) : MapFitDecision
}

/**
 * Pure proposal policy, never a camera authority. The caller must apply each SDK result and
 * supply all projections only after that setter's causal Idle and matching native read-back.
 * Eight applications / twelve seconds bound work, not geometric acceptance or user data.
 */
internal class MapCameraFitRefinement(
    private val widthPx: Int,
    private val heightPx: Int,
    private val absolutePadding: MapCameraPadding,
    private val coordinateCount: Int,
    private val minZoom: Double,
    private val startedAtMs: Long,
    private val maxApplications: Int = 8,
    val maxDurationMs: Long = 12_000L,
) {
    init {
        require(minZoom.isFinite() && maxApplications > 0 && maxDurationMs > 0)
        require(coordinateCount > 0)
    }

    val initialAttempt = MapFitAttempt(1)
    private var current = initialAttempt
    private var appliedNonce: Int? = null
    private var closed = false
    private var previousCamera: MapCameraPosition? = null
    private var previousPixels: List<MapFitPixel>? = null

    fun isCurrent(attempt: MapFitAttempt): Boolean = !closed && attempt == current && appliedNonce == null

    fun applied(attempt: MapFitAttempt): Boolean {
        if (!isCurrent(attempt)) return false
        appliedNonce = attempt.nonce
        return true
    }

    fun cancel() { closed = true; appliedNonce = null }

    fun observe(actual: MapCameraPosition, pixels: List<MapFitPixel>, nowMs: Long): MapFitDecision {
        if (closed || appliedNonce != current.nonce) return MapFitDecision.Waiting
        if (nowMs - startedAtMs >= maxDurationMs) return fail(MapFitFailure.BUDGET_EXHAUSTED)
        val paneWidth = widthPx - absolutePadding.left - absolutePadding.right
        val paneHeight = heightPx - absolutePadding.top - absolutePadding.bottom
        if (paneWidth <= 0 || paneHeight <= 0) return fail(MapFitFailure.INVALID_PANE)
        if (pixels.size != coordinateCount) return fail(MapFitFailure.INVALID_MEASUREMENT)
        fun usable(p: MapFitPixel) = p.x.isFinite() && p.y.isFinite() && !(p.x == -1.0 && p.y == -1.0)
        // Keep the existing half-physical-pixel projection roundoff allowance unchanged.
        val allInside = pixels.all { p -> usable(p) &&
            p.x >= absolutePadding.left - 0.5 && p.x <= widthPx - absolutePadding.right + 0.5 &&
            p.y >= absolutePadding.top - 0.5 && p.y <= heightPx - absolutePadding.bottom + 0.5 }
        if (allInside) { closed = true; return MapFitDecision.Verified }
        if (previousCamera == actual && previousPixels == pixels) return fail(MapFitFailure.NO_PROGRESS)
        if (current.nonce >= maxApplications) return fail(MapFitFailure.BUDGET_EXHAUSTED)
        previousCamera = actual
        previousPixels = pixels.toList()

        val next = if (pixels.any { !usable(it) }) {
            // A censored projection has no measurable overflow. Bracket by doubling ground
            // scale through the SDK's maxZoom parameter, only in this failure branch.
            val upper = min(actual.zoom, current.maxZoom ?: actual.zoom)
            val limit = max(minZoom, upper - 1.0)
            if (limit >= upper) return fail(MapFitFailure.MIN_ZOOM)
            current.copy(nonce = current.nonce + 1, maxZoom = limit)
        } else {
            val residual = MapCameraPadding(
                top = max(0.0, absolutePadding.top - pixels.minOf { it.y }),
                left = max(0.0, absolutePadding.left - pixels.minOf { it.x }),
                bottom = max(0.0, pixels.maxOf { it.y } - (heightPx - absolutePadding.bottom)),
                right = max(0.0, pixels.maxOf { it.x } - (widthPx - absolutePadding.right)),
            )
            val prior = current.coordinatesPadding
            val extra = MapCameraPadding(prior.top + residual.top, prior.left + residual.left,
                prior.bottom + residual.bottom, prior.right + residual.right)
            if (extra.left + extra.right >= paneWidth || extra.top + extra.bottom >= paneHeight)
                return fail(MapFitFailure.INVALID_PANE)
            current.copy(nonce = current.nonce + 1, coordinatesPadding = extra)
        }
        current = next
        appliedNonce = null
        return MapFitDecision.Retry(next)
    }

    private fun fail(reason: MapFitFailure): MapFitDecision.Failed {
        cancel()
        return MapFitDecision.Failed(reason)
    }
}
