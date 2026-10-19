package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.forestry.counter.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WoodObjectDao {
    @Query("SELECT * FROM wood_objects WHERE objectId = :id") suspend fun getObject(id: String): WoodObjectEntity?
    @Query("SELECT * FROM wood_objects WHERE depotId = :depotId ORDER BY createdAt, objectId")
    suspend fun objectsInDepot(depotId: String): List<WoodObjectEntity>
    @Query("SELECT revision FROM wood_objects WHERE depotId = :depotId ORDER BY objectId")
    fun changesInDepot(depotId: String): Flow<List<Int>>
    @Query("SELECT * FROM wood_observations WHERE objectId = :objectId ORDER BY recordedAt, observationId")
    suspend fun observations(objectId: String): List<WoodObservationEntity>
    @Query("SELECT * FROM wood_effective_observations WHERE objectId = :objectId")
    suspend fun effective(objectId: String): WoodEffectiveObservationEntity?
    @Query("SELECT * FROM wood_commands WHERE objectId = :id ORDER BY appliedAt, commandId") suspend fun history(id: String): List<WoodCommandEntity>
    @Query("SELECT * FROM wood_commands WHERE commandId = :id") suspend fun receipt(id: String): WoodCommandEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertObject(row: WoodObjectEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertObservation(row: WoodObservationEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCommand(row: WoodCommandEntity)
    @Upsert suspend fun select(row: WoodEffectiveObservationEntity)

    @Query("""UPDATE wood_objects SET headerJson = :json, headerSha256 = :sha,
        revision = revision + 1, updatedAt = :time, updatedBy = :actor, archivedAt = :archive
        WHERE objectId = :id AND revision = :expected AND revision < 2147483647 AND archivedAt IS NULL""")
    suspend fun advance(id: String, expected: Int, json: String, sha: String, time: Long, actor: String, archive: Long?): Int

    @Query("DELETE FROM wood_commands") suspend fun deleteCommands()
    @Query("DELETE FROM wood_effective_observations") suspend fun deleteSelections()
    @Query("DELETE FROM wood_observations") suspend fun deleteObservations()
    @Query("DELETE FROM wood_objects") suspend fun deleteObjects()
}
