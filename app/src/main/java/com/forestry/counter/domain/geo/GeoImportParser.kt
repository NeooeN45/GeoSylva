package com.forestry.counter.domain.geo

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

/**
 * Parseur universel de formats géospatiaux → GeoJSON FeatureCollection.
 *
 * Formats supportés (sans dépendance externe) :
 *   • GeoJSON / JSON  — pass-through avec normalisation
 *   • KML             — extraction Placemark (Point, LineString, Polygon)
 *   • KMZ             — KML compressé ZIP
 *   • GPX             — waypoints, traces, routes
 *   • CSV coords      — colonnes lat/lon auto-détectées
 *   • GeoPackage      — SQLite, décodage WKB ISO/EWKB
 */
object GeoImportParser {

    private const val TAG = "GeoImportParser"
    private const val MAX_GPKG_ROWS = 8000

    data class ParseResult(
        val geoJson: String,
        val featureCount: Int,
        val displayName: String,
        val format: String,
        val warnings: List<String> = emptyList()
    )

    // ─── Détection de format ───────────────────────────────────────────────

    fun detect(context: Context, uri: Uri): String {
        val mime = context.contentResolver.getType(uri) ?: ""
        val seg  = uri.lastPathSegment?.lowercase() ?: ""
        return when {
            seg.endsWith(".geojson") || seg.endsWith(".json") -> "geojson"
            seg.endsWith(".kml")  -> "kml"
            seg.endsWith(".kmz")  -> "kmz"
            seg.endsWith(".gpx")  -> "gpx"
            seg.endsWith(".gpkg") -> "gpkg"
            seg.endsWith(".csv") || seg.endsWith(".txt") -> "csv"
            seg.endsWith(".zip")  -> "zip"
            mime.contains("json")       -> "geojson"
            mime.contains("kml")        -> "kml"
            mime.contains("gpx")        -> "gpx"
            mime.contains("geopackage") -> "gpkg"
            mime.contains("sqlite")     -> "gpkg"
            mime.contains("csv") || mime.contains("text/plain") -> "csv"
            else -> "unknown"
        }
    }

    // ─── Point d'entrée principal ──────────────────────────────────────────

    fun parse(context: Context, uri: Uri, displayName: String? = null): ParseResult? {
        val format   = detect(context, uri)
        val baseName = displayName
            ?: uri.lastPathSegment?.substringBeforeLast('.')?.ifBlank { null }
            ?: "Import"

        return try {
            when (format) {
                "geojson" -> {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
                    parseGeoJson(bytes.toString(detectBomEncoding(bytes)), baseName)
                }
                "kml" -> {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
                    parseKml(bytes.toString(Charsets.UTF_8), baseName)
                }
                "kmz" -> {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
                    val kmlText = extractKmlFromZip(bytes) ?: return null
                    parseKml(kmlText, baseName)
                }
                "gpx" -> {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
                    parseGpx(bytes.toString(Charsets.UTF_8), baseName)
                }
                "csv" -> {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
                    parseCsv(bytes.toString(detectBomEncoding(bytes)), baseName)
                }
                "gpkg" -> {
                    val tmp = File(context.cacheDir, "geoimport_${System.currentTimeMillis()}.gpkg")
                    context.contentResolver.openInputStream(uri)?.use { i ->
                        tmp.outputStream().use { o -> i.copyTo(o) }
                    } ?: return null
                    try { parseGeoPackage(tmp, baseName) } finally { tmp.delete() }
                }
                "zip" -> {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
                    val kmlText = extractKmlFromZip(bytes) ?: return null
                    parseKml(kmlText, baseName)
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "parse($format) failed", e)
            null
        }
    }

    // ─── GeoJSON ──────────────────────────────────────────────────────────

    private fun parseGeoJson(text: String, name: String): ParseResult {
        val stripped = text.trimStart('\uFEFF')
        val json = JSONObject(stripped)
        val type = json.optString("type")
        val fc: JSONObject
        val count: Int
        when (type) {
            "FeatureCollection" -> {
                fc = json
                count = json.optJSONArray("features")?.length() ?: 0
            }
            "Feature" -> {
                val arr = JSONArray().put(json)
                fc = JSONObject().apply { put("type","FeatureCollection"); put("features",arr) }
                count = 1
            }
            else -> {
                val feat = JSONObject().apply {
                    put("type","Feature"); put("geometry",json); put("properties",JSONObject())
                }
                val arr = JSONArray().put(feat)
                fc = JSONObject().apply { put("type","FeatureCollection"); put("features",arr) }
                count = 1
            }
        }
        return ParseResult(fc.toString(), count, name, "geojson")
    }

    // ─── KML ──────────────────────────────────────────────────────────────

    private fun parseKml(text: String, name: String): ParseResult {
        val features = JSONArray()
        val warnings = mutableListOf<String>()
        try {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(StringReader(text))
            }

            // ── Per-placemark state ──
            var inPlacemark  = false
            var geomType     = ""          // "Point" | "LineString" | "Polygon"
            var inOuterBound = false
            var inInnerBound = false
            var pmName  = ""
            var pmDesc  = ""
            val textBuf = StringBuilder()  // accumulator for TEXT events

            val pointCoords  = StringBuilder()
            val lineCoords   = StringBuilder()
            val outerRing    = StringBuilder()
            val innerRings   = mutableListOf<String>()
            var innerRingBuf = StringBuilder()

            fun localTag(raw: String) = raw.substringAfterLast(':').lowercase()

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        textBuf.clear()
                        val tag = localTag(parser.name)
                        when (tag) {
                            "placemark" -> {
                                inPlacemark = true
                                geomType = ""; pmName = ""; pmDesc = ""
                                pointCoords.clear(); lineCoords.clear()
                                outerRing.clear(); innerRings.clear()
                            }
                            "point"          -> if (inPlacemark) geomType = "Point"
                            "linestring"     -> if (inPlacemark) geomType = "LineString"
                            "polygon"        -> if (inPlacemark) geomType = "Polygon"
                            "outerboundaryis" -> inOuterBound = true
                            "innerboundaryis" -> { inInnerBound = true; innerRingBuf = StringBuilder() }
                        }
                    }
                    XmlPullParser.TEXT -> textBuf.append(parser.text ?: "")
                    XmlPullParser.END_TAG -> {
                        val tag  = localTag(parser.name)
                        val text = textBuf.toString().trim()
                        textBuf.clear()
                        when (tag) {
                            "name" -> if (inPlacemark && pmName.isBlank()) pmName = text
                            "description" -> if (inPlacemark && pmDesc.isBlank()) pmDesc = text
                            "coordinates" -> when {
                                geomType == "Point"      -> pointCoords.append(text)
                                geomType == "LineString" -> lineCoords.append(text)
                                inOuterBound             -> outerRing.append(text)
                                inInnerBound             -> innerRingBuf.append(text)
                            }
                            "outerboundaryis" -> inOuterBound = false
                            "innerboundaryis" -> {
                                inInnerBound = false
                                val s = innerRingBuf.toString().trim()
                                if (s.isNotBlank()) innerRings.add(s)
                            }
                            "placemark" -> {
                                val geom = when (geomType) {
                                    "Point"      -> kmlPoint(pointCoords.toString())
                                    "LineString" -> kmlLine(lineCoords.toString())
                                    "Polygon"    -> kmlPolygon(outerRing.toString(), innerRings)
                                    else         -> null
                                }
                                if (geom != null) {
                                    val props = JSONObject().apply {
                                        if (pmName.isNotBlank()) put("name", pmName)
                                        if (pmDesc.isNotBlank()) put("description", pmDesc)
                                    }
                                    features.put(JSONObject().apply {
                                        put("type","Feature"); put("geometry",geom); put("properties",props)
                                    })
                                }
                                inPlacemark = false
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            warnings.add("Avertissement KML : ${e.message?.take(100)}")
        }
        val fc = JSONObject().apply { put("type","FeatureCollection"); put("features",features) }
        return ParseResult(fc.toString(), features.length(), name, "kml", warnings)
    }

    private fun kmlPoint(raw: String): JSONObject? {
        val parts = raw.trim().split(Regex("[,\\s]+"))
        val lon = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
        val lat = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
        val c = JSONArray().apply { put(lon); put(lat)
            parts.getOrNull(2)?.toDoubleOrNull()?.let { put(it) } }
        return JSONObject().apply { put("type","Point"); put("coordinates",c) }
    }

    private fun kmlLine(raw: String): JSONObject? {
        val pts = kmlCoordList(raw)
        if (pts.size < 2) return null
        return JSONObject().apply { put("type","LineString"); put("coordinates", JSONArray(pts)) }
    }

    private fun kmlPolygon(outer: String, inner: List<String>): JSONObject? {
        val outerPts = kmlCoordList(outer)
        if (outerPts.size < 3) return null
        val rings = JSONArray().apply {
            put(JSONArray(outerPts))
            inner.forEach { s -> val r = kmlCoordList(s); if (r.size >= 3) put(JSONArray(r)) }
        }
        return JSONObject().apply { put("type","Polygon"); put("coordinates",rings) }
    }

    private fun kmlCoordList(raw: String): List<JSONArray> =
        raw.trim().split(Regex("\\s+")).mapNotNull { tuple ->
            val p = tuple.split(',')
            val lon = p.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
            val lat = p.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            JSONArray().apply { put(lon); put(lat); p.getOrNull(2)?.toDoubleOrNull()?.let { put(it) } }
        }

    // ─── GPX ──────────────────────────────────────────────────────────────

    private fun parseGpx(text: String, name: String): ParseResult {
        val features = JSONArray()
        val warnings = mutableListOf<String>()
        try {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(StringReader(text))
            }
            fun localTag(raw: String) = raw.substringAfterLast(':').lowercase()

            var inWpt = false; var inTrk = false; var inRte = false; var inTrkSeg = false
            var wptLat = 0.0; var wptLon = 0.0; var wptEle = ""
            var elemName = ""; var rteNam = ""
            val textBuf = StringBuilder()
            val trkPts  = mutableListOf<JSONArray>()
            val rtePts  = mutableListOf<JSONArray>()
            var curSection = ""   // "wpt"|"trk"|"rte"

            fun ptArr(lon: Double, lat: Double, ele: String?): JSONArray =
                JSONArray().apply { put(lon); put(lat); ele?.toDoubleOrNull()?.let { put(it) } }

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        textBuf.clear()
                        val tag = localTag(parser.name)
                        when (tag) {
                            "wpt" -> {
                                inWpt = true; curSection = "wpt"
                                wptLat = parser.getAttributeValue(null,"lat")?.toDoubleOrNull() ?: 0.0
                                wptLon = parser.getAttributeValue(null,"lon")?.toDoubleOrNull() ?: 0.0
                                elemName = ""; wptEle = ""
                            }
                            "trk"    -> { inTrk = true; curSection = "trk"; elemName = ""; trkPts.clear() }
                            "trkseg" -> inTrkSeg = true
                            "trkpt"  -> if (inTrkSeg) {
                                val la = parser.getAttributeValue(null,"lat")?.toDoubleOrNull() ?: 0.0
                                val lo = parser.getAttributeValue(null,"lon")?.toDoubleOrNull() ?: 0.0
                                trkPts.add(ptArr(lo, la, null))
                            }
                            "rte"    -> { inRte = true; curSection = "rte"; rteNam = ""; rtePts.clear() }
                            "rtept"  -> if (inRte) {
                                val la = parser.getAttributeValue(null,"lat")?.toDoubleOrNull() ?: 0.0
                                val lo = parser.getAttributeValue(null,"lon")?.toDoubleOrNull() ?: 0.0
                                rtePts.add(ptArr(lo, la, null))
                            }
                        }
                    }
                    XmlPullParser.TEXT -> textBuf.append(parser.text ?: "")
                    XmlPullParser.END_TAG -> {
                        val tag = localTag(parser.name)
                        val t   = textBuf.toString().trim(); textBuf.clear()
                        when (tag) {
                            "name" -> when (curSection) { "wpt" -> elemName = t; "trk" -> elemName = t; "rte" -> rteNam = t }
                            "ele"  -> if (inWpt) wptEle = t
                            "wpt"  -> {
                                val geom = JSONObject().apply {
                                    put("type","Point")
                                    put("coordinates", ptArr(wptLon, wptLat, wptEle.ifBlank { null }))
                                }
                                val props = JSONObject().apply { if (elemName.isNotBlank()) put("name", elemName) }
                                features.put(JSONObject().apply { put("type","Feature"); put("geometry",geom); put("properties",props) })
                                inWpt = false; curSection = ""
                            }
                            "trkseg" -> inTrkSeg = false
                            "trk" -> {
                                if (trkPts.size >= 2) {
                                    val geom = JSONObject().apply { put("type","LineString"); put("coordinates", JSONArray(trkPts)) }
                                    val props = JSONObject().apply { if (elemName.isNotBlank()) put("name", elemName) }
                                    features.put(JSONObject().apply { put("type","Feature"); put("geometry",geom); put("properties",props) })
                                }
                                inTrk = false; trkPts.clear(); curSection = ""
                            }
                            "rte"  -> {
                                if (rtePts.size >= 2) {
                                    val geom = JSONObject().apply { put("type","LineString"); put("coordinates", JSONArray(rtePts)) }
                                    val props = JSONObject().apply { if (rteNam.isNotBlank()) put("name", rteNam) }
                                    features.put(JSONObject().apply { put("type","Feature"); put("geometry",geom); put("properties",props) })
                                }
                                inRte = false; rtePts.clear(); curSection = ""
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            warnings.add("Avertissement GPX : ${e.message?.take(100)}")
        }
        val fc = JSONObject().apply { put("type","FeatureCollection"); put("features",features) }
        return ParseResult(fc.toString(), features.length(), name, "gpx", warnings)
    }

    // ─── CSV coords ───────────────────────────────────────────────────────

    private fun parseCsv(text: String, name: String): ParseResult {
        val stripped = text.trimStart('\uFEFF')
        val lines    = stripped.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyResult(name, "csv", listOf("Fichier CSV vide ou sans données."))

        val sep = when { lines[0].contains(';') -> ";"; lines[0].contains('\t') -> "\t"; else -> "," }
        val headers = lines[0].split(sep).map { it.trim().trim('"').lowercase() }

        val LAT_KEYS = setOf("lat","latitude","y","ylat","nord","n","northing")
        val LON_KEYS = setOf("lon","longitude","lng","x","xlon","est","e","long","easting")
        val NOM_KEYS = setOf("name","nom","id","label","libelle","libellé","titre","title")

        val latIdx = headers.indexOfFirst { it in LAT_KEYS }
        val lonIdx = headers.indexOfFirst { it in LON_KEYS }
        val nomIdx = headers.indexOfFirst { it in NOM_KEYS }

        if (latIdx < 0 || lonIdx < 0) {
            return emptyResult(name, "csv", listOf(
                "Colonnes lat/lon non détectées. En-têtes attendus : lat, lon (ou latitude/longitude/x/y/northing/easting)."))
        }

        val features = JSONArray()
        val warnings = mutableListOf<String>()
        var skipped  = 0

        lines.drop(1).forEachIndexed { i, line ->
            val cols = line.split(sep).map { it.trim().trim('"') }
            val lat  = cols.getOrNull(latIdx)?.replace(',', '.')?.toDoubleOrNull()
            val lon  = cols.getOrNull(lonIdx)?.replace(',', '.')?.toDoubleOrNull()
            if (lat == null || lon == null) { skipped++; return@forEachIndexed }
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) { skipped++; return@forEachIndexed }

            val geom  = JSONObject().apply { put("type","Point"); put("coordinates", JSONArray().apply { put(lon); put(lat) }) }
            val props = JSONObject()
            headers.forEachIndexed { idx, h ->
                val v = cols.getOrNull(idx); if (!v.isNullOrBlank() && idx != latIdx && idx != lonIdx) props.put(h, v)
            }
            if (nomIdx >= 0) cols.getOrNull(nomIdx)?.let { if (it.isNotBlank()) props.put("name", it) }

            features.put(JSONObject().apply { put("type","Feature"); put("geometry",geom); put("properties",props) })
        }

        if (skipped > 0) warnings.add("$skipped lignes ignorées (coordonnées manquantes ou hors plage).")
        if (features.length() == 0) warnings.add("Aucun point valide trouvé.")

        val fc = JSONObject().apply { put("type","FeatureCollection"); put("features",features) }
        return ParseResult(fc.toString(), features.length(), name, "csv", warnings)
    }

    // ─── GeoPackage ───────────────────────────────────────────────────────

    private fun parseGeoPackage(file: File, name: String): ParseResult {
        val features = JSONArray()
        val warnings = mutableListOf<String>()
        var resultName = name

        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            // Lister les couches features
            val tables = mutableListOf<Pair<String, String>>()
            db.rawQuery("SELECT table_name, identifier FROM gpkg_contents WHERE data_type='features'", null).use { c ->
                while (c.moveToNext()) tables.add(c.getString(0) to (c.getString(1) ?: c.getString(0)))
            }

            if (tables.isEmpty()) {
                warnings.add("Aucune couche vectorielle (features) dans ce GeoPackage.")
                return emptyResult(name, "gpkg", warnings)
            }
            if (tables.size == 1) resultName = tables[0].second.ifBlank { name }

            tables.forEach { (tableName, tableLabel) ->
                // Security: validate tableName against SQL injection (S-C3)
                // Only allow alphanumeric + underscore, reject sqlite_ system tables
                val safeTableName = tableName.trim()
                require(safeTableName.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$"))) {
                    "Invalid GeoPackage table name: $tableName"
                }
                require(!safeTableName.lowercase().startsWith("sqlite_")) {
                    "System table name rejected: $tableName"
                }

                // Colonne géométrie
                var geomCol = "geom"
                db.rawQuery("SELECT column_name FROM gpkg_geometry_columns WHERE table_name=?", arrayOf(safeTableName)).use { c ->
                    if (c.moveToFirst()) geomCol = c.getString(0)
                }

                // Use parameterized LIMIT via supportSQLiteQuery to avoid any injection vector
                db.rawQuery(
                    "SELECT * FROM \"$safeTableName\" LIMIT ?",
                    arrayOf(MAX_GPKG_ROWS.toString())
                ).use { row ->
                    val cols    = row.columnNames
                    val geomIdx = cols.indexOfFirst { it.equals(geomCol, ignoreCase = true) }
                    var n = 0
                    while (row.moveToNext()) {
                        val geomBytes = if (geomIdx >= 0 && !row.isNull(geomIdx)) row.getBlob(geomIdx) else null
                        val geomJson  = geomBytes?.let { safeDecodeGpkgGeometry(it) }

                        val props = JSONObject()
                        cols.forEachIndexed { idx, col ->
                            if (idx == geomIdx || row.isNull(idx)) return@forEachIndexed
                            runCatching { props.put(col, row.getString(idx)) }
                        }
                        if (tables.size > 1) props.put("_layer", tableLabel)

                        features.put(JSONObject().apply {
                            put("type","Feature")
                            put("geometry", geomJson ?: JSONObject.NULL)
                            put("properties", props)
                        })
                        n++
                    }
                    if (n >= MAX_GPKG_ROWS) warnings.add("Couche '$tableLabel' : limite $MAX_GPKG_ROWS entités atteinte.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "GeoPackage parse error", e)
            warnings.add("Erreur lecture GeoPackage : ${e.message?.take(120)}")
        } finally {
            db.close()
        }

        val fc = JSONObject().apply { put("type","FeatureCollection"); put("features",features) }
        return ParseResult(fc.toString(), features.length(), resultName, "gpkg", warnings)
    }

    // ─── Décodeur WKB GeoPackage ──────────────────────────────────────────

    private fun safeDecodeGpkgGeometry(bytes: ByteArray): JSONObject? = runCatching {
        if (bytes.size < 8) return null
        // GeoPackage binary header : GP(2) + version(1) + flags(1) + srs_id(4) = 8 bytes
        val flags   = bytes[3].toInt() and 0xFF
        val envType = (flags ushr 1) and 0x07
        val envSkip = when (envType) { 1 -> 32; 2, 3 -> 48; 4 -> 64; else -> 0 }
        val wkbStart = 8 + envSkip
        if (wkbStart >= bytes.size) return null

        val buf = ByteBuffer.wrap(bytes, wkbStart, bytes.size - wkbStart)
        decodeWkb(buf)
    }.getOrNull()

    private fun decodeWkb(buf: ByteBuffer): JSONObject? {
        if (buf.remaining() < 5) return null
        val byteOrder = buf.get().toInt() and 0xFF
        buf.order(if (byteOrder == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        val typeRaw   = buf.getInt()
        val hasZ      = (typeRaw and 0x80000000.toInt()) != 0 || ((typeRaw and 0xFFFF) in 1001..1007)
        val hasM      = (typeRaw and 0x40000000.toInt()) != 0
        val baseType  = typeRaw and 0x0000FFFF
        val geomType  = if (baseType > 1000) baseType - 1000 else baseType

        return when (geomType) {
            1 -> wkbPoint(buf, hasZ, hasM)
            2 -> wkbLine(buf, hasZ, hasM)
            3 -> wkbPolygon(buf, hasZ, hasM)
            4 -> wkbMulti(buf, "MultiPoint")
            5 -> wkbMulti(buf, "MultiLineString")
            6 -> wkbMulti(buf, "MultiPolygon")
            7 -> wkbGeomCollection(buf)
            else -> null
        }
    }

    private fun readPos(buf: ByteBuffer, hasZ: Boolean, hasM: Boolean): JSONArray {
        val x = buf.getDouble(); val y = buf.getDouble()
        if (hasZ) buf.getDouble()
        if (hasM) buf.getDouble()
        return JSONArray().apply { put(x); put(y) }
    }

    private fun readRing(buf: ByteBuffer, hasZ: Boolean, hasM: Boolean): JSONArray {
        val n = buf.getInt()
        return JSONArray().apply { repeat(n) { put(readPos(buf, hasZ, hasM)) } }
    }

    private fun wkbPoint(buf: ByteBuffer, hasZ: Boolean, hasM: Boolean): JSONObject =
        JSONObject().apply { put("type","Point"); put("coordinates", readPos(buf, hasZ, hasM)) }

    private fun wkbLine(buf: ByteBuffer, hasZ: Boolean, hasM: Boolean): JSONObject =
        JSONObject().apply { put("type","LineString"); put("coordinates", readRing(buf, hasZ, hasM)) }

    private fun wkbPolygon(buf: ByteBuffer, hasZ: Boolean, hasM: Boolean): JSONObject {
        val nRings = buf.getInt()
        val rings  = JSONArray().apply { repeat(nRings) { put(readRing(buf, hasZ, hasM)) } }
        return JSONObject().apply { put("type","Polygon"); put("coordinates", rings) }
    }

    private fun wkbMulti(buf: ByteBuffer, typeName: String): JSONObject {
        val n     = buf.getInt()
        val parts = JSONArray()
        repeat(n) {
            val sub = decodeWkb(buf)
            sub?.optJSONArray("coordinates")?.let { parts.put(it) }
                ?: sub?.optJSONArray("geometries")?.let { parts.put(it) }
        }
        return JSONObject().apply {
            put("type", typeName)
            put("coordinates", parts)
        }
    }

    private fun wkbGeomCollection(buf: ByteBuffer): JSONObject {
        val n    = buf.getInt()
        val geoms = JSONArray().apply { repeat(n) { decodeWkb(buf)?.let { put(it) } } }
        return JSONObject().apply { put("type","GeometryCollection"); put("geometries", geoms) }
    }

    // ─── Utilitaires ──────────────────────────────────────────────────────

    private fun extractKmlFromZip(bytes: ByteArray): String? {
        ZipInputStream(bytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".kml", ignoreCase = true)) {
                    return zis.readBytes().toString(Charsets.UTF_8)
                }
                entry = zis.nextEntry
            }
        }
        return null
    }

    private fun detectBomEncoding(bytes: ByteArray): java.nio.charset.Charset = when {
        bytes.size >= 3
                && bytes[0] == 0xEF.toByte()
                && bytes[1] == 0xBB.toByte()
                && bytes[2] == 0xBF.toByte() -> Charsets.UTF_8
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
        else -> Charsets.UTF_8
    }

    private fun emptyResult(name: String, format: String, warnings: List<String> = emptyList()) =
        ParseResult("""{"type":"FeatureCollection","features":[]}""", 0, name, format, warnings)
}
