package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.forestry.counter.data.local.entity.ParcelSyncCounts
import com.forestry.counter.data.local.entity.ParcelSyncEntity
import com.forestry.counter.data.local.entity.BusinessReadOwner
import com.forestry.counter.data.local.entity.BusinessReadParcelParent
import kotlinx.coroutines.flow.Flow

@Dao
interface ParcelSyncDao {
    @Query(
        """
        SELECT
            CAST(COALESCE(SUM(CASE WHEN state = 'PENDING' THEN 1 ELSE 0 END), 0) AS INTEGER) AS pending,
            CAST(COALESCE(SUM(CASE WHEN state = 'SYNCING' THEN 1 ELSE 0 END), 0) AS INTEGER) AS syncing,
            CAST(COALESCE(SUM(CASE WHEN state = 'SYNCED' THEN 1 ELSE 0 END), 0) AS INTEGER) AS synced,
            CAST(COALESCE(SUM(CASE WHEN state = 'CONFLICT' THEN 1 ELSE 0 END), 0) AS INTEGER) AS conflicts,
            CAST(COALESCE(SUM(CASE WHEN state = 'ERROR' THEN 1 ELSE 0 END), 0) AS INTEGER) AS errors,
            MAX(lastSuccessAt) AS lastSuccessAt
        FROM parcel_sync_queue
        WHERE accountId = :accountId
        """,
    )
    fun observeCounts(accountId: String): Flow<ParcelSyncCounts>

    /** Existing queue rows remain checkpoints/associations, never historical request snapshots. */
    @Query(
        """
        SELECT
          0 AS pending, 0 AS syncing,
          CAST(COALESCE(SUM(CASE WHEN q.state = 'SYNCED' AND NOT EXISTS (
            SELECT 1 FROM parcel_sync_operations o WHERE o.originAccountId = q.accountId
              AND o.parcelId = q.parcelId AND o.state NOT IN ('SYNCED','SUPERSEDED','LOCAL_ONLY')
          ) THEN 1 ELSE 0 END), 0) AS INTEGER) AS synced,
          CAST(COALESCE(SUM(CASE WHEN q.state = 'CONFLICT' AND NOT EXISTS (
            SELECT 1 FROM parcel_sync_operations o WHERE o.operationId = q.operationId
          ) THEN 1 ELSE 0 END), 0) AS INTEGER) AS conflicts,
          CAST(COALESCE(SUM(CASE WHEN q.state NOT IN ('SYNCED','CONFLICT') AND NOT EXISTS (
            SELECT 1 FROM parcel_sync_operations o WHERE o.operationId = q.operationId
          ) THEN 1 ELSE 0 END), 0) AS INTEGER) AS errors,
          MAX(q.lastSuccessAt) AS lastSuccessAt
        FROM parcel_sync_queue q WHERE q.accountId = :accountId
        """,
    )
    fun observeCheckpointCounts(accountId: String): Flow<ParcelSyncCounts>

    @Query(
        """
        SELECT q.* FROM parcel_sync_queue q
        WHERE q.accountId = :accountId AND q.state NOT IN ('SYNCED','CONFLICT')
          AND NOT EXISTS (SELECT 1 FROM parcel_sync_operations o WHERE o.operationId = q.operationId)
        ORDER BY q.queuedAt ASC
        """,
    )
    suspend fun getLegacyRecoverable(accountId: String): List<ParcelSyncEntity>

    @Query("DELETE FROM parcel_sync_queue")
    suspend fun hardDeleteAll(): Int

    @Query(
        """
        SELECT * FROM parcel_sync_queue
        WHERE accountId = :accountId
          AND (
            (state IN ('PENDING', 'ERROR') AND nextAttemptAt <= :now)
            OR (state = 'SYNCING' AND lastAttemptAt <= :staleBefore)
          )
        ORDER BY queuedAt ASC
        LIMIT :limit
        """,
    )
    suspend fun getReady(
        accountId: String,
        now: Long,
        staleBefore: Long,
        limit: Int,
    ): List<ParcelSyncEntity>

    @Query("SELECT * FROM parcel_sync_queue WHERE accountId = :accountId AND parcelId = :parcelId")
    suspend fun get(accountId: String, parcelId: String): ParcelSyncEntity?

    @Query("SELECT accountId FROM parcel_sync_queue WHERE parcelId = :parcelId")
    suspend fun getAccountIdsForParcel(parcelId: String): List<String>

    @Query("SELECT h.accountId FROM hierarchy_sync_associations h JOIN parcelles p ON p.foretId = h.forestId WHERE p.parcelleId = :parcelId")
    suspend fun getHierarchyAccountsForParcel(parcelId: String): List<String>

    @Query("SELECT accountId FROM hierarchy_sync_associations WHERE forestId = :forestId")
    suspend fun getHierarchyAccountsForForest(forestId: String): List<String>

    @Query("SELECT DISTINCT q.accountId FROM parcel_sync_queue q JOIN parcelles p ON q.parcelId = p.parcelleId WHERE p.foretId = :forestId")
    suspend fun getLegacyAccountsForForest(forestId: String): List<String>

    /** Only explicit associations/destinations confer ownership; author metadata never does. */
    @Query("""
        SELECT 'FOREST' AS scope, forestId AS objectId, accountId FROM hierarchy_sync_associations
        UNION SELECT 'FOREST', forestId, accountId FROM hierarchy_sync_operations
        UNION SELECT 'PARCEL', parcelId, accountId FROM parcel_sync_queue
        UNION SELECT 'PARCEL', parcelId, originAccountId FROM parcel_sync_operations WHERE originAccountId IS NOT NULL
        UNION SELECT 'FOREST', p.foretId, q.accountId FROM parcel_sync_queue q
          JOIN parcelles p ON p.parcelleId = q.parcelId WHERE p.foretId IS NOT NULL
        UNION SELECT 'FOREST', p.foretId, o.originAccountId FROM parcel_sync_operations o
          JOIN parcelles p ON p.parcelleId = o.parcelId WHERE p.foretId IS NOT NULL AND o.originAccountId IS NOT NULL
        UNION SELECT 'FOREST', p.forestOwnerId, q.accountId FROM parcel_sync_queue q
          JOIN parcelles p ON p.parcelleId = q.parcelId WHERE p.forestOwnerId IS NOT NULL
        UNION SELECT 'FOREST', p.forestOwnerId, o.originAccountId FROM parcel_sync_operations o
          JOIN parcelles p ON p.parcelleId = o.parcelId WHERE p.forestOwnerId IS NOT NULL AND o.originAccountId IS NOT NULL
        UNION SELECT 'FOREST', p.forestOwnerId, h.accountId FROM hierarchy_sync_associations h
          JOIN parcelles p ON p.foretId = h.forestId WHERE p.forestOwnerId IS NOT NULL
        UNION SELECT 'FOREST', p.forestOwnerId, h.accountId FROM hierarchy_sync_operations h
          JOIN parcelles p ON p.foretId = h.forestId WHERE p.forestOwnerId IS NOT NULL
        UNION SELECT 'FOREST', p.forestOwnerId, q.accountId FROM parcelles p
          JOIN parcelles sibling ON sibling.foretId = p.foretId
          JOIN parcel_sync_queue q ON q.parcelId = sibling.parcelleId WHERE p.forestOwnerId IS NOT NULL
        UNION SELECT 'FOREST', p.forestOwnerId, o.originAccountId FROM parcelles p
          JOIN parcelles sibling ON sibling.foretId = p.foretId
          JOIN parcel_sync_operations o ON o.parcelId = sibling.parcelleId
          WHERE p.forestOwnerId IS NOT NULL AND o.originAccountId IS NOT NULL
        ORDER BY scope, objectId, accountId
    """)
    fun observeBusinessReadOwners(): Flow<List<BusinessReadOwner>>

    @Query("""
        SELECT 'FOREST' AS scope, forestId AS objectId, accountId FROM hierarchy_sync_associations
        UNION SELECT 'FOREST', forestId, accountId FROM hierarchy_sync_operations
        UNION SELECT 'PARCEL', parcelId, accountId FROM parcel_sync_queue
        UNION SELECT 'PARCEL', parcelId, originAccountId FROM parcel_sync_operations WHERE originAccountId IS NOT NULL
        UNION SELECT 'FOREST', p.foretId, q.accountId FROM parcel_sync_queue q
          JOIN parcelles p ON p.parcelleId = q.parcelId WHERE p.foretId IS NOT NULL
        UNION SELECT 'FOREST', p.foretId, o.originAccountId FROM parcel_sync_operations o
          JOIN parcelles p ON p.parcelleId = o.parcelId WHERE p.foretId IS NOT NULL AND o.originAccountId IS NOT NULL
        UNION SELECT 'FOREST', p.forestOwnerId, q.accountId FROM parcel_sync_queue q
          JOIN parcelles p ON p.parcelleId = q.parcelId WHERE p.forestOwnerId IS NOT NULL
        UNION SELECT 'FOREST', p.forestOwnerId, o.originAccountId FROM parcel_sync_operations o
          JOIN parcelles p ON p.parcelleId = o.parcelId WHERE p.forestOwnerId IS NOT NULL AND o.originAccountId IS NOT NULL
        UNION SELECT 'FOREST', p.forestOwnerId, h.accountId FROM hierarchy_sync_associations h
          JOIN parcelles p ON p.foretId = h.forestId WHERE p.forestOwnerId IS NOT NULL
        UNION SELECT 'FOREST', p.forestOwnerId, h.accountId FROM hierarchy_sync_operations h
          JOIN parcelles p ON p.foretId = h.forestId WHERE p.forestOwnerId IS NOT NULL
        UNION SELECT 'FOREST', p.forestOwnerId, q.accountId FROM parcelles p
          JOIN parcelles sibling ON sibling.foretId = p.foretId
          JOIN parcel_sync_queue q ON q.parcelId = sibling.parcelleId WHERE p.forestOwnerId IS NOT NULL
        UNION SELECT 'FOREST', p.forestOwnerId, o.originAccountId FROM parcelles p
          JOIN parcelles sibling ON sibling.foretId = p.foretId
          JOIN parcel_sync_operations o ON o.parcelId = sibling.parcelleId
          WHERE p.forestOwnerId IS NOT NULL AND o.originAccountId IS NOT NULL
        ORDER BY scope, objectId, accountId
    """)
    suspend fun getBusinessReadOwners(): List<BusinessReadOwner>

    @Query("SELECT parcelleId AS parcelId, foretId AS forestId, forestOwnerId AS legacyGroupId FROM parcelles ORDER BY parcelleId")
    fun observeBusinessReadParcelParents(): Flow<List<BusinessReadParcelParent>>

    @Query("SELECT parcelleId AS parcelId, foretId AS forestId, forestOwnerId AS legacyGroupId FROM parcelles ORDER BY parcelleId")
    suspend fun getBusinessReadParcelParents(): List<BusinessReadParcelParent>

    @Query("SELECT COUNT(*) FROM parcelles WHERE parcelleId = :parcelId AND deletedAt IS NULL")
    suspend fun countLiveParcel(parcelId: String): Int

    /** INSERT IGNORE empêche une confirmation de réinitialiser une opération existante. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entities: List<ParcelSyncEntity>): List<Long>

    /** Vérification intercomptes et insertion forment une seule transaction Room. */
    @Transaction
    suspend fun insertSelectedIfUnbound(entities: List<ParcelSyncEntity>): Int {
        entities.forEach { entity ->
            check(countLiveParcel(entity.parcelId) == 1) { "PARCEL_NOT_AVAILABLE" }
            check((getAccountIdsForParcel(entity.parcelId) + getHierarchyAccountsForParcel(entity.parcelId))
                .none { it != entity.accountId }) {
                "PARCEL_LINKED_TO_ANOTHER_ACCOUNT"
            }
        }
        return insertIfAbsent(entities).count { it != -1L }
    }

    /** Le callback doit contenir uniquement des opérations locales sur cette même base. */
    @Transaction
    suspend fun withParcelMergeTransaction(block: suspend () -> Unit) {
        block()
    }

    /** Nouvelle modification seulement sur un lien existant et sans conflit ni autre compte. */
    @Query(
        """
        UPDATE parcel_sync_queue
        SET operation = :operation, operationId = :operationId, state = 'PENDING',
            retryCount = 0, queuedAt = :queuedAt, nextAttemptAt = :queuedAt, lastErrorCode = NULL
        WHERE accountId = :accountId AND parcelId = :parcelId AND state != 'CONFLICT'
          AND NOT EXISTS (
            SELECT 1 FROM parcel_sync_queue AS other
            WHERE other.parcelId = :parcelId AND other.accountId != :accountId
          )
        """,
    )
    suspend fun updateLinkedOperation(
        accountId: String,
        parcelId: String,
        operation: String,
        operationId: String,
        queuedAt: Long,
    ): Int

    @Query(
        """
        UPDATE parcel_sync_queue
        SET state = 'SYNCING', lastAttemptAt = :attemptedAt
        WHERE accountId = :accountId
          AND parcelId = :parcelId
          AND operationId = :operationId
          AND state = :expectedState
          AND (
            (lastAttemptAt IS NULL AND :expectedLastAttemptAt IS NULL)
            OR lastAttemptAt = :expectedLastAttemptAt
          )
        """,
    )
    suspend fun claimIfCurrent(
        accountId: String,
        parcelId: String,
        operationId: String,
        expectedState: String,
        expectedLastAttemptAt: Long?,
        attemptedAt: Long,
    ): Int

    @Query(
        """
        UPDATE parcel_sync_queue
        SET serverVersion = :serverVersion,
            lastSuccessAt = :completedAt,
            state = CASE WHEN operationId = :operationId THEN 'SYNCED' ELSE state END,
            retryCount = CASE WHEN operationId = :operationId THEN 0 ELSE retryCount END,
            nextAttemptAt = CASE WHEN operationId = :operationId THEN :completedAt ELSE nextAttemptAt END,
            lastErrorCode = CASE WHEN operationId = :operationId THEN NULL ELSE lastErrorCode END
        WHERE accountId = :accountId AND parcelId = :parcelId
        """,
    )
    suspend fun recordSuccess(
        accountId: String,
        parcelId: String,
        operationId: String,
        serverVersion: Int,
        completedAt: Long,
    ): Int

    @Query(
        """
        UPDATE parcel_sync_queue
        SET state = :state,
            retryCount = :retryCount,
            nextAttemptAt = :nextAttemptAt,
            lastErrorCode = :errorCode
        WHERE accountId = :accountId
          AND parcelId = :parcelId
          AND operationId = :operationId
        """,
    )
    suspend fun recordFailureIfCurrent(
        accountId: String,
        parcelId: String,
        operationId: String,
        state: String,
        retryCount: Int,
        nextAttemptAt: Long,
        errorCode: String,
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ParcelSyncEntity)
}
