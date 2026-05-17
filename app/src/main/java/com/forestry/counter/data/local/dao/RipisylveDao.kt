package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.forestry.counter.data.local.entity.RipisylveEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RipisylveDao {
    @Query("SELECT * FROM ripisylve_observation WHERE parcelleId = :parcelleId ORDER BY observationDate DESC")
    fun getByParcelle(parcelleId: String): Flow<List<RipisylveEntity>>

    @Query("SELECT * FROM ripisylve_observation ORDER BY observationDate DESC")
    fun getAll(): Flow<List<RipisylveEntity>>

    @Query("SELECT * FROM ripisylve_observation WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RipisylveEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RipisylveEntity)

    @Query("DELETE FROM ripisylve_observation WHERE id = :id")
    suspend fun deleteById(id: String)
}
