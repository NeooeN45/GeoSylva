package com.forestry.counter.domain.repository

import com.forestry.counter.domain.model.InventaireSession
import com.forestry.counter.domain.model.MartelageCalculationSnapshotV1
import com.forestry.counter.domain.model.MartelageClockReading
import com.forestry.counter.domain.model.MartelageSnapshotV1
import com.forestry.counter.domain.model.SurveyContextDraft
import kotlinx.coroutines.flow.Flow

interface InventaireSessionRepository {
    fun getByParcelle(parcelleId: String): Flow<List<InventaireSession>>
    fun getByPlacette(placetteId: String): Flow<List<InventaireSession>>
    suspend fun getById(id: String): InventaireSession?
    suspend fun getLatestByType(parcelleId: String, type: String): InventaireSession?
    suspend fun insert(session: InventaireSession)
    suspend fun update(session: InventaireSession)
    suspend fun deleteById(id: String)

    fun currentClockReading(): MartelageClockReading
    suspend fun reconcileMartelage(parcelleId: String)
    suspend fun reconcileForPlacette(parcelleId: String, placetteId: String)
    suspend fun startForPlacette(parcelleId: String, placetteId: String): InventaireSession
    /**
     * Crée le relevé et sa déclaration dans une seule transaction.
     * La garde du brouillon restreint les droits canoniques ; elle ne les accorde jamais.
     * Une implémentation ancienne échoue sans écrire plutôt que d'enchaîner deux sauvegardes.
     */
    suspend fun startForPlacetteWithContext(
        parcelleId: String,
        placetteId: String,
        draft: SurveyContextDraft,
        requestGuard: () -> Boolean = { true },
    ): InventaireSession = error("SURVEY_CONTEXT_ATOMIC_START_NOT_IMPLEMENTED")
    suspend fun startMartelage(parcelleId: String): InventaireSession
    suspend fun pauseMartelage(sessionId: String): InventaireSession
    suspend fun resumeMartelage(sessionId: String): InventaireSession
    suspend fun closeMartelage(
        sessionId: String,
        calculation: MartelageCalculationSnapshotV1? = null,
    ): InventaireSession
    suspend fun createCorrection(closedSessionId: String): InventaireSession
    suspend fun getClosedSnapshot(sessionId: String): MartelageSnapshotV1?
}
