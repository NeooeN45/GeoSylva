package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.forestry.counter.data.local.entity.StationEnvironnementaleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StationEnvironnementaleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(station: StationEnvironnementaleEntity)

    @Update
    suspend fun update(station: StationEnvironnementaleEntity)

    @Query("SELECT * FROM stations_environnementales WHERE parcelleId = :parcelleId")
    fun getByParcelle(parcelleId: String): Flow<StationEnvironnementaleEntity?>

    @Query("SELECT * FROM stations_environnementales WHERE parcelleId = :parcelleId")
    suspend fun getByParcelleOnce(parcelleId: String): StationEnvironnementaleEntity?

    @Query("SELECT * FROM stations_environnementales WHERE codeSer = :codeSer")
    suspend fun getBySer(codeSer: String): List<StationEnvironnementaleEntity>

    @Query("DELETE FROM stations_environnementales WHERE parcelleId = :parcelleId")
    suspend fun deleteByParcelle(parcelleId: String)

    @Query("UPDATE stations_environnementales SET dvfPrixMedianEurM2 = :prix, dvfNbTransactions = :nb, dvfDateFetch = :fetchedAt WHERE parcelleId = :parcelleId")
    suspend fun updateDvf(parcelleId: String, prix: Double?, nb: Int?, fetchedAt: Long)
}
