package com.forestry.counter.domain.model

import com.forestry.counter.domain.repository.IdentitySessionGuard

/** Choix initial lié à une génération précise de session, sans exposer de jeton. */
data class ParcelSyncSelection(
    val accountId: String,
    val sessionGuard: IdentitySessionGuard,
    val parcels: List<ParcelSyncCandidate>,
)

data class ParcelSyncCandidate(
    val parcelId: String,
    val name: String,
    val surfaceHa: Double?,
    val commune: String?,
    val isLinked: Boolean,
    val blockedByOtherAccount: Boolean,
    val recoveryRequired: Boolean = false,
    val recoveryPreview: ParcelSyncRecoveryPreview? = null,
    val recoveryUnavailable: Boolean = false,
    val isDeleted: Boolean = false,
)

/** Read-only preview. The opaque token binds the exact local/server versions held by the repository. */
data class ParcelSyncRecoveryPreview(
    val token: String,
    val localName: String,
    val localUpdatedAt: Long,
    val remoteName: String?,
    val remoteUpdatedAt: String?,
    val remoteVersion: Int?,
    val deletesParcel: Boolean,
)

data class ParcelSyncSummary(
    val pending: Int = 0,
    val syncing: Int = 0,
    val synced: Int = 0,
    val conflicts: Int = 0,
    val errors: Int = 0,
    val lastSuccessAt: Long? = null,
    val recoveryRequired: Int = 0,
) {
    val hasWork: Boolean
        get() = pending > 0 || syncing > 0
}

data class ParcelSyncProcessResult(
    val synchronized: Int,
    val conflicts: Int,
    val errors: Int,
    val shouldRetry: Boolean,
)

/**
 * Résultat d'un pull (serveur → local) — GEOSYLVA P0-3, 2e moitié.
 *
 * [skippedLocalDirty] compte les parcelles ignorées par le pull car elles ont
 * une modification locale non encore synchronisée (PENDING/SYNCING/CONFLICT/
 * ERROR dans la file d'attente) — le local gagne tant qu'il n'a pas été
 * poussé avec succès, jamais écrasé silencieusement par le serveur.
 */
data class ParcelSyncPullResult(
    val inserted: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
    val skippedLocalDirty: Int = 0,
    val pagesFetched: Int = 0,
    val skippedOtherAccount: Int = 0,
)
