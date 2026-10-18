package com.forestry.counter.data.local.dao

import androidx.room.*
import com.forestry.counter.data.local.entity.ParcelleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParcelleDao {
    @Query("SELECT * FROM parcelles WHERE deletedAt IS NULL ORDER BY name ASC")
    fun getAllParcelles(): Flow<List<ParcelleEntity>>

    @Query("SELECT * FROM parcelles WHERE deletedAt IS NULL ORDER BY name ASC")
    suspend fun getAllParcellesNow(): List<ParcelleEntity>

    @Query("SELECT * FROM parcelles WHERE (foretId = :forestId OR forestOwnerId = :forestId) AND deletedAt IS NULL ORDER BY name ASC")
    fun getParcellesByForest(forestId: String): Flow<List<ParcelleEntity>>

    @Query("SELECT * FROM parcelles WHERE parcelleId = :id AND deletedAt IS NULL")
    fun getParcelleByIdFlow(id: String): Flow<ParcelleEntity?>

    @Query("SELECT * FROM parcelles WHERE parcelleId = :id AND deletedAt IS NULL")
    suspend fun getParcelleById(id: String): ParcelleEntity?

    /**
     * Comme [getParcelleById] mais inclut les parcelles supprimées
     * (deletedAt non nul) — nécessaire au pull serveur→local pour distinguer
     * "jamais vue" de "supprimée localement" (GEOSYLVA P0-3, résolution).
     */
    @Query("SELECT * FROM parcelles WHERE parcelleId = :id")
    suspend fun getParcelleByIdAny(id: String): ParcelleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertParcelleIfAbsent(entity: ParcelleEntity): Long

    /** REPLACE deletes a parent row before inserting it and would cascade to its children. */
    @Transaction
    suspend fun insertParcelle(entity: ParcelleEntity) {
        if (insertParcelleIfAbsent(entity) == -1L) {
            // A different row with the same unique UUID must fail rather than disappear silently.
            check(getParcelleByIdAny(entity.parcelleId) != null) { "PARCEL_IDENTITY_CONFLICT" }
            updateParcelle(entity)
        }
    }

    @Transaction
    suspend fun insertParcelles(entities: List<ParcelleEntity>) {
        entities.forEach { insertParcelle(it) }
    }

    @Update
    suspend fun updateParcelle(entity: ParcelleEntity)

    // --- Soft delete (Vague C) : suppression logique via deletedAt ---

    /** Soft delete d'une parcelle par son identifiant. */
    @Query("UPDATE parcelles SET deletedAt = :timestamp WHERE parcelleId = :id")
    suspend fun deleteParcelle(id: String, timestamp: Long)

    /** Soft delete d'une parcelle par son identifiant (alias sémantique). */
    @Query("UPDATE parcelles SET deletedAt = :timestamp WHERE parcelleId = :id")
    suspend fun deleteParcelleById(id: String, timestamp: Long)

    /** Soft delete massif des parcelles non encore supprimées. */
    @Query("UPDATE parcelles SET deletedAt = :timestamp WHERE deletedAt IS NULL")
    suspend fun deleteAllParcelles(timestamp: Long)

    /**
     * Suppression physique de toutes les parcelles (droit à l'effacement RGPD).
     * À n'utiliser que depuis [DeleteAllUserDataUseCase].
     */
    @Query("DELETE FROM parcelles")
    suspend fun hardDeleteAll()

    // --- Backfill UUID (Lot 1) ---

    @Query("SELECT * FROM parcelles WHERE uuid IS NULL AND deletedAt IS NULL")
    suspend fun getWithoutUuid(): List<ParcelleEntity>

    @Query("UPDATE parcelles SET uuid = :uuid WHERE parcelleId = :id")
    suspend fun setUuid(id: String, uuid: String)
}
