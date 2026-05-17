package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.forestry.counter.data.local.entity.FertiliteEssenceSerEntity

@Dao
interface FertiliteEssenceSerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<FertiliteEssenceSerEntity>)

    @Query("SELECT * FROM fertilite_essence_ser WHERE essenceCode = :essenceCode AND codeSer = :codeSer ORDER BY classeStation ASC")
    suspend fun getByEssenceAndSer(essenceCode: String, codeSer: String): List<FertiliteEssenceSerEntity>

    @Query("SELECT * FROM fertilite_essence_ser WHERE essenceCode = :essenceCode ORDER BY codeSer ASC, classeStation ASC")
    suspend fun getByEssence(essenceCode: String): List<FertiliteEssenceSerEntity>

    @Query("SELECT * FROM fertilite_essence_ser WHERE codeSer = :codeSer ORDER BY essenceCode ASC, classeStation ASC")
    suspend fun getBySer(codeSer: String): List<FertiliteEssenceSerEntity>

    @Query("SELECT COUNT(*) FROM fertilite_essence_ser")
    suspend fun count(): Int

    @Query("DELETE FROM fertilite_essence_ser")
    suspend fun deleteAll()
}
