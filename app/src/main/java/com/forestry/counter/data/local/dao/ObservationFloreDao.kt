package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.forestry.counter.data.local.entity.ObservationFloreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ObservationFloreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(observation: ObservationFloreEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(observations: List<ObservationFloreEntity>)

    @Update
    suspend fun update(observation: ObservationFloreEntity)

    @Delete
    suspend fun delete(observation: ObservationFloreEntity)

    @Query("SELECT * FROM observations_flore WHERE parcelleId = :parcelleId ORDER BY strate ASC, nomScientifique ASC")
    fun getByParcelle(parcelleId: String): Flow<List<ObservationFloreEntity>>

    @Query("SELECT * FROM observations_flore WHERE placetteId = :placetteId ORDER BY strate ASC, nomScientifique ASC")
    fun getByPlacette(placetteId: String): Flow<List<ObservationFloreEntity>>

    @Query("SELECT * FROM observations_flore WHERE sessionId = :sessionId ORDER BY strate ASC, nomScientifique ASC")
    suspend fun getBySession(sessionId: String): List<ObservationFloreEntity>

    @Query("SELECT COUNT(DISTINCT codeEspece) FROM observations_flore WHERE parcelleId = :parcelleId")
    suspend fun countSpeciesByParcelle(parcelleId: String): Int

    @Query("SELECT * FROM observations_flore WHERE parcelleId = :parcelleId AND isEspeceProtegee = 1")
    suspend fun getProtectedSpeciesByParcelle(parcelleId: String): List<ObservationFloreEntity>

    @Query("DELETE FROM observations_flore WHERE parcelleId = :parcelleId")
    suspend fun deleteByParcelle(parcelleId: String)
}
