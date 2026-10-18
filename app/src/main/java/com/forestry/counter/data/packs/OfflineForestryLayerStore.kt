package com.forestry.counter.data.packs

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.forestry.counter.BuildConfig
import com.forestry.counter.capsule.json.CanonicalJson
import com.forestry.counter.capsule.json.StrictJsonParser
import com.forestry.counter.domain.location.ParcelBounds
import com.forestry.counter.domain.location.ParcelGeometry
import com.forestry.counter.domain.model.pack.GeoPackDescriptor
import com.forestry.counter.domain.usecase.pack.PackInstallationStore
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.coroutines.coroutineContext

/** Objet public provenant d'un artefact GSIE actif, pas d'un dossier utilisateur. */
data class OfflineForestryFeature(
    val sourceId: String,
    val sourceFeatureId: String,
    val sourceLayer: String,
    val name: String?,
    val geometryGeoJson: String,
    val propertiesJson: String,
    val geometrySha256: String,
    val packId: String,
    val packVersion: String,
    val territoryId: String,
    val sourceEdition: String,
    val attribution: String,
)

data class OfflineForestryResult(
    val features: List<OfflineForestryFeature>,
    val layerAvailable: Boolean,
    val coveredTerritoryIds: Set<String>,
    val truncated: Boolean,
    /** Vrai seulement si un territoire installé couvre entièrement la fenêtre. */
    val fullyCovered: Boolean = false,
    /** IDs retained in installed packs but deliberately unavailable on the map. */
    val unavailableSourceIds: Set<String> = emptySet(),
)

/**
 * Lecture exclusivement depuis le registre d'installations atomique existant.
 * Une archive administrative ancienne n'est jamais annoncée comme un cadastre.
 * Signature, empreinte, schéma et provenance sont revérifiés avant lecture :
 * aucun fichier externe ou cache réseau non signé ne devient une couche locale.
 */
class OfflineForestryLayerStore(
    context: Context,
    publicKeyDerBase64: String = BuildConfig.GSIE_PACK_PUBLIC_KEY_DER_BASE64,
) {
    private val packDirectory = File(context.applicationContext.filesDir, "packs")
    private val installationStore = PackInstallationStore(packDirectory)
    private val contract = AdministrativePackContract(publicKeyDerBase64)

    suspend fun searchForests(query: String, limit: Int = 50): OfflineForestryResult {
        val normalized = searchKey(query.trim())
        require(normalized.length in 2..120) { "Recherche de forêt hors limites" }
        val escaped = normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return this.query("public_forests", null, limit, "f.search_name LIKE ? ESCAPE '\\'", arrayOf("%$escaped%"))
    }

    suspend fun loadForests(bounds: ParcelBounds, limit: Int = 200): OfflineForestryResult = query("public_forests", bounds, limit)

    suspend fun loadParcels(bounds: ParcelBounds, sourceLayer: String, limit: Int = 200): OfflineForestryResult {
        require(sourceLayer in setOf("cadastre", "onf_parcels")) { "Type de parcellaire non supporté" }
        if (sourceLayer !in QUALIFIED_SOURCES) return OfflineForestryResult(emptyList(), false, emptySet(), false)
        return query(sourceLayer, bounds, limit)
    }

    private suspend fun query(
        layer: String, bounds: ParcelBounds?, limit: Int,
        condition: String? = null, arguments: Array<String> = emptyArray(),
    ): OfflineForestryResult = withContext(Dispatchers.IO) {
        require(limit in 1..500) { "Limite d'objets cartographiques hors bornes" }
        if (!contract.isConfigured) return@withContext OfflineForestryResult(emptyList(), false, emptySet(), false)
        val currentContext = coroutineContext
        val features = linkedMapOf<Pair<String, String>, OfflineForestryFeature>()
        val covered = linkedSetOf<String>()
        val unavailable = linkedSetOf<String>()
        var available = false
        var truncated = false
        var fullyCovered = false
        // Un département est prioritaire sur son archive régionale redondante.
        val installed = installationStore.activePacks().filter { it.contentKind == CONTENT_KIND }
            .sortedWith(compareByDescending<GeoPackDescriptor> { it.level.priority }.thenBy { it.id })
        for (descriptor in installed) {
            currentContext.ensureActive()
            val manifest = contract.verify(descriptor.signedManifest ?: error("Signature de la couche locale absente"))
            val layerManifest = manifest.layers.singleOrNull { it.layer == layer } ?: continue
            val file = installationStore.fileFor(descriptor.id, descriptor.version)
            require(file.canonicalPath.startsWith(packDirectory.canonicalPath + File.separator)) { "Chemin de couche hors du stockage des packs" }
            validateOnce(file, descriptor, contract) { currentContext.ensureActive() }
            openReadOnly(file).use { database ->
                val territory = database.rawQuery("SELECT value FROM pack_metadata WHERE key='territory_geometry'", null).use { cursor ->
                    check(cursor.moveToFirst()); json.parseToJsonElement(cursor.getString(0)).jsonObject
                }
                if (bounds != null && !intersects(territoryBounds(territory), bounds)) return@use
                if (bounds != null && territoryCoversBounds(territory, bounds)) fullyCovered = true
                available = true
                covered += manifest.territoryId
                if (manifest.format == FORMAT_V2) database.rawQuery("SELECT source_feature_id FROM quarantined_features WHERE layer=? ORDER BY source_feature_id LIMIT 51", arrayOf(layer)).use { cursor ->
                    while (cursor.moveToNext()) {
                        if (unavailable.size >= 50) { truncated = true; break }
                        unavailable += cursor.getString(0)
                    }
                }
                val predicates = mutableListOf("f.layer=?")
                val args = mutableListOf(layer)
                if (bounds != null) {
                    predicates += "b.west<=? AND b.east>=? AND b.south<=? AND b.north>=?"
                    args += listOf(bounds.east, bounds.west, bounds.north, bounds.south).map { it.toString() }
                }
                if (condition != null) { predicates += condition; args += arguments }
                args += (limit + 1).toString()
                val sql = "SELECT f.source_id,f.source_feature_id,f.name,f.geometry_json,f.properties_json,f.geometry_sha256 FROM features f JOIN feature_bounds b ON b.id=f.id WHERE ${predicates.joinToString(" AND ")} ORDER BY f.source_feature_id LIMIT ?"
                database.rawQuery(sql, args.toTypedArray()).use { cursor ->
                    var count = 0
                    while (cursor.moveToNext()) {
                        currentContext.ensureActive()
                        count++
                        if (count > limit) { truncated = true; break }
                        val record = OfflineForestryFeature(
                            cursor.getString(0), cursor.getString(1), layer,
                            cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getString(5),
                            descriptor.id, descriptor.version, manifest.territoryId,
                            layerManifest.sourceEdition, layerManifest.attribution,
                        )
                        require(record.sourceId == layerManifest.sourceId)
                        ParcelGeometry.fromGeoJson(record.geometryGeoJson)
                        require(sha(record.geometryGeoJson.toByteArray(Charsets.UTF_8)) == record.geometrySha256)
                        val key = record.sourceId to record.sourceFeatureId
                        if (key !in features && features.size >= limit) { truncated = true; break }
                        features.putIfAbsent(key, record)
                    }
                }
            }
        }
        OfflineForestryResult(features.values.toList(), available, covered, truncated, fullyCovered, unavailable)
    }

    companion object {
        const val CONTENT_KIND = "forestry_layers"
        const val FORMAT = "gsie.forestry.sqlite.v1"
        const val FORMAT_V2 = "gsie.forestry.sqlite.v2"
        const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
        internal const val APPLICATION_ID = 0x47534631
        internal val QUALIFIED_SOURCES = mapOf(
            "cadastre" to "ign-parcellaire-express-wfs-2026",
            "public_forests" to "onf-public-forest-extents-wfs-2026",
            "onf_parcels" to "onf-public-forest-parcels-arcgis-2026",
        )
        internal const val ONF_LICENCE = "Utilisation libre sous réserve de mentionner la source (a minima le nom du producteur) et la date de sa dernière mise à jour."
        internal const val ONF_ATTRIBUTION = "Parcelles forestières publiques, © ONF Paris, 2026. Mise à jour publiée : 2026-03-22."
        private val json = Json { encodeDefaults = true }
        private val validatedFiles = ConcurrentHashMap<String, String>()

        internal fun validateOnce(file: File, descriptor: GeoPackDescriptor, contract: AdministrativePackContract, checkCancellation: () -> Unit = {}) {
            checkCancellation()
            require(file.isFile && !java.nio.file.Files.isSymbolicLink(file.toPath()))
            val signed = descriptor.signedManifest ?: error("Manifeste signé absent")
            contract.verify(signed) // La confiance cryptographique ne vient jamais du cache.
            val identity = listOf(descriptor.id, descriptor.version, descriptor.checksum, file.length(), file.lastModified(), sha(signed.manifestJson.toByteArray(Charsets.UTF_8)), signed.keyId).joinToString("|")
            val path = file.canonicalPath
            if (validatedFiles[path] != identity) {
                contract.validateArtifact(file, descriptor, checkCancellation)
                checkCancellation()
                validatedFiles[path] = identity
            }
        }

        internal fun administrativeProjection(file: File, manifest: AdministrativeManifest): AdministrativePackContent = openReadOnly(file).use { database ->
            fun value(key: String) = database.rawQuery("SELECT value FROM pack_metadata WHERE key=?", arrayOf(key)).use { cursor -> check(cursor.moveToFirst()); cursor.getString(0) }
            val name = value("territory_name")
            val props = buildJsonObject {
                put("territory_id", manifest.territoryId); put("name", name)
                put("code", manifest.territoryId.substringAfter(':')); put("region_code", requireNotNull(manifest.regionCode))
            }
            fun collection(geometry: String): String = buildJsonObject {
                put("type", "FeatureCollection")
                put("features", buildJsonArray { add(buildJsonObject {
                    put("type", "Feature"); put("id", manifest.territoryId); put("properties", props)
                    put("geometry", json.parseToJsonElement(geometry))
                }) })
            }.toString()
            AdministrativePackContent(collection(value("territory_geometry")), collection(value("territory_label")), manifest.territoryId, manifest.version,
                "© IGN · ADMIN EXPRESS COG CARTO PE 2026 · Licence Ouverte 2.0", "Licence Ouverte / Etalab 2.0", name)
        }

        private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        private fun rings(geometry: JsonObject): List<List<List<Double>>> {
            val coordinates = geometry.getValue("coordinates").jsonArray
            val polygons = when (geometry.getValue("type").jsonPrimitive.content) {
                "Polygon" -> listOf(coordinates)
                "MultiPolygon" -> coordinates.map { it.jsonArray }
                else -> error("Contour administratif non polygonal")
            }
            return polygons.flatMap { polygon -> polygon.map { ring -> ring.jsonArray.map { point -> point.jsonArray.map { it.jsonPrimitive.double } } } }
        }

        private fun territoryBounds(geometry: JsonObject): ParcelBounds {
            val points = rings(geometry).flatten()
            require(points.isNotEmpty() && points.size <= 150_000 && points.all { it.size == 2 && it[0].isFinite() && it[1].isFinite() && it[0] in -180.0..180.0 && it[1] in -90.0..90.0 })
            return ParcelBounds(points.minOf { it[0] }, points.minOf { it[1] }, points.maxOf { it[0] }, points.maxOf { it[1] })
        }

        private fun territoryCoversBounds(geometry: JsonObject, bounds: ParcelBounds): Boolean {
            val coordinates = geometry.getValue("coordinates").jsonArray
            val polygons = if (geometry.getValue("type").jsonPrimitive.content == "Polygon") listOf(coordinates) else coordinates.map { it.jsonArray }
            fun inside(ring: JsonArray, x: Double, y: Double): Boolean {
                var found = false
                for (index in 1 until ring.size) {
                    val a = ring[index - 1].jsonArray.map { it.jsonPrimitive.double }; val b = ring[index].jsonArray.map { it.jsonPrimitive.double }
                    if ((a[1] > y) != (b[1] > y) && x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]) found = !found
                }
                return found
            }
            val corners = listOf(bounds.west to bounds.south, bounds.west to bounds.north, bounds.east to bounds.south, bounds.east to bounds.north)
            if (!corners.all { (x, y) -> polygons.any { polygon -> inside(polygon.first().jsonArray, x, y) && polygon.drop(1).none { inside(it.jsonArray, x, y) } } }) return false
            // Couverture conservatrice : aucune limite extérieure/île/trou ne
            // doit entrer dans la fenêtre. Une union de packs reste non certifiée.
            for (ring in rings(geometry)) for (index in 1 until ring.size) {
                val a = ring[index - 1]; val b = ring[index]
                if (maxOf(a[0], b[0]) >= bounds.west && minOf(a[0], b[0]) <= bounds.east && maxOf(a[1], b[1]) >= bounds.south && minOf(a[1], b[1]) <= bounds.north) return false
            }
            return true
        }

        internal fun searchKey(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "").trim().replace(Regex("\\s+"), " ")

        private fun intersects(a: ParcelBounds, b: ParcelBounds): Boolean = a.west <= b.east && a.east >= b.west && a.south <= b.north && a.north >= b.south

        private fun openReadOnly(file: File): SQLiteDatabase = SQLiteDatabase.openDatabase(file.absolutePath, null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).also {
                it.execSQL("PRAGMA query_only=ON")
            }

        internal fun validateSQLite(file: File, manifest: AdministrativeManifest, checkCancellation: () -> Unit = {}) {
            checkCancellation()
            openReadOnly(file).use { database ->
                fun scalar(sql: String): String = database.rawQuery(sql, null).use { cursor -> check(cursor.moveToFirst()); cursor.getString(0) }
                require(scalar("PRAGMA application_id") == APPLICATION_ID.toString() && scalar("PRAGMA user_version") == if (manifest.format == FORMAT_V2) "2" else "1") { "Format forestier SQLite non supporté" }
                require(scalar("PRAGMA quick_check(1)") == "ok") { "Base forestière corrompue" }
                validatePortableSchema(database, manifest.format == FORMAT_V2)
                val metadata = mutableMapOf<String, String>()
                database.rawQuery("SELECT key,value FROM pack_metadata", null).use { cursor ->
                    while (cursor.moveToNext()) require(metadata.put(cursor.getString(0), cursor.getString(1)) == null)
                }
                require(metadata.keys == setOf("manifest", "territory_geometry", "territory_label", "territory_name", "territory_departments"))
                val expectedMetadata = json.encodeToJsonElement(AdministrativeManifest.serializer(), manifest).jsonObject.toMutableMap().apply {
                    remove("sha256"); remove("archive_bytes"); remove("created_at")
                    if (manifest.format != FORMAT_V2) {
                        remove("quarantined_count")
                        this["layers"] = JsonArray(manifest.layers.map { layer ->
                            JsonObject(json.encodeToJsonElement(ForestryLayerManifest.serializer(), layer).jsonObject.toMutableMap().apply { remove("quarantined_count") })
                        })
                    }
                }
                require(json.parseToJsonElement(requireNotNull(metadata["manifest"])) == JsonObject(expectedMetadata)) { "Identité de la base forestière divergente du manifeste" }
                val territoryGeometry = json.parseToJsonElement(requireNotNull(metadata["territory_geometry"])).jsonObject
                val territory = territoryBounds(territoryGeometry)
                val departmentCodes = json.parseToJsonElement(requireNotNull(metadata["territory_departments"])).jsonArray.map { it.jsonPrimitive.content }.toSet()
                require(departmentCodes.isNotEmpty() && departmentCodes.all { it.matches(Regex("[0-9]{2,3}|2A|2B")) })
                if (manifest.territoryId.startsWith("department:")) require(departmentCodes == setOf(manifest.territoryId.substringAfter(':')))
                val label = json.parseToJsonElement(requireNotNull(metadata["territory_label"])).jsonObject
                require(label.keys == setOf("type", "coordinates") && label.getValue("type") == JsonPrimitive("Point") && requireNotNull(metadata["territory_name"]).isNotBlank())
                val anchor = label.getValue("coordinates").jsonArray
                require(anchor.size == 2 && anchor.all { !it.jsonPrimitive.isString && it.jsonPrimitive.double.isFinite() })
                val point = com.forestry.counter.domain.location.ParcelCoordinate(anchor[0].jsonPrimitive.double, anchor[1].jsonPrimitive.double)
                require(territoryCoversBounds(territoryGeometry, ParcelBounds(point.longitude, point.latitude, point.longitude, point.latitude))) { "Territorial label outside covered geometry" }
                val counts = mutableMapOf<String, Int>()
                database.rawQuery("SELECT layer,count(*) FROM features GROUP BY layer", null).use { cursor ->
                    while (cursor.moveToNext()) counts[cursor.getString(0)] = cursor.getInt(1)
                }
                val quarantined = mutableMapOf<String, Int>()
                if (manifest.format == FORMAT_V2) database.rawQuery("SELECT layer,count(*) FROM quarantined_features GROUP BY layer", null).use { cursor ->
                    while (cursor.moveToNext()) quarantined[cursor.getString(0)] = cursor.getInt(1)
                }
                require((counts.keys + quarantined.keys).all { layer -> manifest.layers.any { it.layer == layer } } && manifest.layers.all {
                    counts.getOrDefault(it.layer, 0) + quarantined.getOrDefault(it.layer, 0) == it.featureCount && quarantined.getOrDefault(it.layer, 0) == it.quarantinedCount
                })
                require(scalar("SELECT count(*) FROM feature_bounds").toLong() == (manifest.featureCount - manifest.quarantinedCount).toLong())
                if (manifest.format == FORMAT_V2) validateQuarantined(database, manifest, departmentCodes, checkCancellation)
                database.rawQuery("SELECT f.layer,f.source_id,f.source_feature_id,f.forest_id,f.name,f.search_name,f.geometry_json,f.geometry_sha256,f.properties_json,b.west,b.east,b.south,b.north FROM features f LEFT JOIN feature_bounds b ON b.id=f.id", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        checkCancellation()
                        val layer = manifest.layers.single { it.layer == cursor.getString(0) }
                        require(cursor.getString(1) == layer.sourceId)
                        val geometryText = cursor.getString(6)
                        val propertiesText = cursor.getString(8)
                        require(geometryText.toByteArray(Charsets.UTF_8).size <= 2 * 1024 * 1024 && propertiesText.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
                        val geometry = ParcelGeometry.fromGeoJson(geometryText)
                        require(json.parseToJsonElement(geometryText).jsonObject.keys == setOf("type", "coordinates")) { "Unexpected offline geometry members" }
                        val properties = json.parseToJsonElement(propertiesText).jsonObject
                        fun property(key: String): String? = sourceText(properties[key])
                        val forestId = if (layer.layer == "cadastre") null else property("iidtn_frt")
                        if (layer.layer == "onf_parcels") property("llib_frt")
                        val id = when (layer.layer) {
                            "cadastre" -> property("idu")
                            "onf_parcels" -> { require(!forestId.isNullOrBlank() && !property("ccod_prf").isNullOrBlank()); "$forestId:${property("ccod_prf")}" }
                            else -> forestId
                        }
                        val name = when (layer.layer) {
                            "cadastre" -> listOfNotNull(property("section"), property("numero")).joinToString(" ").ifBlank { null }
                            "onf_parcels" -> property("ccod_prf")
                            else -> property("llib_frt")
                        }
                        require(!id.isNullOrBlank() && cursor.getString(2) == id && cursor.getString(4) == name && cursor.getString(5) == searchKey(name.orEmpty()))
                        require(cursor.getString(3) == if (layer.layer != "cadastre") forestId else null)
                        val hash = MessageDigest.getInstance("SHA-256").digest(geometryText.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                        require(hash == cursor.getString(7))
                        val bounds = geometry.bounds
                        require((9..12).all { cursor.getType(it) in setOf(android.database.Cursor.FIELD_TYPE_INTEGER, android.database.Cursor.FIELD_TYPE_FLOAT) }) { "Bornes spatiales absentes ou non numériques" }
                        val west = cursor.getDouble(9)
                        val east = cursor.getDouble(10)
                        val south = cursor.getDouble(11)
                        val north = cursor.getDouble(12)
                        require(listOf(west, east, south, north).all { it.isFinite() } && west in -180.0..180.0 && east in -180.0..180.0 && south in -90.0..90.0 && north in -90.0..90.0 && west <= east && south <= north) { "Bornes spatiales invalides" }
                        // Ordinary REAL columns preserve IEEE-754 doubles; unlike an
                        // optional RTree module, no expanded float32 bounds are used.
                        require(west == bounds.west && east == bounds.east && south == bounds.south && north == bounds.north) { "Bornes divergentes de la géométrie source" }
                        val department = properties[if (layer.layer == "cadastre") "code_dep" else "cinse_dep"]?.jsonPrimitive?.takeIf { it.isString }?.content
                        require(department in departmentCodes || intersects(territory, bounds)) { "Objet hors du territoire de la couche" }
                        // Parser strict protège UTF-8, doublons et profondeur ; la forme canonique
                        // assure que l'empreinte cite les octets de géométrie réellement conservés.
                        require(CanonicalJson.encode(StrictJsonParser.parse(geometryText.toByteArray(Charsets.UTF_8))).toString(Charsets.UTF_8) == geometryText)
                        require(CanonicalJson.encode(StrictJsonParser.parse(propertiesText.toByteArray(Charsets.UTF_8))).toString(Charsets.UTF_8) == propertiesText)
                    }
                }
            }
        }

        /** Geographic identifiers are strings; provenance objects retain their original bytes. */
        private fun sourceText(value: JsonElement?): String? {
            if (value == null || value == JsonNull) return null
            require(value is JsonPrimitive && value.isString) { "Geographic identity must be text" }
            val original = value.content
            require(original.length <= 300 && '\u0000' !in original && original.trim().isNotEmpty()) { "Invalid geographic identity text" }
            return original.trim()
        }

        /** Original invalid contours remain byte identifiable, never queryable as map features. */
        private fun validateQuarantined(database: SQLiteDatabase, manifest: AdministrativeManifest, departments: Set<String>, cancellation: () -> Unit) {
            database.rawQuery("SELECT 1 FROM features f JOIN quarantined_features q ON f.layer=q.layer AND f.source_id=q.source_id AND f.source_feature_id=q.source_feature_id LIMIT 1", null).use { require(!it.moveToFirst()) { "Duplicate usable/quarantined source identity" } }
            database.rawQuery("SELECT layer,source_id,source_feature_id,forest_id,name,original_feature_json,original_feature_sha256,reason_code,diagnostic FROM quarantined_features", null).use { cursor ->
                while (cursor.moveToNext()) {
                    cancellation()
                    val layer = manifest.layers.single { it.layer == cursor.getString(0) }
                    require(layer.layer == "onf_parcels" && cursor.getString(1) == layer.sourceId && cursor.getString(7) == "invalid_topology")
                    require(cursor.getString(8).length in 1..1000)
                    val original = cursor.getString(5)
                    require(original.toByteArray(Charsets.UTF_8).size in 1..(2 * 1024 * 1024))
                    require(sha(original.toByteArray(Charsets.UTF_8)) == cursor.getString(6)) { "Quarantined original checksum mismatch" }
                    require(CanonicalJson.encode(StrictJsonParser.parse(original.toByteArray(Charsets.UTF_8))).toString(Charsets.UTF_8) == original)
                    val feature = json.parseToJsonElement(original).jsonObject
                    require(feature["type"] == JsonPrimitive("Feature"))
                    val properties = feature.getValue("properties").jsonObject
                    require(properties.toString().toByteArray(Charsets.UTF_8).size <= 64 * 1024)
                    fun prop(key: String) = sourceText(properties[key])
                    prop("llib_frt")
                    require(!prop("iidtn_frt").isNullOrBlank() && !prop("ccod_prf").isNullOrBlank())
                    require(cursor.getString(2) == "${prop("iidtn_frt")}:${prop("ccod_prf")}" && cursor.getString(3) == prop("iidtn_frt") && cursor.getString(4) == prop("ccod_prf"))
                    require(properties["cinse_dep"]?.jsonPrimitive?.takeIf { it.isString }?.content in departments)
                    var invalidTopology = false
                    val rawGeometry = feature.getValue("geometry").jsonObject
                    require(rawGeometry.keys == setOf("type", "coordinates")) { "Unexpected quarantined geometry members" }
                    try { ParcelGeometry.fromGeoJson(rawGeometry.toString()) }
                    catch (_: com.forestry.counter.domain.location.ParcelTopologyException) { invalidTopology = true }
                    require(invalidTopology) { "Quarantine must contain a structurally valid but topologically invalid contour" }
                }
            }
        }

        /** The Android platform SQLite build does not guarantee the RTree extension. */
        private fun validatePortableSchema(database: SQLiteDatabase, version2: Boolean) {
            fun normalizedSql(sql: String): String = sql.trim().trimEnd(';').replace(Regex("\\s+"), "").lowercase(Locale.ROOT)
            val tables = mutableMapOf(
                "pack_metadata" to "CREATE TABLE pack_metadata(key TEXT PRIMARY KEY NOT NULL,value TEXT NOT NULL)",
                "features" to "CREATE TABLE features(id INTEGER PRIMARY KEY,layer TEXT NOT NULL,source_id TEXT NOT NULL,source_feature_id TEXT NOT NULL,forest_id TEXT,name TEXT,search_name TEXT NOT NULL,geometry_json TEXT NOT NULL,geometry_sha256 TEXT NOT NULL,properties_json TEXT NOT NULL,UNIQUE(layer,source_id,source_feature_id))",
                "feature_bounds" to "CREATE TABLE feature_bounds(id INTEGER PRIMARY KEY,west REAL NOT NULL,east REAL NOT NULL,south REAL NOT NULL,north REAL NOT NULL)",
            )
            if (version2) tables["quarantined_features"] = "CREATE TABLE quarantined_features(id INTEGER PRIMARY KEY,layer TEXT NOT NULL,source_id TEXT NOT NULL,source_feature_id TEXT NOT NULL,forest_id TEXT,name TEXT,original_feature_json TEXT NOT NULL,original_feature_sha256 TEXT NOT NULL,reason_code TEXT NOT NULL,diagnostic TEXT NOT NULL,UNIQUE(layer,source_id,source_feature_id))"
            val indexes = mapOf(
                "features_layer_name" to ("features" to listOf("layer", "search_name")),
                "features_forest" to ("features" to listOf("layer", "forest_id")),
                "idx_feature_bounds_west_east" to ("feature_bounds" to listOf("west", "east", "id")),
                "idx_feature_bounds_south_north" to ("feature_bounds" to listOf("south", "north", "id")),
            )
            val autoIndexes = mutableMapOf(
                "sqlite_autoindex_pack_metadata_1" to ("pack_metadata" to listOf("key")),
                "sqlite_autoindex_features_1" to ("features" to listOf("layer", "source_id", "source_feature_id")),
            )
            if (version2) autoIndexes["sqlite_autoindex_quarantined_features_1"] = "quarantined_features" to listOf("layer", "source_id", "source_feature_id")
            val foundTables = mutableSetOf<String>()
            val foundIndexes = mutableSetOf<String>()
            val foundAutoIndexes = mutableSetOf<String>()
            database.rawQuery("SELECT type,name,tbl_name,sql FROM sqlite_master", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val type = cursor.getString(0)
                    val name = cursor.getString(1)
                    val table = cursor.getString(2)
                    when {
                        type == "table" && name in tables -> {
                            require(table == name && !cursor.isNull(3) && normalizedSql(cursor.getString(3)) == normalizedSql(tables.getValue(name))) { "Définition de table forestière incompatible" }
                            foundTables += name
                        }
                        type == "index" && name in indexes -> {
                            val (expectedTable, columns) = indexes.getValue(name)
                            require(table == expectedTable && !cursor.isNull(3) && normalizedSql(cursor.getString(3)) == normalizedSql("CREATE INDEX $name ON $expectedTable(${columns.joinToString(",")})")) { "Définition d'index forestier incompatible" }
                            foundIndexes += name
                        }
                        type == "index" && name in autoIndexes -> {
                            require(table == autoIndexes.getValue(name).first && cursor.isNull(3)) { "Contrainte d'identité forestière incompatible" }
                            foundAutoIndexes += name
                        }
                        else -> error("Objet SQLite non autorisé")
                    }
                }
            }
            require(foundTables == tables.keys && foundIndexes == indexes.keys && foundAutoIndexes == autoIndexes.keys) { "Schéma forestier incompatible" }
            val expectedBoundsColumns = listOf(
                Triple("id", "INTEGER", 0 to 1), Triple("west", "REAL", 1 to 0),
                Triple("east", "REAL", 1 to 0), Triple("south", "REAL", 1 to 0), Triple("north", "REAL", 1 to 0),
            )
            val actualBoundsColumns = mutableListOf<Triple<String, String, Pair<Int, Int>>>()
            database.rawQuery("PRAGMA table_info(feature_bounds)", null).use { cursor ->
                while (cursor.moveToNext()) {
                    require(cursor.getInt(0) == actualBoundsColumns.size && cursor.isNull(4))
                    actualBoundsColumns += Triple(cursor.getString(1), cursor.getString(2), cursor.getInt(3) to cursor.getInt(5))
                }
            }
            require(actualBoundsColumns == expectedBoundsColumns) { "Colonnes spatiales incompatibles" }
            for ((name, specification) in indexes + autoIndexes) {
                val columns = mutableListOf<String>()
                // Names are constants in the whitelist above, never SQL supplied by a pack.
                database.rawQuery("PRAGMA index_info($name)", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        require(cursor.getInt(0) == columns.size && !cursor.isNull(2))
                        columns += cursor.getString(2)
                    }
                }
                require(columns == specification.second) { "Colonnes d'index forestier incompatibles" }
            }
        }
    }
}
