package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.forestry.counter.data.local.entity.FieldControlBasisEntity

/** Reconstruction minimale : captures immuables et lectures bornées au propriétaire fourni. */
@Dao
interface FieldControlBasisDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: FieldControlBasisEntity)

    @Query("SELECT * FROM field_control_bases WHERE ownerKey = :ownerKey AND controlEntryId = :controlEntryId")
    suspend fun byControlEntryId(ownerKey: String, controlEntryId: String): FieldControlBasisEntity?

    @Query("SELECT * FROM field_control_bases WHERE ownerKey = :ownerKey AND objectId = :objectId ORDER BY controlEntryId ASC")
    suspend fun forObject(ownerKey: String, objectId: String): List<FieldControlBasisEntity>

    /** Effacement global réservé à la purge explicite des données locales. */
    @Query("DELETE FROM field_control_bases")
    suspend fun deleteAllForPurge()
}
