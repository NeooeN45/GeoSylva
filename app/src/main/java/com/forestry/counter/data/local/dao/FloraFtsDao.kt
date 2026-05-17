package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.forestry.counter.data.local.entity.FloraFtsEntity
import com.forestry.counter.data.local.entity.GpsContextCacheEntity

@Dao
interface FloraFtsDao {
    @Query("SELECT * FROM flora_fts WHERE flora_fts MATCH :query LIMIT :limit")
    suspend fun search(query: String, limit: Int = 20): List<FloraFtsEntity>

    @Query("SELECT * FROM flora_fts WHERE flora_fts MATCH :query AND typeMilieu = :milieu LIMIT :limit")
    suspend fun searchInMilieu(query: String, milieu: String, limit: Int = 20): List<FloraFtsEntity>

    @Query("SELECT COUNT(*) FROM flora_fts")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<FloraFtsEntity>)

    @Query("DELETE FROM flora_fts")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM gps_context_cache")
    suspend fun gpsContextCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGpsContext(entity: GpsContextCacheEntity)

    @Query("SELECT * FROM gps_context_cache WHERE latKey = :lat AND lonKey = :lon")
    suspend fun getGpsContext(lat: Double, lon: Double): GpsContextCacheEntity?

    @Query("DELETE FROM gps_context_cache WHERE computedAt < :before")
    suspend fun purgeOlderThan(before: Long)

    @Query("DELETE FROM gps_context_cache WHERE computedAt < :before")
    suspend fun evictOldContexts(before: Long)
}
