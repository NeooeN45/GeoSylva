package com.forestry.counter.domain.location

import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ParcelGeometryTest {
    private val square = "POLYGON ((2 46, 2.01 46, 2.01 46.01, 2 46.01, 2 46))"
    private val squareWithHole = "POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0), (1 1, 2 1, 2 2, 1 2, 1 1))"

    @Test fun `bounds and centre retain longitude latitude axes`() {
        val geometry = ParcelGeometry.fromWkt(square)
        assertEquals(ParcelBounds(2.0, 46.0, 2.01, 46.01), geometry.bounds)
        assertEquals(2.005, geometry.bounds.center.longitude, 1e-14)
        assertEquals(46.005, geometry.bounds.center.latitude, 1e-14)
        assertTrue(geometry.contains(ParcelCoordinate(2.005, 46.005)))
        assertFalse(geometry.contains(ParcelCoordinate(46.005, 2.005)))
    }

    @Test fun `outer boundaries included and definitely exterior points excluded`() {
        val geometry = ParcelGeometry.fromWkt(square)
        assertTrue(geometry.contains(ParcelCoordinate(2.0, 46.0)))
        assertTrue(geometry.contains(ParcelCoordinate(2.01, 46.005)))
        assertFalse(geometry.contains(ParcelCoordinate(2.010001, 46.005)))
        assertFalse(geometry.contains(ParcelCoordinate(2.005, 46.010001)))
    }

    @Test fun `hole interior and every hole boundary excluded`() {
        val geometry = ParcelGeometry.fromWkt(squareWithHole)
        assertTrue(geometry.contains(ParcelCoordinate(3.0, 3.0)))
        assertFalse(geometry.contains(ParcelCoordinate(1.5, 1.5)))
        assertFalse(geometry.contains(ParcelCoordinate(1.0, 1.0)))
        assertFalse(geometry.contains(ParcelCoordinate(1.0, 1.5)))
        assertFalse(geometry.contains(ParcelCoordinate(2.0, 1.5)))
    }

    @Test fun `concave parcel does not accept points merely inside bbox`() {
        val geometry = ParcelGeometry.fromWkt("POLYGON ((0 0, 3 0, 3 1, 1 1, 1 3, 0 3, 0 0))")
        assertTrue(geometry.contains(ParcelCoordinate(0.5, 2.0)))
        assertFalse(geometry.contains(ParcelCoordinate(2.0, 2.0)))
        // The bounds centre is for map framing and is not promised to be in a concave parcel.
        assertFalse(geometry.contains(geometry.bounds.center))
    }

    @Test fun `multipolygon selects either island without selecting gap`() {
        val geometry = ParcelGeometry.fromWkt("MULTIPOLYGON (((0 0, 1 0, 1 1, 0 1, 0 0)), ((3 0, 4 0, 4 1, 3 1, 3 0)))")
        assertTrue(geometry.contains(ParcelCoordinate(0.5, 0.5)))
        assertTrue(geometry.contains(ParcelCoordinate(3.5, 0.5)))
        assertFalse(geometry.contains(ParcelCoordinate(2.0, 0.5)))
        assertTrue(geometry.toGeoJson().contains("\"MultiPolygon\""))
    }

    @Test fun `multipolygon island inside a hole is valid`() {
        val geometry = ParcelGeometry.fromWkt("MULTIPOLYGON (((0 0, 5 0, 5 5, 0 5, 0 0), (1 1, 4 1, 4 4, 1 4, 1 1)), ((2 2, 3 2, 3 3, 2 3, 2 2)))")
        assertTrue(geometry.contains(ParcelCoordinate(2.5, 2.5)))
        assertFalse(geometry.contains(ParcelCoordinate(1.5, 1.5)))
    }

    @Test fun `multipolygon may touch at a single corner`() {
        val geometry = ParcelGeometry.fromWkt("MULTIPOLYGON (((0 0, 1 0, 1 1, 0 1, 0 0)), ((1 1, 2 1, 2 2, 1 2, 1 1)))")
        assertTrue(geometry.contains(ParcelCoordinate(1.0, 1.0)))
    }

    @Test fun `spherical area is square metres with documented radius`() {
        val geometry = ParcelGeometry.fromWkt("POLYGON ((5 44, 5.001 44, 5.001 44.001, 5 44.001, 5 44))")
        val expected = 6_371_008.8 * 6_371_008.8 * Math.toRadians(0.001) *
            (sin(Math.toRadians(44.001)) - sin(Math.toRadians(44.0)))
        assertEquals(expected, geometry.areaSquareMetres, expected * 1e-8)
        assertTrue(geometry.areaSquareMetres in 8_800.0..9_000.0)
    }

    @Test fun `area subtracts holes independently of winding`() {
        val outer = ParcelGeometry.fromWkt("POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0))")
        val hole = ParcelGeometry.fromWkt("POLYGON ((1 1, 2 1, 2 2, 1 2, 1 1))")
        val forward = ParcelGeometry.fromWkt(squareWithHole)
        val reverse = ParcelGeometry.fromWkt("POLYGON ((0 0, 0 4, 4 4, 4 0, 0 0), (1 1, 1 2, 2 2, 2 1, 1 1))")
        assertEquals(outer.areaSquareMetres - hole.areaSquareMetres, forward.areaSquareMetres, 0.001)
        assertEquals(forward.areaSquareMetres, reverse.areaSquareMetres, 0.001)
    }

    @Test fun `geojson and wkt roundtrip preserve holes and precise coordinates`() {
        val source = ParcelGeometry.fromWkt("polygon ((2.123456789012345 46.123456789012345, 2.13 46.123456789012345, 2.13 46.13, 2.123456789012345 46.13, 2.123456789012345 46.123456789012345))")
        val jsonRoundtrip = ParcelGeometry.fromGeoJson(source.toGeoJson())
        val wktRoundtrip = ParcelGeometry.fromWkt(jsonRoundtrip.toWkt())
        assertEquals(source.bounds, wktRoundtrip.bounds)
        assertEquals(source.areaSquareMetres, wktRoundtrip.areaSquareMetres, 0.0)
        val withHole = ParcelGeometry.fromGeoJson(ParcelGeometry.fromWkt(squareWithHole).toGeoJson())
        assertFalse(withHole.contains(ParcelCoordinate(1.5, 1.5)))
        assertEquals(ParcelGeometry.fromWkt(squareWithHole).toWkt(), withHole.toWkt())
    }

    @Test fun `wkt scientific notation and whitespace accepted without locale dependence`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            val geometry = ParcelGeometry.fromWkt(" \n polygon\t((2e0 4.6e1, 2.01 46, 2.01 46.01, 2 46.01, 2 46)) \n ")
            assertEquals(ParcelGeometry.fromWkt(square).toWkt(), geometry.toWkt())
            assertTrue(geometry.toWkt().contains("2.01 46.01"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun `negative longitudes and signed zero are normalised`() {
        val geometry = ParcelGeometry.fromWkt("POLYGON ((-1 -0, 0 0, 0 1, -1 1, -1 0))")
        assertTrue(geometry.contains(ParcelCoordinate(-0.5, 0.5)))
        assertFalse(geometry.toWkt().contains("-0 "))
    }

    @Test fun `known explicit crs84 axes accepted`() {
        val json = """{"type":"Polygon","crs":{"type":"name","properties":{"name":"urn:ogc:def:crs:OGC::CRS84"}},"coordinates":[[[2,46],[2.01,46],[2.01,46.01],[2,46.01],[2,46]]]}"""
        assertEquals(ParcelGeometry.fromWkt(square).bounds, ParcelGeometry.fromGeoJson(json).bounds)
    }

    @Test fun `unknown or ambiguous declared crs rejected`() {
        listOf("EPSG:2154", "EPSG:4326", "unknown").forEach { crs ->
            rejected { ParcelGeometry.fromGeoJson("""{"type":"Polygon","crs":{"type":"name","properties":{"name":"$crs"}},"coordinates":[[[2,46],[2.01,46],[2.01,46.01],[2,46.01],[2,46]]]}""") }
        }
    }

    @Test fun `valid bbox may accompany real geometry but never replace it`() {
        val json = ParcelGeometry.fromWkt(square).toGeoJson().dropLast(1) + ",\"bbox\":[2,46,2.01,46.01]}"
        assertEquals(ParcelGeometry.fromWkt(square).bounds, ParcelGeometry.fromGeoJson(json).bounds)
        rejected { ParcelGeometry.fromGeoJson("""{"type":"Polygon","bbox":[2,46,2.01,46.01]}""") }
        rejected { ParcelGeometry.fromGeoJson(json.replace("\"bbox\":[2,46,2.01,46.01]", "\"bbox\":[0,0,1,1]")) }
    }

    @Test fun `feature line point and multipoint are not parcel polygons`() {
        listOf(
            """{"type":"Feature","geometry":{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,0]]]}}""",
            """{"type":"LineString","coordinates":[[0,0],[1,1]]}""",
            """{"type":"Point","coordinates":[0,0]}""",
            """{"type":"MultiPoint","coordinates":[[0,0],[1,1]]}""",
            """{"type":"FeatureCollection","features":[]}""",
        ).forEach { json -> rejected { ParcelGeometry.fromGeoJson(json) } }
        listOf("POINT (0 0)", "LINESTRING (0 0, 1 1)", "POLYGON EMPTY", "SRID=4326;$square", "POLYGON Z ((0 0 0, 1 0 0, 1 1 0, 0 0 0))").forEach { wkt ->
            rejected { ParcelGeometry.fromWkt(wkt) }
        }
    }

    @Test fun `quoted json coordinates third axis and malformed nesting rejected`() {
        listOf(
            """{"type":"Polygon","coordinates":[[["0",0],[1,0],[1,1],[0,0]]]}""",
            """{"type":"Polygon","coordinates":[[[0,0,1],[1,0,1],[1,1,1],[0,0,1]]]}""",
            """{"type":"Polygon","coordinates":[[0,0],[1,0],[1,1],[0,0]]}""",
            """{"type":"Polygon","coordinates":[]}""",
            """{"type":"MultiPolygon","coordinates":[]}""",
            """{"type":1,"coordinates":[]}""",
        ).forEach { json -> rejected { ParcelGeometry.fromGeoJson(json) } }
    }

    @Test fun `json syntax and wkt trailing or extra coordinate content rejected`() {
        listOf("", "{} trailing", "{type:Polygon}", "[", "{\"type\":\"Polygon\",\"coordinates\":[],}").forEach { json ->
            rejected { ParcelGeometry.fromGeoJson(json) }
        }
        listOf("$square trailing", "$square,$square", "POLYGON ((0 0 0, 1 0, 1 1, 0 0))", "POLYGON ((0+0, 1 0, 1 1, 0 0))", "POLYGON ((0 0, 1e 0, 1 1, 0 0))").forEach { wkt ->
            rejected { ParcelGeometry.fromWkt(wkt) }
        }
    }

    @Test fun `duplicate json members including escaped keys rejected`() {
        val coordinates = "[[[0,0],[1,0],[1,1],[0,0]]]"
        listOf(
            "{\"type\":\"Point\",\"type\":\"Polygon\",\"coordinates\":$coordinates}",
            "{\"type\":\"Point\",\"t\\u0079pe\":\"Polygon\",\"coordinates\":$coordinates}",
            "{\"type\":\"Polygon\",\"coordinates\":[],\"coordinates\":$coordinates}",
        ).forEach { json -> rejected { ParcelGeometry.fromGeoJson(json) } }
    }

    @Test fun `nonfinite out of range and projected coordinates rejected`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 181.0).forEach { longitude ->
            rejected { ParcelCoordinate(longitude, 0.0) }
        }
        rejected { ParcelCoordinate(0.0, 90.00001) }
        listOf(
            "POLYGON ((0 0, 181 0, 1 1, 0 0))",
            "POLYGON ((0 0, 1 0, 1 91, 0 0))",
            "POLYGON ((0 0, 1e999 0, 1 1, 0 0))",
            "POLYGON ((700000 6600000, 700001 6600000, 700001 6600001, 700000 6600000))",
            "POLYGON ((NaN 0, 1 0, 1 1, NaN 0))",
        ).forEach { wkt -> rejected { ParcelGeometry.fromWkt(wkt) } }
        rejected { ParcelGeometry.fromGeoJson("""{"type":"Polygon","coordinates":[[[0,0],[1e999,0],[1,1],[0,0]]]}""") }
    }

    @Test fun `rings must close and have at least three distinct noncollinear vertices`() {
        listOf(
            "POLYGON ((0 0, 1 0, 1 1, 0 1))",
            "POLYGON ((0 0, 1 0, 0 0))",
            "POLYGON ((0 0, 1 0, 1 0, 0 0))",
            "POLYGON ((0 0, 1 1, 2 2, 0 0))",
            "POLYGON ((0 0, 1 0, 1 0, 1 1, 0 0))",
        ).forEach { wkt -> rejected { ParcelGeometry.fromWkt(wkt) } }
    }

    @Test fun `self crossing self touching and adjacent backtracking rejected`() {
        listOf(
            "POLYGON ((0 0, 4 4, 0 4, 4 0, 5 2, 0 0))",
            "POLYGON ((0 0, 2 0, 2 2, 1 1, 0 2, 1 1, 0 0))",
            "POLYGON ((0 0, 2 0, 1 0, 1 1, 0 1, 0 0))",
        ).forEach { wkt -> rejected { ParcelGeometry.fromWkt(wkt) } }
    }

    @Test fun `monotonic collinear extra vertices remain valid`() {
        val geometry = ParcelGeometry.fromWkt("POLYGON ((0 0, 1 0, 2 0, 2 2, 0 2, 0 0))")
        assertTrue(geometry.contains(ParcelCoordinate(1.0, 1.0)))
    }

    @Test fun `outside touching crossing nested or overlapping holes rejected`() {
        listOf(
            "(3 3, 5 3, 5 5, 3 5, 3 3)",
            "(0 1, 1 1, 1 2, 0 2, 0 1)",
            "(1 1, 3 1, 3 3, 1 3, 1 1), (2 2, 3.5 2, 3.5 3.5, 2 3.5, 2 2)",
            "(1 1, 3 1, 3 3, 1 3, 1 1), (1.5 1.5, 2 1.5, 2 2, 1.5 2, 1.5 1.5)",
        ).forEach { holes -> rejected { ParcelGeometry.fromWkt("POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0), $holes)") } }
    }

    @Test fun `overlapping or shared edge multipolygon components rejected`() {
        listOf(
            "((0.5 0.5, 1.5 0.5, 1.5 1.5, 0.5 1.5, 0.5 0.5))",
            "((0.2 0.2, 0.8 0.2, 0.8 0.8, 0.2 0.8, 0.2 0.2))",
            "((1 0, 2 0, 2 1, 1 1, 1 0))",
            "((0 0, 1 0, 1 1, 0 1, 0 0))",
        ).forEach { second -> rejected { ParcelGeometry.fromWkt("MULTIPOLYGON (((0 0, 1 0, 1 1, 0 1, 0 0)), $second)") } }
    }

    @Test fun `antimeridian geometry rejected rather than selecting global bbox`() {
        rejected { ParcelGeometry.fromWkt("POLYGON ((179 0, -179 0, -179 1, 179 1, 179 0))") }
    }

    @Test fun `byte nesting ring polygon and total coordinate limits fail closed`() {
        rejected { ParcelGeometry.fromWkt(" ".repeat(2 * 1024 * 1024 + 1)) }
        rejected { ParcelGeometry.fromGeoJson("[".repeat(17) + "0" + "]".repeat(17)) }
        rejected { ParcelGeometry.fromWkt("POLYGON (" + circleRing(4_001, 0.0) + ")") }
        val tiny = "((0 0, 0.1 0, 0.1 0.1, 0 0))"
        rejected { ParcelGeometry.fromWkt(List(201) { tiny }.joinToString(",", "MULTIPOLYGON (", ")")) }
        rejected { ParcelGeometry.fromWkt((0..4).joinToString(",", "MULTIPOLYGON (", ")") { "(" + circleRing(1_601, it * 3.0) + ")" }) }
        val json = """{"type":"Polygon","coordinates":[${List(4_001) { "[0,0]" }.joinToString(",", "[", "]")}]}"""
        rejected { ParcelGeometry.fromGeoJson(json) }
    }

    @Test fun `qualified forest with more than two thousand vertices preserves exact geometry`() {
        val geometry = ParcelGeometry.fromWkt("POLYGON (" + circleRing(2_134, 0.0) + ")")
        assertEquals(geometry.toWkt(), ParcelGeometry.fromGeoJson(geometry.toGeoJson()).toWkt())
    }

    @Test fun `invalid first ring cannot hide malformed structure in a later ring`() {
        val mixed = """{"type":"Polygon","coordinates":[[[0,0],[1,1],[1,0],[0,1],[0,0]],[[0.2,0.2],[0.3,0.2],[0.3,0.3],[0.2,0.3]]]}"""
        val failure = runCatching { ParcelGeometry.fromGeoJson(mixed) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertFalse(failure is ParcelTopologyException)
    }

    @Test fun `repeated adjacent position is a reader compatibility failure rather than quarantinable topology`() {
        val duplicated = "POLYGON ((0 0, 1 0, 1 0, 1 1, 0 1, 0 0))"
        val failure = runCatching { ParcelGeometry.fromWkt(duplicated) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertFalse(failure is ParcelTopologyException)
    }

    @Test fun `invalid bounds cannot masquerade as a valid parcel`() {
        rejected { ParcelBounds(3.0, 46.0, 2.0, 47.0) }
        rejected { ParcelBounds(2.0, 47.0, 3.0, 46.0) }
        rejected { ParcelBounds(Double.NaN, 0.0, 1.0, 1.0) }
    }

    private fun circleRing(countIncludingClosure: Int, longitudeOffset: Double): String {
        val points = (0 until countIncludingClosure - 1).map { index ->
            val angle = 2 * PI * index / (countIncludingClosure - 1)
            "${longitudeOffset + cos(angle)} ${sin(angle)}"
        }
        return (points + points.first()).joinToString(",", "(", ")")
    }

    private fun rejected(block: () -> Unit) {
        try {
            block()
            fail("Expected geometry to fail closed")
        } catch (_: IllegalArgumentException) {
            // Expected validation failure, not Android framework or an arbitrary runtime exception.
        }
    }
}
