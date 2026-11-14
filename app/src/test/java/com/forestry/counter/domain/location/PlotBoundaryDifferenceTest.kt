package com.forestry.counter.domain.location

import org.junit.Assert.*
import org.junit.Test
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.operation.overlayng.OverlayNG
import org.locationtech.jts.operation.overlayng.OverlayNGRobust
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Synthetic polygon topology, without a field measurement or cadastral accuracy claim. */
class PlotBoundaryDifferenceTest {
    @Test fun `partial overlap returns only the outside polygon without changing either source`() {
        assertDifference(SQUARE, "POLYGON ((8 2,12 2,12 6,8 6,8 2))", 8.0)
    }

    @Test fun `an enclosed parent hole is included in the outside result`() {
        assertDifference("POLYGON ((0 0,10 0,10 10,0 10,0 0),(6 4,6 6,8 6,8 4,6 4))",
            "POLYGON ((1 1,9 1,9 9,1 9,1 1))", 4.0)
    }

    @Test fun `concavity and multipart parent are not replaced by their bounds`() {
        assertDifference("POLYGON ((0 0,10 0,10 3,3 3,3 10,0 10,0 0))",
            "POLYGON ((1 1,4 1,4 4,1 4,1 1))", 1.0)
        assertDifference("MULTIPOLYGON (((0 0,4 0,4 4,0 4,0 0)),((6 6,10 6,10 10,6 10,6 6)))",
            "POLYGON ((-2 -2,8 -2,8 8,-2 8,-2 -2))", 80.0)
    }

    @Test fun `tangent contact can have no outside preview without becoming an effective area`() {
        val result = PlotBoundaryDifference.outsideWkt(SQUARE, "POLYGON ((8 2,10 2,10 4,8 4,8 2))")
        assertEquals(PlotOutsidePreview.Empty, result)
    }

    @Test fun `nearly coincident segments preserve the polygon difference invariants`() {
        assertDifference(SQUARE, "POLYGON ((9.999999999 2,12 2,12 6,9.999999999 6,9.999999999 2))", 8.0)
    }

    @Test fun `invalid input and excessive complexity return no polygon rather than a repaired boundary`() {
        assertEquals(PlotOutsidePreview.Unavailable(PlotOutsidePreviewFailure.INVALID_GEOMETRY),
            PlotBoundaryDifference.outsideWkt("POLYGON ((0 0,4 4,0 4,4 0,0 0))", SQUARE))
        assertEquals(PlotOutsidePreview.Unavailable(PlotOutsidePreviewFailure.INVALID_GEOMETRY),
            PlotBoundaryDifference.outsideWkt("not WKT", SQUARE))
        val count = PlotBoundaryDifference.MAX_PARENT_POINTS + 1
        val points = (0 until count).map { i ->
            val angle = i * 2.0 * PI / count
            "${5.0 + cos(angle)} ${45.0 + sin(angle)}"
        }
        val tooComplex = points.plus(points.first()).joinToString(",", "POLYGON ((", "))")
        assertEquals(PlotOutsidePreview.Unavailable(PlotOutsidePreviewFailure.TOO_COMPLEX),
            PlotBoundaryDifference.outsideWkt(tooComplex, SQUARE))
        assertEquals(PlotOutsidePreview.Unavailable(PlotOutsidePreviewFailure.TOO_COMPLEX),
            PlotBoundaryDifference.outsideWkt(SQUARE, tooComplex))
    }

    @Test fun `the existing circular preview is clipped without moving its planned centre`() {
        val parent = ParcelGeometry.fromWkt("POLYGON ((5.4670 45.0536,5.4692 45.0536,5.4692 45.0552,5.4670 45.0552,5.4670 45.0536))")
        val center = ParcelCoordinate(5.46702, 45.0544)
        val footprint = PlotSamplingGeometry.polygon(center, SampleFootprintShape.CIRCLE, PI * 12.5 * 12.5)
        val original = footprint.toWkt()
        val result = PlotBoundaryDifference.outside(parent, footprint) as PlotOutsidePreview.Available
        val outside = WKTReader().read(result.geometry.toWkt())
        assertFalse(outside.isEmpty)
        assertEquals(0.0, operation(outside, WKTReader().read(parent.toWkt()), OverlayNG.INTERSECTION).area, 1e-14)
        assertEquals(original, footprint.toWkt())
        assertTrue(parent.contains(center))
    }

    private fun assertDifference(parentWkt: String, footprintWkt: String, expectedSyntheticArea: Double) {
        val parent = ParcelGeometry.fromWkt(parentWkt)
        val footprint = ParcelGeometry.fromWkt(footprintWkt)
        val originalParent = parent.toWkt()
        val originalFootprint = footprint.toWkt()
        val outside = (PlotBoundaryDifference.outside(parent, footprint) as PlotOutsidePreview.Available).geometry
        val reader = WKTReader()
        val result = reader.read(outside.toWkt())
        val original = reader.read(footprintWkt)
        val boundary = reader.read(parentWkt)
        assertTrue(result.isValid)
        // These areas are dimensionless topology fixtures, never an exposed survey result.
        assertEquals(expectedSyntheticArea, result.area, 1e-9)
        assertEquals(0.0, operation(result, boundary, OverlayNG.INTERSECTION).area, 1e-12)
        val inside = operation(original, boundary, OverlayNG.INTERSECTION)
        val reconstructed = operation(result, inside, OverlayNG.UNION)
        assertEquals(0.0, operation(reconstructed, original, OverlayNG.SYMDIFFERENCE).area, 1e-12)
        assertEquals(originalParent, parent.toWkt())
        assertEquals(originalFootprint, footprint.toWkt())
    }
    private fun operation(a: Geometry, b: Geometry, operator: Int) = OverlayNGRobust.overlay(a, b, operator)
    companion object { private const val SQUARE = "POLYGON ((0 0,10 0,10 10,0 10,0 0))" }
}
