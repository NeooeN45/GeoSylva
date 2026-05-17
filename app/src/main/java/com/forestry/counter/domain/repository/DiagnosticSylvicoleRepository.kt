package com.forestry.counter.domain.repository

import com.forestry.counter.data.local.entity.DiagnosticSylvicoleEntity
import kotlinx.coroutines.flow.Flow

interface DiagnosticSylvicoleRepository {
    fun getByParcelle(parcelleId: String): Flow<List<DiagnosticSylvicoleEntity>>
    suspend fun getLatestByParcelle(parcelleId: String): DiagnosticSylvicoleEntity?
    suspend fun getById(id: String): DiagnosticSylvicoleEntity?
    suspend fun insert(diagnostic: DiagnosticSylvicoleEntity)
    suspend fun update(diagnostic: DiagnosticSylvicoleEntity)
    suspend fun deleteById(id: String)
}
