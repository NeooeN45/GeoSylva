package com.forestry.counter.domain.repository

import com.forestry.counter.data.local.entity.InventaireSessionEntity
import kotlinx.coroutines.flow.Flow

interface InventaireSessionRepository {
    fun getByParcelle(parcelleId: String): Flow<List<InventaireSessionEntity>>
    suspend fun getById(id: String): InventaireSessionEntity?
    suspend fun getLatestByType(parcelleId: String, type: String): InventaireSessionEntity?
    suspend fun insert(session: InventaireSessionEntity)
    suspend fun update(session: InventaireSessionEntity)
    suspend fun deleteById(id: String)
}
