package com.forestry.counter.data.repository

import com.forestry.counter.data.local.dao.ForetDao
import com.forestry.counter.data.local.dao.ParcelSyncDao
import com.forestry.counter.data.local.dao.ParcelSyncOperationDao
import com.forestry.counter.data.mapper.toDomain
import com.forestry.counter.data.mapper.toEntity
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.repository.ForetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ForetRepositoryImpl(
    private val dao: ForetDao,
    private val metadataService: MetadataService,
    private val syncDao: ParcelSyncDao,
    private val operationDao: ParcelSyncOperationDao,
    private val mutationGuardFactory: ((String?) -> (() -> Boolean))? = null,
    private val readAccess: BusinessReadAccess? = null,
) : ForetRepository {
    override fun getAll(): Flow<List<Foret>> {
        val source = dao.getAll().map { list -> list.map { it.toDomain() } }
        val policy = readAccess ?: return source
        return policy.observe(source, emptyList()) { values, access ->
            values.filter { policy.canReadForest(it.foretId, access) }
        }
    }
    override suspend fun getById(id: String): Foret? {
        val policy = readAccess ?: return dao.getById(id)?.toDomain()
        val access = policy.capture()
        if (!policy.canReadForest(id, access)) return null
        val forest = dao.getById(id)?.toDomain()
        return if (policy.revalidate(access)) forest else null
    }
    override suspend fun insert(foret: Foret) = mutate(foret.foretId) { actor ->
        val defaults = metadataService.enrichForCreate(foret)
        dao.insert(defaults.copy(auteur = actor ?: "anonymous", source = foret.source ?: defaults.source).toEntity())
    }
    override suspend fun update(foret: Foret) = mutate(foret.foretId) { actor ->
        check(dao.getById(foret.foretId) != null) { "FOREST_NOT_AVAILABLE" }
        dao.update(metadataService.enrichForUpdate(foret, baseVersion = foret.version).copy(auteur = actor ?: "anonymous").toEntity())
    }
    override suspend fun deleteById(id: String) = mutate(id) {
        check(dao.getById(id) != null) { "FOREST_NOT_AVAILABLE" }
        check(dao.countActiveSessionsForForest(id) == 0) { "MARTELAGE_ACTIVE_FOREST_DELETE_REFUSED" }
        dao.deleteById(id, System.currentTimeMillis())
    }

    private suspend fun mutate(id: String, write: suspend (String?) -> Unit) {
        val actor = metadataService.currentAccountId()
        val current = mutationGuardFactory?.invoke(actor) ?: { metadataService.currentAccountId() == actor }
        operationDao.runJournalTransaction {
            check(current()) { "MUTATION_SESSION_CHANGED" }
            val accounts = (syncDao.getHierarchyAccountsForForest(id) + syncDao.getLegacyAccountsForForest(id)).distinct()
            check(accounts.size <= 1 && (accounts.isEmpty() || actor != null && accounts.single() == actor)) {
                "FOREST_LINKED_TO_ANOTHER_ACCOUNT"
            }
            write(actor)
            check(current()) { "MUTATION_SESSION_CHANGED" }
        }
    }
}
