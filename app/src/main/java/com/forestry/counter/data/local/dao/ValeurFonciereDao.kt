package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.forestry.counter.data.local.entity.ValeurFonciereEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ValeurFonciereDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(valeur: ValeurFonciereEntity)

    @Update
    suspend fun update(valeur: ValeurFonciereEntity)

    @Query("SELECT * FROM valeurs_foncieres WHERE parcelleId = :parcelleId")
    fun getByParcelle(parcelleId: String): Flow<ValeurFonciereEntity?>

    @Query("SELECT * FROM valeurs_foncieres WHERE parcelleId = :parcelleId")
    suspend fun getByParcelleOnce(parcelleId: String): ValeurFonciereEntity?

    @Query("SELECT SUM(valeurTotalePatrimoineEur) FROM valeurs_foncieres")
    suspend fun sumPatrimoineTotal(): Double?

    @Query("SELECT SUM(carboneTotalTonnes) FROM valeurs_foncieres")
    suspend fun sumCarboneTotal(): Double?

    @Query("DELETE FROM valeurs_foncieres WHERE parcelleId = :parcelleId")
    suspend fun deleteByParcelle(parcelleId: String)
}
