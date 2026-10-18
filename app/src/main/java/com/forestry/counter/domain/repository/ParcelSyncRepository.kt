package com.forestry.counter.domain.repository

import com.forestry.counter.domain.model.ParcelSyncProcessResult
import com.forestry.counter.domain.model.ParcelSyncPullResult
import com.forestry.counter.domain.model.ParcelSyncSummary
import com.forestry.counter.domain.model.ParcelSyncSelection
import kotlinx.coroutines.flow.Flow

interface ParcelSyncRepository {
    fun observeSummary(): Flow<ParcelSyncSummary>

    suspend fun loadSelection(): Result<ParcelSyncSelection>

    /**
     * Lie les nouveaux dossiers choisis avec leur snapshot initial dans la même transaction.
     * Une ancienne opération sans snapshot exige un aperçu puis une confirmation explicite :
     * publie alors la version locale capturée sous un nouvel identifiant et la base serveur observée.
     * Les conflits et les autres opérations déjà associées ne sont jamais réinitialisés.
     */
    suspend fun enqueueSelected(selection: ParcelSyncSelection, parcelIds: Set<String>): Result<Int>

    /** Compatibility wake-up only; business durability is written by the local mutation transaction. */
    suspend fun enqueueUpsert(parcelleId: String)

    /** Compatibility wake-up only; never constructs a request by rereading a committed business row. */
    suspend fun enqueueDelete(parcelleId: String)

    suspend fun processPending(): ParcelSyncProcessResult

    /**
     * Récupère les parcelles depuis le serveur GSIE et les fusionne en local
     * (GEOSYLVA P0-3, 2e moitié — pull et résolution).
     *
     * Une parcelle avec une modification locale non synchronisée n'est
     * jamais écrasée : le local gagne jusqu'à ce que sa modification soit
     * poussée avec succès (voir [ParcelSyncPullResult.skippedLocalDirty]).
     */
    suspend fun pull(): Result<ParcelSyncPullResult>
}
