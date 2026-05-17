package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.forestry.counter.data.local.entity.ProjectionClimatiqueSerEntity

@Dao
interface ProjectionClimatiqueSerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(projections: List<ProjectionClimatiqueSerEntity>)

    @Query("SELECT * FROM projections_climatiques_ser WHERE codeSer = :codeSer AND scenario = :scenario AND horizon = :horizon LIMIT 1")
    suspend fun get(codeSer: String, scenario: String, horizon: Int): ProjectionClimatiqueSerEntity?

    @Query("SELECT * FROM projections_climatiques_ser WHERE codeSer = :codeSer ORDER BY scenario ASC, horizon ASC")
    suspend fun getBySer(codeSer: String): List<ProjectionClimatiqueSerEntity>

    @Query("SELECT COUNT(*) FROM projections_climatiques_ser")
    suspend fun count(): Int

    @Query("DELETE FROM projections_climatiques_ser")
    suspend fun deleteAll()
}
