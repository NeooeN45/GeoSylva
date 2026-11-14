package com.forestry.counter.data.cartography

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.forestry.counter.domain.geo.GeoImportParser
import com.forestry.counter.domain.geo.GeoImportCoordinatePolicy
import com.forestry.counter.domain.geo.GeoImportCoordinateException
import com.forestry.counter.domain.geo.GeoImportRejection
import com.forestry.counter.domain.model.cartography.*
import com.forestry.counter.domain.repository.*
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.*

internal class LayerFailure(val error: LayerStoreError) : RuntimeException()
internal fun layerFail(error: LayerStoreError): Nothing = throw LayerFailure(error)
internal fun layerHash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
internal fun layerHash(value: String): String = layerHash(value.toByteArray(Charsets.UTF_8))

/** Bounds untrusted input before invoking the existing parser; preserves original bytes. */
internal class LayerImportStager(private val context: Context) {
    fun parse(bytes: ByteArray, filename: String, format: LayerImportFormat, recordedAt: Long): CartographicLayer {
        if (bytes.isEmpty()) layerFail(LayerStoreError.EMPTY)
        if (bytes.size > MAX_SOURCE_BYTES) layerFail(LayerStoreError.LIMIT_EXCEEDED)
        if (filename.isBlank() || filename.length > 240 || filename.any(Char::isISOControl)) layerFail(LayerStoreError.INVALID_INPUT)
        val safeName = filename.substringAfterLast('/').substringAfterLast('\\')
        var sourceCrs: String? = null
        var sourceOrder: String? = null
        val file = File.createTempFile("layer_staging_", ".${format.extension}", context.cacheDir)
        try {
            file.writeBytes(bytes)
            if (format == LayerImportFormat.KMZ) validateArchive(bytes)
            if (format in setOf(LayerImportFormat.KML, LayerImportFormat.GPX)) validateXml(bytes)
            val typedProperties = if (format == LayerImportFormat.GEOPACKAGE) geoPackageProperties(file) else null
            val normalized = if (format == LayerImportFormat.CSV) {
                sourceCrs = "EPSG:4326"
                sourceOrder = "named_latitude_longitude_columns"
                parseCsv(bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF"))
            } else {
                if (format in setOf(LayerImportFormat.GEOJSON, LayerImportFormat.KML, LayerImportFormat.KMZ, LayerImportFormat.GPX)) {
                    sourceCrs = "EPSG:4326"
                    sourceOrder = if (format == LayerImportFormat.GPX) "named_latitude_longitude_attributes" else "longitude_latitude"
                }
                val parsed = GeoImportParser.parse(context, Uri.fromFile(file), safeName)
                    ?: layerFail(LayerStoreError.INVALID_GEOMETRY)
                if (parsed.rejectionReason != null) {
                    val error = if (parsed.rejectionReason == context.getString(com.forestry.counter.R.string.cartography_import_unsupported_crs))
                        LayerStoreError.UNSUPPORTED_CRS else LayerStoreError.INVALID_GEOMETRY
                    layerFail(error)
                }
                // Existing extractors report skipped/truncated/unsupported objects. Never silently accept a partial import.
                if (parsed.warnings.isNotEmpty()) layerFail(LayerStoreError.INVALID_GEOMETRY)
                if (parsed.geoJson.length > MAX_NORMALIZED_CHARACTERS) layerFail(LayerStoreError.LIMIT_EXCEEDED)
                Json.parseToJsonElement(parsed.geoJson).jsonObject
            }
            val inputFeatures = normalized["features"] as? JsonArray ?: layerFail(LayerStoreError.INVALID_GEOMETRY)
            if (inputFeatures.isEmpty()) layerFail(LayerStoreError.EMPTY)
            if (inputFeatures.size > MAX_FEATURES) layerFail(LayerStoreError.LIMIT_EXCEEDED)
            if (typedProperties != null && inputFeatures.size != typedProperties.first.size) layerFail(LayerStoreError.INVALID_GEOMETRY)
            if (typedProperties != null) {
                sourceCrs = typedProperties.second.singleOrNull()
                sourceOrder = if (sourceCrs == "EPSG:4326") "longitude_latitude" else if (sourceCrs == "EPSG:2154") "easting_northing" else null
            }
            val features = inputFeatures.mapIndexed { index, value ->
                var feature = value as? JsonObject ?: layerFail(LayerStoreError.INVALID_GEOMETRY)
                if (feature["type"] != JsonPrimitive("Feature")) layerFail(LayerStoreError.INVALID_GEOMETRY)
                val properties = typedProperties?.first?.get(index) ?: feature["properties"]?.takeUnless { it == JsonNull } ?: JsonObject(emptyMap())
                if (properties !is JsonObject || properties.keys.any { it.length > MAX_FIELD_LENGTH || it.any(Char::isISOControl) }) layerFail(LayerStoreError.INVALID_INPUT)
                feature = JsonObject(feature + ("properties" to properties))
                LayerFeature("f-$index", feature)
            }
            LayerGeometryValidation.validate(features)
            val id = UUID.randomUUID().toString()
            val label = features.flatMap { it.properties.keys }.firstOrNull { it.equals("code", true) }
                ?: features.flatMap { it.properties.keys }.firstOrNull { it.lowercase() in setOf("name", "nom", "id") }
            return CartographicLayer(id, safeName, LayerSource(safeName, format.name, layerHash(bytes),
                Base64.getEncoder().encodeToString(bytes), sourceCrs, sourceOrder,
                warnings = if (format in setOf(LayerImportFormat.KML, LayerImportFormat.KMZ)) listOf("KML_BASIC_ATTRIBUTES") else emptyList()),
                features, LayerStyle(labelField = label), recordedAt = recordedAt)
        } catch (failure: LayerFailure) { throw failure }
        catch (failure: GeoImportCoordinateException) {
            layerFail(if (failure.reason == GeoImportRejection.UNSUPPORTED_CRS) LayerStoreError.UNSUPPORTED_CRS else LayerStoreError.INVALID_GEOMETRY)
        } catch (_: Exception) { layerFail(LayerStoreError.INVALID_GEOMETRY) }
        finally { file.delete() }
    }

    private fun validateXml(bytes: ByteArray) {
        val text = bytes.toString(Charsets.UTF_8)
        if (Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(text)) layerFail(LayerStoreError.INVALID_INPUT)
    }

    private fun validateArchive(bytes: ByteArray) {
        var count = 0
        var total = 0L
        var kmlCount = 0
        val names = mutableSetOf<String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            val buffer = ByteArray(8192)
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++count > MAX_ZIP_ENTRIES) layerFail(LayerStoreError.LIMIT_EXCEEDED)
                val name = entry.name.replace('\\', '/')
                if (name.startsWith('/') || Regex("^[a-zA-Z]:").containsMatchIn(name) ||
                    name.split('/').any { it == ".." } || name.any(Char::isISOControl) || !names.add(name)) layerFail(LayerStoreError.UNSAFE_ARCHIVE)
                var entryBytes = 0L
                val kml = name.endsWith(".kml", true) && !entry.isDirectory
                val kmlOutput = if (kml) java.io.ByteArrayOutputStream() else null
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    entryBytes += read
                    total += read
                    if (entryBytes > MAX_SOURCE_BYTES || total > MAX_ZIP_EXPANDED_BYTES) layerFail(LayerStoreError.LIMIT_EXCEEDED)
                    kmlOutput?.write(buffer, 0, read)
                }
                if (kml) { kmlCount++; validateXml(requireNotNull(kmlOutput).toByteArray()) }
                zip.closeEntry()
            }
        }
        if (kmlCount != 1) layerFail(LayerStoreError.UNSAFE_ARCHIVE)
    }

    /** Read types from SQLite itself; the legacy geometry parser stringifies and drops null attributes. */
    private fun geoPackageProperties(file: File): Pair<List<JsonObject>, Set<String>> {
        val properties = mutableListOf<JsonObject>()
        val crs = mutableSetOf<String>()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val tables = mutableListOf<Pair<String, String>>()
            db.rawQuery("SELECT table_name, identifier FROM gpkg_contents WHERE data_type='features'", null).use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0)
                    if (!name.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*")) || name.startsWith("sqlite_", true)) layerFail(LayerStoreError.INVALID_INPUT)
                    tables += name to (c.getString(1) ?: name)
                }
            }
            if (tables.size !in 1..MAX_GPKG_TABLES) layerFail(LayerStoreError.LIMIT_EXCEEDED)
            tables.forEach { (table, label) ->
                val geomColumn = db.rawQuery("SELECT g.column_name,s.organization,s.organization_coordsys_id FROM gpkg_geometry_columns g JOIN gpkg_spatial_ref_sys s ON s.srs_id=g.srs_id WHERE g.table_name=?", arrayOf(table)).use { c ->
                    if (!c.moveToFirst()) layerFail(LayerStoreError.UNSUPPORTED_CRS)
                    val code = GeoImportCoordinatePolicy.requireSupportedEpsg(c.getString(1).orEmpty(), c.getInt(2))
                    crs += "EPSG:$code"
                    val column = c.getString(0)
                    if (c.moveToNext()) layerFail(LayerStoreError.INVALID_GEOMETRY)
                    column
                }
                db.rawQuery("SELECT * FROM \"$table\"", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        if (properties.size >= MAX_FEATURES) layerFail(LayerStoreError.LIMIT_EXCEEDED)
                        val values = cursor.columnNames.mapIndexedNotNull { index, name ->
                            if (name.equals(geomColumn, true)) null else name to when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_NULL -> JsonNull
                                Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(cursor.getLong(index))
                                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index).let { if (!it.isFinite()) layerFail(LayerStoreError.INVALID_INPUT); JsonPrimitive(it) }
                                Cursor.FIELD_TYPE_BLOB -> buildJsonObject { put("encoding", "base64"); put("value", Base64.getEncoder().encodeToString(cursor.getBlob(index))) }
                                else -> JsonPrimitive(cursor.getString(index))
                            }
                        }.toMap().toMutableMap()
                        if (tables.size > 1) values["_layer"] = JsonPrimitive(label)
                        properties += JsonObject(values)
                    }
                }
            }
        }
        return properties to crs
    }

    /** CSV values remain text, including empty strings; only explicitly named lat/lon columns are coordinates. */
    private fun parseCsv(text: String): JsonObject {
        val firstLine = text.lineSequence().firstOrNull() ?: layerFail(LayerStoreError.EMPTY)
        val separator = listOf(';', '\t', ',').maxBy { firstLine.count { character -> character == it } }
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var afterQuote = false
        var index = 0
        fun finishCell() { row += cell.toString(); cell.clear(); afterQuote = false }
        fun finishRow() { finishCell(); if (row.any(String::isNotEmpty)) rows += row; row = mutableListOf() }
        while (index < text.length) {
            val char = text[index++]
            if (quoted) {
                if (char == '"') {
                    if (text.getOrNull(index) == '"') { cell.append('"'); index++ } else { quoted = false; afterQuote = true }
                } else cell.append(char)
            } else when {
                char == '"' && cell.isEmpty() && !afterQuote -> quoted = true
                char == separator -> finishCell()
                char == '\n' -> finishRow()
                char == '\r' -> { if (text.getOrNull(index) == '\n') index++; finishRow() }
                afterQuote -> layerFail(LayerStoreError.INVALID_INPUT)
                char == '"' -> layerFail(LayerStoreError.INVALID_INPUT)
                else -> cell.append(char)
            }
            if (rows.size > MAX_FEATURES + 1 || cell.length > MAX_NORMALIZED_CHARACTERS) layerFail(LayerStoreError.LIMIT_EXCEEDED)
        }
        if (quoted) layerFail(LayerStoreError.INVALID_INPUT)
        if (cell.isNotEmpty() || row.isNotEmpty() || afterQuote) finishRow()
        val header = rows.firstOrNull()?.map(String::trim) ?: layerFail(LayerStoreError.EMPTY)
        if (header.any(String::isBlank) || header.distinct().size != header.size) layerFail(LayerStoreError.INVALID_INPUT)
        val lat = header.indexOfFirst { it.lowercase() in setOf("lat", "latitude") }
        val lon = header.indexOfFirst { it.lowercase() in setOf("lon", "lng", "longitude") }
        if (lat < 0 || lon < 0) layerFail(LayerStoreError.UNSUPPORTED_CRS)
        val features = rows.drop(1).map { cells ->
            if (cells.size != header.size) layerFail(LayerStoreError.INVALID_INPUT)
            val latitude = cells[lat].trim().replace(',', '.').toDoubleOrNull() ?: layerFail(LayerStoreError.INVALID_GEOMETRY)
            val longitude = cells[lon].trim().replace(',', '.').toDoubleOrNull() ?: layerFail(LayerStoreError.INVALID_GEOMETRY)
            GeoImportCoordinatePolicy.longitudeLatitude(longitude, latitude, 4326)
            buildJsonObject {
                put("type", "Feature")
                put("geometry", buildJsonObject { put("type", "Point"); put("coordinates", JsonArray(listOf(JsonPrimitive(longitude), JsonPrimitive(latitude)))) })
                put("properties", JsonObject(header.mapIndexed { i, name -> name to JsonPrimitive(cells[i]) }.toMap()))
            }
        }
        return buildJsonObject { put("type", "FeatureCollection"); put("features", JsonArray(features)) }
    }

    companion object {
        const val MAX_SOURCE_BYTES = 8 * 1024 * 1024
        const val MAX_NORMALIZED_CHARACTERS = 20 * 1024 * 1024
        const val MAX_FEATURES = 7999 // Existing GeoPackage parser warns/truncates at 8000 rows.
        const val MAX_FIELD_LENGTH = 200
        private const val MAX_ZIP_EXPANDED_BYTES = 16 * 1024 * 1024
        private const val MAX_ZIP_ENTRIES = 128
        private const val MAX_GPKG_TABLES = 32
    }
}

internal object LayerGeometryValidation {
    fun validate(features: List<LayerFeature>) {
        var positions = 0
        fun position(value: JsonElement): JsonArray {
            val array = value as? JsonArray ?: layerFail(LayerStoreError.INVALID_GEOMETRY)
            if (array.size !in 2..3 || ++positions > MAX_POSITIONS) layerFail(LayerStoreError.LIMIT_EXCEEDED)
            val values = array.map { it as? JsonPrimitive ?: layerFail(LayerStoreError.INVALID_GEOMETRY) }
            if (values.any { it.isString || it.doubleOrNull?.isFinite() != true }) layerFail(LayerStoreError.INVALID_GEOMETRY)
            GeoImportCoordinatePolicy.longitudeLatitude(requireNotNull(values[0].doubleOrNull), requireNotNull(values[1].doubleOrNull), 4326)
            return array
        }
        fun line(value: JsonElement, ring: Boolean) {
            val values = value as? JsonArray ?: layerFail(LayerStoreError.INVALID_GEOMETRY)
            if (values.size < if (ring) 4 else 2) layerFail(LayerStoreError.INVALID_GEOMETRY)
            values.forEach(::position)
            if (ring && values.first() != values.last()) layerFail(LayerStoreError.INVALID_GEOMETRY)
        }
        fun polygon(value: JsonElement) {
            val values = value as? JsonArray ?: layerFail(LayerStoreError.INVALID_GEOMETRY)
            if (values.isEmpty()) layerFail(LayerStoreError.INVALID_GEOMETRY)
            values.forEach { line(it, true) }
        }
        fun geometry(value: JsonElement?, depth: Int) {
            if (depth > 16) layerFail(LayerStoreError.LIMIT_EXCEEDED)
            if (value == null || value == JsonNull) return
            val obj = value as? JsonObject ?: layerFail(LayerStoreError.INVALID_GEOMETRY)
            fun coordinates() = obj["coordinates"] ?: layerFail(LayerStoreError.INVALID_GEOMETRY)
            fun children(value: JsonElement, block: (JsonElement) -> Unit) {
                val children = value as? JsonArray ?: layerFail(LayerStoreError.INVALID_GEOMETRY)
                if (children.isEmpty()) layerFail(LayerStoreError.INVALID_GEOMETRY)
                children.forEach(block)
            }
            when ((obj["type"] as? JsonPrimitive)?.content) {
                "Point" -> position(coordinates())
                "MultiPoint" -> children(coordinates()) { position(it) }
                "LineString" -> line(coordinates(), false)
                "MultiLineString" -> children(coordinates()) { line(it, false) }
                "Polygon" -> polygon(coordinates())
                "MultiPolygon" -> children(coordinates(), ::polygon)
                "GeometryCollection" -> children(obj["geometries"] ?: layerFail(LayerStoreError.INVALID_GEOMETRY)) { geometry(it, depth + 1) }
                else -> layerFail(LayerStoreError.INVALID_GEOMETRY)
            }
        }
        features.forEach { geometry(it.sourceFeature["geometry"], 0) }
    }
    private const val MAX_POSITIONS = 200_000
}
