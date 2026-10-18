package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Explicit personal-account consent, retained across logout and local soft deletion. */
@Entity(tableName = "hierarchy_sync_associations", indices = [Index("accountId")])
data class HierarchySyncAssociationEntity(
    @PrimaryKey val forestId: String,
    val accountId: String,
    val apiOrigin: String,
    val activatedAt: Long,
    val lastCapturedHash: String?,
    val serverVersion: Int?,
    val lastSuccessAt: Long?,
    val lastErrorCode: String?,
)

/** No business FK: retries and conflict candidates survive physical deletion of a local forest. */
@Entity(
    tableName = "hierarchy_sync_operations",
    indices = [
        Index(value = ["operationId"], unique = true),
        Index(value = ["accountId", "forestId", "sequence"]),
        Index(value = ["accountId", "state", "nextAttemptAt"]),
    ],
)
data class HierarchySyncOperationEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val operationId: String,
    val forestId: String,
    val accountId: String,
    val apiOrigin: String,
    /** Complete UTF-8 wire envelope, replayed directly rather than reserialized from business data. */
    val requestJson: String,
    val requestSha256: String,
    val dossierSha256: String,
    val baseVersion: Int?,
    val expectedServerVersion: Int,
    val predecessorOperationId: String?,
    val createdAt: Long,
    val containsLocalPhotos: Boolean,
    val recoveryRemoteJson: String? = null,
    val state: String,
    val retryCount: Int = 0,
    val nextAttemptAt: Long,
    val attemptId: String? = null,
    val lastAttemptAt: Long? = null,
    val lastErrorCode: String? = null,
    val ackVersion: Int? = null,
    /** Server-normalized operation digest; distinct from our exact frozen-wire SHA. */
    val serverPayloadSha256: String? = null,
    val receiptJson: String? = null,
)

/** Lightweight state projection; observing summaries must never load retained multi-MiB requests. */
data class HierarchyDeliveryStatus(val forestId: String, val state: String)

data class HierarchyLocalCounts(val parcels: Int, val plots: Int, val sessions: Int, val trees: Int, val localPhotos: Int)
