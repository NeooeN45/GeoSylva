package com.forestry.counter.domain.repository

import com.forestry.counter.data.local.entity.ValeurFonciereEntity
import kotlinx.coroutines.flow.Flow

interface ValeurFonciereRepository {
    fun getByParcelle(parcelleId: String): Flow<ValeurFonciereEntity?>
    suspend fun getByParcelleOnce(parcelleId: String): ValeurFonciereEntity?
    suspend fun sumPatrimoineTotal(): Double?
    suspend fun sumCarboneTotal(): Double?
    suspend fun insert(valeur: ValeurFonciereEntity)
    suspend fun update(valeur: ValeurFonciereEntity)
    suspend fun deleteByParcelle(parcelleId: String)
}
