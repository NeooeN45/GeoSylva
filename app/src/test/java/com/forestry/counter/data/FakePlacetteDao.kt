package com.forestry.counter.data

import com.forestry.counter.data.local.dao.PlacetteDao
import com.forestry.counter.data.local.entity.PlacetteEntity
import com.forestry.counter.data.local.entity.ParcelleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakePlacetteDao : PlacetteDao {
    val data = mutableListOf<PlacetteEntity>()

    override fun getPlacettesByParcelle(parcelleId: String): Flow<List<PlacetteEntity>> =
        flowOf(data.filter { it.parcelleOwnerId == parcelleId && it.deletedAt == null })
    override fun getPlacetteByIdFlow(id: String): Flow<PlacetteEntity?> =
        flowOf(data.find { it.placetteId == id && it.deletedAt == null })
    override suspend fun getPlacetteById(id: String): PlacetteEntity? =
        data.find { it.placetteId == id && it.deletedAt == null }
    override suspend fun getAllPlacettesNow(): List<PlacetteEntity> = data.filter { it.deletedAt == null }
    override suspend fun insertPlacette(entity: PlacetteEntity) { data.add(entity) }
    override suspend fun insertPlacettes(entities: List<PlacetteEntity>) { data.addAll(entities) }
    override suspend fun insertNewPlacettes(entities: List<PlacetteEntity>) {
        check(entities.map { it.placetteId }.distinct().size == entities.size)
        check(entities.none { incoming -> data.any { it.placetteId == incoming.placetteId } })
        data.addAll(entities)
    }
    override suspend fun getLiveParentForCreation(parcelleId: String): ParcelleEntity? = null
    override suspend fun getLinkedAccountsForCreation(parcelleId: String): List<String> = emptyList()
    override suspend fun countActiveSessionsForPlot(parcelleId: String, placetteId: String): Int = 0
    override suspend fun countActiveSessionsForParcel(parcelleId: String): Int = 0
    override suspend fun updatePlacette(entity: PlacetteEntity) {
        val idx = data.indexOfFirst { it.placetteId == entity.placetteId }
        if (idx >= 0) data[idx] = entity
    }
    override suspend fun deletePlacette(id: String, timestamp: Long) {
        val idx = data.indexOfFirst { it.placetteId == id }
        if (idx >= 0) data[idx] = data[idx].copy(deletedAt = timestamp)
    }
    override suspend fun deletePlacetteById(id: String, timestamp: Long) = deletePlacette(id, timestamp)
    override suspend fun deletePlacettesByParcelle(parcelleId: String, timestamp: Long) {
        data.indices.forEach { i ->
            if (data[i].parcelleOwnerId == parcelleId) data[i] = data[i].copy(deletedAt = timestamp)
        }
    }
    override suspend fun deleteAll(timestamp: Long) {
        data.indices.forEach { i -> data[i] = data[i].copy(deletedAt = timestamp) }
    }
    override suspend fun hardDeleteAll() { data.clear() }
    override suspend fun getWithoutUuid(): List<PlacetteEntity> = data.filter { it.uuid == null && it.deletedAt == null }
    override suspend fun setUuid(id: String, uuid: String) {
        val idx = data.indexOfFirst { it.placetteId == id }
        if (idx >= 0) data[idx] = data[idx].copy(uuid = uuid)
    }
}
