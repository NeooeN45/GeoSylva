package com.forestry.counter.data.local.dao

import androidx.room.*
import com.forestry.counter.data.local.entity.TigeEntity
import com.forestry.counter.data.local.entity.InventaireSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TigeDao {

    @Transaction
    suspend fun withTigeMutationTransaction(block: suspend () -> Unit) { block() }

    @Query("SELECT * FROM inventaire_sessions WHERE parcelleId = :parcelleId AND martelageState IS NOT NULL ORDER BY dateDebut DESC")
    suspend fun getManagedSessionsForMutation(parcelleId: String): List<InventaireSessionEntity>

    @Query("SELECT * FROM inventaire_sessions WHERE parcelleId = :parcelleId AND (placetteId = :placetteId OR placetteId IS NULL) AND martelageState IS NOT NULL ORDER BY dateDebut DESC")
    suspend fun getManagedSessionsForPlacetteMutation(parcelleId: String, placetteId: String?): List<InventaireSessionEntity>

    @Query("SELECT * FROM inventaire_sessions WHERE sessionId = :sessionId")
    suspend fun getSessionForMutation(sessionId: String): InventaireSessionEntity?

    @Query("SELECT COUNT(*) FROM parcelles WHERE parcelleId = :parcelleId AND deletedAt IS NULL")
    suspend fun countLiveParentForMutation(parcelleId: String): Int

    @Query("""
        SELECT accountId FROM parcel_sync_queue WHERE parcelId = :parcelleId
        UNION
        SELECT a.accountId FROM hierarchy_sync_associations a
        JOIN parcelles p ON p.foretId = a.forestId WHERE p.parcelleId = :parcelleId
    """)
    suspend fun getLinkedAccountsForMutation(parcelleId: String): List<String>

    @Query("SELECT COUNT(*) FROM placettes WHERE placetteId = :placetteId AND parcelleOwnerId = :parcelleId AND deletedAt IS NULL")
    suspend fun countLivePlotForMutation(parcelleId: String, placetteId: String): Int

    @Query("SELECT * FROM tiges WHERE parcelleOwnerId = :parcelleId AND deletedAt IS NULL ORDER BY timestamp ASC")
    suspend fun getTigesByParcelleNow(parcelleId: String): List<TigeEntity>

    @Query("""
        SELECT * FROM tiges WHERE parcelleOwnerId = :parcelleId
        AND ((:placetteId IS NULL AND placetteOwnerId IS NULL) OR placetteOwnerId = :placetteId)
        AND essenceCode = :essenceCode AND diamCm = :diamCm AND deletedAt IS NULL
        AND ((:sessionId IS NULL AND sessionId IS NULL) OR sessionId = :sessionId)
        ORDER BY timestamp DESC, tigeId DESC LIMIT 1
    """)
    suspend fun getLatestMatchingInSession(
        parcelleId: String, placetteId: String?, essenceCode: String, diamCm: Double, sessionId: String?,
    ): TigeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNewTiges(entities: List<TigeEntity>)

    @Query("SELECT * FROM tiges WHERE deletedAt IS NULL ORDER BY timestamp ASC")
    fun getAllTiges(): Flow<List<TigeEntity>>

    @Query("SELECT * FROM tiges WHERE deletedAt IS NULL ORDER BY timestamp ASC")
    suspend fun getAllTigesNow(): List<TigeEntity>

    @Query("SELECT * FROM tiges WHERE parcelleOwnerId = :parcelleId AND deletedAt IS NULL ORDER BY timestamp ASC")
    fun getTigesByParcelle(parcelleId: String): Flow<List<TigeEntity>>

    @Query("SELECT * FROM tiges WHERE placetteOwnerId = :placetteId AND deletedAt IS NULL ORDER BY timestamp ASC")
    fun getTigesByPlacette(placetteId: String): Flow<List<TigeEntity>>

    @Query("SELECT * FROM tiges WHERE tigeId = :id AND deletedAt IS NULL")
    fun getTigeByIdFlow(id: String): Flow<TigeEntity?>

    @Query("SELECT * FROM tiges WHERE tigeId = :id AND deletedAt IS NULL")
    suspend fun getTigeById(id: String): TigeEntity?

    @Query("""
        SELECT * FROM tiges
        WHERE parcelleOwnerId = :parcelleId
          AND ((:placetteId IS NULL AND placetteOwnerId IS NULL) OR placetteOwnerId = :placetteId)
          AND essenceCode = :essenceCode
          AND diamCm = :diamCm
          AND deletedAt IS NULL
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    suspend fun getLatestMatching(
        parcelleId: String,
        placetteId: String?,
        essenceCode: String,
        diamCm: Double
    ): TigeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTige(entity: TigeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTiges(entities: List<TigeEntity>)

    @Update
    suspend fun updateTige(entity: TigeEntity)

    // --- Soft delete (Vague C) : suppression logique via deletedAt ---

    /** Soft delete d'une tige par son identifiant. */
    @Query("UPDATE tiges SET deletedAt = :timestamp WHERE tigeId = :id")
    suspend fun deleteTige(id: String, timestamp: Long)

    /** Soft delete d'une tige par son identifiant (alias sémantique). */
    @Query("UPDATE tiges SET deletedAt = :timestamp WHERE tigeId = :id")
    suspend fun deleteTigeById(id: String, timestamp: Long)

    /** Soft delete massif des tiges rattachées à une parcelle. */
    @Query("UPDATE tiges SET deletedAt = :timestamp WHERE parcelleOwnerId = :parcelleId AND deletedAt IS NULL")
    suspend fun deleteTigesByParcelle(parcelleId: String, timestamp: Long)

    /** Soft delete massif des tiges rattachées à une placette. */
    @Query("UPDATE tiges SET deletedAt = :timestamp WHERE placetteOwnerId = :placetteId AND deletedAt IS NULL")
    suspend fun deleteTigesByPlacette(placetteId: String, timestamp: Long)

    /** Soft delete massif des tiges d'une placette pour une essence donnée. */
    @Query("UPDATE tiges SET deletedAt = :timestamp WHERE placetteOwnerId = :placetteId AND essenceCode = :essenceCode AND deletedAt IS NULL")
    suspend fun deleteTigesByPlacetteAndEssence(placetteId: String, essenceCode: String, timestamp: Long)

    /** Soft delete massif des tiges non encore supprimées. */
    @Query("UPDATE tiges SET deletedAt = :timestamp WHERE deletedAt IS NULL")
    suspend fun deleteAll(timestamp: Long)

    /**
     * Suppression physique de toutes les tiges (droit à l'effacement RGPD).
     * À n'utiliser que depuis [DeleteAllUserDataUseCase].
     */
    @Query("DELETE FROM tiges")
    suspend fun hardDeleteAll()

    @Query("""
        UPDATE tiges
        SET classeKraft = :classeKraft,
            etatSanitaire = :etatSanitaire,
            vigueur = :vigueur,
            origine = :origine,
            isTigeHabitat = :isTigeHabitat
        WHERE tigeId = :tigeId
    """)
    suspend fun updateTigeSylviculture(
        tigeId: String,
        classeKraft: Int?,
        etatSanitaire: String?,
        vigueur: String?,
        origine: String?,
        isTigeHabitat: Boolean
    ): Int

    // --- Backfill UUID (Lot 1) ---

    @Query("SELECT * FROM tiges WHERE uuid IS NULL AND deletedAt IS NULL")
    suspend fun getWithoutUuid(): List<TigeEntity>

    @Query("UPDATE tiges SET uuid = :uuid WHERE tigeId = :id")
    suspend fun setUuid(id: String, uuid: String)
}
