package com.forestry.counter.data.repository

import com.forestry.counter.data.local.dao.ParcelleDao
import com.forestry.counter.data.local.dao.PlacetteDao
import com.forestry.counter.data.local.dao.TigeDao
import com.forestry.counter.data.mapper.toParcelle
import com.forestry.counter.data.mapper.toParcelleEntity
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.data.sync.ParcelMutationJournal
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.repository.ParcelleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ParcelleRepositoryImpl(
    private val parcelleDao: ParcelleDao,
    private val metadataService: MetadataService,
    private val mutationJournal: ParcelMutationJournal,
    private val tigeDao: TigeDao,
    private val placetteDao: PlacetteDao,
    private val readAccess: BusinessReadAccess? = null,
) : ParcelleRepository {
    override fun getAllParcelles(): Flow<List<Parcelle>> =
        readable(parcelleDao.getAllParcelles().map { list -> list.map { it.toParcelle() } })

    override fun getParcellesByForest(forestId: String): Flow<List<Parcelle>> =
        readable(parcelleDao.getParcellesByForest(forestId).map { list -> list.map { it.toParcelle() } })

    override fun getParcelleById(parcelleId: String): Flow<Parcelle?> {
        val source = parcelleDao.getParcelleByIdFlow(parcelleId).map { it?.toParcelle() }
        val policy = readAccess ?: return source
        return policy.observe(source, null) { value, access ->
            value?.takeIf { policy.canReadParcelle(it.id, access) }
        }
    }

    private fun readable(source: Flow<List<Parcelle>>): Flow<List<Parcelle>> {
        val policy = readAccess ?: return source
        return policy.observe(source, emptyList()) { values, access ->
            values.filter { policy.canReadParcelle(it.id, access) }
        }
    }

    override suspend fun insertParcelle(parcelle: Parcelle) {
        mutationJournal.mutate(parcelle.id, ParcelMutationJournal.UPSERT, parcelle.foretId) { actor ->
            val defaults = metadataService.enrichForCreate(parcelle)
            val enriched = defaults.copy(source = parcelle.source ?: defaults.source, auteur = actor ?: "anonymous")
            parcelleDao.insertParcelle(enriched.toParcelleEntity())
        }
    }

    override suspend fun updateParcelle(parcelle: Parcelle) {
        mutationJournal.mutate(parcelle.id, ParcelMutationJournal.UPSERT, parcelle.foretId) { actor ->
            val enriched = metadataService.enrichForUpdate(parcelle, baseVersion = parcelle.version).copy(auteur = actor ?: "anonymous")
            check(parcelleDao.getParcelleById(parcelle.id) != null) { "PARCEL_NOT_AVAILABLE" }
            parcelleDao.updateParcelle(enriched.toParcelleEntity())
        }
    }

    override suspend fun deleteParcelle(parcelleId: String) {
        val timestamp = System.currentTimeMillis()
        mutationJournal.mutate(parcelleId, ParcelMutationJournal.DELETE) {
            check(parcelleDao.getParcelleById(parcelleId) != null) { "PARCEL_NOT_AVAILABLE" }
            check(placetteDao.countActiveSessionsForParcel(parcelleId) == 0) { "MARTELAGE_ACTIVE_PARCEL_DELETE_REFUSED" }
            // Children, tombstone and immutable delete operation commit or roll back together.
            tigeDao.deleteTigesByParcelle(parcelleId, timestamp)
            placetteDao.deletePlacettesByParcelle(parcelleId, timestamp)
            parcelleDao.deleteParcelleById(parcelleId, timestamp)
        }
    }

    override suspend fun deleteAllParcelles() {
        mutationJournal.deleteAll { parcels, timestamp ->
            // Preflight every parent before changing any child or journal entry, including null-scoped legacy campaigns.
            check(parcels.all { placetteDao.countActiveSessionsForParcel(it.parcelleId) == 0 }) {
                "MARTELAGE_ACTIVE_PARCEL_DELETE_REFUSED"
            }
            parcels.forEach { parcel ->
                tigeDao.deleteTigesByParcelle(parcel.parcelleId, timestamp)
                placetteDao.deletePlacettesByParcelle(parcel.parcelleId, timestamp)
            }
            parcelleDao.deleteAllParcelles(timestamp)
        }
    }
}
