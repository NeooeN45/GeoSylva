package com.forestry.counter.data.repository

import com.forestry.counter.data.local.dao.ValeurFonciereDao
import com.forestry.counter.data.local.entity.ValeurFonciereEntity
import com.forestry.counter.domain.repository.ValeurFonciereRepository
import kotlinx.coroutines.flow.Flow

class ValeurFonciereRepositoryImpl(private val dao: ValeurFonciereDao) : ValeurFonciereRepository {
    override fun getByParcelle(parcelleId: String): Flow<ValeurFonciereEntity?> = dao.getByParcelle(parcelleId)
    override suspend fun getByParcelleOnce(parcelleId: String): ValeurFonciereEntity? = dao.getByParcelleOnce(parcelleId)
    override suspend fun sumPatrimoineTotal(): Double? = dao.sumPatrimoineTotal()
    override suspend fun sumCarboneTotal(): Double? = dao.sumCarboneTotal()
    override suspend fun insert(valeur: ValeurFonciereEntity) = dao.insert(valeur)
    override suspend fun update(valeur: ValeurFonciereEntity) = dao.update(valeur)
    override suspend fun deleteByParcelle(parcelleId: String) = dao.deleteByParcelle(parcelleId)
}
