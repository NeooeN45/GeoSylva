package com.forestry.counter.domain.geo

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GeoImportCoordinatePolicyTest {
    private fun point(x: Double, y: Double, srsId: Int = 4326, order: ByteOrder = ByteOrder.LITTLE_ENDIAN): ByteArray =
        ByteBuffer.allocate(29).order(order).apply {
            put('G'.code.toByte()); put('P'.code.toByte()); put(0)
            put(if (order == ByteOrder.LITTLE_ENDIAN) 1.toByte() else 0.toByte())
            putInt(srsId)
            put(if (order == ByteOrder.LITTLE_ENDIAN) 1.toByte() else 0.toByte())
            putInt(1); putDouble(x); putDouble(y)
        }.array()

    private fun rejected(reason: GeoImportRejection = GeoImportRejection.INVALID_GEOMETRY, action: () -> Unit) {
        try { action(); fail("Import must be refused") }
        catch (e: GeoImportCoordinateException) { assertEquals(reason, e.reason) }
    }

    @Test fun `WGS84 coordinates remain longitude latitude for either byte order and custom SRS identifier`() {
        for (order in listOf(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) {
            val geometry = GeoPackageGeometryDecoder.decode(point(2.5, 45.5, 42, order), 42, 4326)
            assertEquals("Point", geometry.getValue("type").jsonPrimitive.content)
            assertEquals(listOf(2.5, 45.5), geometry.getValue("coordinates").jsonArray.map { it.jsonPrimitive.double })
        }
    }

    @Test fun `Lambert93 false origin is transformed rather than rendered as degrees`() {
        // EPSG:2154 projection origin is exactly E=700000, N=6600000 at 3°E, 46.5°N.
        val geometry = GeoPackageGeometryDecoder.decode(point(700000.0, 6600000.0, 2154), 2154, 2154)
        val coordinates = geometry.getValue("coordinates").jsonArray
        assertEquals(3.0, coordinates[0].jsonPrimitive.double, 1e-8)
        assertEquals(46.5, coordinates[1].jsonPrimitive.double, 1e-8)
    }

    @Test fun `unknown authority or EPSG never falls back to geographic coordinates`() {
        rejected(GeoImportRejection.UNSUPPORTED_CRS) { GeoImportCoordinatePolicy.requireSupportedEpsg("NONE", 4326) }
        rejected(GeoImportRejection.UNSUPPORTED_CRS) { GeoPackageGeometryDecoder.decode(point(2.5,45.5), 4326, 3857) }
        rejected(GeoImportRejection.UNSUPPORTED_CRS) { GeoPackageGeometryDecoder.decode(point(2.5,45.5), 4326, 0) }
    }

    @Test fun `inconsistent SRS malformed header unsupported dimensions and trailing bytes are refused`() {
        rejected { GeoPackageGeometryDecoder.decode(point(2.5,45.5,2154), 4326, 4326) }
        rejected { GeoPackageGeometryDecoder.decode(point(2.5,45.5).also { it[0]=0 }, 4326,4326) }
        rejected { GeoPackageGeometryDecoder.decode(point(2.5,45.5).also { it[2]=1 }, 4326,4326) }
        rejected { GeoPackageGeometryDecoder.decode(point(2.5,45.5).also { it[3]=33 }, 4326,4326) }
        rejected { GeoPackageGeometryDecoder.decode(point(2.5,45.5).also { it[8]=2 }, 4326,4326) }
        rejected { GeoPackageGeometryDecoder.decode(point(2.5,45.5).also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(9,1001) },4326,4326) }
        rejected { GeoPackageGeometryDecoder.decode(point(2.5,45.5).copyOf(28),4326,4326) }
        rejected { GeoPackageGeometryDecoder.decode(point(2.5,45.5)+byteArrayOf(0),4326,4326) }
    }

    @Test fun `nonfinite and projected coordinates cannot masquerade as longitude latitude`() {
        for ((x,y) in listOf(Double.NaN to 45.5, 2.5 to Double.POSITIVE_INFINITY, 700000.0 to 6600000.0)) {
            rejected { GeoPackageGeometryDecoder.decode(point(x,y),4326,4326) }
        }
    }

    @Test fun `GeoJSON checks nested CRS declarations and coordinates but preserves property metadata`() {
        fun check(json: String) = GeoImportCoordinatePolicy.validateGeoJson(Json.parseToJsonElement(json).jsonObject)
        check("""{"type":"Feature","properties":{"crs":"user metadata"},"geometry":{"type":"Point","coordinates":[2.5,45.5]}}""")
        check("""{"type":"Point","crs":{"type":"name","properties":{"name":"OGC:CRS84"}},"coordinates":[2.5,45.5]}""")
        rejected(GeoImportRejection.UNSUPPORTED_CRS) { check("""{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","crs":{"type":"name","properties":{"name":"EPSG:2154"}},"coordinates":[700000,6600000]}}]}""") }
        rejected { check("""{"type":"Point","coordinates":[700000,6600000]}""") }
    }

    @Test fun `nested WKB preserves geometry types and refuses wrong multipoint children`() {
        val child = point(2.5,45.5).drop(8).toByteArray()
        val multi = ByteBuffer.allocate(17+child.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(point(2.5,45.5).copyOf(8)); put(1); putInt(4); putInt(1); put(child)
        }.array()
        val geometry = GeoPackageGeometryDecoder.decode(multi,4326,4326)
        assertEquals("MultiPoint",geometry.getValue("type").jsonPrimitive.content)
        assertEquals(listOf(2.5,45.5),geometry.getValue("coordinates").jsonArray.single().jsonArray.map { it.jsonPrimitive.double })
        rejected { GeoPackageGeometryDecoder.decode(multi.also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(18,2) },4326,4326) }
    }
}
