package com.forestry.counter.data.repository

import com.forestry.counter.data.local.dao.RipisylveDao
import com.forestry.counter.data.local.entity.RipisylveEntity
import com.forestry.counter.domain.model.ripisylve.InadapteesMode
import com.forestry.counter.domain.model.ripisylve.LargeurMode
import com.forestry.counter.domain.model.ripisylve.RipisylveObservation
import com.forestry.counter.domain.repository.RipisylveRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RipisylveRepositoryImpl(private val dao: RipisylveDao) : RipisylveRepository {

    override fun getByParcelle(parcelleId: String): Flow<List<RipisylveObservation>> =
        dao.getByParcelle(parcelleId).map { list -> list.map { it.toDomain() } }

    override fun getById(id: String): Flow<RipisylveObservation?> =
        dao.getById(id).map { it?.toDomain() }

    override fun getAll(): Flow<List<RipisylveObservation>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun save(obs: RipisylveObservation) = dao.upsert(obs.toEntity())

    override suspend fun delete(obs: RipisylveObservation) = dao.delete(obs.toEntity())

    override suspend fun deleteById(id: String) = dao.deleteById(id)

    // ── Mapping ──

    private fun RipisylveEntity.toDomain() = RipisylveObservation(
        id = id,
        parcelleId = parcelleId,
        observerName = observerName,
        observationDate = observationDate,
        createdAt = createdAt,
        updatedAt = updatedAt,
        latitude = latitude,
        longitude = longitude,
        altitudeM = altitudeM,
        sectionLengthM = sectionLengthM,
        sectionNotes = sectionNotes,
        continuitePct = continuitePct,
        largeurMode = runCatching { LargeurMode.valueOf(largeurMode) }.getOrDefault(LargeurMode.UNE_RANGEE),
        strateHerbacee = strateHerbacee,
        strateArbustive = strateArbustive,
        strateArborescente = strateArborescente,
        nbEspecesObservees = nbEspecesObservees,
        especesObservees = especesObserveesCsv.split(",").filter { it.isNotBlank() },
        diamAutoFromDendro = diamAutoFromDendro,
        hasTresPetitBois = hasTresPetitBois,
        hasPetitBois = hasPetitBois,
        hasMoyenBois = hasMoyenBois,
        hasGrosBois = hasGrosBois,
        microhabitatCavites = microhabitatCavites,
        microhabitatFissures = microhabitatFissures,
        microhabitatDecollementEcorce = microhabitatDecollementEcorce,
        microhabitatChampignons = microhabitatChampignons,
        microhabitatBoisMort = microhabitatBoisMort,
        microhabitatTresGrosBois = microhabitatTresGrosBois,
        sanitairePct = sanitairePct,
        invasivesPct = invasivesPct,
        invasivesIdentifiees = invasivesCsv.split(",").filter { it.isNotBlank() },
        inadapteesMode = runCatching { InadapteesMode.valueOf(inadapteesMode) }.getOrDefault(InadapteesMode.ABSENCE),
        stabilitePct = stabilitePct,
        globalNotes = globalNotes,
        photoUris = photoUrisCsv.split(",").filter { it.isNotBlank() }
    )

    private fun RipisylveObservation.toEntity() = RipisylveEntity(
        id = id,
        parcelleId = parcelleId,
        observerName = observerName,
        observationDate = observationDate,
        createdAt = createdAt,
        updatedAt = updatedAt,
        latitude = latitude,
        longitude = longitude,
        altitudeM = altitudeM,
        sectionLengthM = sectionLengthM,
        sectionNotes = sectionNotes,
        continuitePct = continuitePct,
        largeurMode = largeurMode.name,
        strateHerbacee = strateHerbacee,
        strateArbustive = strateArbustive,
        strateArborescente = strateArborescente,
        nbEspecesObservees = nbEspecesObservees,
        especesObserveesCsv = especesObservees.joinToString(","),
        diamAutoFromDendro = diamAutoFromDendro,
        hasTresPetitBois = hasTresPetitBois,
        hasPetitBois = hasPetitBois,
        hasMoyenBois = hasMoyenBois,
        hasGrosBois = hasGrosBois,
        microhabitatCavites = microhabitatCavites,
        microhabitatFissures = microhabitatFissures,
        microhabitatDecollementEcorce = microhabitatDecollementEcorce,
        microhabitatChampignons = microhabitatChampignons,
        microhabitatBoisMort = microhabitatBoisMort,
        microhabitatTresGrosBois = microhabitatTresGrosBois,
        sanitairePct = sanitairePct,
        invasivesPct = invasivesPct,
        invasivesCsv = invasivesIdentifiees.joinToString(","),
        inadapteesMode = inadapteesMode.name,
        stabilitePct = stabilitePct,
        globalNotes = globalNotes,
        photoUrisCsv = photoUris.joinToString(",")
    )
}
