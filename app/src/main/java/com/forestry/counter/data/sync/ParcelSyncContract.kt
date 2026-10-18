package com.forestry.counter.data.sync

import com.forestry.counter.data.local.entity.ParcelleEntity
import com.forestry.counter.data.local.entity.ProvenanceEmbed
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val PARCEL_STATUS_DELETED = "deleted"

/** Résultat de la fusion serveur→local d'une parcelle (GEOSYLVA P0-3, pull). */
internal enum class MergeOutcome { INSERTED, UPDATED, DELETED, SKIPPED_LOCAL_DIRTY, SKIPPED_OTHER_ACCOUNT, NOOP }

/**
 * Décide l'action de fusion pour UNE parcelle reçue du serveur, sans
 * effectuer aucun accès disque — la logique de décision est pure et
 * testable indépendamment de Room/Retrofit (voir [ParcelSyncPolicyTest]).
 *
 * Règle non négociable : une modification locale non encore synchronisée
 * avec succès ([isLocalDirty]) gagne toujours — le pull ne l'écrase jamais.
 */
internal fun decideMergeOutcome(
    isLocalDirty: Boolean,
    isTombstone: Boolean,
    existsLocally: Boolean,
    isAlreadyDeletedLocally: Boolean,
): MergeOutcome = when {
    isLocalDirty -> MergeOutcome.SKIPPED_LOCAL_DIRTY
    isTombstone -> if (existsLocally && !isAlreadyDeletedLocally) {
        MergeOutcome.DELETED
    } else {
        MergeOutcome.NOOP
    }
    !existsLocally -> MergeOutcome.INSERTED
    else -> MergeOutcome.UPDATED
}

internal enum class SyncFailureAction {
    REFRESH_SESSION,
    RETRY,
    CONFLICT,
    PERMANENT_ERROR,
}

internal fun classifySyncHttpFailure(code: Int): SyncFailureAction = when {
    code == 401 -> SyncFailureAction.REFRESH_SESSION
    code == 409 -> SyncFailureAction.CONFLICT
    code in setOf(408, 425, 429) || code >= 500 -> SyncFailureAction.RETRY
    else -> SyncFailureAction.PERMANENT_ERROR
}

@Serializable
internal data class ParcelSyncPayloadDto(
    @SerialName("forest_owner_id") val forestOwnerId: String? = null,
    @SerialName("forest_id") val forestId: String? = null,
    val name: String,
    @SerialName("surface_ha") val surfaceHa: Double? = null,
    val shape: String? = null,
    @SerialName("slope_pct") val slopePct: Double? = null,
    val aspect: String? = null,
    val access: String? = null,
    @SerialName("altitude_m") val altitudeM: Double? = null,
    @SerialName("objective_type") val objectiveType: String? = null,
    @SerialName("objective_value") val objectiveValue: Double? = null,
    @SerialName("tolerance_pct") val tolerancePct: Double? = null,
    @SerialName("sampling_mode") val samplingMode: String? = null,
    @SerialName("sample_area_m2") val sampleAreaM2: Double? = null,
    @SerialName("target_species_csv") val targetSpeciesCsv: String? = null,
    val srid: Int? = null,
    val remarks: String? = null,
    @SerialName("municipality_code") val municipalityCode: String? = null,
    @SerialName("municipality_name") val municipalityName: String? = null,
    @SerialName("cadastral_section") val cadastralSection: String? = null,
    @SerialName("cadastral_number") val cadastralNumber: String? = null,
    @SerialName("cadastral_area_ha") val cadastralAreaHa: Double? = null,
    @SerialName("ign_geometry_wkt") val ignGeometryWkt: String? = null,
    @SerialName("cadastral_nature_code") val cadastralNatureCode: String? = null,
    @SerialName("location_mode") val locationMode: String? = null,
    @SerialName("ser_code") val serCode: String? = null,
    @SerialName("ser_name") val serName: String? = null,
    @SerialName("created_at_ms") val createdAtMs: Long,
    @SerialName("updated_at_ms") val updatedAtMs: Long,
)

internal fun ParcelleEntity.toSyncPayload(): ParcelSyncPayloadDto = ParcelSyncPayloadDto(
    forestOwnerId = forestOwnerId,
    forestId = foretId,
    name = name,
    surfaceHa = surfaceHa,
    shape = shape,
    slopePct = slopePct,
    aspect = aspect,
    access = access,
    altitudeM = altitudeM,
    objectiveType = objectifType,
    objectiveValue = objectifVal,
    tolerancePct = tolerancePct,
    samplingMode = samplingMode,
    sampleAreaM2 = sampleAreaM2,
    targetSpeciesCsv = targetSpeciesCsv,
    srid = srid,
    remarks = remarks,
    municipalityCode = codeInseeCommune,
    municipalityName = nomCommune,
    cadastralSection = sectionCadastrale,
    cadastralNumber = numeroCadastral,
    cadastralAreaHa = contenanceCadastraleHa,
    ignGeometryWkt = geometrieIgnWkt,
    cadastralNatureCode = natureCadastraleCode,
    locationMode = localisationMode,
    serCode = codeSer,
    serName = nomSer,
    createdAtMs = createdAt,
    updatedAtMs = updatedAt,
)

@Serializable
internal data class ParcelUpsertRequestDto(
    @SerialName("operation_id") val operationId: String,
    @SerialName("base_version") val baseVersion: Int?,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
    val parcel: ParcelSyncPayloadDto,
)

@Serializable
internal data class ParcelDeleteRequestDto(
    @SerialName("operation_id") val operationId: String,
    @SerialName("base_version") val baseVersion: Int?,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)

@Serializable
internal data class ParcelSyncResponseDto(
    @SerialName("client_id") val clientId: String,
    val status: String,
    @SerialName("server_version") val serverVersion: Int,
    @SerialName("client_updated_at") val clientUpdatedAt: String? = null,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    val parcel: ParcelSyncPayloadDto? = null,
)

/**
 * Sens inverse de [toSyncPayload] — reconstruit l'entité locale à partir de
 * la réponse serveur (GEOSYLVA P0-3, pull).
 *
 * Retourne `null` si [ParcelSyncResponseDto.parcel] est absent (tombstone
 * `status="deleted"` — voir [PARCEL_STATUS_DELETED], géré séparément par
 * l'appelant via une suppression douce plutôt qu'une reconstruction).
 *
 * [existing] fournit les champs que le contrat réseau ne transporte pas
 * (`uuid`, `auteur`, `source`, `provenance`) — préservés tels quels s'ils
 * existaient déjà localement, sinon laissés à leur valeur par défaut.
 */
internal fun ParcelSyncResponseDto.toParcelleEntity(existing: ParcelleEntity?): ParcelleEntity? {
    val payload = parcel ?: return null
    return ParcelleEntity(
        parcelleId = clientId,
        uuid = existing?.uuid,
        forestOwnerId = payload.forestOwnerId,
        foretId = payload.forestId,
        name = payload.name,
        surfaceHa = payload.surfaceHa,
        shape = payload.shape,
        slopePct = payload.slopePct,
        aspect = payload.aspect,
        access = payload.access,
        altitudeM = payload.altitudeM,
        objectifType = payload.objectiveType,
        objectifVal = payload.objectiveValue,
        tolerancePct = payload.tolerancePct,
        samplingMode = payload.samplingMode,
        sampleAreaM2 = payload.sampleAreaM2,
        targetSpeciesCsv = payload.targetSpeciesCsv,
        srid = payload.srid,
        remarks = payload.remarks,
        codeInseeCommune = payload.municipalityCode,
        nomCommune = payload.municipalityName,
        sectionCadastrale = payload.cadastralSection,
        numeroCadastral = payload.cadastralNumber,
        contenanceCadastraleHa = payload.cadastralAreaHa,
        geometrieIgnWkt = payload.ignGeometryWkt,
        natureCadastraleCode = payload.cadastralNatureCode,
        localisationMode = payload.locationMode,
        codeSer = payload.serCode,
        nomSer = payload.serName,
        createdAt = payload.createdAtMs,
        updatedAt = payload.updatedAtMs,
        provenance = existing?.provenance ?: ProvenanceEmbed(null, null, null, null, null),
        deletedAt = null,
        auteur = existing?.auteur,
        source = existing?.source,
        version = serverVersion,
    )
}

@Serializable
internal data class GeoSylvaParcelPageDto(
    val items: List<ParcelSyncResponseDto>,
    val page: Int,
    val size: Int,
    val total: Int,
)
