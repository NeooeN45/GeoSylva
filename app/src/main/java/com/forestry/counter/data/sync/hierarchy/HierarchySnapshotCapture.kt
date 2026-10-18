package com.forestry.counter.data.sync.hierarchy

import com.forestry.counter.data.local.dao.HierarchySyncDao
import com.forestry.counter.data.mapper.toDomain
import com.forestry.counter.data.mapper.toParcelle
import com.forestry.counter.data.mapper.toPlacette
import com.forestry.counter.data.mapper.toTige
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal data class HierarchyCapture(
    val dossier: HierarchyDossierDto,
    val json: String,
    val fingerprint: String,
    val containsLocalPhotos: Boolean,
)

/** Caller holds the same Room transaction throughout all five table reads. */
internal suspend fun captureHierarchy(dao: HierarchySyncDao, forestId: String): HierarchyCapture {
    require(UUID.fromString(forestId).toString() == forestId) { "FOREST_UUID_REQUIRED" }
    val counts = dao.counts(forestId)
    check(counts.parcels <= 1000 && counts.plots <= 5000 && counts.sessions <= 1000 && counts.trees <= 50000) {
        "DOSSIER_SEGMENTATION_REQUIRED"
    }
    check(dao.largeFieldBytes(forestId) <= HIERARCHY_MAX_BYTES) { "DOSSIER_TOO_LARGE" }
    val forest = checkNotNull(dao.forest(forestId)) { "FOREST_NOT_AVAILABLE" }
    val parcels = dao.parcels(forestId)
    val plots = dao.plots(forestId)
    val sessions = dao.sessions(forestId)
    val trees = dao.trees(forestId)
    check(parcels.size <= 1000 && plots.size <= 5000 && sessions.size <= 1000 && trees.size <= 50000) {
        "DOSSIER_SEGMENTATION_REQUIRED"
    }
    val geometries = listOf(forest.shape) + parcels.flatMap { listOf(it.shape, it.geometrieIgnWkt) } +
        plots.map { it.footprintGeoJson }
    geometries.forEach { geometry ->
        check(geometry == null || geometry.toByteArray(Charsets.UTF_8).size <= HIERARCHY_MAX_GEOMETRY_BYTES) {
            "SOURCE_GEOMETRY_TOO_LARGE"
        }
    }
    plots.forEach { plot ->
        check(plot.samplingPlanJson == null || plot.samplingPlanJson.toByteArray(Charsets.UTF_8).size <= HIERARCHY_MAX_SAMPLING_PLAN_BYTES) {
            "SAMPLING_PLAN_TOO_LARGE"
        }
    }
    sessions.forEach { session ->
        check(session.traceJson == null || session.traceJson.toByteArray(Charsets.UTF_8).size <= HIERARCHY_MAX_TRACE_BYTES) {
            "GPS_TRACE_TOO_LARGE"
        }
    }
    val containsPhotos = trees.any { !it.photoUri.isNullOrBlank() } || sessions.any {
        hasClosedPhotoReferences(it.closedSnapshotJson)
    }
    val dossier = HierarchyDossierDto(
        forest = forest.toDomain(), parcelles = parcels.map { it.toParcelle() },
        placettes = plots.map { it.toPlacette() }, sessions = sessions.map { it.toHierarchySession() },
        tiges = trees.map { it.toTige().copy(photoUri = null) },
    )
    val json = hierarchyJson.encodeToString(dossier)
    check(json.toByteArray(Charsets.UTF_8).size <= HIERARCHY_MAX_BYTES) { "DOSSIER_TOO_LARGE" }
    return HierarchyCapture(dossier, json, hierarchySha256(json), containsPhotos)
}

/** Inspection only; the original closed JSON string is never reconstructed from this parsed view. */
internal fun hasClosedPhotoReferences(snapshot: String?): Boolean = snapshot?.let {
    runCatching {
        hierarchyJson.parseToJsonElement(it).jsonObject["tiges"]?.jsonArray?.any { tree ->
            tree.jsonObject["photoUri"]?.let { uri -> uri != JsonNull && uri.toString() != "\"\"" } == true
        } == true
    }.getOrDefault(false)
} ?: false
