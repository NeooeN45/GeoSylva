package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.forestry.counter.data.local.entity.StationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Query("SELECT * FROM station_diagnostics WHERE parcelleId = :parcelleId ORDER BY observationDate DESC")
    fun getByParcelle(parcelleId: String): Flow<List<StationEntity>>

    @Query("SELECT * FROM station_diagnostics WHERE id = :id")
    fun getById(id: String): Flow<StationEntity?>

    @Query("SELECT * FROM station_diagnostics ORDER BY observationDate DESC")
    fun getAll(): Flow<List<StationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: StationEntity)

    @Query("DELETE FROM station_diagnostics WHERE id = :id")
    suspend fun deleteById(id: String)
}
