package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.forestry.counter.data.local.entity.WoodDepotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WoodDepotDao {
    @Query("SELECT * FROM wood_depots WHERE depotId = :id")
    suspend fun getByIdIncludingDeleted(id: String): WoodDepotEntity?

    @Query("""
        SELECT * FROM wood_depots
        WHERE ownerScope = :scope AND ownerAccountId IS :accountId AND deletedAt IS NULL
        ORDER BY updatedAt DESC, depotId
    """)
    fun observeActive(scope: String, accountId: String?): Flow<List<WoodDepotEntity>>

    /** Canonical read inside the repository transaction; Flow tuples are wake-up signals only. */
    @Query("""
        SELECT * FROM wood_depots
        WHERE ownerScope = :scope AND ownerAccountId IS :accountId AND deletedAt IS NULL
        ORDER BY updatedAt DESC, depotId
    """)
    suspend fun getActive(scope: String, accountId: String?): List<WoodDepotEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: WoodDepotEntity)

    @Query("""
        UPDATE wood_depots SET name = :name, kind = :kind, notes = :notes,
            latitude = :latitude, longitude = :longitude, accuracyM = :accuracyM,
            locationSource = :locationSource, revision = revision + 1,
            updatedAt = :updatedAt, updatedBy = :updatedBy
        WHERE depotId = :id AND ownerScope = :scope AND ownerAccountId IS :accountId
            AND deletedAt IS NULL AND revision = :expectedRevision AND revision < 2147483647
    """)
    suspend fun compareAndSet(
        id: String, scope: String, accountId: String?, expectedRevision: Int,
        name: String, kind: String, notes: String?, latitude: Double?, longitude: Double?,
        accuracyM: Double?, locationSource: String?, updatedAt: Long, updatedBy: String,
    ): Int

    @Query("""
        UPDATE wood_depots SET deletedAt = :deletedAt, revision = revision + 1,
            updatedAt = :deletedAt, updatedBy = :updatedBy
        WHERE depotId = :id AND ownerScope = :scope AND ownerAccountId IS :accountId
            AND deletedAt IS NULL AND revision = :expectedRevision AND revision < 2147483647
    """)
    suspend fun softDelete(
        id: String, scope: String, accountId: String?, expectedRevision: Int,
        deletedAt: Long, updatedBy: String,
    ): Int

    /** Reserved for the existing explicit local personal-data purge. */
    @Query("DELETE FROM wood_depots")
    suspend fun hardDeleteAll()
}
