package com.forestry.counter.data.repository

import com.forestry.counter.data.local.dao.InventaireSessionDao
import com.forestry.counter.data.local.entity.InventaireSessionEntity
import com.forestry.counter.domain.repository.InventaireSessionRepository
import kotlinx.coroutines.flow.Flow

class InventaireSessionRepositoryImpl(private val dao: InventaireSessionDao) : InventaireSessionRepository {
    override fun getByParcelle(parcelleId: String): Flow<List<InventaireSessionEntity>> = dao.getByParcelle(parcelleId)
    override suspend fun getById(id: String): InventaireSessionEntity? = dao.getById(id)
    override suspend fun getLatestByType(parcelleId: String, type: String): InventaireSessionEntity? = dao.getLatestByType(parcelleId, type)
    override suspend fun insert(session: InventaireSessionEntity) = dao.insert(session)
    override suspend fun update(session: InventaireSessionEntity) = dao.update(session)
    override suspend fun deleteById(id: String) = dao.deleteById(id)
}
