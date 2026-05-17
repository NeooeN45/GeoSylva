package com.forestry.counter.domain.repository

import com.forestry.counter.domain.model.ripisylve.RipisylveObservation
import com.forestry.counter.domain.model.ripisylve.RipisylveScore
import kotlinx.coroutines.flow.Flow

interface RipisylveRepository {
    fun getByParcelle(parcelleId: String): Flow<List<RipisylveObservation>>
    suspend fun save(observation: RipisylveObservation)
    suspend fun delete(id: String)
    suspend fun getById(id: String): RipisylveObservation?
}
