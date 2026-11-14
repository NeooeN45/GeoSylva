package com.forestry.counter.data.packs.offline

import com.forestry.counter.domain.usecase.pack.PackArtifactIdentity
import com.forestry.counter.domain.usecase.pack.PackManager

/** Adapter to the existing signed-artifact service, also injectable in isolated Room tests. */
interface OfflinePackDatasets {
    suspend fun available(dataset: OfflineDataset): Boolean
    suspend fun pendingTransferBytes(required: List<OfflineDataset>): Long
    suspend fun install(dataset: OfflineDataset)
}

class SignedOfflinePackDatasets(private val manager: PackManager) : OfflinePackDatasets {
    override suspend fun available(dataset: OfflineDataset): Boolean = manager.verifyActiveArtifact(
        PackArtifactIdentity(dataset.id, dataset.version, dataset.sha256))
    override suspend fun install(dataset: OfflineDataset) = manager.installPack(dataset.id,
        PackArtifactIdentity(dataset.id, dataset.version, dataset.sha256))
    override suspend fun pendingTransferBytes(required: List<OfflineDataset>): Long {
        var total = 0L
        for (requested in required) {
            if (available(requested)) continue
            val current = manager.packState.value.allPacks.singleOrNull {
                it.id == requested.id && it.version == requested.version && it.checksum?.lowercase() == requested.sha256.lowercase()
            } ?: error("DATASET_CHANGED")
            require(current.signedManifest != null && current.contentKind in setOf("administrative_boundaries", "forestry_layers"))
            total = Math.addExact(total, current.archiveBytes?.takeIf { it > 0 } ?: error("UNKNOWN_SIZE"))
        }
        return total
    }
}
