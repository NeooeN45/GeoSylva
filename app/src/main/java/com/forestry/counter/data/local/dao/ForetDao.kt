package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.forestry.counter.data.local.entity.ForetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ForetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(foret: ForetEntity)

    @Update
    suspend fun update(foret: ForetEntity)

    @Delete
    suspend fun delete(foret: ForetEntity)

    @Query("SELECT * FROM forets ORDER BY nom ASC")
    fun getAll(): Flow<List<ForetEntity>>

    @Query("SELECT * FROM forets WHERE foretId = :id")
    suspend fun getById(id: String): ForetEntity?

    @Query("SELECT * FROM forets WHERE proprietaireNom LIKE '%' || :query || '%' ORDER BY nom ASC")
    fun searchByProprietaire(query: String): Flow<List<ForetEntity>>

    @Query("DELETE FROM forets WHERE foretId = :id")
    suspend fun deleteById(id: String)
}
