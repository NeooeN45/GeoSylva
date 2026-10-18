package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An immutable local mutation and its independently mutable delivery receipt.
 *
 * There is deliberately no parcel foreign key: a parcel INSERT OR REPLACE or
 * physical deletion must not erase an operation already accepted locally.
 * Only the explicit user-data purge may remove this journal.
 *
 * [requestJson] is the complete frozen wire request for a transmissible entry.
 * LOCAL_ONLY and blocked entries also retain the local snapshot in that JSON;
 * they must never become transmissible by changing their destination.
 */
@Entity(
    tableName = "parcel_sync_operations",
    indices = [
        Index(
            name = "index_parcel_sync_operations_operationId",
            value = ["operationId"],
            unique = true,
        ),
        Index(
            name = "index_parcel_sync_operations_account_parcel_sequence",
            value = ["originAccountId", "parcelId", "sequence"],
        ),
        Index(
            name = "index_parcel_sync_operations_ready",
            value = ["originAccountId", "state", "nextAttemptAt"],
        ),
        Index(
            name = "index_parcel_sync_operations_predecessor",
            value = ["predecessorOperationId"],
        ),
    ],
)
data class ParcelSyncOperationEntity(
    @PrimaryKey(autoGenerate = true)
    val sequence: Long = 0,
    val operationId: String,
    val parcelId: String,
    val originAccountId: String?,
    val destinationKind: String?,
    /** Captured API origin; configuration changes never retarget an old request. */
    val apiOrigin: String?,
    val operation: String,
    val requestJson: String,
    val baseVersion: Int?,
    val expectedServerVersion: Int?,
    val predecessorOperationId: String?,
    val createdAt: Long,
    val payloadFingerprint: String? = null,
    val legacyReferenceJson: String? = null,
    val state: String,
    val retryCount: Int = 0,
    val lastAttemptAt: Long? = null,
    /** UUID lease token; wall-clock milliseconds do not identify an attempt. */
    val attemptId: String? = null,
    val nextAttemptAt: Long,
    val lastErrorCode: String? = null,
    val ackVersion: Int? = null,
    val ackAt: Long? = null,
)

data class ParcelSyncOperationCounts(
    val pending: Int,
    val syncing: Int,
    val synced: Int,
    val conflicts: Int,
    val errors: Int,
    /** Subset of [errors], for explanation only; do not add it to [errors]. */
    val blocked: Int,
    val lastSuccessAt: Long?,
)
