package com.forestry.counter.domain.repository

import com.forestry.counter.domain.model.ripisylve.RipisylveObservation
import kotlinx.coroutines.flow.Flow

interface RipisylveRepository {
    fun getByParcelle(parcelleId: String): Flow<List<RipisylveObservation>>
    fun getById(id: String): Flow<RipisylveObservation?>
    fun getAll(): Flow<List<RipisylveObservation>>
    suspend fun save(obs: RipisylveObservation)
    suspend fun delete(obs: RipisylveObservation)
    suspend fun deleteById(id: String)
}
