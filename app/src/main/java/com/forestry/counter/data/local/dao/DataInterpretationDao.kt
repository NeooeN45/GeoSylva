package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.forestry.counter.data.local.entity.DataInterpretationEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour les interprétations de données.
 * Permet de gérer les analyses et recommandations générées par le système.
 */
@Dao
interface DataInterpretationDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInterpretation(interpretation: DataInterpretationEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInterpretations(interpretations: List<DataInterpretationEntity>)
    
    @Update
    suspend fun updateInterpretation(interpretation: DataInterpretationEntity)
    
    @Query("SELECT * FROM data_interpretations WHERE interpretationId = :interpretationId")
    suspend fun getInterpretationById(interpretationId: String): DataInterpretationEntity?
    
    @Query("SELECT * FROM data_interpretations WHERE parcelleId = :parcelleId")
    suspend fun getInterpretationsByParcelle(parcelleId: String): List<DataInterpretationEntity>
    
    @Query("SELECT * FROM data_interpretations WHERE parcelleId = :parcelleId")
    fun getInterpretationsByParcelleFlow(parcelleId: String): Flow<List<DataInterpretationEntity>>
    
    @Query("SELECT * FROM data_interpretations WHERE interpretationType = :type")
    suspend fun getInterpretationsByType(type: String): List<DataInterpretationEntity>
    
    @Query("SELECT * FROM data_interpretations WHERE priority = :priority")
    suspend fun getInterpretationsByPriority(priority: String): List<DataInterpretationEntity>
    
    @Query("SELECT * FROM data_interpretations WHERE actionable = 1")
    suspend fun getActionableInterpretations(): List<DataInterpretationEntity>
    
    @Query("SELECT * FROM data_interpretations WHERE confidenceScore >= :minConfidence")
    suspend fun getHighConfidenceInterpretations(minConfidence: Double): List<DataInterpretationEntity>
    
    @Query("SELECT * FROM data_interpretations WHERE validUntil > :currentTime OR validUntil IS NULL")
    suspend fun getValidInterpretations(currentTime: Long): List<DataInterpretationEntity>
    
    @Query("SELECT * FROM data_interpretations WHERE createdAt >= :since")
    suspend fun getRecentInterpretations(since: Long): List<DataInterpretationEntity>
    
    @Query("DELETE FROM data_interpretations WHERE interpretationId = :interpretationId")
    suspend fun deleteInterpretation(interpretationId: String)
    
    @Query("DELETE FROM data_interpretations WHERE parcelleId = :parcelleId")
    suspend fun deleteInterpretationsByParcelle(parcelleId: String)
    
    @Query("DELETE FROM data_interpretations WHERE validUntil < :currentTime")
    suspend fun deleteExpiredInterpretations(currentTime: Long)
    
    @Query("DELETE FROM data_interpretations WHERE createdAt < :before")
    suspend fun deleteOldInterpretations(before: Long)
    
    @Query("SELECT COUNT(*) FROM data_interpretations")
    suspend fun getInterpretationCount(): Int
    
    @Query("SELECT COUNT(*) FROM data_interpretations WHERE parcelleId = :parcelleId AND actionable = 1")
    suspend fun getActionableInterpretationCount(parcelleId: String): Int
    
    @Query("SELECT * FROM data_interpretations ORDER BY priority DESC, confidenceScore DESC LIMIT :limit")
    suspend fun getTopPriorityInterpretations(limit: Int): List<DataInterpretationEntity>
    
    @Query("SELECT * FROM data_interpretations WHERE tags LIKE '%' || :tag || '%'")
    suspend fun getInterpretationsByTag(tag: String): List<DataInterpretationEntity>
}
