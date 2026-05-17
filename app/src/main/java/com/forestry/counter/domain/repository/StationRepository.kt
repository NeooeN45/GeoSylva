package com.forestry.counter.domain.repository

import com.forestry.counter.domain.model.station.StationObservation
import kotlinx.coroutines.flow.Flow

interface StationRepository {
    fun getByParcelle(parcelleId: String): Flow<List<StationObservation>>
    fun getById(id: String): Flow<StationObservation?>
    fun getAll(): Flow<List<StationObservation>>
    suspend fun save(obs: StationObservation)
    suspend fun delete(obs: StationObservation)
    suspend fun deleteById(id: String)
}
