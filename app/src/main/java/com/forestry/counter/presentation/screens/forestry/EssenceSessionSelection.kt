package com.forestry.counter.presentation.screens.forestry

import com.forestry.counter.domain.model.InventaireSession
import com.forestry.counter.domain.model.MartelageStates
import com.forestry.counter.domain.model.Tige

/** A current campaign is never combined with a closed revision or legacy observations. */
internal data class EssenceSessionSelection(
    val session: InventaireSession?,
    val managed: Boolean,
    val writable: Boolean,
    val inconsistent: Boolean = false,
    val legacySessionIds: Set<String> = emptySet(),
    val parcelleId: String,
    val placetteId: String? = null,
) {
    fun selectTiges(tiges: List<Tige>): List<Tige> {
        val live = tiges.filter { it.deletedAt == null && it.parcelleId == parcelleId &&
            (placetteId == null || it.placetteId == placetteId) }
        return when {
            inconsistent -> emptyList()
            managed -> session?.let { selected -> live.filter { it.sessionId == selected.sessionId } }.orEmpty()
            else -> live.filter { it.sessionId == null || it.sessionId in legacySessionIds }
        }
    }
}

internal fun selectEssenceSession(
    sessions: List<InventaireSession>,
    parcelleId: String,
    placetteId: String? = null,
): EssenceSessionSelection {
    val live = sessions.filter { it.parcelleId == parcelleId && it.deletedAt == null &&
        (placetteId == null || it.placetteId == placetteId) }
    val managed = live.filter { it.martelageState != null }
    if (managed.isEmpty()) return EssenceSessionSelection(
        session = null, managed = false, writable = placetteId == null,
        legacySessionIds = live.map { it.sessionId }.toSet(),
        parcelleId = parcelleId, placetteId = placetteId,
    )
    val active = managed.filter { it.martelageState in MartelageStates.ACTIVE }
    if (active.size > 1 || managed.any {
            it.typeSession != MartelageStates.TYPE ||
                it.martelageState !in MartelageStates.ACTIVE + MartelageStates.CLOSED
        }) return EssenceSessionSelection(null, managed = true, writable = false,
            inconsistent = true, parcelleId = parcelleId, placetteId = placetteId)
    val selected = active.singleOrNull() ?: managed.maxWithOrNull(
        compareBy<InventaireSession> { it.createdAt }
            .thenBy { it.dateDebut }.thenBy { it.revisionNumber }.thenBy { it.sessionId }
    )
    return EssenceSessionSelection(
        session = selected,
        managed = true,
        writable = selected?.martelageState == MartelageStates.RUNNING && !selected.recoveryRequired,
        parcelleId = parcelleId, placetteId = placetteId,
    )
}
