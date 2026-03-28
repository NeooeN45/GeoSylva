package com.forestry.counter.data.local.dao

import androidx.room.*
import com.forestry.counter.data.local.entity.FloraFtsEntity
import com.forestry.counter.data.local.entity.GpsContextCacheEntity

@Dao
interface FloraFtsDao {

    // ── FTS Flora ─────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<FloraFtsEntity>)

    @Query("SELECT * FROM flora_fts WHERE flora_fts MATCH :query LIMIT :limit")
    suspend fun search(query: String, limit: Int = 20): List<FloraFtsEntity>

    @Query("SELECT * FROM flora_fts WHERE flora_fts MATCH :query AND type_milieu LIKE '%' || :milieu || '%' LIMIT :limit")
    suspend fun searchInMilieu(query: String, milieu: String, limit: Int = 15): List<FloraFtsEntity>

    @Query("SELECT COUNT(*) FROM flora_fts")
    suspend fun count(): Int

    @Query("DELETE FROM flora_fts")
    suspend fun clearAll()

    // ── GPS Context Cache ─────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGpsContext(entity: GpsContextCacheEntity)

    @Query("SELECT * FROM gps_context_cache WHERE lat_key = :lat AND lon_key = :lon LIMIT 1")
    suspend fun getGpsContext(lat: Double, lon: Double): GpsContextCacheEntity?

    @Query("DELETE FROM gps_context_cache WHERE computed_at < :olderThanMs")
    suspend fun evictOldContexts(olderThanMs: Long)

    @Query("SELECT COUNT(*) FROM gps_context_cache")
    suspend fun gpsContextCount(): Int
}
