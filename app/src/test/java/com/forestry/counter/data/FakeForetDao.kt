package com.forestry.counter.data

import com.forestry.counter.data.local.dao.ForetDao
import com.forestry.counter.data.local.entity.ForetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeForetDao : ForetDao {
    val data = mutableListOf<ForetEntity>()

    override suspend fun insertIfAbsent(foret: ForetEntity): Long {
        if (data.any { it.foretId == foret.foretId || foret.uuid != null && it.uuid == foret.uuid }) return -1L
        data.add(foret)
        return data.size.toLong()
    }
    override suspend fun update(foret: ForetEntity) {
        val idx = data.indexOfFirst { it.foretId == foret.foretId }
        if (idx >= 0) data[idx] = foret
    }
    override suspend fun countActiveSessionsForForest(foretId: String): Int = 0
    override suspend fun delete(id: String, timestamp: Long) {
        val idx = data.indexOfFirst { it.foretId == id }
        if (idx >= 0) data[idx] = data[idx].copy(deletedAt = timestamp)
    }
    override suspend fun deleteById(id: String, timestamp: Long) = delete(id, timestamp)
    override suspend fun deleteAll(timestamp: Long) {
        data.indices.forEach { i -> data[i] = data[i].copy(deletedAt = timestamp) }
    }
    override suspend fun hardDeleteAll() { data.clear() }
    override fun getAll(): Flow<List<ForetEntity>> = flowOf(data.filter { it.deletedAt == null })
    override suspend fun getAllNow(): List<ForetEntity> = data.filter { it.deletedAt == null }
    override suspend fun getById(id: String): ForetEntity? = data.find { it.foretId == id && it.deletedAt == null }
    override suspend fun getByIdAny(id: String): ForetEntity? = data.find { it.foretId == id }
    override fun searchByProprietaire(query: String): Flow<List<ForetEntity>> =
        flowOf(data.filter { it.proprietaireNom.contains(query) && it.deletedAt == null })
    override suspend fun getWithoutUuid(): List<ForetEntity> = data.filter { it.uuid == null && it.deletedAt == null }
    override suspend fun setUuid(id: String, uuid: String) {
        val idx = data.indexOfFirst { it.foretId == id }
        if (idx >= 0) data[idx] = data[idx].copy(uuid = uuid)
    }
}
