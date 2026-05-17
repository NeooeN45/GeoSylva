package com.forestry.counter.data.repository

import com.forestry.counter.data.local.dao.DiagnosticSylvicoleDao
import com.forestry.counter.data.local.entity.DiagnosticSylvicoleEntity
import com.forestry.counter.domain.repository.DiagnosticSylvicoleRepository
import kotlinx.coroutines.flow.Flow

class DiagnosticSylvicoleRepositoryImpl(private val dao: DiagnosticSylvicoleDao) : DiagnosticSylvicoleRepository {
    override fun getByParcelle(parcelleId: String): Flow<List<DiagnosticSylvicoleEntity>> = dao.getByParcelle(parcelleId)
    override suspend fun getLatestByParcelle(parcelleId: String): DiagnosticSylvicoleEntity? = dao.getLatestByParcelle(parcelleId)
    override suspend fun getById(id: String): DiagnosticSylvicoleEntity? = dao.getById(id)
    override suspend fun insert(diagnostic: DiagnosticSylvicoleEntity) = dao.insert(diagnostic)
    override suspend fun update(diagnostic: DiagnosticSylvicoleEntity) = dao.update(diagnostic)
    override suspend fun deleteById(id: String) = dao.deleteById(id)
}
