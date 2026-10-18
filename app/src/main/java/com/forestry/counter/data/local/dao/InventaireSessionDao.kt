package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.forestry.counter.data.local.entity.InventaireSessionEntity
import com.forestry.counter.data.local.entity.ParcelleEntity
import com.forestry.counter.data.local.entity.PlacetteEntity
import com.forestry.counter.data.local.entity.TigeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventaireSessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: InventaireSessionEntity)

    @Update
    suspend fun update(session: InventaireSessionEntity)

    @Transaction
    suspend fun withMartelageTransaction(block: suspend () -> Unit) { block() }

    @Query("SELECT * FROM inventaire_sessions WHERE parcelleId = :parcelleId AND martelageState IN ('RUNNING', 'PAUSED') AND deletedAt IS NULL")
    suspend fun getActiveForParcelle(parcelleId: String): List<InventaireSessionEntity>

    @Query("SELECT * FROM inventaire_sessions WHERE parcelleId = :parcelleId AND placetteId = :placetteId AND martelageState IN ('RUNNING', 'PAUSED') AND deletedAt IS NULL")
    suspend fun getActiveForPlacette(parcelleId: String, placetteId: String): List<InventaireSessionEntity>

    @Query("SELECT * FROM placettes WHERE placetteId = :placetteId AND parcelleOwnerId = :parcelleId AND deletedAt IS NULL")
    suspend fun getLivePlot(parcelleId: String, placetteId: String): PlacetteEntity?

    @Query("SELECT * FROM inventaire_sessions WHERE campaignId = :campaignId AND deletedAt IS NULL ORDER BY revisionNumber DESC")
    suspend fun getCampaignRevisions(campaignId: String): List<InventaireSessionEntity>

    @Query("SELECT * FROM parcelles WHERE parcelleId = :parcelleId AND deletedAt IS NULL")
    suspend fun getLiveParent(parcelleId: String): ParcelleEntity?

    @Query("""
        SELECT accountId FROM parcel_sync_queue WHERE parcelId = :parcelleId
        UNION
        SELECT a.accountId FROM hierarchy_sync_associations a
        JOIN parcelles p ON p.foretId = a.forestId WHERE p.parcelleId = :parcelleId
    """)
    suspend fun getLinkedAccounts(parcelleId: String): List<String>

    @Query("SELECT * FROM placettes WHERE parcelleOwnerId = :parcelleId AND deletedAt IS NULL ORDER BY placetteId")
    suspend fun getLivePlots(parcelleId: String): List<PlacetteEntity>

    @Query("SELECT * FROM tiges WHERE sessionId = :sessionId AND deletedAt IS NULL ORDER BY tigeId")
    suspend fun getLiveTrees(sessionId: String): List<TigeEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCorrectionTrees(trees: List<TigeEntity>)


    // --- Soft delete (Vague C) : suppression logique via deletedAt ---

    /** Soft delete d'une session par son identifiant. */
    @Query("UPDATE inventaire_sessions SET deletedAt = :timestamp WHERE sessionId = :id")
    suspend fun delete(id: String, timestamp: Long)

    /** Soft delete d'une session par son identifiant (alias sémantique). */
    @Query("UPDATE inventaire_sessions SET deletedAt = :timestamp WHERE sessionId = :id")
    suspend fun deleteById(id: String, timestamp: Long)

    /** Soft delete massif des sessions non encore supprimées. */
    @Query("UPDATE inventaire_sessions SET deletedAt = :timestamp WHERE deletedAt IS NULL")
    suspend fun deleteAll(timestamp: Long)

    /**
     * Suppression physique de toutes les sessions (droit à l'effacement RGPD).
     * À n'utiliser que depuis [DeleteAllUserDataUseCase].
     */
    @Query("DELETE FROM inventaire_sessions")
    suspend fun hardDeleteAll()

    // --- Lectures : les lignes soft-deleted sont filtrées ---

    @Query("SELECT * FROM inventaire_sessions WHERE parcelleId = :parcelleId AND deletedAt IS NULL ORDER BY dateDebut DESC")
    fun getByParcelle(parcelleId: String): Flow<List<InventaireSessionEntity>>

    @Query("SELECT * FROM inventaire_sessions WHERE placetteId = :placetteId AND deletedAt IS NULL ORDER BY dateDebut DESC")
    fun getByPlacette(placetteId: String): Flow<List<InventaireSessionEntity>>

    @Query("SELECT * FROM inventaire_sessions WHERE sessionId = :id AND deletedAt IS NULL")
    suspend fun getById(id: String): InventaireSessionEntity?

    @Query("SELECT * FROM inventaire_sessions WHERE parcelleId = :parcelleId AND typeSession = :type AND deletedAt IS NULL ORDER BY dateDebut DESC LIMIT 1")
    suspend fun getLatestByType(parcelleId: String, type: String): InventaireSessionEntity?
}
