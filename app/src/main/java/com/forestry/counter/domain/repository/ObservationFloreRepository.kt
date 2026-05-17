package com.forestry.counter.domain.repository

import com.forestry.counter.data.local.entity.ObservationFloreEntity
import kotlinx.coroutines.flow.Flow

interface ObservationFloreRepository {
    fun getByParcelle(parcelleId: String): Flow<List<ObservationFloreEntity>>
    fun getByPlacette(placetteId: String): Flow<List<ObservationFloreEntity>>
    suspend fun getBySession(sessionId: String): List<ObservationFloreEntity>
    suspend fun countSpeciesByParcelle(parcelleId: String): Int
    suspend fun getProtectedSpeciesByParcelle(parcelleId: String): List<ObservationFloreEntity>
    suspend fun insert(observation: ObservationFloreEntity)
    suspend fun insertAll(observations: List<ObservationFloreEntity>)
    suspend fun update(observation: ObservationFloreEntity)
    suspend fun delete(observation: ObservationFloreEntity)
    suspend fun deleteByParcelle(parcelleId: String)
}
