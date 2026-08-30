package com.forestry.counter.data.local.dao

import androidx.room.*
import com.forestry.counter.data.local.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY sortIndex ASC, name ASC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE groupId = :groupId")
    suspend fun getGroupById(groupId: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE groupId = :groupId")
    fun getGroupByIdFlow(groupId: String): Flow<GroupEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<GroupEntity>)

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Delete
    suspend fun deleteGroup(group: GroupEntity)

    @Query("DELETE FROM groups WHERE groupId = :groupId")
    suspend fun deleteGroupById(groupId: String)

    @Query("DELETE FROM groups")
    suspend fun deleteAllGroups()

    @Query("SELECT MAX(sortIndex) FROM groups")
    suspend fun getMaxSortIndex(): Int?

    @Query("SELECT * FROM groups WHERE foret_id = :foretId LIMIT 1")
    suspend fun getByForetId(foretId: String): GroupEntity?

    @Query("DELETE FROM groups WHERE foret_id = :foretId")
    suspend fun deleteByForetId(foretId: String)
}
