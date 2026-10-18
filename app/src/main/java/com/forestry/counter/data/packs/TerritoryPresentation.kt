package com.forestry.counter.data.packs

import com.forestry.counter.capsule.DetachedManifestVerifier
import com.forestry.counter.capsule.json.StrictJsonParser
import java.net.URI
import java.time.OffsetDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Présentation éditoriale embarquée : distincte du catalogue et des archives GSIE. */
@Serializable
data class TerritoryPresentation(
    @SerialName("territory_id") val territoryId: String,
    val title: String,
    val description: String,
    val highlights: List<TerritoryHighlight>,
    val images: List<TerritoryImage>,
    val sources: List<TerritoryPresentationSource>,
    val emblem: TerritoryEmblem
)

@Serializable
data class TerritoryHighlight(val name: String, val kind: String, val summary: String, val sources: List<String>)

@Serializable
data class TerritoryImage(
    @SerialName("asset_path") val assetPath: String,
    val title: String,
    val author: String,
    @SerialName("license_spdx") val licenseSpdx: String,
    @SerialName("license_url") val licenseUrl: String,
    @SerialName("source_url") val sourceUrl: String,
    @SerialName("source_revision") val sourceRevision: String,
    val attribution: String,
    val sha256: String,
    val bytes: Long,
    val width: Int,
    val height: Int,
    val type: String = "ground_photo",
    val caption: String = "",
    val coordinates: List<Double>? = null,
    val zoom: Int? = null,
    @SerialName("tile_source_url") val tileSourceUrl: String? = null,
    @SerialName("catalogue_date_utc") val catalogueDateUtc: String? = null,
    @SerialName("capture_date") val captureDate: String? = null
)

@Serializable
data class TerritoryMediaRightsQualification(
    @SerialName("license_spdx") val licenseSpdx: String,
    @SerialName("qualified_utc") val qualifiedUtc: String,
    val sources: List<TerritoryPresentationSource>,
    @SerialName("commercial_use") val commercialUse: Boolean,
    val derivatives: Boolean,
    @SerialName("redistribution_apk") val redistributionApk: Boolean,
    @SerialName("bulk_restriction_respected") val bulkRestrictionRespected: Boolean,
    val description: String
)

internal data class TerritoryPresentationDocument(
    val territories: Map<String, TerritoryPresentation>,
    val rightsQualification: TerritoryMediaRightsQualification?
)

@Serializable
data class TerritoryPresentationSource(
    val url: String, val title: String, val publisher: String,
    @SerialName("retrieved_utc") val retrievedUtc: String
)

@Serializable
data class TerritoryEmblem(
    val kind: String, val source: String,
    @SerialName("license_spdx") val licenseSpdx: String,
    val reason: String
)

internal object TerritoryPresentationParser {
    private val json = Json { ignoreUnknownKeys = false }
    @Serializable private data class Index(
        @SerialName("schema_version") val schemaVersion: Int, val territories: List<TerritoryPresentation>,
        @SerialName("rights_qualification") val rightsQualification: TerritoryMediaRightsQualification? = null
    )
    private val commercialPhotoLicenses = setOf("CC-BY-4.0", "CC-BY-SA-4.0", "CC-BY-3.0", "CC-BY-SA-3.0", "CC0-1.0")

    fun parse(bytes: ByteArray): TerritoryPresentationDocument {
        require(bytes.size in 1..MAX_INDEX_BYTES) { "Index de présentation hors limites" }
        DetachedManifestVerifier.requireBoundedJsonDepth(bytes, 16)
        StrictJsonParser.parse(bytes)
        val index = json.decodeFromString<Index>(bytes.toString(Charsets.UTF_8))
        require(index.schemaVersion == 1 && index.territories.size <= 119)
        index.rightsQualification?.let { rights ->
            require(rights.licenseSpdx == "etalab-2.0" && rights.commercialUse && rights.derivatives && rights.redistributionApk && rights.bulkRestrictionRespected)
            require(OffsetDateTime.parse(rights.qualifiedUtc).offset.totalSeconds == 0)
            requireText(rights.description, 4000)
            require(rights.sources.size in 1..20)
            require(rights.sources.map { it.url }.distinct().size == rights.sources.size) { "Source de qualification dupliquée" }
            rights.sources.forEach { source -> requireHttps(source.url); requireText(source.title, 500); requireText(source.publisher, 200); require(OffsetDateTime.parse(source.retrievedUtc).offset.totalSeconds == 0) }
        }
        require(index.territories.map { it.territoryId }.distinct().size == index.territories.size) { "Territoire de présentation dupliqué" }
        for (territory in index.territories) {
            require(isKnownTerritoryId(territory.territoryId)) { "Identifiant territorial inconnu" }
            requireText(territory.title, 200); requireText(territory.description, 4000)
            require(territory.highlights.size <= 12 && territory.images.size in 1..2 && territory.sources.size in 1..20)
            require(territory.highlights.map { it.name }.distinct().size == territory.highlights.size) { "Repère de présentation dupliqué" }
            val sourceUrls = territory.sources.map { it.url }.toSet()
            require(sourceUrls.size == territory.sources.size) { "Source de présentation dupliquée" }
            require(territory.emblem.kind == "territory_outline" && territory.emblem.licenseSpdx == "etalab-2.0") {
                "Aucune autorisation de logo commercial n'est qualifiée dans cette version"
            }
            requireText(territory.emblem.source, 500); requireText(territory.emblem.reason, 1000)
            for (highlight in territory.highlights) {
                requireText(highlight.name, 200); requireText(highlight.kind, 100); requireText(highlight.summary, 1000)
                require(highlight.sources.size in 1..10); highlight.sources.forEach(::requireHttps)
                require(highlight.sources.distinct().size == highlight.sources.size && highlight.sources.all { it in sourceUrls }) { "Source de repère non déclarée ou dupliquée" }
            }
            for (source in territory.sources) {
                requireHttps(source.url); requireText(source.title, 500); requireText(source.publisher, 200)
                require(OffsetDateTime.parse(source.retrievedUtc).offset.totalSeconds == 0)
            }
            require(territory.images.map { it.assetPath }.distinct().size == territory.images.size)
            for (image in territory.images) {
                requireSafeAssetPath(image.assetPath)
                require(image.licenseSpdx in commercialPhotoLicenses || image.licenseSpdx == "etalab-2.0" && index.rightsQualification != null) { "Licence photo commerciale non qualifiée" }
                require(image.type == "ground_photo" || image.type == "aerial_view")
                if (image.licenseSpdx == "etalab-2.0") require(image.type == "aerial_view")
                if (image.type == "aerial_view") {
                    requireText(image.caption, 1500)
                    val coordinates = requireNotNull(image.coordinates)
                    require(coordinates.size == 2 && coordinates.all { it.isFinite() } && coordinates[0] in -180.0..180.0 && coordinates[1] in -90.0..90.0)
                    require(image.zoom?.let { it in 0..22 } == true)
                    requireHttps(requireNotNull(image.tileSourceUrl))
                    require(OffsetDateTime.parse(requireNotNull(image.catalogueDateUtc)).offset.totalSeconds == 0)
                    image.captureDate?.let { requireText(it, 200) }
                }
                requireHttps(image.licenseUrl); requireHttps(image.sourceUrl)
                requireText(image.author, 500); requireText(image.title, 500)
                requireText(image.attribution, 1500); requireText(image.sourceRevision, 200)
                require(image.sha256.matches(Regex("[0-9a-f]{64}")) && image.bytes in 1..MAX_PHOTO_BYTES)
                require(image.width in 1..4096 && image.height in 1..4096 && image.width.toLong() * image.height <= 16_777_216)
            }
        }
        return TerritoryPresentationDocument(index.territories.associateBy { it.territoryId }, index.rightsQualification)
    }

    fun requireSafeAssetPath(path: String) {
        require(path.matches(Regex("[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)*")) && path.split('/').none { it == "." || it == ".." } &&
            (path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png"))) { "Chemin de média local invalide" }
    }

    private fun requireText(value: String, maximum: Int) {
        require(value.isNotBlank() && value == value.trim() && value.length <= maximum) { "Texte de présentation invalide" }
    }
    private fun requireHttps(value: String) {
        val uri = URI(value)
        require(uri.scheme == "https" && !uri.host.isNullOrEmpty() && uri.userInfo == null && value.length <= 2000) { "Source HTTPS attendue" }
    }
    private fun isKnownTerritoryId(id: String): Boolean {
        val regions = setOf("01", "02", "03", "04", "06", "11", "24", "27", "28", "32", "44", "52", "53", "75", "76", "84", "93", "94")
        return when {
            id.startsWith("region:") -> id.substringAfter(':') in regions
            id.startsWith("department:") -> id.substringAfter(':') in DEPARTMENT_CODES
            else -> false
        }
    }
    private val DEPARTMENT_CODES = (1..95).filter { it != 20 }.map { it.toString().padStart(2, '0') }.toSet() + setOf("2A", "2B", "971", "972", "973", "974", "976")
    const val MAX_PHOTO_BYTES = 8L * 1024 * 1024
    const val MAX_INDEX_BYTES = 1024 * 1024
}
