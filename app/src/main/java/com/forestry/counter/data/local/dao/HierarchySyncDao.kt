package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.forestry.counter.data.local.entity.ForetEntity
import com.forestry.counter.data.local.entity.HierarchySyncAssociationEntity
import com.forestry.counter.data.local.entity.HierarchySyncOperationEntity
import com.forestry.counter.data.local.entity.HierarchyDeliveryStatus
import com.forestry.counter.data.local.entity.HierarchyLocalCounts
import com.forestry.counter.data.local.entity.InventaireSessionEntity
import com.forestry.counter.data.local.entity.ParcelleEntity
import com.forestry.counter.data.local.entity.PlacetteEntity
import com.forestry.counter.data.local.entity.TigeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HierarchySyncDao {
    @Transaction suspend fun transaction(block: suspend () -> Unit) { block() }

    @Query("SELECT * FROM forets ORDER BY foretId") suspend fun forests(): List<ForetEntity>
    @Query("SELECT * FROM forets WHERE foretId = :forestId") suspend fun forest(forestId: String): ForetEntity?
    @Query("SELECT * FROM parcelles WHERE foretId = :forestId ORDER BY parcelleId LIMIT 1001")
    suspend fun parcels(forestId: String): List<ParcelleEntity>
    @Query("SELECT p.* FROM placettes p JOIN parcelles parent ON p.parcelleOwnerId = parent.parcelleId WHERE parent.foretId = :forestId ORDER BY p.placetteId LIMIT 5001")
    suspend fun plots(forestId: String): List<PlacetteEntity>
    @Query("SELECT s.* FROM inventaire_sessions s JOIN parcelles parent ON s.parcelleId = parent.parcelleId WHERE parent.foretId = :forestId ORDER BY s.sessionId LIMIT 1001")
    suspend fun sessions(forestId: String): List<InventaireSessionEntity>
    @Query("SELECT t.* FROM tiges t JOIN parcelles parent ON t.parcelleOwnerId = parent.parcelleId WHERE parent.foretId = :forestId ORDER BY t.tigeId LIMIT 50001")
    suspend fun trees(forestId: String): List<TigeEntity>
    @Query("SELECT DISTINCT q.accountId FROM parcel_sync_queue q JOIN parcelles p ON q.parcelId = p.parcelleId WHERE p.foretId = :forestId")
    suspend fun parcelAccounts(forestId: String): List<String>
    @Query("""
        SELECT (SELECT COUNT(*) FROM parcelles WHERE foretId = :forestId) AS parcels,
        (SELECT COUNT(*) FROM placettes p JOIN parcelles parent ON p.parcelleOwnerId = parent.parcelleId WHERE parent.foretId = :forestId) AS plots,
        (SELECT COUNT(*) FROM inventaire_sessions s JOIN parcelles parent ON s.parcelleId = parent.parcelleId WHERE parent.foretId = :forestId) AS sessions,
        (SELECT COUNT(*) FROM tiges t JOIN parcelles parent ON t.parcelleOwnerId = parent.parcelleId WHERE parent.foretId = :forestId) AS trees,
        (SELECT COUNT(*) FROM tiges t JOIN parcelles parent ON t.parcelleOwnerId = parent.parcelleId WHERE parent.foretId = :forestId AND t.photoUri IS NOT NULL AND t.photoUri != '') AS localPhotos
    """) suspend fun counts(forestId: String): HierarchyLocalCounts
    @Query("""
        SELECT COALESCE((SELECT length(CAST(shape AS BLOB)) FROM forets WHERE foretId = :forestId), 0)
        + (SELECT COALESCE(SUM(COALESCE(length(CAST(closedSnapshotJson AS BLOB)), 0)
            + COALESCE(length(CAST(traceJson AS BLOB)), 0)), 0)
            FROM inventaire_sessions s JOIN parcelles p ON s.parcelleId = p.parcelleId WHERE p.foretId = :forestId)
        + (SELECT COALESCE(SUM(COALESCE(length(CAST(shape AS BLOB)), 0)
            + COALESCE(length(CAST(geometrieIgnWkt AS BLOB)), 0)), 0) FROM parcelles WHERE foretId = :forestId)
        + (SELECT COALESCE(SUM(COALESCE(length(CAST(samplingPlanJson AS BLOB)), 0)
            + COALESCE(length(CAST(footprintGeoJson AS BLOB)), 0)), 0)
            FROM placettes plot JOIN parcelles parent ON plot.parcelleOwnerId = parent.parcelleId WHERE parent.foretId = :forestId)
    """)
    suspend fun largeFieldBytes(forestId: String): Long

    /** Every table is a dependency: even an equal result is emitted after its invalidation. */
    @Query("SELECT (SELECT COUNT(*) FROM forets) + (SELECT COUNT(*) FROM parcelles) + (SELECT COUNT(*) FROM placettes) + (SELECT COUNT(*) FROM inventaire_sessions) + (SELECT COUNT(*) FROM tiges)")
    fun observeContentInvalidations(): Flow<Long>

    @Query("SELECT * FROM hierarchy_sync_associations WHERE forestId = :forestId")
    suspend fun association(forestId: String): HierarchySyncAssociationEntity?
    @Query("SELECT * FROM hierarchy_sync_associations WHERE accountId = :accountId ORDER BY forestId")
    suspend fun associations(accountId: String): List<HierarchySyncAssociationEntity>
    @Query("SELECT * FROM hierarchy_sync_associations WHERE accountId = :accountId ORDER BY forestId")
    fun observeAssociations(accountId: String): Flow<List<HierarchySyncAssociationEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAssociation(value: HierarchySyncAssociationEntity)
    @Query("UPDATE hierarchy_sync_associations SET lastCapturedHash = :hash, lastErrorCode = NULL WHERE forestId = :forestId AND accountId = :accountId")
    suspend fun recordCapture(forestId: String, accountId: String, hash: String): Int
    @Query("UPDATE hierarchy_sync_associations SET lastErrorCode = :code WHERE forestId = :forestId AND accountId = :accountId")
    suspend fun captureError(forestId: String, accountId: String, code: String): Int
    @Query("UPDATE hierarchy_sync_associations SET serverVersion = :version, lastSuccessAt = :at, lastErrorCode = NULL WHERE forestId = :forestId AND accountId = :accountId AND (serverVersion IS NULL OR serverVersion <= :version)")
    suspend fun checkpoint(forestId: String, accountId: String, version: Int, at: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun append(value: HierarchySyncOperationEntity): Long
    @Query("SELECT * FROM hierarchy_sync_operations WHERE operationId = :operationId")
    suspend fun operation(operationId: String): HierarchySyncOperationEntity?
    @Query("SELECT * FROM hierarchy_sync_operations WHERE forestId = :forestId AND accountId = :accountId AND state != 'SUPERSEDED' ORDER BY sequence DESC LIMIT 1")
    suspend fun tail(forestId: String, accountId: String): HierarchySyncOperationEntity?
    @Query("""
        SELECT forestId, CASE MAX(CASE WHEN state = 'CONFLICT' THEN 4
            WHEN state IN ('ERROR','BLOCKED_PREDECESSOR','BLOCKED_API_ORIGIN') THEN 3
            WHEN state IN ('PENDING','SYNCING') THEN 2 WHEN state = 'SYNCED' THEN 1 ELSE 0 END)
            WHEN 4 THEN 'CONFLICT' WHEN 3 THEN 'ERROR' WHEN 2 THEN 'PENDING' ELSE 'SYNCED' END AS state
        FROM hierarchy_sync_operations WHERE accountId = :accountId AND state != 'SUPERSEDED' GROUP BY forestId
    """)
    fun observeOperations(accountId: String): Flow<List<HierarchyDeliveryStatus>>
    @Query("SELECT COUNT(*) FROM hierarchy_sync_operations WHERE accountId = :accountId AND (state IN ('PENDING','SYNCING') OR (state = 'ERROR' AND nextAttemptAt != 9223372036854775807))")
    suspend fun pendingCount(accountId: String): Int
    @Query("""
        SELECT o.* FROM hierarchy_sync_operations o
        WHERE o.accountId = :accountId AND (
            (o.state IN ('PENDING','ERROR') AND o.nextAttemptAt <= :now)
            OR (o.state = 'SYNCING' AND o.lastAttemptAt <= :staleBefore)
        ) AND (o.predecessorOperationId IS NULL OR EXISTS (
            SELECT 1 FROM hierarchy_sync_operations p WHERE p.operationId = o.predecessorOperationId
            AND p.accountId = o.accountId AND p.forestId = o.forestId AND p.state = 'SYNCED'
            AND p.ackVersion = o.baseVersion
        )) AND NOT EXISTS (
            SELECT 1 FROM hierarchy_sync_operations earlier WHERE earlier.accountId = o.accountId
            AND earlier.forestId = o.forestId AND earlier.sequence < o.sequence
            AND earlier.state NOT IN ('SYNCED','SUPERSEDED')
        ) ORDER BY o.sequence LIMIT :limit
    """)
    suspend fun ready(accountId: String, now: Long, staleBefore: Long, limit: Int): List<HierarchySyncOperationEntity>

    @Query("""
        UPDATE hierarchy_sync_operations SET state = 'SYNCING', attemptId = :lease, lastAttemptAt = :now
        WHERE operationId = :operationId AND accountId = :accountId AND state = :state
        AND (attemptId = :oldLease OR (attemptId IS NULL AND :oldLease IS NULL))
        AND (lastAttemptAt = :oldAttempt OR (lastAttemptAt IS NULL AND :oldAttempt IS NULL))
        AND NOT EXISTS (SELECT 1 FROM hierarchy_sync_operations e WHERE e.accountId = hierarchy_sync_operations.accountId
            AND e.forestId = hierarchy_sync_operations.forestId AND e.sequence < hierarchy_sync_operations.sequence
            AND e.state NOT IN ('SYNCED','SUPERSEDED'))
    """)
    suspend fun claim(operationId: String, accountId: String, state: String, oldLease: String?, oldAttempt: Long?, lease: String, now: Long): Int
    @Query("""
        UPDATE hierarchy_sync_operations SET state = 'SYNCED', ackVersion = :version,
            serverPayloadSha256 = :serverHash, receiptJson = :receipt, lastErrorCode = NULL, retryCount = 0
        WHERE operationId = :operationId AND accountId = :accountId AND state = 'SYNCING'
            AND attemptId = :lease AND expectedServerVersion = :version
    """)
    suspend fun acknowledge(operationId: String, accountId: String, lease: String, version: Int, serverHash: String, receipt: String): Int
    @Query("""
        UPDATE hierarchy_sync_operations SET state = :state, retryCount = :retryCount,
            nextAttemptAt = :nextAttempt, lastErrorCode = :code, receiptJson = :receipt
        WHERE operationId = :operationId AND accountId = :accountId AND state = 'SYNCING' AND attemptId = :lease
    """)
    suspend fun fail(operationId: String, accountId: String, lease: String, state: String, retryCount: Int, nextAttempt: Long, code: String, receipt: String?): Int
    @Query("UPDATE hierarchy_sync_operations SET state = 'BLOCKED_PREDECESSOR', lastErrorCode = :code WHERE forestId = :forestId AND accountId = :accountId AND sequence > :sequence AND state NOT IN ('SYNCED','SUPERSEDED')")
    suspend fun blockFollowing(forestId: String, accountId: String, sequence: Long, code: String): Int
    @Query("UPDATE hierarchy_sync_operations SET state = 'SUPERSEDED', lastErrorCode = 'EXPLICIT_LOCAL_REPUBLICATION' WHERE forestId = :forestId AND accountId = :accountId AND state != 'SYNCED'")
    suspend fun supersede(forestId: String, accountId: String): Int
    /** Called only by the explicit local privacy purge, never logout or ordinary business deletion. */
    @Query("DELETE FROM hierarchy_sync_operations") suspend fun hardDeleteOperations(): Int
    @Query("DELETE FROM hierarchy_sync_associations") suspend fun hardDeleteAssociations(): Int
}
