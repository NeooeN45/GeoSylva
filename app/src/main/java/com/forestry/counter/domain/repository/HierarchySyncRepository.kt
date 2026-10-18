package com.forestry.counter.domain.repository

import com.forestry.counter.domain.model.HierarchyRecoveryPreview
import com.forestry.counter.domain.model.HierarchyRemoteDossier
import com.forestry.counter.domain.model.HierarchyRemoteMetadata
import com.forestry.counter.domain.model.HierarchySyncProcessResult
import com.forestry.counter.domain.model.HierarchySyncSelection
import com.forestry.counter.domain.model.HierarchySyncSummary
import kotlinx.coroutines.flow.Flow

interface HierarchySyncRepository {
    fun observeSummary(): Flow<HierarchySyncSummary>
    suspend fun loadSelection(): Result<HierarchySyncSelection>
    suspend fun activateSelected(selection: HierarchySyncSelection, forestIds: Set<String>): Result<Int>
    /** Catch up only forests explicitly activated for the current personal account. */
    suspend fun captureActivated(expected: IdentitySessionGuard? = null): Int
    suspend fun processPending(expected: IdentitySessionGuard? = null): HierarchySyncProcessResult
    suspend fun listRemote(): Result<List<HierarchyRemoteMetadata>>
    suspend fun getRemote(forestId: String): Result<HierarchyRemoteDossier?>
    suspend fun prepareRecovery(forestId: String): Result<HierarchyRecoveryPreview>
    /** User expressly republishes the captured local version; no merge or local overwrite occurs. */
    suspend fun confirmRepublishLocal(preview: HierarchyRecoveryPreview): Result<Unit>
}
