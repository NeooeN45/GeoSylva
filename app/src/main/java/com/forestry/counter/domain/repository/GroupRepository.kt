package com.forestry.counter.domain.repository

import com.forestry.counter.domain.model.Group
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    fun getAllGroups(): Flow<List<Group>>
    fun getGroupById(groupId: String): Flow<Group?>
    suspend fun getByForetId(foretId: String): Group?
    suspend fun insertGroup(group: Group)
    suspend fun updateGroup(group: Group)
    suspend fun deleteGroup(groupId: String)
    suspend fun deleteByForetId(foretId: String)
    suspend fun deleteAllGroups()
    suspend fun duplicateGroup(groupId: String): String
}
