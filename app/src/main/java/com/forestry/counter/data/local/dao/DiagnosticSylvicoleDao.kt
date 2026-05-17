package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.forestry.counter.data.local.entity.DiagnosticSylvicoleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticSylvicoleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(diagnostic: DiagnosticSylvicoleEntity)

    @Update
    suspend fun update(diagnostic: DiagnosticSylvicoleEntity)

    @Delete
    suspend fun delete(diagnostic: DiagnosticSylvicoleEntity)

    @Query("SELECT * FROM diagnostics_sylvicoles WHERE parcelleId = :parcelleId ORDER BY dateCreation DESC")
    fun getByParcelle(parcelleId: String): Flow<List<DiagnosticSylvicoleEntity>>

    @Query("SELECT * FROM diagnostics_sylvicoles WHERE parcelleId = :parcelleId ORDER BY dateCreation DESC LIMIT 1")
    suspend fun getLatestByParcelle(parcelleId: String): DiagnosticSylvicoleEntity?

    @Query("SELECT * FROM diagnostics_sylvicoles WHERE diagnosticId = :id")
    suspend fun getById(id: String): DiagnosticSylvicoleEntity?

    @Query("SELECT * FROM diagnostics_sylvicoles WHERE sessionId = :sessionId ORDER BY dateCreation DESC LIMIT 1")
    suspend fun getBySession(sessionId: String): DiagnosticSylvicoleEntity?

    @Query("SELECT * FROM diagnostics_sylvicoles WHERE scoreGlobal IS NOT NULL ORDER BY scoreGlobal ASC")
    suspend fun getAllByScoreAsc(): List<DiagnosticSylvicoleEntity>

    @Query("DELETE FROM diagnostics_sylvicoles WHERE diagnosticId = :id")
    suspend fun deleteById(id: String)
}
