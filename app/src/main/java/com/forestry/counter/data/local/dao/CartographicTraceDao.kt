package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.forestry.counter.data.local.entity.CartographicTraceEntity
import com.forestry.counter.data.local.entity.CartographicTraceJournalEntity

@Dao
interface CartographicTraceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: CartographicTraceEntity)
    @Update
    suspend fun update(row: CartographicTraceEntity): Int
    @Query("SELECT * FROM cartographic_traces WHERE id = :id")
    suspend fun byId(id: String): CartographicTraceEntity?
    @Query("SELECT * FROM cartographic_traces WHERE authorityKey = :authorityKey ORDER BY updatedAt DESC, id")
    suspend fun inScope(authorityKey: String): List<CartographicTraceEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun appendJournal(rows: List<CartographicTraceJournalEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updatePortion(row: CartographicTraceJournalEntity)
    @Query("SELECT * FROM cartographic_trace_journal WHERE traceId = :id ORDER BY kind, ordinal")
    suspend fun journal(id: String): List<CartographicTraceJournalEntity>
    @Query("DELETE FROM cartographic_trace_journal WHERE traceId = :id")
    suspend fun deleteJournal(id: String)
    @Query("DELETE FROM cartographic_trace_journal")
    suspend fun deleteAllJournal()
    @Query("DELETE FROM cartographic_traces")
    suspend fun deleteRecords()
    @Transaction
    suspend fun deleteAll() { deleteAllJournal(); deleteRecords() }
}
