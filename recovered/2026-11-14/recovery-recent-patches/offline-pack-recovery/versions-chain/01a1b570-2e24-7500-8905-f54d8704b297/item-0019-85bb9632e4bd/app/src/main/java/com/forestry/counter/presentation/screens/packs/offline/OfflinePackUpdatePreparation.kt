package com.forestry.counter.presentation.screens.packs.offline

import com.forestry.counter.data.local.entity.OfflineMapPackEntity
import com.forestry.counter.data.packs.offline.OfflineDataset
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

sealed interface OfflinePackUpdatePreparation {
    data class Ready(val draft: OfflineViewPackDraft) : OfflinePackUpdatePreparation
    data class MissingRequired(val datasetIds: Set<String>) : OfflinePackUpdatePreparation
}

/** A missing publication cannot silently turn a previously required layer into an unchecked one. */
fun prepareOfflinePackUpdate(row: OfflineMapPackEntity, qualifiedCandidates: List<OfflineDataset>): OfflinePackUpdatePreparation {
    val required = Json.decodeFromString<List<OfflineDataset>>(row.datasetsJson)
    val byId = qualifiedCandidates.groupBy { it.id }
    val missing = required.filter { byId[it.id]?.size != 1 }.map { it.id }.toSet()
    if (missing.isNotEmpty()) return OfflinePackUpdatePreparation.MissingRequired(missing)
    val candidates = required.map { requireNotNull(byId[it.id]).single() }
    return OfflinePackUpdatePreparation.Ready(OfflineViewPackDraft(name = row.name, geometryJson = row.geometryJson,
        minZoom = row.minZoom, maxZoom = row.maxZoom, pixelRatio = row.pixelRatio, styleUri = row.styleUri,
        initiallyRelief = row.extraTilesetsJson.contains("mapbox.mapbox-terrain-dem-v1"),
        territories = Json.decodeFromString<List<String>>(row.territoriesJson).toSet(),
        datasetCandidates = candidates, initiallySelectedDatasetIds = required.map { it.id }.toSet(), replacesPackId = row.id))
}
