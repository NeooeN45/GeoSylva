package com.forestry.counter.domain.location

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.operation.overlayng.OverlayNG
import org.locationtech.jts.operation.overlayng.OverlayNGRobust

/** A visual difference of the recorded polygons, never a cadastral or effective sampling area. */
sealed interface PlotOutsidePreview {
    data object NotRequested : PlotOutsidePreview
    data object Computing : PlotOutsidePreview
    data object Empty : PlotOutsidePreview
    data class Available(val geometry: ParcelGeometry) : PlotOutsidePreview
    data class Unavailable(val reason: PlotOutsidePreviewFailure) : PlotOutsidePreview
}

enum class PlotOutsidePreviewFailure { INVALID_GEOMETRY, TOO_COMPLEX, OPERATION_FAILED }

/**
 * JTS OverlayNG uses the same straight longitude/latitude ring segments as
 * ParcelGeometry topology. It does not repair source contours or calculate an
 * effective area. The circular input is the existing 48-segment preview polygon.
 * Call on a worker for the selected candidate only; finite input/output budgets
 * bound the operation, and no result is written back to an observation or contour.
 */
object PlotBoundaryDifference {
    const val MAX_PARENT_POINTS = 4_096
    const val MAX_FOOTPRINT_POINTS = 65
    const val MAX_RESULT_POINTS = 8_000
    private const val MAX_TEXT = 1_000_000

    fun outside(parent: ParcelGeometry, footprint: ParcelGeometry): PlotOutsidePreview =
        outsideWkt(parent.toWkt(), footprint.toWkt())

    internal fun outsideWkt(parentWkt: String, footprintWkt: String): PlotOutsidePreview {
        if (parentWkt.length > MAX_TEXT || footprintWkt.length > MAX_TEXT)
            return PlotOutsidePreview.Unavailable(PlotOutsidePreviewFailure.TOO_COMPLEX)
        return try {
            val reader = WKTReader()
            val parent = reader.read(parentWkt)
            val footprint = reader.read(footprintWkt)
            if (parent.numPoints > MAX_PARENT_POINTS || footprint.numPoints > MAX_FOOTPRINT_POINTS)
                return PlotOutsidePreview.Unavailable(PlotOutsidePreviewFailure.TOO_COMPLEX)
            if (!valid(parent) || !valid(footprint))
                return PlotOutsidePreview.Unavailable(PlotOutsidePreviewFailure.INVALID_GEOMETRY)
            val result = OverlayNGRobust.overlay(footprint, parent, OverlayNG.DIFFERENCE)
            when {
                result.isEmpty -> PlotOutsidePreview.Empty
                result.numPoints > MAX_RESULT_POINTS -> PlotOutsidePreview.Unavailable(PlotOutsidePreviewFailure.TOO_COMPLEX)
                !valid(result) -> PlotOutsidePreview.Unavailable(PlotOutsidePreviewFailure.OPERATION_FAILED)
                else -> PlotOutsidePreview.Available(ParcelGeometry.fromWkt(result.toText()))
            }
        } catch (_: org.locationtech.jts.io.ParseException) {
            PlotOutsidePreview.Unavailable(PlotOutsidePreviewFailure.INVALID_GEOMETRY)
        } catch (_: Exception) {
            PlotOutsidePreview.Unavailable(PlotOutsidePreviewFailure.OPERATION_FAILED)
        }
    }

    private fun valid(geometry: Geometry): Boolean = (geometry is Polygon || geometry is MultiPolygon) &&
        !geometry.isEmpty && geometry.coordinates.all {
            it.x.isFinite() && it.y.isFinite() && it.x in -180.0..180.0 && it.y in -80.0..80.0
        } && geometry.isValid
}
