package com.forestry.counter.data.repository

import com.forestry.counter.data.local.dao.StationDao
import com.forestry.counter.data.local.entity.StationEntity
import com.forestry.counter.domain.model.station.StationObservation
import com.forestry.counter.domain.repository.StationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StationRepositoryImpl(
    private val dao: StationDao
) : StationRepository {
    override fun getByParcelle(parcelleId: String): Flow<List<StationObservation>> {
        return dao.getByParcelle(parcelleId).map { list -> list.map { it.toDomain() } }
    }

    override fun getById(id: String): Flow<StationObservation?> {
        return dao.getById(id).map { it?.toDomain() }
    }

    override fun getAll(): Flow<List<StationObservation>> {
        return dao.getAll().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun save(obs: StationObservation) {
        dao.insert(StationEntity.fromDomain(obs))
    }

    override suspend fun delete(obs: StationObservation) {
        dao.deleteById(obs.id)
    }

    override suspend fun deleteById(id: String) {
        dao.deleteById(id)
    }
}
