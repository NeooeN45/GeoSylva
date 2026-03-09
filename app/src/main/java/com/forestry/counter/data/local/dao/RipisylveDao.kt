package com.forestry.counter.data.local.dao

import androidx.room.*
import com.forestry.counter.data.local.entity.RipisylveEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RipisylveDao {

    @Query("SELECT * FROM ripisylve_diagnostics WHERE parcelleId = :parcelleId ORDER BY observationDate DESC")
    fun getByParcelle(parcelleId: String): Flow<List<RipisylveEntity>>

    @Query("SELECT * FROM ripisylve_diagnostics WHERE id = :id")
    fun getById(id: String): Flow<RipisylveEntity?>

    @Query("SELECT * FROM ripisylve_diagnostics ORDER BY observationDate DESC")
    fun getAll(): Flow<List<RipisylveEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RipisylveEntity)

    @Delete
    suspend fun delete(entity: RipisylveEntity)

    @Query("DELETE FROM ripisylve_diagnostics WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM ripisylve_diagnostics WHERE parcelleId = :parcelleId")
    suspend fun deleteByParcelle(parcelleId: String)
}
