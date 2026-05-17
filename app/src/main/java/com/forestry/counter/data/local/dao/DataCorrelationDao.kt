package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.forestry.counter.data.local.entity.DataCorrelationEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour les corrélations de données.
 * Permet de gérer les corrélations entre différentes entités forestières.
 */
@Dao
interface DataCorrelationDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCorrelation(correlation: DataCorrelationEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCorrelations(correlations: List<DataCorrelationEntity>)
    
    @Update
    suspend fun updateCorrelation(correlation: DataCorrelationEntity)
    
    @Query("SELECT * FROM data_correlations WHERE correlationId = :correlationId")
    suspend fun getCorrelationById(correlationId: String): DataCorrelationEntity?
    
    @Query("SELECT * FROM data_correlations WHERE sourceParcelleId = :parcelleId")
    suspend fun getCorrelationsByParcelle(parcelleId: String): List<DataCorrelationEntity>
    
    @Query("SELECT * FROM data_correlations WHERE sourceParcelleId = :parcelleId")
    fun getCorrelationsByParcelleFlow(parcelleId: String): Flow<List<DataCorrelationEntity>>
    
    @Query("SELECT * FROM data_correlations WHERE correlationType = :type")
    suspend fun getCorrelationsByType(type: String): List<DataCorrelationEntity>
    
    @Query("SELECT * FROM data_correlations WHERE correlationStrength >= :minStrength")
    suspend fun getStrongCorrelations(minStrength: Double): List<DataCorrelationEntity>
    
    @Query("SELECT * FROM data_correlations WHERE confidenceLevel >= :minConfidence")
    suspend fun getHighConfidenceCorrelations(minConfidence: Double): List<DataCorrelationEntity>
    
    @Query("SELECT * FROM data_correlations WHERE sourceDataType = :dataType AND targetDataType = :targetDataType")
    suspend fun getCorrelationsByDataTypes(dataType: String, targetDataType: String?): List<DataCorrelationEntity>
    
    @Query("SELECT * FROM data_correlations WHERE createdAt >= :since")
    suspend fun getRecentCorrelations(since: Long): List<DataCorrelationEntity>
    
    @Query("DELETE FROM data_correlations WHERE correlationId = :correlationId")
    suspend fun deleteCorrelation(correlationId: String)
    
    @Query("DELETE FROM data_correlations WHERE sourceParcelleId = :parcelleId")
    suspend fun deleteCorrelationsByParcelle(parcelleId: String)
    
    @Query("DELETE FROM data_correlations WHERE createdAt < :before")
    suspend fun deleteOldCorrelations(before: Long)
    
    @Query("SELECT COUNT(*) FROM data_correlations")
    suspend fun getCorrelationCount(): Int
    
    @Query("SELECT AVG(correlationStrength) FROM data_correlations WHERE sourceParcelleId = :parcelleId")
    suspend fun getAverageCorrelationStrength(parcelleId: String): Double?
    
    @Query("SELECT * FROM data_correlations ORDER BY correlationStrength DESC LIMIT :limit")
    suspend fun getTopCorrelations(limit: Int): List<DataCorrelationEntity>
    
    @Query("SELECT * FROM data_correlations WHERE statisticalSignificance < :significance")
    suspend fun getSignificantCorrelations(significance: Double): List<DataCorrelationEntity>
}
