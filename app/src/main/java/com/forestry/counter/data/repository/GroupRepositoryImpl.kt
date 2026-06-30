package com.forestry.counter.data.repository

import androidx.room.withTransaction
import com.forestry.counter.data.local.ForestryDatabase
import com.forestry.counter.data.local.dao.CounterDao
import com.forestry.counter.data.local.dao.FormulaDao
import com.forestry.counter.data.local.dao.GroupDao
import com.forestry.counter.data.local.dao.GroupVariableDao
import com.forestry.counter.data.local.dao.ParcelleDao
import com.forestry.counter.data.local.dao.PlacetteDao
import com.forestry.counter.data.local.dao.TigeDao
import com.forestry.counter.data.local.entity.GroupEntity
import com.forestry.counter.data.mapper.toCounter
import com.forestry.counter.data.mapper.toCounterEntity
import com.forestry.counter.data.mapper.toFormula
import com.forestry.counter.data.mapper.toFormulaEntity
import com.forestry.counter.data.mapper.toGroup
import com.forestry.counter.data.mapper.toGroupEntity
import com.forestry.counter.domain.model.Group
import com.forestry.counter.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class GroupRepositoryImpl(
    private val database: ForestryDatabase,
    private val groupDao: GroupDao,
    private val counterDao: CounterDao,
    private val formulaDao: FormulaDao,
    private val groupVariableDao: GroupVariableDao,
    private val parcelleDao: ParcelleDao? = null,
    private val placetteDao: PlacetteDao? = null,
    private val tigeDao: TigeDao? = null
) : GroupRepository {

    override fun getAllGroups(): Flow<List<Group>> {
        return groupDao.getAllGroups().transform { entities ->
            val result = mutableListOf<Group>()
            for (entity in entities) {
                val count = counterDao.getCounterCountByGroup(entity.groupId)
                val total = counterDao.getTotalValueByGroup(entity.groupId) ?: 0.0
                result.add(entity.toGroup(counterCount = count, totalValue = total))
            }
            emit(result)
        }
    }

    override fun getGroupById(groupId: String): Flow<Group?> {
        return groupDao.getGroupByIdFlow(groupId).transform { entity ->
            if (entity == null) {
                emit(null)
            } else {
                val count = counterDao.getCounterCountByGroup(entity.groupId)
                val total = counterDao.getTotalValueByGroup(entity.groupId) ?: 0.0
                emit(entity.toGroup(counterCount = count, totalValue = total))
            }
        }
    }

    override suspend fun insertGroup(group: Group) {
        groupDao.insertGroup(group.toGroupEntity())
    }

    override suspend fun updateGroup(group: Group) {
        groupDao.updateGroup(group.toGroupEntity())
    }

    override suspend fun deleteGroup(groupId: String) {
        groupDao.deleteGroupById(groupId)
    }

    override suspend fun deleteAllGroups() {
        android.util.Log.w("GroupRepo", "deleteAllGroups() called — purging ALL groups and cascaded data")
        database.withTransaction {
            groupDao.deleteAllGroups()
        }
    }

    override suspend fun duplicateGroup(groupId: String): String = database.withTransaction {
        val group = groupDao.getGroupById(groupId) ?: return@withTransaction ""
        val counters = counterDao.getCountersByGroup(groupId).first()
        val formulas = formulaDao.getFormulasByGroup(groupId).first()
        val variables = groupVariableDao.getVariablesByGroup(groupId).first()

        val newGroupId = UUID.randomUUID().toString()
        val newGroup = group.copy(
            groupId = newGroupId,
            name = "${group.name} (Copy)",
            sortIndex = (groupDao.getMaxSortIndex() ?: 0) + 1,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        groupDao.insertGroup(newGroup)

        // Duplicate counters
        val newCounters = counters.map { counter ->
            counter.copy(
                counterId = UUID.randomUUID().toString(),
                groupOwnerId = newGroupId,
                value = 0.0,
                createdAt = System.currentTimeMillis(),
                lastUpdatedAt = System.currentTimeMillis()
            )
        }
        if (newCounters.isNotEmpty()) counterDao.insertCounters(newCounters)

        // Duplicate formulas
        val newFormulas = formulas.map { formula ->
            formula.copy(
                formulaId = UUID.randomUUID().toString(),
                groupOwnerId = newGroupId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }
        if (newFormulas.isNotEmpty()) formulaDao.insertFormulas(newFormulas)

        // Duplicate variables
        val newVariables = variables.map { variable ->
            variable.copy(
                variableId = UUID.randomUUID().toString(),
                groupOwnerId = newGroupId
            )
        }
        if (newVariables.isNotEmpty()) groupVariableDao.insertVariables(newVariables)

        // Duplicate parcelles, placettes and tiges (forestry data)
        if (parcelleDao != null) {
            val parcelles = parcelleDao.getParcellesByForest(groupId).first()
            if (parcelles.isNotEmpty()) {
                // Map old parcelleId -> new parcelleId
                val parcelleIdMap = mutableMapOf<String, String>()
                val newParcelles = parcelles.map { p ->
                    val newId = UUID.randomUUID().toString()
                    parcelleIdMap[p.parcelleId] = newId
                    p.copy(
                        parcelleId = newId,
                        forestOwnerId = newGroupId,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                }
                parcelleDao.insertParcelles(newParcelles)

                // Duplicate placettes for each parcelle
                val placetteIdMap = mutableMapOf<String, String>()
                if (placetteDao != null) {
                    for ((oldParcelleId, newParcelleId) in parcelleIdMap) {
                        val placettes = placetteDao.getPlacettesByParcelle(oldParcelleId).first()
                        if (placettes.isNotEmpty()) {
                            val newPlacettes = placettes.map { pl ->
                                val newPlId = UUID.randomUUID().toString()
                                placetteIdMap[pl.placetteId] = newPlId
                                pl.copy(
                                    placetteId = newPlId,
                                    parcelleOwnerId = newParcelleId,
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis()
                                )
                            }
                            placetteDao.insertPlacettes(newPlacettes)
                        }
                    }
                }

                // Duplicate tiges for each parcelle
                if (tigeDao != null) {
                    for ((oldParcelleId, newParcelleId) in parcelleIdMap) {
                        val tiges = tigeDao.getTigesByParcelle(oldParcelleId).first()
                        if (tiges.isNotEmpty()) {
                            val newTiges = tiges.map { t ->
                                t.copy(
                                    tigeId = UUID.randomUUID().toString(),
                                    parcelleOwnerId = newParcelleId,
                                    placetteOwnerId = t.placetteOwnerId?.let { placetteIdMap[it] },
                                    timestamp = System.currentTimeMillis()
                                )
                            }
                            tigeDao.insertTiges(newTiges)
                        }
                    }
                }
            }
        }

        return@withTransaction newGroupId
    }
}
