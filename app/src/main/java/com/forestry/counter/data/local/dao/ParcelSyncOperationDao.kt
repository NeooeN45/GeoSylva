package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.forestry.counter.data.local.entity.ParcelSyncOperationCounts
import com.forestry.counter.data.local.entity.ParcelSyncOperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParcelSyncOperationDao {
    /** Duplicate IDs abort the surrounding business transaction; never replace. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun append(entity: ParcelSyncOperationEntity): Long

    @Query("SELECT * FROM parcel_sync_operations WHERE operationId = :operationId")
    suspend fun getByOperationId(operationId: String): ParcelSyncOperationEntity?

    @Query(
        """
        SELECT * FROM parcel_sync_operations
        WHERE parcelId = :parcelId
          AND (originAccountId = :accountId OR (originAccountId IS NULL AND :accountId IS NULL))
        ORDER BY sequence DESC LIMIT 1
        """,
    )
    suspend fun getLastForParcel(accountId: String?, parcelId: String): ParcelSyncOperationEntity?

    @Query(
        """
        SELECT * FROM parcel_sync_operations
        WHERE originAccountId = :accountId AND parcelId = :parcelId
          AND state NOT IN ('LOCAL_ONLY', 'SUPERSEDED')
        ORDER BY sequence DESC LIMIT 1
        """,
    )
    suspend fun getLastActiveForParcel(accountId: String, parcelId: String): ParcelSyncOperationEntity?

    @Query(
        """
        SELECT * FROM parcel_sync_operations
        WHERE parcelId = :parcelId
          AND (originAccountId = :accountId OR (originAccountId IS NULL AND :accountId IS NULL))
        ORDER BY sequence ASC
        """,
    )
    suspend fun getAllForParcel(accountId: String?, parcelId: String): List<ParcelSyncOperationEntity>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM parcel_sync_operations
            WHERE originAccountId = :accountId AND parcelId = :parcelId
              AND state NOT IN ('LOCAL_ONLY', 'SYNCED', 'SUPERSEDED')
        )
        """,
    )
    suspend fun hasOutstanding(accountId: String, parcelId: String): Boolean

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM parcel_sync_operations
            WHERE originAccountId = :accountId AND parcelId = :parcelId
        )
        """,
    )
    suspend fun hasAnyOperations(accountId: String, parcelId: String): Boolean

    @Query(
        """
        SELECT COUNT(*) FROM parcel_sync_operations
        WHERE originAccountId = :accountId
          AND (state IN ('PENDING', 'SYNCING') OR (state = 'ERROR' AND nextAttemptAt != 9223372036854775807))
        """,
    )
    suspend fun getPendingCountForAccount(accountId: String): Int

    /** Only one eligible head per account/parcel; a deferred predecessor blocks its successors. */
    @Query(
        """
        SELECT operation.* FROM parcel_sync_operations AS operation
        WHERE operation.originAccountId = :accountId
          AND operation.destinationKind = 'PERSONAL_ACCOUNT'
          AND operation.apiOrigin IS NOT NULL
          AND operation.expectedServerVersion IS NOT NULL
          AND (
              (operation.state IN ('PENDING', 'ERROR') AND operation.nextAttemptAt <= :now)
              OR (operation.state = 'SYNCING' AND operation.lastAttemptAt <= :staleBefore)
          )
          AND (
              operation.predecessorOperationId IS NULL
              OR EXISTS (
                  SELECT 1 FROM parcel_sync_operations AS predecessor
                  WHERE predecessor.operationId = operation.predecessorOperationId
                    AND predecessor.originAccountId = operation.originAccountId
                    AND predecessor.parcelId = operation.parcelId
                    AND predecessor.state = 'SYNCED'
                    AND predecessor.ackVersion = operation.baseVersion
              )
          )
          AND NOT EXISTS (
              SELECT 1 FROM parcel_sync_operations AS earlier
              WHERE earlier.originAccountId = operation.originAccountId
                AND earlier.parcelId = operation.parcelId
                AND earlier.sequence < operation.sequence
                AND earlier.state NOT IN ('LOCAL_ONLY', 'SYNCED', 'SUPERSEDED')
          )
        ORDER BY operation.sequence ASC
        LIMIT :limit
        """,
    )
    suspend fun getReady(
        accountId: String,
        now: Long,
        staleBefore: Long,
        limit: Int,
    ): List<ParcelSyncOperationEntity>

    /** Recheck FIFO when claiming; the captured lease prevents stale-worker ABA. */
    @Query(
        """
        UPDATE parcel_sync_operations
        SET state = 'SYNCING', lastAttemptAt = :attemptedAt, attemptId = :attemptId
        WHERE originAccountId = :accountId AND operationId = :operationId
          AND destinationKind = 'PERSONAL_ACCOUNT'
          AND apiOrigin IS NOT NULL AND expectedServerVersion IS NOT NULL
          AND state = :expectedState AND state IN ('PENDING', 'ERROR', 'SYNCING')
          AND (lastAttemptAt = :expectedLastAttemptAt OR (lastAttemptAt IS NULL AND :expectedLastAttemptAt IS NULL))
          AND (attemptId = :expectedAttemptId OR (attemptId IS NULL AND :expectedAttemptId IS NULL))
          AND (
              predecessorOperationId IS NULL
              OR EXISTS (
                  SELECT 1 FROM parcel_sync_operations AS predecessor
                  WHERE predecessor.operationId = parcel_sync_operations.predecessorOperationId
                    AND predecessor.originAccountId = parcel_sync_operations.originAccountId
                    AND predecessor.parcelId = parcel_sync_operations.parcelId
                    AND predecessor.state = 'SYNCED'
                    AND predecessor.ackVersion = parcel_sync_operations.baseVersion
              )
          )
          AND NOT EXISTS (
              SELECT 1 FROM parcel_sync_operations AS earlier
              WHERE earlier.originAccountId = parcel_sync_operations.originAccountId
                AND earlier.parcelId = parcel_sync_operations.parcelId
                AND earlier.sequence < parcel_sync_operations.sequence
                AND earlier.state NOT IN ('LOCAL_ONLY', 'SYNCED', 'SUPERSEDED')
          )
        """,
    )
    suspend fun claimIfCurrent(
        operationId: String,
        accountId: String,
        expectedState: String,
        expectedLastAttemptAt: Long?,
        expectedAttemptId: String?,
        attemptedAt: Long,
        attemptId: String,
    ): Int

    /** Update the checkpoint in the same Room transaction, only when this returns one. */
    @Query(
        """
        UPDATE parcel_sync_operations
        SET state = 'SYNCED', retryCount = 0, nextAttemptAt = :completedAt,
            lastErrorCode = NULL, ackVersion = :serverVersion, ackAt = :completedAt
        WHERE operationId = :operationId AND originAccountId = :accountId
          AND state = 'SYNCING' AND attemptId = :attemptId
          AND expectedServerVersion = :serverVersion
        """,
    )
    suspend fun markSuccessIfClaim(
        operationId: String,
        accountId: String,
        attemptId: String,
        serverVersion: Int,
        completedAt: Long,
    ): Int

    @Query(
        """
        UPDATE parcel_sync_operations
        SET state = :state, retryCount = :retryCount, nextAttemptAt = :nextAttemptAt,
            lastErrorCode = :errorCode
        WHERE operationId = :operationId AND originAccountId = :accountId
          AND state = 'SYNCING' AND attemptId = :attemptId
          AND :state IN ('PENDING', 'ERROR', 'CONFLICT', 'BLOCKED_LEGACY', 'BLOCKED_PREDECESSOR', 'BLOCKED_API_ORIGIN')
        """,
    )
    suspend fun markFailureIfClaim(
        operationId: String,
        accountId: String,
        attemptId: String,
        state: String,
        retryCount: Int,
        nextAttemptAt: Long,
        errorCode: String,
    ): Int

    @Query(
        """
        UPDATE parcel_sync_operations
        SET state = 'BLOCKED_PREDECESSOR', lastErrorCode = :errorCode
        WHERE originAccountId = :accountId AND parcelId = :parcelId AND sequence > :afterSequence
          AND state NOT IN ('LOCAL_ONLY', 'SYNCED', 'SUPERSEDED')
        """,
    )
    suspend fun blockFollowing(
        accountId: String,
        parcelId: String,
        afterSequence: Long,
        errorCode: String,
    ): Int

    /** Explicit recovery only; conflict resolution is a separate, unimplemented decision. */
    @Query(
        """
        UPDATE parcel_sync_operations
        SET state = 'SUPERSEDED', nextAttemptAt = :completedAt,
            lastErrorCode = 'LEGACY_EXPLICITLY_RESTARTED'
        WHERE originAccountId = :accountId AND parcelId = :parcelId
          AND state NOT IN ('LOCAL_ONLY', 'SYNCED', 'SUPERSEDED', 'CONFLICT')
        """,
    )
    suspend fun supersedeOutstandingForParcel(accountId: String, parcelId: String, completedAt: Long): Int

    @Query(
        """
        SELECT
            CAST(COALESCE(SUM(CASE WHEN priority = 2 THEN 1 ELSE 0 END), 0) AS INTEGER) AS pending,
            CAST(COALESCE(SUM(CASE WHEN priority = 3 THEN 1 ELSE 0 END), 0) AS INTEGER) AS syncing,
            CAST(COALESCE(SUM(CASE WHEN priority = 1 THEN 1 ELSE 0 END), 0) AS INTEGER) AS synced,
            CAST(COALESCE(SUM(CASE WHEN priority = 5 THEN 1 ELSE 0 END), 0) AS INTEGER) AS conflicts,
            CAST(COALESCE(SUM(CASE WHEN priority = 4 THEN 1 ELSE 0 END), 0) AS INTEGER) AS errors,
            CAST(COALESCE(SUM(CASE WHEN priority = 4 AND hasBlocked = 1 THEN 1 ELSE 0 END), 0) AS INTEGER) AS blocked,
            MAX(lastAckAt) AS lastSuccessAt
        FROM (
            SELECT parcelId,
                MAX(CASE
                    WHEN state = 'CONFLICT' THEN 5
                    WHEN state IN ('ERROR', 'BLOCKED_LEGACY', 'BLOCKED_PREDECESSOR', 'BLOCKED_API_ORIGIN') THEN 4
                    WHEN state = 'SYNCING' THEN 3
                    WHEN state = 'PENDING' THEN 2
                    WHEN state = 'SYNCED' THEN 1
                    ELSE 0
                END) AS priority,
                MAX(CASE WHEN state IN ('BLOCKED_LEGACY', 'BLOCKED_PREDECESSOR', 'BLOCKED_API_ORIGIN') THEN 1 ELSE 0 END) AS hasBlocked,
                MAX(ackAt) AS lastAckAt
            FROM parcel_sync_operations AS journal
            WHERE journal.originAccountId = :accountId
              AND NOT EXISTS (
                  SELECT 1 FROM parcel_sync_queue AS legacy
                  WHERE legacy.accountId = journal.originAccountId
                    AND legacy.parcelId = journal.parcelId
                    AND legacy.state != 'SYNCED'
                    AND NOT EXISTS (
                        SELECT 1 FROM parcel_sync_operations AS known
                        WHERE known.operationId = legacy.operationId
                    )
              )
            GROUP BY journal.parcelId
        )
        """,
    )
    fun observeCounts(accountId: String): Flow<ParcelSyncOperationCounts>

    /** Privacy purge only; ordinary deletion/logout must retain pending snapshots. */
    @Query("DELETE FROM parcel_sync_operations")
    suspend fun hardDeleteAll(): Int

    /** Local DAOs from this same Room database only; never perform network work here. */
    @Transaction
    suspend fun runJournalTransaction(block: suspend () -> Unit) {
        block()
    }
}
