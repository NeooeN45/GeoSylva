package com.forestry.counter.domain.location

import kotlin.math.PI
import kotlin.math.cos
import org.junit.Assert.*
import org.junit.Test

/** Independent metric references; synthetic WGS84 geometry only, no product data. */
class PlotSamplingGeometryTest {
    @Test fun `one hectare cells have independent spacing and a 500 square metre sample footprint`() {
        val preview = PlotSamplingGeometry.grid(rectangle(1000.0, 500.0), 1.0, SampleFootprintShape.CIRCLE, 500.0)
        assertEquals(107.4569931823542, preview.neighborDistanceM, 1e-8)
        assertEquals(93.06048591020996, preview.rowDistanceM, 1e-8)
        assertEquals(203, preview.footprints.size)
        assertEquals(preview.footprints.size, preview.cells.size)
        // Finite circle outline is inscribed; it is not a replacement for target area.
        preview.footprints.forEach { assertEquals(498.5733287, it.geometry.areaSquareMetres, 0.5) }
        preview.cells.forEach { assertEquals(10_000.0, it.areaSquareMetres, 5.0) }
    }

    @Test fun `hexagon of one hectare has side 62 metres and no circle radius`() {
        val hexagon = PlotSamplingGeometry.polygon(ParcelCoordinate(2.0, 46.0), SampleFootprintShape.HEXAGON, 10_000.0)
        val positions = kotlinx.serialization.json.Json.parseToJsonElement(hexagon.toGeoJson()).let {
            (it as kotlinx.serialization.json.JsonObject).getValue("coordinates") as kotlinx.serialization.json.JsonArray
        }.first() as kotlinx.serialization.json.JsonArray
        assertEquals(7, positions.size)
        assertEquals(10_000.0, hexagon.areaSquareMetres, 0.01)
        val sideLongitude = (positions.first() as kotlinx.serialization.json.JsonArray).first().toString().toDouble() - 2.0
        assertEquals(62.04032394013998, sideLongitude * PI / 180.0 * 6_371_008.8 * cos(46.0 * PI / 180.0), 1e-7)
    }

    @Test fun `holes and detached islands keep only covered centres`() {
        val outer = ring(1000.0, 500.0)
        val hole = ring(200.0, 200.0)
        val parcel = ParcelGeometry.fromWkt("POLYGON ($outer, $hole)")
        val preview = PlotSamplingGeometry.grid(parcel, 1.0, SampleFootprintShape.HEXAGON, 500.0)
        assertTrue(preview.footprints.isNotEmpty())
        assertFalse(preview.footprints.any { it.center == ParcelCoordinate(2.0, 46.0) })
        preview.footprints.forEach { assertTrue(parcel.contains(it.center)) }
        val islands = ParcelGeometry.fromWkt("MULTIPOLYGON ((${ring(100.0, 100.0, -400.0)}), (${ring(100.0, 100.0, 400.0)}))")
        val islandPreview = PlotSamplingGeometry.grid(islands, 0.1, SampleFootprintShape.CIRCLE, 25.0)
        assertTrue(islandPreview.footprints.isNotEmpty())
        islandPreview.footprints.forEach { assertTrue(islands.contains(it.center)) }
    }

    @Test fun `manual footprint crossing a boundary remains whole and signals target area only`() {
        val parcel = rectangle(100.0, 100.0)
        val point = coordinate(98.0, 0.0)
        val footprints = PlotSamplingGeometry.footprints(parcel, listOf(point), SampleFootprintShape.CIRCLE, PI * 4.0 * 4.0)
        assertTrue(footprints.single().crossesBoundary)
        assertTrue(footprints.single().geometry.bounds.east > parcel.bounds.east)
        assertTrue(footprints.single().geometry.areaSquareMetres > 50.0)
    }

    @Test fun `a hole fully enclosed inside a hexagonal footprint is still flagged`() {
        val hole = ring(1.0, 1.0, 20.0)
        val parcel = ParcelGeometry.fromWkt("POLYGON (${ring(100.0, 100.0)}, $hole)")
        val footprint = PlotSamplingGeometry.footprints(parcel, listOf(coordinate(0.0, 0.0)), SampleFootprintShape.HEXAGON, 10_000.0).single()
        assertTrue(footprint.crossesBoundary)
        assertTrue(parcel.contains(footprint.center))
    }

    @Test fun `rotation is deterministic and changes the planned centres without moving origin`() {
        val parcel = rectangle(500.0, 200.0)
        val initial = PlotSamplingGeometry.grid(parcel, 1.0, SampleFootprintShape.CIRCLE, 500.0)
        val rotated = PlotSamplingGeometry.grid(parcel, 1.0, SampleFootprintShape.CIRCLE, 500.0, 35.0)
        val repeated = PlotSamplingGeometry.grid(parcel, 1.0, SampleFootprintShape.CIRCLE, 500.0, 35.0)
        assertEquals(rotated.footprints.map { it.center }, repeated.footprints.map { it.center })
        assertNotEquals(initial.footprints.map { it.center }, rotated.footprints.map { it.center })
        assertTrue(rotated.footprints.any { it.center == ParcelCoordinate(2.0, 46.0) })
    }

    @Test fun `dense grids are refused as a whole instead of silently limited to 500 centres`() {
        assertThrows(SamplingGridLimitException::class.java) {
            PlotSamplingGeometry.grid(rectangle(1000.0, 1000.0), 0.1, SampleFootprintShape.CIRCLE, 10.0)
        }
        assertThrows(SamplingGridLimitException::class.java) {
            PlotSamplingGeometry.grid(rectangle(20_000.0, 20_000.0), 0.01, SampleFootprintShape.CIRCLE, 10.0)
        }
    }

    @Test fun `invalid units extremes and oversized local metric extent are refused`() {
        for (value in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 0.001, 10_001.0)) {
            assertThrows(IllegalArgumentException::class.java) {
                PlotSamplingGeometry.grid(rectangle(100.0, 100.0), value, SampleFootprintShape.CIRCLE, 500.0)
            }
        }
        assertThrows(IllegalArgumentException::class.java) { PlotSamplingGeometry.grid(rectangle(26_000.0, 100.0), 100.0, SampleFootprintShape.CIRCLE, 500.0) }
        assertThrows(IllegalArgumentException::class.java) { PlotSamplingGeometry.grid(rectangle(100.0, 100.0), 1.0, SampleFootprintShape.CIRCLE, 500.0, 360.0) }
        assertThrows(IllegalArgumentException::class.java) { PlotSamplingGeometry.polygon(ParcelCoordinate(2.0, 46.0), SampleFootprintShape.CIRCLE, Double.NaN) }
    }

    @Test fun `plan round trip retains density footprint boundary provenance and refuses invalid policy`() {
        val plan = PlotSamplingPlan(placement = "AUTO", gridLayout = "HEX", cellAreaHa = 1.0,
            footprintShape = "CIRCLE", footprintAreaM2 = 500.0, boundaryWarning = true)
        val decoded = PlotSamplingPlan.fromJson(plan.toJson())
        assertEquals(plan, decoded)
        assertTrue(plan.toJson().contains("LOCAL_TANGENT_APPROXIMATION"))
        assertThrows(IllegalArgumentException::class.java) { PlotSamplingPlan.fromJson(plan.copy(boundaryPolicy = "FULLY_INSIDE").toJson()) }
        assertThrows(IllegalArgumentException::class.java) { PlotSamplingPlan.fromJson(plan.copy(placement = "MANUAL").toJson()) }
    }

    private fun rectangle(halfWidth: Double, halfHeight: Double) = ParcelGeometry.fromWkt("POLYGON (${ring(halfWidth, halfHeight)})")
    private fun ring(halfWidth: Double, halfHeight: Double, offsetX: Double = 0.0): String {
        val positions = listOf(coordinate(offsetX - halfWidth, -halfHeight), coordinate(offsetX + halfWidth, -halfHeight),
            coordinate(offsetX + halfWidth, halfHeight), coordinate(offsetX - halfWidth, halfHeight), coordinate(offsetX - halfWidth, -halfHeight))
        return positions.joinToString(", ", "(", ")") { "${it.longitude} ${it.latitude}" }
    }
    private fun coordinate(x: Double, y: Double) = ParcelCoordinate(
        2.0 + x / (6_371_008.8 * cos(46.0 * PI / 180.0)) * 180.0 / PI,
        46.0 + y / 6_371_008.8 * 180.0 / PI,
    )
}
