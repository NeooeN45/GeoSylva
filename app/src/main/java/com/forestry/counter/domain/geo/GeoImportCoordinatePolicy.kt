package com.forestry.counter.domain.geo

import com.forestry.counter.domain.location.Lambert93Converter
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal enum class GeoImportRejection { UNSUPPORTED_CRS, INVALID_GEOMETRY }

internal class GeoImportCoordinateException(val reason: GeoImportRejection) :
    IllegalArgumentException(reason.name)

/** Explicit coordinate contracts; no projection is inferred from coordinate magnitudes. */
internal object GeoImportCoordinatePolicy {
    fun requireSupportedEpsg(organization: String, code: Int): Int {
        if (!organization.equals("EPSG", ignoreCase = true) || code !in setOf(4326, 2154)) {
            throw GeoImportCoordinateException(GeoImportRejection.UNSUPPORTED_CRS)
        }
        return code
    }

    fun longitudeLatitude(x: Double, y: Double, epsg: Int): Pair<Double, Double> {
        requireSupportedEpsg("EPSG", epsg)
        if (!x.isFinite() || !y.isFinite()) invalid()
        val point = if (epsg == 2154) Lambert93Converter.toWGS84(x, y) else x to y
        if (!point.first.isFinite() || !point.second.isFinite() ||
            point.first !in -180.0..180.0 || point.second !in -90.0..90.0 ||
            (epsg == 2154 && !Lambert93Converter.isInFranceMetro(point.first, point.second))
        ) invalid()
        return point
    }

    /** RFC 7946 longitude/latitude, or the unambiguous legacy CRS84 declaration. */
    fun validateGeoJson(root: JsonObject) {
        fun visit(node: JsonObject, depth: Int) {
            if (depth > 16) invalid()
            node["crs"]?.let { declaration ->
                val crs = declaration as? JsonObject
                val properties = crs?.get("properties") as? JsonObject
                if (crs?.get("type") != JsonPrimitive("name") ||
                    properties?.get("name") !in setOf(JsonPrimitive("OGC:CRS84"),
                        JsonPrimitive("urn:ogc:def:crs:OGC::CRS84"), JsonPrimitive("CRS:84"))
                ) throw GeoImportCoordinateException(GeoImportRejection.UNSUPPORTED_CRS)
            }
            fun coordinates(value: JsonElement, nesting: Int) {
                if (nesting > 8) invalid()
                val array = value as? JsonArray ?: invalid()
                if (array.isEmpty()) return
                if (array.first() is JsonArray) array.forEach { coordinates(it, nesting + 1) }
                else {
                    if (array.size !in 2..3) invalid()
                    val values = array.map {
                        val number = it as? JsonPrimitive ?: invalid()
                        if (number.isString) invalid()
                        number.doubleOrNull?.takeIf(Double::isFinite) ?: invalid()
                    }
                    longitudeLatitude(values[0], values[1], 4326)
                }
            }
            node["coordinates"]?.let { coordinates(it, 0) }
            node["geometry"]?.takeUnless { it == JsonNull }?.let { visit(it as? JsonObject ?: invalid(), depth + 1) }
            for (key in listOf("features", "geometries")) {
                node[key]?.let { children ->
                    (children as? JsonArray ?: invalid()).forEach { visit(it as? JsonObject ?: invalid(), depth + 1) }
                }
            }
        }
        visit(root, 0)
    }

    private fun invalid(): Nothing = throw GeoImportCoordinateException(GeoImportRejection.INVALID_GEOMETRY)
}

/**
 * OGC GeoPackage standard binary geometry, 2D WKB types 1–7.
 * Header srs_id must match the layer's registered SRS; EPSG comes from
 * gpkg_spatial_ref_sys, not from assuming that srs_id is an EPSG identifier.
 * https://www.geopackage.org/spec/#gpb_format
 * Z/M, extended WKB, unknown SRS and malformed data are explicitly refused.
 */
internal object GeoPackageGeometryDecoder {
    fun decode(bytes: ByteArray, layerSrsId: Int, epsg: Int): JsonObject {
        GeoImportCoordinatePolicy.requireSupportedEpsg("EPSG", epsg)
        try {
            require(bytes.size in 13..2_000_000)
            val buffer = ByteBuffer.wrap(bytes)
            require(buffer.get() == 'G'.code.toByte() && buffer.get() == 'P'.code.toByte())
            require(buffer.get() == 0.toByte())
            val flags = buffer.get().toInt() and 0xff
            require(flags and 0xf0 == 0) // Standard, non-empty, no reserved bits.
            val envelope = (flags ushr 1) and 7
            require(envelope in 0..1) // Only 2D geometries in this importer.
            buffer.order(if (flags and 1 == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
            require(buffer.int == layerSrsId)
            if (envelope == 1) repeat(4) { require(buffer.double.isFinite()) }
            var positions = 0
            fun count(minimum: Int): Int = buffer.int.also { require(it in minimum..100_000) }
            fun position(): JsonArray {
                require(++positions <= 100_000)
                val (lon, lat) = GeoImportCoordinatePolicy.longitudeLatitude(buffer.double, buffer.double, epsg)
                return JsonArray(listOf(JsonPrimitive(lon), JsonPrimitive(lat)))
            }
            fun geometry(depth: Int, expectedType: Int? = null): JsonObject {
                require(depth <= 16)
                val byteOrder = buffer.get().toInt() and 0xff
                require(byteOrder in 0..1)
                buffer.order(if (byteOrder == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
                val type = buffer.int
                require(type in 1..7 && (expectedType == null || type == expectedType))
                val name = listOf("Point", "LineString", "Polygon", "MultiPoint", "MultiLineString", "MultiPolygon", "GeometryCollection")[type - 1]
                val data: JsonElement = when (type) {
                    1 -> position()
                    2 -> JsonArray(List(count(2)) { position() })
                    3 -> JsonArray(List(count(1)) {
                        val ring = List(count(4)) { position() }
                        require(ring.first() == ring.last())
                        JsonArray(ring)
                    })
                    else -> JsonArray(List(count(1)) {
                        val child = geometry(depth + 1, if (type == 7) null else type - 3)
                        if (type == 7) child else requireNotNull(child["coordinates"])
                    })
                }
                return buildJsonObject {
                    put("type", name)
                    put(if (type == 7) "geometries" else "coordinates", data)
                }
            }
            val result = geometry(0)
            require(!buffer.hasRemaining())
            return result
        } catch (e: GeoImportCoordinateException) {
            throw e
        } catch (e: RuntimeException) {
            throw GeoImportCoordinateException(GeoImportRejection.INVALID_GEOMETRY)
        }
    }
}
