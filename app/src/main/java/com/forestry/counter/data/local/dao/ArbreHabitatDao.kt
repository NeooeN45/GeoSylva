package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.forestry.counter.data.local.entity.ArbreHabitatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArbreHabitatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(arbre: ArbreHabitatEntity)

    @Update
    suspend fun update(arbre: ArbreHabitatEntity)

    @Delete
    suspend fun delete(arbre: ArbreHabitatEntity)

    @Query("SELECT * FROM arbres_habitat WHERE parcelleId = :parcelleId ORDER BY diamCm DESC")
    fun getByParcelle(parcelleId: String): Flow<List<ArbreHabitatEntity>>

    @Query("SELECT * FROM arbres_habitat WHERE placetteId = :placetteId ORDER BY diamCm DESC")
    fun getByPlacette(placetteId: String): Flow<List<ArbreHabitatEntity>>

    @Query("SELECT * FROM arbres_habitat WHERE isArbreRemarquable = 1 AND parcelleId = :parcelleId")
    suspend fun getRemarquablesByParcelle(parcelleId: String): List<ArbreHabitatEntity>

    @Query("SELECT SUM(treemScore) FROM arbres_habitat WHERE parcelleId = :parcelleId AND treemScore IS NOT NULL")
    suspend fun sumTreemScoreByParcelle(parcelleId: String): Int?

    @Query("SELECT COUNT(*) FROM arbres_habitat WHERE parcelleId = :parcelleId AND boisMortSurPied = 1")
    suspend fun countBoisMortByParcelle(parcelleId: String): Int

    @Query("DELETE FROM arbres_habitat WHERE arbreHabitatId = :id")
    suspend fun deleteById(id: String)
}
