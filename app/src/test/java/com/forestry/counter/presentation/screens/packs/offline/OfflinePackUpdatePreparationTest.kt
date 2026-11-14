package com.forestry.counter.presentation.screens.packs.offline

import com.forestry.counter.data.local.entity.OfflineMapPackEntity
import com.forestry.counter.data.packs.offline.OfflineDataset
import com.forestry.counter.data.packs.offline.OfflinePackStatus
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class OfflinePackUpdatePreparationTest {
    private val required = OfflineDataset("TEST-required", "v1", "0".repeat(64), "TEST source", null)
    private val row = OfflineMapPackEntity(id = UUID.randomUUID().toString(), authorityKey = "TEST", ownerKey = "LOCAL", scopeKind = "FREE", scopeId = "",
        name = "TEST version", geometryJson = "TEST geometry", minZoom = 11, maxZoom = 12, pixelRatio = 1f,
        styleUri = "mapbox://styles/mapbox/satellite-v9", extraTilesetsJson = "[]", datasetsJson = Json.encodeToString(listOf(required)),
        territoriesJson = "[]", definitionHash = "TEST hash", requestSha256 = "TEST request", nativeRegionId = "TEST native",
        localVersion = "TEST local", createdAt = 1, status = OfflinePackStatus.READY.name)

    @Test fun `a missing required publication blocks an update instead of producing an empty selection`() {
        val result = prepareOfflinePackUpdate(row, listOf(required.copy(id = "TEST-unrelated")))
        assertTrue(result is OfflinePackUpdatePreparation.MissingRequired)
        assertEquals(setOf(required.id), (result as OfflinePackUpdatePreparation.MissingRequired).datasetIds)
    }
    @Test fun `all previous requirements stay checked when an available new version is reviewed`() {
        val latest = required.copy(version = "v2", sha256 = "1".repeat(64))
        val result = prepareOfflinePackUpdate(row, listOf(latest)) as OfflinePackUpdatePreparation.Ready
        assertEquals(listOf(latest), result.draft.datasetCandidates)
        assertEquals(setOf(required.id), result.draft.initiallySelectedDatasetIds)
        assertEquals(row.id, result.draft.replacesPackId)
        assertNotEquals(row.id, result.draft.id)
    }
    @Test fun `ambiguous identities and corrupt requirements are blocked`() {
        assertTrue(prepareOfflinePackUpdate(row, listOf(required, required.copy(version = "v2"))) is OfflinePackUpdatePreparation.MissingRequired)
        assertTrue(prepareOfflinePackUpdate(row.copy(datasetsJson = "[{}]"), emptyList()) is OfflinePackUpdatePreparation.MissingRequired)
    }
}
