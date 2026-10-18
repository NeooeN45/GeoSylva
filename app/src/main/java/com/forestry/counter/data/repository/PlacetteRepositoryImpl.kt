package com.forestry.counter.data.repository

import com.forestry.counter.data.local.dao.PlacetteDao
import com.forestry.counter.data.local.entity.ParcelleEntity
import com.forestry.counter.data.mapper.toPlacette
import com.forestry.counter.data.mapper.toPlacetteEntity
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.location.ParcelCoordinate
import com.forestry.counter.domain.location.ParcelGeometry
import com.forestry.counter.domain.location.PlotSamplingGeometry
import com.forestry.counter.domain.location.PlotSamplingPlan
import com.forestry.counter.domain.location.SampleFootprintShape
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.repository.PlacetteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.PI
import kotlin.math.abs

class PlacetteRepositoryImpl(
    private val placetteDao: PlacetteDao,
    private val metadataService: MetadataService,
    private val mutationGuardFactory: ((String?) -> (() -> Boolean))? = null,
    private val readAccess: BusinessReadAccess? = null,
) : PlacetteRepository {

    override fun getPlacettesByParcelle(parcelleId: String): Flow<List<Placette>> {
        val source = placetteDao.getPlacettesByParcelle(parcelleId).map { list -> list.map { it.toPlacette() } }
        val access = readAccess ?: return source
        return access.observe(source, emptyList()) { values, snapshot ->
            values.filter { access.canReadParcelle(it.parcelleId, snapshot) }
        }
    }

    override fun getPlacetteById(placetteId: String): Flow<Placette?> {
        val source = placetteDao.getPlacetteByIdFlow(placetteId).map { it?.toPlacette() }
        val access = readAccess ?: return source
        return access.observe(source, null) { value, snapshot ->
            value?.takeIf { access.canReadParcelle(it.parcelleId, snapshot) }
        }
    }

    override suspend fun insertPlacette(placette: Placette) {
        require(placette.deletedAt == null) { "PLOT_DELETED_INSERT_REFUSED" }
        val actor = metadataService.currentAccountId()
        withAuthorizedMutation {
            authorizeParent(placette.parcelleId, actor)
            val enriched = metadataService.enrichForCreate(placette)
            // Creation never replaces an existing plot and its dependent observations.
            placetteDao.insertNewPlacettes(listOf(enriched.toPlacetteEntity()))
            checkCurrentAccount(actor)
        }
    }

    override suspend fun insertPlacettesForParcelle(placettes: List<Placette>) {
        require(placettes.size in 1..500) { "PLOT_BATCH_SIZE_INVALID" }
        require(placettes.map { it.id }.distinct().size == placettes.size) { "PLOT_IDS_DUPLICATED" }
        val parcelleId = placettes.first().parcelleId
        require(placettes.all { it.parcelleId == parcelleId && it.deletedAt == null }) {
            "PLOT_PARENT_MISMATCH"
        }
        val actor = metadataService.currentAccountId()
        // Capture metadata exactly once. Every member keeps the same author/origin.
        val first = metadataService.enrichForCreate(placettes.first())
        val enriched = placettes.map {
            it.withMetadata(requireNotNull(first.auteur), requireNotNull(first.source), first.version)
        }
        withAuthorizedMutation {
            val parent = authorizeParent(parcelleId, actor)
            require(parent.srid == 4326) { "PLOT_BOUNDARY_UNAVAILABLE" }
            val geometry = ParcelGeometry.fromWkt(
                parent.geometrieIgnWkt ?: parent.shape ?: error("PLOT_BOUNDARY_UNAVAILABLE")
            )
            enriched.forEach { plot ->
                require(plot.surfaceM2?.let { it.isFinite() && it > 0.0 } == true) {
                    "PLOT_AREA_INVALID"
                }
                when (plot.type) {
                    "ENTIRE_PARCEL" -> {
                        require(enriched.size == 1 && plot.rayonM == null && plot.centerWkt == null) {
                            "PLOT_WHOLE_SCOPE_INVALID"
                        }
                        require(plot.samplingPlanJson == null && plot.footprintGeoJson == null) { "PLOT_WHOLE_PLAN_INVALID" }
                        val area = parent.surfaceHa?.times(10_000.0)
                        require(area != null && area.isFinite() && area > 0.0 &&
                            abs(requireNotNull(plot.surfaceM2) - area) <= maxOf(1e-6, area * 1e-9)) {
                            "PLOT_PARENT_AREA_CHANGED"
                        }
                    }
                    "CIRC", "HEX" -> {
                        if (plot.type == "CIRC") {
                            val radius = plot.rayonM
                            require(radius != null && radius.isFinite() && radius > 0.0) { "PLOT_RADIUS_INVALID" }
                            val expectedArea = PI * radius * radius
                            require(expectedArea.isFinite() &&
                                abs(requireNotNull(plot.surfaceM2) - expectedArea) <= maxOf(1e-6, expectedArea * 1e-9)) { "PLOT_AREA_INVALID" }
                        } else {
                            require(plot.rayonM == null) { "PLOT_HEX_RADIUS_INVALID" }
                        }
                        val match = POINT_PATTERN.matchEntire(plot.centerWkt.orEmpty())
                        val point = match?.let {
                            val longitude = it.groupValues[1].toDoubleOrNull()
                            val latitude = it.groupValues[2].toDoubleOrNull()
                            if (longitude == null || latitude == null) null
                            else ParcelCoordinate(longitude, latitude)
                        }
                        require(point != null && geometry.contains(point)) { "PLOT_POINT_OUTSIDE_PARCEL" }
                        validateSamplingPlan(plot, requireNotNull(point), geometry)
                    }
                    else -> error("PLOT_SCOPE_INVALID")
                }
            }
            placetteDao.insertNewPlacettes(enriched.map { it.toPlacetteEntity() })
            checkCurrentAccount(actor)
        }
    }

    private fun validateSamplingPlan(plot: Placette, center: ParcelCoordinate, parcel: ParcelGeometry) {
        val encodedPlan = plot.samplingPlanJson
        val encodedFootprint = plot.footprintGeoJson
        // Existing circular plots remain valid without retroactively inventing a plan.
        if (encodedPlan == null && encodedFootprint == null) {
            require(plot.type == "CIRC") { "PLOT_HEX_PLAN_REQUIRED" }
            return
        }
        require(encodedPlan != null && encodedFootprint != null) { "PLOT_SAMPLING_FIELDS_INCOMPLETE" }
        val plan = PlotSamplingPlan.fromJson(encodedPlan)
        val expectedShape = if (plot.type == "CIRC") SampleFootprintShape.CIRCLE else SampleFootprintShape.HEXAGON
        require(plan.footprintShape == expectedShape.name &&
            abs(plan.footprintAreaM2 - requireNotNull(plot.surfaceM2)) <= maxOf(1e-6, plan.footprintAreaM2 * 1e-9)) { "PLOT_PLAN_AREA_OR_SHAPE_INVALID" }
        val expected = PlotSamplingGeometry.footprints(parcel, listOf(center), expectedShape, plan.footprintAreaM2, plan.rotationDeg).single()
        val supplied = ParcelGeometry.fromGeoJson(encodedFootprint)
        require(supplied.toGeoJson() == expected.geometry.toGeoJson()) { "PLOT_FOOTPRINT_PLAN_MISMATCH" }
        require(plan.boundaryWarning == expected.crossesBoundary) { "PLOT_BOUNDARY_WARNING_INVALID" }
        require(plan.boundaryPolicy != "FULLY_INSIDE" || !expected.crossesBoundary) { "PLOT_FOOTPRINT_OUTSIDE_PARCEL" }
    }

    override suspend fun updatePlacette(placette: Placette) {
        val actor = metadataService.currentAccountId()
        withAuthorizedMutation {
            val current = checkNotNull(placetteDao.getPlacetteById(placette.id)) { "PLOT_UNAVAILABLE" }
            require(current.parcelleOwnerId == placette.parcelleId) { "PLOT_PARENT_CHANGE_REFUSED" }
            authorizeParent(current.parcelleOwnerId, actor)
            val enriched = metadataService.enrichForUpdate(placette, baseVersion = current.version)
            placetteDao.updatePlacette(enriched.toPlacetteEntity().copy(
                uuid = current.uuid, sessionId = current.sessionId, typeReleve = current.typeReleve,
                referenceGpsWkt = current.referenceGpsWkt, azimutRef = current.azimutRef,
                createdAt = current.createdAt,
            ))
            checkCurrentAccount(actor)
        }
    }

    override suspend fun deletePlacette(placetteId: String) {
        val actor = metadataService.currentAccountId()
        withAuthorizedMutation {
            val current = placetteDao.getPlacetteById(placetteId) ?: return@withAuthorizedMutation
            authorizeParent(current.parcelleOwnerId, actor)
            check(placetteDao.countActiveSessionsForPlot(current.parcelleOwnerId, placetteId) == 0) {
                "MARTELAGE_ACTIVE_PLOT_DELETE_REFUSED"
            }
            placetteDao.deletePlacetteById(placetteId, System.currentTimeMillis())
            checkCurrentAccount(actor)
        }
    }

    override suspend fun deletePlacettesByParcelle(parcelleId: String) {
        val actor = metadataService.currentAccountId()
        withAuthorizedMutation {
            authorizeParent(parcelleId, actor)
            check(placetteDao.countActiveSessionsForParcel(parcelleId) == 0) { "MARTELAGE_ACTIVE_PLOT_DELETE_REFUSED" }
            placetteDao.deletePlacettesByParcelle(parcelleId, System.currentTimeMillis())
            checkCurrentAccount(actor)
        }
    }

    private suspend fun authorizeParent(parcelleId: String, actor: String?): ParcelleEntity {
        checkCurrentAccount(actor)
        val parent = checkNotNull(placetteDao.getLiveParentForCreation(parcelleId)) { "PLOT_PARENT_UNAVAILABLE" }
        check(placetteDao.getLinkedAccountsForCreation(parcelleId).all { actor != null && it == actor }) {
            "PLOT_PARENT_LINKED_TO_ANOTHER_ACCOUNT"
        }
        checkCurrentAccount(actor)
        return parent
    }

    private fun checkCurrentAccount(actor: String?) {
        check(metadataService.currentAccountId() == actor) { "PLOT_ACCOUNT_CHANGED" }
    }

    private suspend fun withAuthorizedMutation(block: suspend () -> Unit) {
        val account = metadataService.currentAccountId()
        val guard = mutationGuardFactory?.invoke(account) ?: { metadataService.currentAccountId() == account }
        placetteDao.withPlacetteCreationTransaction {
            check(guard() && metadataService.currentAccountId() == account) { "PLOT_ACCOUNT_CHANGED" }
            block()
            check(guard() && metadataService.currentAccountId() == account) { "PLOT_ACCOUNT_CHANGED" }
        }
    }

    private companion object {
        val POINT_PATTERN = Regex("POINT\\s*\\(\\s*([^\\s]+)\\s+([^\\s]+)\\s*\\)")
    }
}
