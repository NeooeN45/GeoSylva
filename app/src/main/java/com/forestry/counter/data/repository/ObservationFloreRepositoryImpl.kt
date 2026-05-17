package com.forestry.counter.data.repository

import com.forestry.counter.data.local.dao.ObservationFloreDao
import com.forestry.counter.data.local.entity.ObservationFloreEntity
import com.forestry.counter.domain.repository.ObservationFloreRepository
import kotlinx.coroutines.flow.Flow

class ObservationFloreRepositoryImpl(private val dao: ObservationFloreDao) : ObservationFloreRepository {
    override fun getByParcelle(parcelleId: String): Flow<List<ObservationFloreEntity>> = dao.getByParcelle(parcelleId)
    override fun getByPlacette(placetteId: String): Flow<List<ObservationFloreEntity>> = dao.getByPlacette(placetteId)
    override suspend fun getBySession(sessionId: String): List<ObservationFloreEntity> = dao.getBySession(sessionId)
    override suspend fun countSpeciesByParcelle(parcelleId: String): Int = dao.countSpeciesByParcelle(parcelleId)
    override suspend fun getProtectedSpeciesByParcelle(parcelleId: String): List<ObservationFloreEntity> = dao.getProtectedSpeciesByParcelle(parcelleId)
    override suspend fun insert(observation: ObservationFloreEntity) = dao.insert(observation)
    override suspend fun insertAll(observations: List<ObservationFloreEntity>) = dao.insertAll(observations)
    override suspend fun update(observation: ObservationFloreEntity) = dao.update(observation)
    override suspend fun delete(observation: ObservationFloreEntity) = dao.delete(observation)
    override suspend fun deleteByParcelle(parcelleId: String) = dao.deleteByParcelle(parcelleId)
}
