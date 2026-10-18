package com.forestry.counter.domain.model

import java.util.UUID
import kotlinx.serialization.Serializable

/** Physical locations, without inferring a forest, organisation or commercial stock. */
@Serializable
enum class WoodDepotKind { ROADSIDE, YARD, OTHER }

@Serializable
enum class WoodDepotLocationSource { MANUAL, GPS }

@Serializable
data class WoodDepotLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double? = null,
    val source: WoodDepotLocationSource = WoodDepotLocationSource.MANUAL,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "WOOD_DEPOT_INVALID_LATITUDE" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "WOOD_DEPOT_INVALID_LONGITUDE" }
        require(accuracyM == null || accuracyM.isFinite() && accuracyM > 0.0) { "WOOD_DEPOT_INVALID_ACCURACY" }
    }
}

/** User-entered information. Unknown position stays null; source is a declaration. */
@Serializable
data class WoodDepotDraft(
    val name: String,
    val kind: WoodDepotKind,
    val notes: String? = null,
    val location: WoodDepotLocation? = null,
) {
    init {
        require(name.isNotBlank() && name.length <= MAX_NAME_LENGTH) { "WOOD_DEPOT_INVALID_NAME" }
        require(notes == null || notes.length <= MAX_NOTES_LENGTH) { "WOOD_DEPOT_INVALID_NOTES" }
    }

    companion object {
        const val MAX_NAME_LENGTH = 200
        const val MAX_NOTES_LENGTH = 5_000
    }
}

@Serializable
enum class WoodDepotOwnerScope { LOCAL_DEVICE, PERSONAL }

/** A saved local dossier; author metadata never confers permission to another account. */
@Serializable
data class WoodDepotRecord(
    val id: String,
    val draft: WoodDepotDraft,
    val ownerScope: WoodDepotOwnerScope,
    val ownerAccountId: String?,
    val revision: Int,
    val createdAt: Long,
    val createdBy: String,
    val updatedAt: Long,
    val updatedBy: String,
    val deletedAt: Long? = null,
) {
    init {
        requireWoodDepotId(id)
        require(revision > 0) { "WOOD_DEPOT_INVALID_REVISION" }
        require(createdAt >= 0 && updatedAt >= 0 && (deletedAt == null || deletedAt >= 0)) {
            "WOOD_DEPOT_INVALID_TIMESTAMP"
        }
        require(createdBy.isNotBlank() && updatedBy.isNotBlank()) { "WOOD_DEPOT_INVALID_AUTHOR" }
        require(when (ownerScope) {
            WoodDepotOwnerScope.LOCAL_DEVICE -> ownerAccountId == null
            WoodDepotOwnerScope.PERSONAL -> !ownerAccountId.isNullOrBlank()
        }) { "WOOD_DEPOT_INVALID_OWNER" }
    }
}

/** Canonical UUIDs prevent alternate spellings from creating different local identities. */
fun requireWoodDepotId(id: String) {
    require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) {
        "WOOD_DEPOT_INVALID_ID"
    }
}
