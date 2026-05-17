package com.forestry.counter.data.repository

import com.forestry.counter.data.local.dao.StationEnvironnementaleDao
import com.forestry.counter.data.local.entity.StationEnvironnementaleEntity
import com.forestry.counter.domain.repository.StationEnvironnementaleRepository
import kotlinx.coroutines.flow.Flow

class StationEnvironnementaleRepositoryImpl(private val dao: StationEnvironnementaleDao) : StationEnvironnementaleRepository {
    override fun getByParcelle(parcelleId: String): Flow<StationEnvironnementaleEntity?> = dao.getByParcelle(parcelleId)
    override suspend fun getByParcelleOnce(parcelleId: String): StationEnvironnementaleEntity? = dao.getByParcelleOnce(parcelleId)
    override suspend fun insert(station: StationEnvironnementaleEntity) = dao.insert(station)
    override suspend fun update(station: StationEnvironnementaleEntity) = dao.update(station)
    override suspend fun updateDvf(parcelleId: String, prix: Double?, nb: Int?, fetchedAt: Long) = dao.updateDvf(parcelleId, prix, nb, fetchedAt)
    override suspend fun deleteByParcelle(parcelleId: String) = dao.deleteByParcelle(parcelleId)
}
