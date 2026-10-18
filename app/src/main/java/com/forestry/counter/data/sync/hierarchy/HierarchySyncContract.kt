package com.forestry.counter.data.sync.hierarchy

import com.forestry.counter.data.local.entity.InventaireSessionEntity
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.Tige
import java.security.MessageDigest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal const val HIERARCHY_MAX_BYTES = 16 * 1024 * 1024
internal const val HIERARCHY_MAX_GEOMETRY_BYTES = 2 * 1024 * 1024
internal const val HIERARCHY_MAX_TRACE_BYTES = 3_000_000
internal const val HIERARCHY_MAX_SAMPLING_PLAN_BYTES = 16 * 1024
internal val hierarchyJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

@Serializable
internal data class HierarchySessionDto(
    val sessionId: String, val parcelleId: String, val typeSession: String,
    val dateDebut: Long, val dateFin: Long?, val operateurNom: String?, val methode: String?,
    val intensiteEchantillonnagePct: Double?, val objectifSession: String?, val remarques: String?,
    val createdAt: Long, val martelageState: String?, val activeDurationMs: Long,
    val campaignId: String?, val revisionNumber: Int, val previousSessionId: String?,
    /** Original string, including whitespace/escaping/order inside it; never parsed and rewritten. */
    val closedSnapshotJson: String?, val deletedAt: Long?, val auteur: String?, val source: String?, val version: Int,
    val placetteId: String? = null,
    /** Contract marker only: null legacy scope is never assigned to a guessed plot. */
    val sessionScopeVersion: Int = 1,
    /** Original trace string; it also stays byte-identical inside the closed V2 snapshot. */
    val traceJson: String? = null,
)

internal fun InventaireSessionEntity.toHierarchySession() = HierarchySessionDto(
    sessionId, parcelleId, typeSession, dateDebut, dateFin, operateurNom, methode,
    intensiteEchantillonnagePct, objectifSession, remarques, createdAt, martelageState,
    activeDurationMs, campaignId, revisionNumber, previousSessionId, closedSnapshotJson,
    deletedAt, auteur, source, version,
    placetteId = placetteId,
    sessionScopeVersion = if (placetteId == null) 1 else 2,
    traceJson = traceJson,
)

@Serializable
internal data class HierarchyDossierDto(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val forest: Foret,
    val parcelles: List<Parcelle>,
    val placettes: List<Placette>,
    val sessions: List<HierarchySessionDto>,
    val tiges: List<Tige>,
)

@Serializable
internal data class HierarchyMutationDto(
    @SerialName("operation_id") val operationId: String,
    @SerialName("base_version") val baseVersion: Int?,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
    val dossier: HierarchyDossierDto,
)

@Serializable
internal data class HierarchyReceiptDto(
    val status: String,
    @SerialName("operation_id") val operationId: String,
    @SerialName("payload_sha256") val payloadSha256: String,
    @SerialName("forest_id") val forestId: String,
    @SerialName("server_version") val serverVersion: Int? = null,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    val reason: String? = null,
    val current: HierarchyRemoteDto? = null,
    @SerialName("incoming_dossier") val incomingDossier: JsonObject? = null,
    /** Exact raw-body hash retained in the original server receipt, including during a replay. */
    @SerialName("request_sha256") val requestSha256: String? = null,
)

@Serializable
internal data class HierarchyRemoteDto(
    @SerialName("forest_id") val forestId: String,
    @SerialName("server_version") val serverVersion: Int,
    @SerialName("client_updated_at") val clientUpdatedAt: String? = null,
    @SerialName("server_updated_at") val serverUpdatedAt: String,
    val dossier: JsonObject,
)

@Serializable
internal data class HierarchyMetadataDto(
    @SerialName("forest_id") val forestId: String,
    val name: String? = null,
    @SerialName("server_version") val serverVersion: Int,
)

@Serializable
internal data class HierarchyPageDto(
    val items: List<HierarchyMetadataDto>, val page: Int, val size: Int, val total: Int,
)

internal fun hierarchySha256(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
