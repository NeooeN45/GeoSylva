package com.forestry.counter.data.repository

import com.forestry.counter.data.local.dao.ForetDao
import com.forestry.counter.data.local.entity.ForetEntity
import com.forestry.counter.domain.repository.ForetRepository
import kotlinx.coroutines.flow.Flow

class ForetRepositoryImpl(private val dao: ForetDao) : ForetRepository {
    override fun getAll(): Flow<List<ForetEntity>> = dao.getAll()
    override suspend fun getById(id: String): ForetEntity? = dao.getById(id)
    override suspend fun insert(foret: ForetEntity) = dao.insert(foret)
    override suspend fun update(foret: ForetEntity) = dao.update(foret)
    override suspend fun deleteById(id: String) = dao.deleteById(id)
}
