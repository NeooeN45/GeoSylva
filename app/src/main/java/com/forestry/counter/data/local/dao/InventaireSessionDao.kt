package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.forestry.counter.data.local.entity.InventaireSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventaireSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: InventaireSessionEntity)

    @Update
    suspend fun update(session: InventaireSessionEntity)

    @Delete
    suspend fun delete(session: InventaireSessionEntity)

    @Query("SELECT * FROM inventaire_sessions WHERE parcelleId = :parcelleId ORDER BY dateDebut DESC")
    fun getByParcelle(parcelleId: String): Flow<List<InventaireSessionEntity>>

    @Query("SELECT * FROM inventaire_sessions WHERE sessionId = :id")
    suspend fun getById(id: String): InventaireSessionEntity?

    @Query("SELECT * FROM inventaire_sessions WHERE parcelleId = :parcelleId AND typeSession = :type ORDER BY dateDebut DESC LIMIT 1")
    suspend fun getLatestByType(parcelleId: String, type: String): InventaireSessionEntity?

    @Query("DELETE FROM inventaire_sessions WHERE sessionId = :id")
    suspend fun deleteById(id: String)
}
