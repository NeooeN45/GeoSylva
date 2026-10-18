package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Independent of forests/parcels. Ownership is explicit and immutable in the repository. */
@Entity(
    tableName = "wood_depots",
    indices = [Index(name = "index_wood_depots_owner_live", value = ["ownerScope", "ownerAccountId", "deletedAt"])],
)
data class WoodDepotEntity(
    @PrimaryKey val depotId: String,
    val name: String,
    val kind: String,
    val notes: String?,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyM: Double?,
    val locationSource: String?,
    val ownerScope: String,
    val ownerAccountId: String?,
    /** Preserves idempotence of the original create command even after later edits. */
    val creationFingerprint: String,
    val revision: Int,
    val createdAt: Long,
    val createdBy: String,
    val updatedAt: Long,
    val updatedBy: String,
    val deletedAt: Long?,
)
