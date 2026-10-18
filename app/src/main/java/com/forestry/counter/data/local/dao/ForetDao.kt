package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.forestry.counter.data.local.entity.ForetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ForetDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(foret: ForetEntity): Long

    /** REPLACE deletes the parent row and detaches its existing parcels. */
    @Transaction
    suspend fun insert(foret: ForetEntity) {
        if (insertIfAbsent(foret) == -1L) {
            check(getByIdAny(foret.foretId) != null) { "FOREST_IDENTITY_CONFLICT" }
            update(foret)
        }
    }

    @Update
    suspend fun update(foret: ForetEntity)

    // --- Soft delete (Vague C) : suppression logique via deletedAt ---

    @Query("""
        SELECT COUNT(*) FROM inventaire_sessions s
        JOIN parcelles p ON p.parcelleId = s.parcelleId
        WHERE (p.foretId = :foretId OR p.forestOwnerId = :foretId)
          AND s.martelageState IN ('RUNNING', 'PAUSED') AND s.deletedAt IS NULL
    """)
    suspend fun countActiveSessionsForForest(foretId: String): Int

    /** Soft delete d'une forêt par son identifiant. */
    @Query("UPDATE forets SET deletedAt = :timestamp WHERE foretId = :id")
    suspend fun delete(id: String, timestamp: Long)

    /** Soft delete d'une forêt par son identifiant (alias sémantique). */
    @Query("UPDATE forets SET deletedAt = :timestamp WHERE foretId = :id")
    suspend fun deleteById(id: String, timestamp: Long)

    /** Soft delete massif des forêts non encore supprimées. */
    @Query("UPDATE forets SET deletedAt = :timestamp WHERE deletedAt IS NULL")
    suspend fun deleteAll(timestamp: Long)

    /**
     * Suppression physique de toutes les forêts (droit à l'effacement RGPD).
     * À n'utiliser que depuis [DeleteAllUserDataUseCase].
     */
    @Query("DELETE FROM forets")
    suspend fun hardDeleteAll()

    // --- Lectures : les lignes soft-deleted sont filtrées ---

    @Query("SELECT * FROM forets WHERE deletedAt IS NULL ORDER BY nom ASC")
    fun getAll(): Flow<List<ForetEntity>>

    @Query("SELECT * FROM forets WHERE deletedAt IS NULL ORDER BY nom ASC")
    suspend fun getAllNow(): List<ForetEntity>

    @Query("SELECT * FROM forets WHERE foretId = :id AND deletedAt IS NULL")
    suspend fun getById(id: String): ForetEntity?

    @Query("SELECT * FROM forets WHERE foretId = :id")
    suspend fun getByIdAny(id: String): ForetEntity?

    @Query("SELECT * FROM forets WHERE proprietaireNom LIKE '%' || :query || '%' AND deletedAt IS NULL ORDER BY nom ASC")
    fun searchByProprietaire(query: String): Flow<List<ForetEntity>>

    // --- Backfill UUID (Lot 1) ---

    @Query("SELECT * FROM forets WHERE uuid IS NULL AND deletedAt IS NULL")
    suspend fun getWithoutUuid(): List<ForetEntity>

    @Query("UPDATE forets SET uuid = :uuid WHERE foretId = :id")
    suspend fun setUuid(id: String, uuid: String)
}
