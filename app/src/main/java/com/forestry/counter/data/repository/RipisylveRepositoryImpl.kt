package com.forestry.counter.data.repository

import com.forestry.counter.data.local.dao.RipisylveDao
import com.forestry.counter.data.local.entity.RipisylveEntity
import com.forestry.counter.domain.model.ripisylve.RipisylveObservation
import com.forestry.counter.domain.repository.RipisylveRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RipisylveRepositoryImpl(
    private val dao: RipisylveDao
) : RipisylveRepository {

    override fun getByParcelle(parcelleId: String): Flow<List<RipisylveObservation>> =
        dao.getByParcelle(parcelleId).map { list -> list.map { it.toDomain() } }

    override suspend fun save(observation: RipisylveObservation) =
        dao.upsert(RipisylveEntity.fromDomain(observation))

    override suspend fun delete(id: String) =
        dao.deleteById(id)

    override suspend fun getById(id: String): RipisylveObservation? =
        dao.getById(id)?.toDomain()
}
