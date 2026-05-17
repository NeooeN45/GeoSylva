package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.forestry.counter.data.local.entity.AdvancedCalculationEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour les calculs avancés.
 * Permet de gérer les calculs complexes et leurs résultats.
 */
@Dao
interface AdvancedCalculationDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalculation(calculation: AdvancedCalculationEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalculations(calculations: List<AdvancedCalculationEntity>)
    
    @Update
    suspend fun updateCalculation(calculation: AdvancedCalculationEntity)
    
    @Query("SELECT * FROM advanced_calculations WHERE calculationId = :calculationId")
    suspend fun getCalculationById(calculationId: String): AdvancedCalculationEntity?
    
    @Query("SELECT * FROM advanced_calculations WHERE parcelleId = :parcelleId")
    suspend fun getCalculationsByParcelle(parcelleId: String): List<AdvancedCalculationEntity>
    
    @Query("SELECT * FROM advanced_calculations WHERE parcelleId = :parcelleId")
    fun getCalculationsByParcelleFlow(parcelleId: String): Flow<List<AdvancedCalculationEntity>>
    
    @Query("SELECT * FROM advanced_calculations WHERE calculationType = :type")
    suspend fun getCalculationsByType(type: String): List<AdvancedCalculationEntity>
    
    @Query("SELECT * FROM advanced_calculations WHERE status = :status")
    suspend fun getCalculationsByStatus(status: String): List<AdvancedCalculationEntity>
    
    @Query("SELECT * FROM advanced_calculations WHERE priority = :priority")
    suspend fun getCalculationsByPriority(priority: String): List<AdvancedCalculationEntity>
    
    @Query("SELECT * FROM advanced_calculations WHERE status = 'PENDING' ORDER BY priority DESC")
    suspend fun getPendingCalculations(): List<AdvancedCalculationEntity>
    
    @Query("SELECT * FROM advanced_calculations WHERE status = 'RUNNING'")
    suspend fun getRunningCalculations(): List<AdvancedCalculationEntity>
    
    @Query("SELECT * FROM advanced_calculations WHERE status = 'FAILED'")
    suspend fun getFailedCalculations(): List<AdvancedCalculationEntity>
    
    @Query("SELECT * FROM advanced_calculations WHERE result IS NOT NULL")
    suspend fun getCompletedCalculations(): List<AdvancedCalculationEntity>
    
    @Query("SELECT * FROM advanced_calculations WHERE validUntil > :currentTime OR validUntil IS NULL")
    suspend fun getValidCalculations(currentTime: Long): List<AdvancedCalculationEntity>
    
    @Query("SELECT * FROM advanced_calculations WHERE executionTime IS NOT NULL")
    suspend fun getCalculationsWithExecutionTime(): List<AdvancedCalculationEntity>
    
    @Query("DELETE FROM advanced_calculations WHERE calculationId = :calculationId")
    suspend fun deleteCalculation(calculationId: String)
    
    @Query("DELETE FROM advanced_calculations WHERE parcelleId = :parcelleId")
    suspend fun deleteCalculationsByParcelle(parcelleId: String)
    
    @Query("DELETE FROM advanced_calculations WHERE validUntil < :currentTime")
    suspend fun deleteExpiredCalculations(currentTime: Long)
    
    @Query("DELETE FROM advanced_calculations WHERE createdAt < :before AND status = 'COMPLETED'")
    suspend fun deleteOldCompletedCalculations(before: Long)
    
    @Query("SELECT COUNT(*) FROM advanced_calculations")
    suspend fun getCalculationCount(): Int
    
    @Query("SELECT COUNT(*) FROM advanced_calculations WHERE status = 'PENDING'")
    suspend fun getPendingCalculationCount(): Int
    
    @Query("SELECT COUNT(*) FROM advanced_calculations WHERE status = 'FAILED'")
    suspend fun getFailedCalculationCount(): Int
    
    @Query("SELECT AVG(executionTime) FROM advanced_calculations WHERE executionTime IS NOT NULL")
    suspend fun getAverageExecutionTime(): Double?
    
    @Query("SELECT * FROM advanced_calculations ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentCalculations(limit: Int): List<AdvancedCalculationEntity>
    
    @Query("SELECT * FROM advanced_calculations WHERE tags LIKE '%' || :tag || '%'")
    suspend fun getCalculationsByTag(tag: String): List<AdvancedCalculationEntity>
    
    @Query("UPDATE advanced_calculations SET status = 'CANCELLED' WHERE calculationId = :calculationId")
    suspend fun cancelCalculation(calculationId: String)
}
