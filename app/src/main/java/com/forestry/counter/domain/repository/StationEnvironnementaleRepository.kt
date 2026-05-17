package com.forestry.counter.domain.repository

import com.forestry.counter.data.local.entity.StationEnvironnementaleEntity
import kotlinx.coroutines.flow.Flow

interface StationEnvironnementaleRepository {
    fun getByParcelle(parcelleId: String): Flow<StationEnvironnementaleEntity?>
    suspend fun getByParcelleOnce(parcelleId: String): StationEnvironnementaleEntity?
    suspend fun insert(station: StationEnvironnementaleEntity)
    suspend fun update(station: StationEnvironnementaleEntity)
    suspend fun updateDvf(parcelleId: String, prix: Double?, nb: Int?, fetchedAt: Long)
    suspend fun deleteByParcelle(parcelleId: String)
}
