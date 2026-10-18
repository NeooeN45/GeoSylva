package com.forestry.counter.data

import com.forestry.counter.data.local.dao.ParcelleDao
import com.forestry.counter.data.local.entity.ParcelleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeParcelleDao : ParcelleDao {
    val data = mutableListOf<ParcelleEntity>()

    override fun getAllParcelles(): Flow<List<ParcelleEntity>> = flowOf(data.filter { it.deletedAt == null })
    override suspend fun getAllParcellesNow(): List<ParcelleEntity> = data.filter { it.deletedAt == null }
    override fun getParcellesByForest(forestId: String): Flow<List<ParcelleEntity>> =
        flowOf(data.filter { it.forestOwnerId == forestId && it.deletedAt == null })
    override fun getParcelleByIdFlow(id: String): Flow<ParcelleEntity?> =
        flowOf(data.find { it.parcelleId == id && it.deletedAt == null })
    override suspend fun getParcelleById(id: String): ParcelleEntity? =
        data.find { it.parcelleId == id && it.deletedAt == null }
    override suspend fun getParcelleByIdAny(id: String): ParcelleEntity? =
        data.find { it.parcelleId == id }
    override suspend fun insertParcelleIfAbsent(entity: ParcelleEntity): Long {
        if (data.any { it.parcelleId == entity.parcelleId }) return -1L
        data.add(entity)
        return data.size.toLong()
    }
    override suspend fun insertParcelles(entities: List<ParcelleEntity>) {
        entities.forEach { insertParcelle(it) }
    }
    override suspend fun updateParcelle(parcelle: ParcelleEntity) {
        val idx = data.indexOfFirst { it.parcelleId == parcelle.parcelleId }
        if (idx >= 0) data[idx] = parcelle
    }
    override suspend fun deleteParcelle(id: String, timestamp: Long) {
        val idx = data.indexOfFirst { it.parcelleId == id }
        if (idx >= 0) data[idx] = data[idx].copy(deletedAt = timestamp)
    }
    override suspend fun deleteParcelleById(id: String, timestamp: Long) {
        val idx = data.indexOfFirst { it.parcelleId == id }
        if (idx >= 0) data[idx] = data[idx].copy(deletedAt = timestamp)
    }
    override suspend fun deleteAllParcelles(timestamp: Long) {
        data.indices.forEach { i -> data[i] = data[i].copy(deletedAt = timestamp) }
    }
    override suspend fun hardDeleteAll() { data.clear() }
    override suspend fun getWithoutUuid(): List<ParcelleEntity> = data.filter { it.uuid == null && it.deletedAt == null }
    override suspend fun setUuid(id: String, uuid: String) {
        val idx = data.indexOfFirst { it.parcelleId == id }
        if (idx >= 0) data[idx] = data[idx].copy(uuid = uuid)
    }
}
