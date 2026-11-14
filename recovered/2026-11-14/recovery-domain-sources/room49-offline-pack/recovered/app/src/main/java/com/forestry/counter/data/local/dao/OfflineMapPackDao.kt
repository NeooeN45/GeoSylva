package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.forestry.counter.data.local.entity.OfflineMapPackEntity

@Dao
interface OfflineMapPackDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(row: OfflineMapPackEntity)
    @Update suspend fun update(row: OfflineMapPackEntity): Int
    @Query("SELECT * FROM offline_map_packs WHERE authorityKey = :authorityKey ORDER BY createdAt DESC, id ASC")
    suspend fun inAuthority(authorityKey: String): List<OfflineMapPackEntity>
    @Query("SELECT * FROM offline_map_packs WHERE id = :id") suspend fun byId(id: String): OfflineMapPackEntity?
    @Query("SELECT * FROM offline_map_packs") suspend fun allForPurge(): List<OfflineMapPackEntity>
    @Query("DELETE FROM offline_map_packs WHERE id = :id AND authorityKey = :authorityKey") suspend fun delete(id: String, authorityKey: String): Int
    @Query("DELETE FROM offline_map_packs") suspend fun deleteAll()
}
