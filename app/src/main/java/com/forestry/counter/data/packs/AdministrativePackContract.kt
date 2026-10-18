package com.forestry.counter.data.packs

import com.forestry.counter.capsule.DetachedManifestVerifier
import com.forestry.counter.capsule.json.StrictJsonParser
import com.forestry.counter.capsule.json.JsonValue
import com.forestry.counter.domain.model.pack.*
import java.io.File
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

enum class PackCatalogIssue { NOT_CONFIGURED, AUTH_REQUIRED, NETWORK, INVALID_RESPONSE, STORAGE, SERVER }

data class AdministrativePackContent(
    val boundariesJson: String,
    val labelsJson: String,
    val territoryId: String,
    val version: String,
    val attribution: String,
    val licence: String,
    val name: String = ""
)

@Serializable
internal data class SignedManifestDto(
    @SerialName("manifest_json") val manifestJson: String,
    @SerialName("signature_base64") val signatureBase64: String,
    @SerialName("key_id") val keyId: String
) {
    fun metadata() = SignedPackMetadata(manifestJson, signatureBase64, keyId)
}

@Serializable
internal data class CatalogDto(val items: List<SignedManifestDto>, val page: Int, val limit: Int, @SerialName("has_more") val hasMore: Boolean)

@Serializable
internal data class AdministrativeManifest(
    @SerialName("pack_id") val packId: String,
    val version: String,
    @SerialName("territory_id") val territoryId: String,
    val name: String,
    @SerialName("region_code") val regionCode: String? = null,
    @SerialName("content_kind") val contentKind: String,
    val format: String,
    @SerialName("archive_bytes") val archiveBytes: Long,
    val sha256: String,
    @SerialName("min_reader_version") val minReaderVersion: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("source_id") val sourceId: String,
    @SerialName("source_edition") val sourceEdition: String,
    val attribution: String,
    val licence: String,
    @SerialName("feature_count") val featureCount: Int,
    @SerialName("label_count") val labelCount: Int,
    @SerialName("download_path") val downloadPath: String,
    val layers: List<ForestryLayerManifest> = emptyList(),
    @SerialName("quarantined_count") val quarantinedCount: Int = 0
)

@Serializable
internal data class ForestryLayerManifest(
    val layer: String,
    @SerialName("source_id") val sourceId: String,
    @SerialName("source_edition") val sourceEdition: String,
    val attribution: String,
    val licence: String,
    @SerialName("feature_count") val featureCount: Int,
    @SerialName("acquisition_sha256") val acquisitionSha256: String,
    val coverage: String,
    @SerialName("quarantined_count") val quarantinedCount: Int = 0
)

/** Vérifie la confiance cryptographique avant toute acceptation ou lecture. */
internal class AdministrativePackContract(publicKeyDerBase64: String) {
    private val trustedDer = publicKeyDerBase64.takeIf(String::isNotBlank)?.let {
        runCatching { Base64.getDecoder().decode(it) }.getOrNull()?.takeIf { der ->
            der.size == 44 && der.copyOfRange(0, 12).contentEquals(byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00))
        }
    }
    val isConfigured: Boolean get() = trustedDer != null
    private val json = Json { ignoreUnknownKeys = false }

    fun verify(signed: SignedPackMetadata): AdministrativeManifest {
        val key = trustedDer ?: error("Clé publique de distribution GSIE non configurée")
        DetachedManifestVerifier.verify(key, signed.manifestJson.toByteArray(Charsets.UTF_8),
            Base64.getDecoder().decode(signed.signatureBase64), signed.keyId)
        val manifest = json.decodeFromString<AdministrativeManifest>(signed.manifestJson)
        require(manifest.packId.matches(Regex("[a-z0-9]+(?:[-_][a-z0-9]+)*")) && manifest.packId.length <= 200)
        require(manifest.version.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,49}")))
        require(manifest.territoryId.matches(Regex("region:[0-9]{2}|department:(?:[0-9]{2,3}|2A|2B)")))
        val forestry = manifest.contentKind == OfflineForestryLayerStore.CONTENT_KIND
        require(if (forestry) manifest.format in setOf(OfflineForestryLayerStore.FORMAT, OfflineForestryLayerStore.FORMAT_V2) &&
            manifest.minReaderVersion == if (manifest.format == OfflineForestryLayerStore.FORMAT_V2) 2 else 1
            else manifest.contentKind == CONTENT_KIND && manifest.format == FORMAT && manifest.minReaderVersion == 1)
        require(manifest.archiveBytes in 1..(if (forestry) OfflineForestryLayerStore.MAX_ARCHIVE_BYTES else MAX_ARCHIVE_BYTES) && manifest.sha256.matches(Regex("[0-9a-f]{64}")))
        if (forestry) verifyForestryLayers(manifest)
        else require(manifest.featureCount == 1 && manifest.labelCount == 1 && manifest.layers.isEmpty() && manifest.quarantinedCount == 0) { "Couverture administrative non supportée" }
        require(OffsetDateTime.parse(manifest.createdAt).offset.totalSeconds == 0)
        require(listOf(manifest.name, manifest.sourceId, manifest.sourceEdition, manifest.attribution, manifest.licence)
            .all { it.isNotBlank() && it == it.trim() })
        require(manifest.name.length <= 200 && manifest.attribution.length <= 1000 && manifest.licence.length <= 200)
        if (!forestry) require(manifest.sourceId == SOURCE_ID && manifest.sourceEdition == "2026" && manifest.licence == LICENCE && manifest.attribution == ATTRIBUTION)
        require(manifest.downloadPath == "/api/v1/packs/${manifest.packId}/versions/${manifest.version}/artifact")
        val code = manifest.territoryId.substringAfter(':')
        require(manifest.regionCode in REGION_CODES)
        require(if (manifest.territoryId.startsWith("region:")) code in REGION_CODES && code == manifest.regionCode else code in DEPARTMENT_CODES)
        return manifest
    }

    fun descriptor(signed: SignedPackMetadata, artifactUrl: String?): GeoPackDescriptor {
        val manifest = verify(signed)
        val regional = manifest.territoryId.startsWith("region:")
        return GeoPackDescriptor(
            id = manifest.packId, level = if (regional) PackLevel.REGIONAL else PackLevel.DEPARTEMENTAL,
            name = manifest.name, codeINSEE = manifest.territoryId.substringAfter(':'),
            parentId = if (regional) null else "fr-region-${manifest.regionCode}",
            version = manifest.version, buildDate = manifest.createdAt,
            sizeKb = (manifest.archiveBytes + 1023) / 1024, status = PackStatus.AVAILABLE,
            downloadUrl = artifactUrl, checksum = manifest.sha256, features = PackFeatures(),
            metaInfo = PackMetaInfo(author = "IGN / fournisseurs cités", license = manifest.layers.takeIf { it.isNotEmpty() }?.joinToString("\n") { "${it.layer} : ${it.licence}" } ?: manifest.licence, notes = manifest.layers.takeIf { it.isNotEmpty() }?.joinToString("\n") { it.attribution } ?: manifest.attribution, minAppVersion = ""),
            contentKind = manifest.contentKind, archiveBytes = manifest.archiveBytes, territoryId = manifest.territoryId,
            signedManifest = signed, forestryLayerCounts = manifest.layers.associate { it.layer to it.featureCount },
            forestryQuarantinedCounts = if (manifest.format == OfflineForestryLayerStore.FORMAT_V2) manifest.layers.associate { it.layer to it.quarantinedCount } else emptyMap()
        )
    }

    fun read(file: File, descriptor: GeoPackDescriptor, checkCancellation: () -> Unit = {}): AdministrativePackContent {
        val manifest = verify(descriptor.signedManifest ?: error("Manifeste signé du fichier absent"))
        require(manifest.contentKind == CONTENT_KIND) { "Ce pack contient des couches forestières SQLite, pas une archive administrative JSON" }
        require(descriptor.id == manifest.packId && descriptor.version == manifest.version && descriptor.checksum == manifest.sha256 &&
            descriptor.archiveBytes == manifest.archiveBytes && descriptor.contentKind == manifest.contentKind && descriptor.territoryId == manifest.territoryId) {
            "Descripteur divergent du manifeste signé"
        }
        require(file.isFile && file.length() == manifest.archiveBytes) { "Taille du fichier différente du manifeste" }
        checkCancellation()
        val bytes = file.readBytes()
        checkCancellation()
        require(MessageDigest.getInstance("SHA-256").digest(bytes).toHex() == manifest.sha256) { "Fichier cartographique altéré" }
        DetachedManifestVerifier.requireBoundedJsonDepth(bytes)
        requireFinite(StrictJsonParser.parse(bytes)) // doublons, UTF-8 et constantes non finies refusés
        val archive = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        require(archive.keys == setOf("pack_id", "version", "territory_id", "name", "region_code", "content_kind", "format", "boundaries", "labels", "provenance"))
        require(archive.string("pack_id") == manifest.packId && archive.string("version") == manifest.version &&
            archive.string("territory_id") == manifest.territoryId && archive.string("name") == manifest.name &&
            archive.string("region_code") == manifest.regionCode && archive.string("content_kind") == CONTENT_KIND && archive.string("format") == FORMAT)
        val boundaries = archive.getValue("boundaries").jsonObject
        val labels = archive.getValue("labels").jsonObject
        val boundary = feature(boundaries, manifest, label = false)
        val label = feature(labels, manifest, label = true)
        require(boundary.getValue("properties") == label.getValue("properties"))
        val point = position(label.getValue("geometry").jsonObject.getValue("coordinates"))
        require(covers(boundary.getValue("geometry").jsonObject, point)) { "Ancre hors du contour" }
        validateProvenance(archive.getValue("provenance").jsonObject, manifest)
        checkCancellation()
        return AdministrativePackContent(boundaries.toString(), labels.toString(), manifest.territoryId, manifest.version,
            manifest.attribution, manifest.licence, boundary.getValue("properties").jsonObject.string("name"))
    }

    fun validateArtifact(file: File, descriptor: GeoPackDescriptor, checkCancellation: () -> Unit = {}) {
        if (descriptor.contentKind == CONTENT_KIND) {
            read(file, descriptor, checkCancellation)
            return
        }
        val manifest = verify(descriptor.signedManifest ?: error("Manifeste signé absent"))
        require(manifest.contentKind == OfflineForestryLayerStore.CONTENT_KIND && descriptor.id == manifest.packId && descriptor.version == manifest.version &&
            descriptor.checksum == manifest.sha256 && descriptor.archiveBytes == manifest.archiveBytes && descriptor.contentKind == manifest.contentKind && descriptor.territoryId == manifest.territoryId)
        require(descriptor.forestryLayerCounts == manifest.layers.associate { it.layer to it.featureCount }) { "Comptes de couches divergents du manifeste" }
        require(descriptor.forestryQuarantinedCounts == if (manifest.format == OfflineForestryLayerStore.FORMAT_V2) manifest.layers.associate { it.layer to it.quarantinedCount } else emptyMap())
        require(file.isFile && !java.nio.file.Files.isSymbolicLink(file.toPath()) && file.length() == manifest.archiveBytes)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                checkCancellation()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        require(digest.digest().toHex() == manifest.sha256) { "Artefact forestier altéré" }
        OfflineForestryLayerStore.validateSQLite(file, manifest, checkCancellation)
    }

    private fun verifyForestryLayers(manifest: AdministrativeManifest) {
        require(manifest.quarantinedCount in 0..manifest.featureCount && manifest.quarantinedCount == manifest.layers.sumOf { it.quarantinedCount })
        if (manifest.format == OfflineForestryLayerStore.FORMAT) require(manifest.quarantinedCount == 0)
        require(manifest.labelCount == 0 && manifest.layers.size in 2..3 && manifest.layers.map { it.layer }.distinct().size == manifest.layers.size)
        require(manifest.layers.map { it.layer }.containsAll(listOf("cadastre", "public_forests")))
        for (layer in manifest.layers) {
            require(layer.quarantinedCount in 0..layer.featureCount && (layer.quarantinedCount == 0 || layer.layer == "onf_parcels"))
            require(layer.featureCount in 0..2_000_000 && layer.coverage == "complete" && layer.acquisitionSha256.matches(Regex("[0-9a-f]{64}")))
            val expectedSource = OfflineForestryLayerStore.QUALIFIED_SOURCES[layer.layer]
            require(expectedSource != null && layer.sourceId == expectedSource) { "Couche forestière non qualifiée pour usage commercial hors ligne" }
            require(layer.sourceEdition == "2026-10-03" && layer.attribution.isNotBlank() && layer.attribution.length <= 1000)
            if (layer.layer == "onf_parcels") require(layer.licence == OfflineForestryLayerStore.ONF_LICENCE && layer.attribution.contains(OfflineForestryLayerStore.ONF_ATTRIBUTION))
            else require(layer.licence == LICENCE)
        }
        require(manifest.featureCount.toLong() == manifest.layers.sumOf { it.featureCount.toLong() } && manifest.featureCount in 1..2_000_000)
        val primary = manifest.layers.single { it.layer == "cadastre" }
        require(manifest.sourceId == primary.sourceId && manifest.sourceEdition == primary.sourceEdition && manifest.attribution == primary.attribution && manifest.licence == primary.licence)
    }

    private fun feature(collection: JsonObject, manifest: AdministrativeManifest, label: Boolean): JsonObject {
        require(collection.keys == setOf("type", "features") && collection.string("type") == "FeatureCollection")
        val features = collection.getValue("features").jsonArray
        require(features.size == if (label) manifest.labelCount else manifest.featureCount)
        val feature = features.single().jsonObject
        require(feature.keys == setOf("type", "id", "properties", "geometry") && feature.string("type") == "Feature" && feature.string("id") == manifest.territoryId)
        val props = feature.getValue("properties").jsonObject
        require(props.keys == setOf("territory_id", "name", "code", "region_code") && props.string("territory_id") == manifest.territoryId &&
            props.string("code") == manifest.territoryId.substringAfter(':') && props.string("region_code") == manifest.regionCode &&
            manifest.name == "Limites administratives — ${props.string("name")}")
        val geometry = feature.getValue("geometry").jsonObject
        require(geometry.keys == setOf("type", "coordinates"))
        if (label) {
            require(geometry.string("type") == "Point"); position(geometry.getValue("coordinates"))
        } else {
            val polygons = polygons(geometry)
            require(polygons.isNotEmpty() && polygons.size <= 1000)
            var rings = 0; var positions = 0
            for (polygon in polygons) {
                require(polygon.jsonArray.isNotEmpty())
                rings += polygon.jsonArray.size; require(rings <= 1000)
                for (ring in polygon.jsonArray) {
                    val values = ring.jsonArray
                    require(values.size >= 4 && values.first() == values.last())
                    positions += values.size; require(positions <= 150_000)
                    values.forEach(::position)
                }
            }
        }
        return feature
    }

    private fun validateProvenance(provenance: JsonObject, manifest: AdministrativeManifest) {
        require(provenance.string("source_id") == manifest.sourceId && provenance.string("source_edition") == manifest.sourceEdition &&
            provenance.string("licence") == manifest.licence && provenance.string("attribution") == manifest.attribution &&
            provenance.string("edition") == "2026" && provenance.string("product") == "ADMIN EXPRESS COG CARTO PE" &&
            provenance.getValue("schema_version").jsonPrimitive.int == 1)
        val licence = provenance.getValue("license").jsonObject
        require(licence.string("spdx") == "etalab-2.0" && licence.string("data_gouv_license_id") == "lov2" &&
            licence.getValue("verified_catalogue_http_status").jsonPrimitive.int == 200 && licence.getValue("verified_catalogue_tls_verify_result").jsonPrimitive.int == 0)
        val coordinates = provenance.getValue("coordinates").jsonObject
        require(coordinates.getValue("order").jsonArray.map { it.jsonPrimitive.content } == listOf("longitude", "latitude") && coordinates.getValue("rfc7946_output").jsonPrimitive.boolean)
        val outputs = provenance.getValue("outputs").jsonObject
        for ((name, hash) in ASSET_HASHES) {
            val output = outputs.getValue(name).jsonObject
            require(output.string("asset_sha256") == hash && output.getValue("asset_bytes").jsonPrimitive.long > 0)
            if (name.endsWith("_labels")) {
                require(output.string("derived_from_sha256") == ASSET_HASHES.getValue(name.removeSuffix("_labels")))
            } else {
                require(output.string("layer") == "ADMINEXPRESS-COG-CARTO-PE.2026:${if (name == "regions") "region" else "departement"}" &&
                    output.getValue("http_status").jsonPrimitive.int == 200 && output.getValue("tls_verify_result").jsonPrimitive.int == 0 &&
                    output.string("source_sha256").matches(Regex("[0-9a-f]{64}")) &&
                    output.getValue("qualification").jsonObject.getValue("source_geometries_preserved_exactly").jsonPrimitive.boolean)
            }
        }
    }

    private fun polygons(geometry: JsonObject): JsonArray = when (geometry.string("type")) {
        "Polygon" -> JsonArray(listOf(geometry.getValue("coordinates")))
        "MultiPolygon" -> geometry.getValue("coordinates").jsonArray
        else -> error("Géométrie de contour non supportée")
    }
    private fun requireFinite(value: JsonValue) {
        when (value) {
            is JsonValue.JsonReal -> require(value.value.isFinite()) { "Nombre JSON non fini" }
            is JsonValue.Arr -> value.items.forEach(::requireFinite)
            is JsonValue.Obj -> value.entries.forEach { requireFinite(it.second) }
            else -> Unit
        }
    }
    private fun position(value: JsonElement): Pair<Double, Double> {
        val array = value.jsonArray
        require(array.size == 2 && array.all { it is JsonPrimitive && !it.isString })
        val x = array[0].jsonPrimitive.double; val y = array[1].jsonPrimitive.double
        require(x.isFinite() && y.isFinite() && x in -180.0..180.0 && y in -90.0..90.0)
        return x to y
    }
    private fun covers(geometry: JsonObject, point: Pair<Double, Double>): Boolean = polygons(geometry).any { polygon ->
        val rings = polygon.jsonArray
        inRing(rings.first().jsonArray, point) && rings.drop(1).none { inRing(it.jsonArray, point) }
    }
    private fun inRing(ring: JsonArray, point: Pair<Double, Double>): Boolean {
        var inside = false
        for (index in 1 until ring.size) {
            val a = position(ring[index - 1]); val b = position(ring[index])
            if ((a.second > point.second) != (b.second > point.second) &&
                point.first < (b.first - a.first) * (point.second - a.second) / (b.second - a.second) + a.first) inside = !inside
        }
        return inside
    }

    companion object {
        const val CONTENT_KIND = "administrative_boundaries"
        const val FORMAT = "gsie.admin.geojson.v1"
        const val MAX_ARCHIVE_BYTES = 4L * 1024 * 1024
        private const val SOURCE_ID = "ign-admin-express-cog-carto-pe-2026"
        private const val LICENCE = "Licence Ouverte / Etalab 2.0"
        private const val ATTRIBUTION = "© IGN · ADMIN EXPRESS COG CARTO PE 2026 · Licence Ouverte 2.0"
        private val REGION_CODES = setOf("01", "02", "03", "04", "06", "11", "24", "27", "28", "32", "44", "52", "53", "75", "76", "84", "93", "94")
        private val DEPARTMENT_CODES = (1..95).filter { it != 20 }.map { it.toString().padStart(2, '0') }.toSet() + setOf("2A", "2B", "971", "972", "973", "974", "976")
        private val ASSET_HASHES = mapOf(
            "regions" to "813d23920f9dfbd06af5b71d9e610b6a97a0b9f0380788008bc2d24b180472bd",
            "departments" to "3f25bf4c7b21d62b49ed6368b50f25ddbc6aa04fb4f62220f12d48e988860687",
            "regions_labels" to "207cc8fc9781325145f157cb04f23add2e887b3a5f14e8d6551091c6e34d4333",
            "departments_labels" to "109a9c4a409d918d5f5faa8c23bd873d94957665276e789087524cfac0e238f2"
        )
    }
}

private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
