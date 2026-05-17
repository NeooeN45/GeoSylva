package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.forestry.counter.data.local.entity.AlerteSanitaireEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlerteSanitaireDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alerte: AlerteSanitaireEntity)

    @Update
    suspend fun update(alerte: AlerteSanitaireEntity)

    @Delete
    suspend fun delete(alerte: AlerteSanitaireEntity)

    @Query("SELECT * FROM alertes_sanitaires WHERE parcelleId = :parcelleId ORDER BY dateDetection DESC")
    fun getByParcelle(parcelleId: String): Flow<List<AlerteSanitaireEntity>>

    @Query("SELECT * FROM alertes_sanitaires WHERE parcelleId = :parcelleId AND niveauRisque IN ('ELEVE', 'CRITIQUE') ORDER BY dateDetection DESC")
    fun getCriticalByParcelle(parcelleId: String): Flow<List<AlerteSanitaireEntity>>

    @Query("SELECT * FROM alertes_sanitaires WHERE isOrganismeReglemente = 1 AND isAlerteDsf = 0")
    suspend fun getPendingDsfAlerts(): List<AlerteSanitaireEntity>

    @Query("SELECT COUNT(*) FROM alertes_sanitaires WHERE parcelleId = :parcelleId AND niveauRisque IN ('ELEVE', 'CRITIQUE')")
    suspend fun countCriticalByParcelle(parcelleId: String): Int

    @Query("DELETE FROM alertes_sanitaires WHERE alerteId = :id")
    suspend fun deleteById(id: String)
}
