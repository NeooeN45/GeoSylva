package com.forestry.counter.domain.repository

import com.forestry.counter.data.local.entity.ForetEntity
import kotlinx.coroutines.flow.Flow

interface ForetRepository {
    fun getAll(): Flow<List<ForetEntity>>
    suspend fun getById(id: String): ForetEntity?
    suspend fun insert(foret: ForetEntity)
    suspend fun update(foret: ForetEntity)
    suspend fun deleteById(id: String)
}
