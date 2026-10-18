package com.forestry.counter.domain.model

import com.forestry.counter.domain.repository.IdentitySessionGuard

data class HierarchySyncCandidate(
    val forestId: String,
    val name: String,
    val parcelCount: Int,
    val plotCount: Int,
    val sessionCount: Int,
    val treeCount: Int,
    val isActivated: Boolean,
    val blockedByOtherAccount: Boolean,
    val containsLocalPhotos: Boolean,
    val state: String? = null,
    val lastErrorCode: String? = null,
)

data class HierarchySyncSelection(
    val accountId: String,
    val sessionGuard: IdentitySessionGuard,
    val forests: List<HierarchySyncCandidate>,
)

data class HierarchySyncSummary(
    val activated: Int = 0,
    val pending: Int = 0,
    val synced: Int = 0,
    val conflicts: Int = 0,
    val errors: Int = 0,
)

data class HierarchySyncProcessResult(
    val synchronized: Int = 0,
    val conflicts: Int = 0,
    val errors: Int = 0,
    val shouldRetry: Boolean = false,
)

/** Read-only remote copy. Its JSON never replaces local observations or closed revisions. */
data class HierarchyRemoteDossier(
    val forestId: String,
    val serverVersion: Int,
    val serverUpdatedAt: String,
    val dossierJson: String,
)

data class HierarchyRemoteMetadata(val forestId: String, val serverVersion: Int, val name: String?)

/** Explicit replacement preview binds one local capture, session and observed remote version. */
data class HierarchyRecoveryPreview(
    val token: String,
    val forestId: String,
    val localName: String,
    val remote: HierarchyRemoteDossier?,
    val containsLocalPhotos: Boolean,
)
