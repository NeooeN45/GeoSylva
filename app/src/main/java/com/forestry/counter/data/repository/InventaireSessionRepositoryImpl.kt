package com.forestry.counter.data.repository

import com.forestry.counter.data.local.dao.InventaireSessionDao
import com.forestry.counter.data.local.dao.ObservationQualificationDao
import com.forestry.counter.data.local.dao.SurveyContextDao
import com.forestry.counter.data.local.entity.SurveyContextEntity
import com.forestry.counter.data.local.entity.ObservationQualificationEntity
import com.forestry.counter.data.local.entity.TigeEntity
import com.forestry.counter.data.mapper.toDomain
import com.forestry.counter.data.mapper.toEntity
import com.forestry.counter.data.mapper.toParcelle
import com.forestry.counter.data.mapper.toPlacette
import com.forestry.counter.data.mapper.toTige
import com.forestry.counter.data.mapper.toTigeEntity
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.model.InventaireSession
import com.forestry.counter.domain.model.MartelageCalculationSnapshotV1
import com.forestry.counter.domain.model.MartelageClock
import com.forestry.counter.domain.model.MartelageClockReading
import com.forestry.counter.domain.model.MartelageSnapshotCodec
import com.forestry.counter.domain.model.MartelageSnapshotFingerprint
import com.forestry.counter.domain.model.MartelageSnapshotV1
import com.forestry.counter.domain.model.MartelageStates
import com.forestry.counter.domain.model.ObservationMeasurementBasis
import com.forestry.counter.domain.model.ObservationQualificationCodec
import com.forestry.counter.domain.model.ObservationQualificationCopySource
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.model.SurveyContextCodec
import com.forestry.counter.domain.model.SurveyContextDraft
import com.forestry.counter.domain.model.SurveyContextRecord
import com.forestry.counter.domain.model.durationAt
import com.forestry.counter.domain.model.needsClockRecovery
import com.forestry.counter.domain.repository.InventaireSessionRepository
import com.forestry.counter.domain.repository.SurveyContextRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class InventaireSessionRepositoryImpl(
    private val dao: InventaireSessionDao,
    private val metadataService: MetadataService,
    private val clock: MartelageClock? = null,
    private val mutationGuardFactory: ((String?) -> (() -> Boolean))? = null,
    private val readAccess: BusinessReadAccess? = null,
    private val surveyContextDao: SurveyContextDao? = null,
    private val qualificationDao: ObservationQualificationDao? = null,
) : InventaireSessionRepository, SurveyContextRepository {
    /** Contexte local séparé : aucune interprétation nouvelle des anciennes tiges. */
    override suspend fun getContext(sessionId: String): SurveyContextRecord? {
        val contexts = checkNotNull(surveyContextDao) { "SURVEY_CONTEXT_NOT_CONFIGURED" }
        val access = readAccess ?: return null
        var result: SurveyContextRecord? = null
        dao.withMartelageTransaction {
            val snapshot = access.capture()
            val session = dao.getById(sessionId) ?: return@withMartelageTransaction
            if (dao.getLiveParent(session.parcelleId) == null || !access.canReadParcelle(session.parcelleId, snapshot)) {
                return@withMartelageTransaction
            }
            session.placetteId?.let {
                if (dao.getLivePlot(session.parcelleId, it) == null) return@withMartelageTransaction
            }
            val stored = contexts.getBySession(sessionId)
            if (session.martelageState == MartelageStates.CLOSED && stored != null) {
                check(stored.sealedPayloadJson != null) { "SURVEY_CONTEXT_UNSEALED_CLOSED" }
            }
            val record = stored?.toRecord()
            if (access.revalidate(snapshot)) result = record
        }
        return result
    }

    /** L'auteur est capturé depuis le compte ; une révision nulle autorise seulement la création. */
    override suspend fun saveContext(
        sessionId: String,
        draft: SurveyContextDraft,
        expectedRevision: Int?,
    ): SurveyContextRecord = saveContextMutation(sessionId, draft, expectedRevision, null)

    override suspend fun saveContextForScope(
        sessionId: String,
        draft: SurveyContextDraft,
        expectedRevision: Int?,
        requestGuard: () -> Boolean,
    ): SurveyContextRecord = saveContextMutation(sessionId, draft, expectedRevision, requestGuard)

    private suspend fun saveContextMutation(
        sessionId: String,
        draft: SurveyContextDraft,
        expectedRevision: Int?,
        requestGuard: (() -> Boolean)?,
    ): SurveyContextRecord {
        require(expectedRevision == null || expectedRevision > 0) { "SURVEY_CONTEXT_INVALID_REVISION" }
        checkNotNull(readAccess) { "SURVEY_CONTEXT_ACCESS_NOT_CONFIGURED" }
        val contexts = checkNotNull(surveyContextDao) { "SURVEY_CONTEXT_NOT_CONFIGURED" }
        val payload = SurveyContextCodec.encode(draft)
        checkRequestScope(requestGuard)
        val actor = capturedActor()
        var saved: SurveyContextRecord? = null
        withAuthorizedMutation(requestGuard) {
            val session = checkNotNull(dao.getById(sessionId)) { "SESSION_UNAVAILABLE" }
            authorizeParent(session.parcelleId, actor)
            authorizePlot(session.parcelleId, checkNotNull(session.placetteId) { "MARTELAGE_LEGACY_SCOPE_READ_ONLY" })
            check(session.martelageState == null || session.martelageState in MartelageStates.ACTIVE) {
                "SURVEY_CONTEXT_SESSION_NOT_EDITABLE"
            }
            check(session.closedSnapshotJson == null) { "SURVEY_CONTEXT_SESSION_NOT_EDITABLE" }
            val previous = contexts.getBySession(sessionId)
            val now = System.currentTimeMillis()
            if (expectedRevision == null) {
                check(previous == null) { "SURVEY_CONTEXT_REVISION_CONFLICT" }
                contexts.insert(SurveyContextEntity(
                    sessionId = sessionId, revision = 1, payloadJson = payload,
                    createdAt = now, createdBy = actor, updatedAt = now, updatedBy = actor,
                ))
            } else {
                check(previous != null && previous.revision == expectedRevision && previous.sealedPayloadJson == null) {
                    "SURVEY_CONTEXT_REVISION_CONFLICT"
                }
                previous.toRecord()
                check(contexts.compareAndSet(sessionId, expectedRevision, payload, now, actor) == 1) {
                    "SURVEY_CONTEXT_REVISION_CONFLICT"
                }
            }
            saved = checkNotNull(contexts.getBySession(sessionId)).toRecord()
        }
        return checkNotNull(saved)
    }
    override fun getByParcelle(parcelleId: String): Flow<List<InventaireSession>> {
        val source = dao.getByParcelle(parcelleId).map { list -> list.map { it.toDomain() } }
        val access = readAccess ?: return source
        return access.observe(source, emptyList()) { values, snapshot ->
            values.filter { access.canReadParcelle(it.parcelleId, snapshot) }
        }
    }

    override fun getByPlacette(placetteId: String): Flow<List<InventaireSession>> {
        val source = dao.getByPlacette(placetteId).map { list -> list.map { it.toDomain() } }
        val access = readAccess ?: return source
        return access.observe(source, emptyList()) { values, snapshot ->
            values.filter { access.canReadParcelle(it.parcelleId, snapshot) }
        }
    }

    override suspend fun getById(id: String): InventaireSession? {
        val access = readAccess
        val snapshot = access?.capture()
        val session = dao.getById(id)?.toDomain() ?: return null
        if (access != null && !access.canReadParcelle(session.parcelleId, checkNotNull(snapshot))) return null
        return if (access == null || access.revalidate(checkNotNull(snapshot))) session else null
    }

    override suspend fun getLatestByType(parcelleId: String, type: String): InventaireSession? {
        val access = readAccess
        val snapshot = access?.capture()
        if (access != null && !access.canReadParcelle(parcelleId, checkNotNull(snapshot))) return null
        val session = dao.getLatestByType(parcelleId, type)?.toDomain() ?: return null
        return if (access == null || access.revalidate(checkNotNull(snapshot))) session else null
    }

    /** Generic legacy inventories cannot bypass the managed lifecycle. */
    override suspend fun insert(session: InventaireSession) {
        require(session.martelageState == null && session.closedSnapshotJson == null && session.traceJson == null) { "MARTELAGE_USE_LIFECYCLE" }
        withAuthorizedMutation {
            authorizeParent(session.parcelleId, capturedActor())
            authorizePlot(session.parcelleId, checkNotNull(session.placetteId) { "SESSION_PLOT_REQUIRED" })
            dao.insert(metadataService.enrichForCreate(session).toEntity())
        }
    }

    override suspend fun update(session: InventaireSession) {
        withAuthorizedMutation {
            val current = checkNotNull(dao.getById(session.sessionId)) { "SESSION_UNAVAILABLE" }
            check(current.martelageState == null && session.martelageState == null && session.closedSnapshotJson == null && session.traceJson == current.traceJson) {
                "MARTELAGE_USE_LIFECYCLE"
            }
            require(session.parcelleId == current.parcelleId && session.placetteId == current.placetteId) { "SESSION_PARENT_CHANGE_REFUSED" }
            authorizeParent(current.parcelleId, capturedActor())
            current.placetteId?.let { authorizePlot(current.parcelleId, it) }
            dao.update(metadataService.enrichForUpdate(session, current.version).toEntity())
        }
    }

    override suspend fun deleteById(id: String) {
        withAuthorizedMutation {
            val current = dao.getById(id) ?: return@withAuthorizedMutation
            authorizeParent(current.parcelleId, capturedActor())
            check(current.martelageState !in MartelageStates.ACTIVE) { "MARTELAGE_ACTIVE_DELETE_REFUSED" }
            dao.deleteById(id, System.currentTimeMillis())
        }
    }

    override fun currentClockReading(): MartelageClockReading =
        checkNotNull(clock) { "MARTELAGE_CLOCK_NOT_CONFIGURED" }.read()

    override suspend fun reconcileMartelage(parcelleId: String) {
        val reading = currentClockReading()
        withAuthorizedMutation {
            authorizeParent(parcelleId, capturedActor())
            val active = dao.getActiveForParcelle(parcelleId)
            // Multiple independent plots may be active. Historical null scope is never attributed.
            active.map { it.toDomain() }.forEach { session ->
                val plotId = session.placetteId ?: return@forEach
                authorizePlot(parcelleId, plotId)
                if (session.needsClockRecovery(reading)) dao.update(recovered(session).toEntity())
            }
        }
    }

    override suspend fun reconcileForPlacette(parcelleId: String, placetteId: String) {
        val reading = currentClockReading()
        withAuthorizedMutation {
            authorizeParent(parcelleId, capturedActor())
            authorizePlot(parcelleId, placetteId)
            val active = dao.getActiveForPlacette(parcelleId, placetteId)
            check(active.size <= 1) { "MARTELAGE_MULTIPLE_ACTIVE_SESSIONS" }
            active.firstOrNull()?.toDomain()?.let { session ->
                if (session.needsClockRecovery(reading)) dao.update(recovered(session).toEntity())
            }
        }
    }

    override suspend fun startMartelage(parcelleId: String): InventaireSession =
        error("MARTELAGE_PLACETTE_REQUIRED")

    override suspend fun startForPlacette(parcelleId: String, placetteId: String): InventaireSession {
        require(placetteId.isNotBlank()) { "MARTELAGE_PLACETTE_REQUIRED" }
        val reading = currentClockReading()
        val id = UUID.randomUUID().toString()
        val candidate = metadataService.enrichForCreate(InventaireSession(
            sessionId = id, parcelleId = parcelleId, placetteId = placetteId, typeSession = MartelageStates.TYPE,
            dateDebut = reading.wallTimeMs, dateFin = null, operateurNom = null,
            methode = null, intensiteEchantillonnagePct = null, objectifSession = null,
            remarques = null, createdAt = reading.wallTimeMs,
            martelageState = if (reading.bootCount != null) MartelageStates.RUNNING else MartelageStates.PAUSED,
            segmentStartedElapsedMs = if (reading.bootCount != null) reading.elapsedRealtimeMs else null,
            segmentBootCount = reading.bootCount,
            campaignId = id,
            recoveryRequired = reading.bootCount == null,
        ))
        withAuthorizedMutation {
            authorizeParent(parcelleId, candidate.auteur)
            authorizePlot(parcelleId, placetteId)
            check(dao.getActiveForPlacette(parcelleId, placetteId).isEmpty()) { "MARTELAGE_ALREADY_ACTIVE" }
            dao.insert(candidate.toEntity())
        }
        return candidate
    }

    /** La session et sa déclaration initiale existent ensemble, ou aucune des deux n'est créée. */
    override suspend fun startForPlacetteWithContext(
        parcelleId: String,
        placetteId: String,
        draft: SurveyContextDraft,
        requestGuard: () -> Boolean,
    ): InventaireSession {
        require(placetteId.isNotBlank()) { "MARTELAGE_PLACETTE_REQUIRED" }
        require(draft.purpose != null) { "SURVEY_CONTEXT_PURPOSE_REQUIRED" }
        checkNotNull(readAccess) { "SURVEY_CONTEXT_ACCESS_NOT_CONFIGURED" }
        val contexts = checkNotNull(surveyContextDao) { "SURVEY_CONTEXT_NOT_CONFIGURED" }
        val payload = SurveyContextCodec.encode(draft)
        checkRequestScope(requestGuard)
        val actor = capturedActor()
        val reading = currentClockReading()
        val id = UUID.randomUUID().toString()
        val candidate = metadataService.enrichForCreate(InventaireSession(
            sessionId = id, parcelleId = parcelleId, placetteId = placetteId, typeSession = MartelageStates.TYPE,
            dateDebut = reading.wallTimeMs, dateFin = null, operateurNom = null,
            methode = null, intensiteEchantillonnagePct = null, objectifSession = null,
            remarques = null, createdAt = reading.wallTimeMs,
            martelageState = if (reading.bootCount != null) MartelageStates.RUNNING else MartelageStates.PAUSED,
            segmentStartedElapsedMs = if (reading.bootCount != null) reading.elapsedRealtimeMs else null,
            segmentBootCount = reading.bootCount,
            campaignId = id,
            recoveryRequired = reading.bootCount == null,
        ))
        withAuthorizedMutation(requestGuard) {
            check(candidate.auteur == actor) { "MARTELAGE_ACCOUNT_CHANGED" }
            authorizeParent(parcelleId, actor)
            authorizePlot(parcelleId, placetteId)
            check(dao.getActiveForPlacette(parcelleId, placetteId).isEmpty()) { "MARTELAGE_ALREADY_ACTIVE" }
            dao.insert(candidate.toEntity())
            val now = System.currentTimeMillis()
            contexts.insert(SurveyContextEntity(
                sessionId = id, revision = 1, payloadJson = payload,
                createdAt = now, createdBy = actor, updatedAt = now, updatedBy = actor,
            ))
            checkNotNull(contexts.getBySession(id)).toRecord()
            authorizeParent(parcelleId, actor)
            authorizePlot(parcelleId, placetteId)
        }
        return candidate
    }

    override suspend fun pauseMartelage(sessionId: String): InventaireSession = changeSession(sessionId) { session, reading ->
        when (session.martelageState) {
            MartelageStates.RUNNING -> session.copy(
                martelageState = MartelageStates.PAUSED,
                activeDurationMs = session.durationAt(reading),
                segmentStartedElapsedMs = null, segmentBootCount = null,
            )
            MartelageStates.PAUSED -> session
            else -> error("MARTELAGE_PAUSE_INVALID")
        }
    }

    override suspend fun resumeMartelage(sessionId: String): InventaireSession = changeSession(sessionId) { session, reading ->
        check(reading.bootCount != null) { "MARTELAGE_CLOCK_IDENTITY_UNAVAILABLE" }
        when (session.martelageState) {
            MartelageStates.PAUSED -> session.copy(
                martelageState = MartelageStates.RUNNING, segmentStartedElapsedMs = reading.elapsedRealtimeMs,
                segmentBootCount = reading.bootCount, recoveryRequired = false,
            )
            MartelageStates.RUNNING -> session
            else -> error("MARTELAGE_RESUME_INVALID")
        }
    }

    override suspend fun closeMartelage(
        sessionId: String,
        calculation: MartelageCalculationSnapshotV1?,
    ): InventaireSession {
        val reading = currentClockReading()
        val actor = capturedActor()
        var closed: InventaireSession? = null
        withAuthorizedMutation {
            var session = checkNotNull(dao.getById(sessionId)) { "SESSION_UNAVAILABLE" }.toDomain()
            authorizeParent(session.parcelleId, actor)
            if (session.martelageState == MartelageStates.CLOSED) {
                checkClosedQualifications(sessionId)
                // Idempotent retry retains the first sealed payload and duration.
                surveyContextDao?.getBySession(sessionId)?.let { context ->
                    check(context.sealedPayloadJson != null) { "SURVEY_CONTEXT_UNSEALED_CLOSED" }
                    context.toRecord()
                }
                closed = session
                return@withAuthorizedMutation
            }
            val plotId = checkNotNull(session.placetteId) { "MARTELAGE_LEGACY_SCOPE_READ_ONLY" }
            authorizePlot(session.parcelleId, plotId)
            check(session.martelageState in MartelageStates.ACTIVE) { "MARTELAGE_CLOSE_INVALID" }
            if (session.needsClockRecovery(reading)) session = recovered(session)
            val parent = checkNotNull(dao.getLiveParent(session.parcelleId)).toParcelle()
            val plots = listOf(checkNotNull(dao.getLivePlot(session.parcelleId, plotId)).toPlacette())
            val trees = dao.getLiveTrees(sessionId).map { it.toTige() }
            check(trees.all { it.parcelleId == session.parcelleId && it.placetteId == plotId }) {
                "MARTELAGE_TREE_SCOPE_MISMATCH"
            }
            if (calculation != null) {
                require(calculation.engineVersion.isNotBlank() && calculation.engineVersion.length <= 128) {
                    "MARTELAGE_CALCULATION_ENGINE_INVALID"
                }
                check(calculation.sourceFingerprint == MartelageSnapshotFingerprint.compute(parent, plots, trees)) {
                    "MARTELAGE_CALCULATION_STALE"
                }
            }
            val duration = session.durationAt(reading)
            val snapshot = MartelageSnapshotV1(
                schemaVersion = 2, sessionId = sessionId, parcelleId = session.parcelleId, placetteId = plotId,
                campaignId = checkNotNull(session.campaignId), revisionNumber = session.revisionNumber,
                previousSessionId = session.previousSessionId, startedAt = session.dateDebut,
                closedAt = reading.wallTimeMs, activeDurationMs = duration, auteur = session.auteur,
                parcelle = parent, placettes = plots.toList(), tiges = trees.toList(), calculation = calculation,
                traceJson = session.traceJson,
            )
            val result = session.copy(
                martelageState = MartelageStates.CLOSED, dateFin = reading.wallTimeMs,
                activeDurationMs = duration, segmentStartedElapsedMs = null, segmentBootCount = null,
                closedSnapshotJson = MartelageSnapshotCodec.encode(snapshot),
                version = session.version + 1,
            )
            // Même transaction que la clôture ; aucun ajout au snapshot historique V1/V2.
            sealSurveyContext(sessionId)
            sealObservationQualifications(sessionId)
            dao.update(result.toEntity())
            closed = result
        }
        return checkNotNull(closed)
    }

    override suspend fun createCorrection(closedSessionId: String): InventaireSession {
        val reading = currentClockReading()
        val actor = capturedActor()
        var created: InventaireSession? = null
        withAuthorizedMutation {
            val previous = checkNotNull(dao.getById(closedSessionId)) { "SESSION_UNAVAILABLE" }.toDomain()
            authorizeParent(previous.parcelleId, actor)
            check(previous.martelageState == MartelageStates.CLOSED) { "MARTELAGE_CORRECTION_REQUIRES_CLOSED" }
            val plotId = checkNotNull(previous.placetteId) { "MARTELAGE_LEGACY_SCOPE_READ_ONLY" }
            authorizePlot(previous.parcelleId, plotId)
            check(dao.getActiveForPlacette(previous.parcelleId, plotId).isEmpty()) { "MARTELAGE_ALREADY_ACTIVE" }
            val snapshot = decodeAndValidate(previous)
            val campaign = checkNotNull(previous.campaignId)
            val revisions = dao.getCampaignRevisions(campaign)
            check(revisions.firstOrNull()?.sessionId == closedSessionId) { "MARTELAGE_CORRECTION_NOT_LATEST" }
            val next = previous.copy(
                sessionId = UUID.randomUUID().toString(), dateDebut = reading.wallTimeMs, dateFin = null,
                createdAt = reading.wallTimeMs, martelageState = MartelageStates.PAUSED,
                activeDurationMs = 0L, segmentStartedElapsedMs = null, segmentBootCount = null,
                revisionNumber = previous.revisionNumber + 1, previousSessionId = previous.sessionId,
                closedSnapshotJson = null, traceJson = null, recoveryRequired = false, auteur = actor,
                source = MetadataService.Source.MANUAL.value, version = 1,
            )
            dao.insert(next.toEntity())
            // IDs and UUIDs are new; measurement values/timestamps and stable origin are unchanged.
            val copies = snapshot.tiges.map { tree ->
                tree.copy(
                    id = UUID.randomUUID().toString(), sessionId = next.sessionId,
                    originTigeId = tree.originTigeId ?: tree.id,
                    auteur = actor, source = MetadataService.Source.MANUAL.value, version = 1,
                ).toTigeEntity().copy(uuid = UUID.randomUUID().toString())
            }
            dao.insertCorrectionTrees(copies)
            copySealedSurveyContext(previous.sessionId, next.sessionId, actor, reading.wallTimeMs)
            copySealedObservationQualifications(previous.sessionId, snapshot.tiges, copies, actor, reading.wallTimeMs)
            created = next
        }
        return checkNotNull(created)
    }

    override suspend fun getClosedSnapshot(sessionId: String): MartelageSnapshotV1? {
        val access = readAccess
        val accessSnapshot = access?.capture()
        val session = dao.getById(sessionId)?.toDomain() ?: return null
        if (access != null && !access.canReadParcelle(session.parcelleId, checkNotNull(accessSnapshot))) return null
        if (session.martelageState != MartelageStates.CLOSED) return null
        // Decode only an authorized parent, then reject an epoch/association change before exposing any measurements.
        val closedSnapshot = decodeAndValidate(session)
        return if (access == null || access.revalidate(checkNotNull(accessSnapshot))) closedSnapshot else null
    }

    private fun decodeAndValidate(session: InventaireSession): MartelageSnapshotV1 {
        val snapshot = MartelageSnapshotCodec.decode(checkNotNull(session.closedSnapshotJson) { "MARTELAGE_SNAPSHOT_MISSING" })
        check(snapshot.sessionId == session.sessionId && snapshot.parcelleId == session.parcelleId &&
            snapshot.placetteId == session.placetteId &&
            snapshot.campaignId == session.campaignId && snapshot.revisionNumber == session.revisionNumber &&
            snapshot.closedAt == session.dateFin && snapshot.activeDurationMs == session.activeDurationMs &&
            snapshot.traceJson == session.traceJson) {
            "MARTELAGE_SNAPSHOT_IDENTITY_MISMATCH"
        }
        snapshot.calculation?.let { calculation ->
            val fingerprint = if (snapshot.schemaVersion == 1) MartelageSnapshotFingerprint.computeLegacyV1(
                snapshot.parcelle, snapshot.placettes, snapshot.tiges,
            ) else MartelageSnapshotFingerprint.compute(snapshot.parcelle, snapshot.placettes, snapshot.tiges)
            check(calculation.sourceFingerprint == fingerprint) { "MARTELAGE_SNAPSHOT_CALCULATION_MISMATCH" }
        }
        return snapshot
    }

    private suspend fun changeSession(
        sessionId: String,
        change: (InventaireSession, MartelageClockReading) -> InventaireSession,
    ): InventaireSession {
        val reading = currentClockReading()
        val actor = capturedActor()
        var result: InventaireSession? = null
        withAuthorizedMutation {
            var current = checkNotNull(dao.getById(sessionId)) { "SESSION_UNAVAILABLE" }.toDomain()
            authorizeParent(current.parcelleId, actor)
            authorizePlot(current.parcelleId, checkNotNull(current.placetteId) { "MARTELAGE_LEGACY_SCOPE_READ_ONLY" })
            if (current.needsClockRecovery(reading)) current = recovered(current)
            val next = change(current, reading)
            val stored = if (next == current) current else next.copy(version = current.version + 1)
            // Persist recovery even when an idempotent pause changes no other field.
            dao.update(stored.toEntity())
            result = stored
        }
        return checkNotNull(result)
    }

    private suspend fun authorizePlot(parcelleId: String, placetteId: String) {
        check(dao.getLivePlot(parcelleId, placetteId) != null) { "MARTELAGE_PLOT_UNAVAILABLE" }
    }

    private suspend fun sealSurveyContext(sessionId: String) {
        val contexts = surveyContextDao ?: return
        val stored = contexts.getBySession(sessionId) ?: return
        stored.toRecord()
        check(stored.sealedPayloadJson == null && contexts.seal(sessionId, stored.revision) == 1) {
            "SURVEY_CONTEXT_REVISION_CONFLICT"
        }
    }

    /** Gèle aussi une déclaration périmée, sans la rendre applicable aux valeurs clôturées. */
    private suspend fun sealObservationQualifications(sessionId: String) {
        val qualifications = qualificationDao ?: return
        val rows = qualifications.getBySession(sessionId)
        if (rows.isNotEmpty()) checkNotNull(readAccess) { "OBSERVATION_QUALIFICATION_ACCESS_NOT_CONFIGURED" }
        checkQualificationScopes(sessionId, rows)
        rows.forEach { row ->
            row.toRecord()
            check(row.sealedPayloadJson == null && qualifications.seal(row.tigeId, row.revision) == 1) {
                "OBSERVATION_QUALIFICATION_REVISION_CONFLICT"
            }
        }
    }

    private suspend fun checkClosedQualifications(sessionId: String) {
        val rows = qualificationDao?.getBySession(sessionId) ?: return
        if (rows.isNotEmpty()) checkNotNull(readAccess) { "OBSERVATION_QUALIFICATION_ACCESS_NOT_CONFIGURED" }
        checkQualificationScopes(sessionId, rows)
        rows.forEach { row ->
            check(row.sealedPayloadJson != null) { "OBSERVATION_QUALIFICATION_UNSEALED_CLOSED" }
            row.toRecord()
        }
    }

    /** Contrôle tout le lot avant le gel : une réaffectation ne donne pas autorité sur l'ancienne portée. */
    private suspend fun checkQualificationScopes(sessionId: String, rows: List<ObservationQualificationEntity>) {
        if (rows.isEmpty()) return
        val session = checkNotNull(dao.getById(sessionId)) { "SESSION_UNAVAILABLE" }
        rows.forEach { row ->
            val basis = row.toRecord().payload.basis
            check(basis.sessionId == sessionId && basis.parcelleId == session.parcelleId &&
                basis.placetteId == session.placetteId) { "OBSERVATION_QUALIFICATION_SCOPE_CONFLICT" }
        }
    }

    /** Copie seulement les déclarations liées à l'état exact du snapshot, avec filiation explicite. */
    private suspend fun copySealedObservationQualifications(
        sourceSessionId: String,
        sources: List<Tige>,
        copies: List<TigeEntity>,
        actor: String,
        now: Long,
    ) {
        val qualifications = qualificationDao ?: return
        val rows = qualifications.getBySession(sourceSessionId).associateBy { it.tigeId }
        if (rows.isNotEmpty()) checkNotNull(readAccess) { "OBSERVATION_QUALIFICATION_ACCESS_NOT_CONFIGURED" }
        check(sources.size == copies.size) { "OBSERVATION_QUALIFICATION_COPY_MISMATCH" }
        sources.zip(copies).forEach { (source, copy) ->
            val stored = rows[source.id] ?: return@forEach
            check(stored.sealedPayloadJson != null) { "OBSERVATION_QUALIFICATION_UNSEALED_CLOSED" }
            val record = stored.toRecord()
            if (!record.payload.basis.matches(source)) return@forEach
            val payload = record.payload.copy(
                basis = ObservationMeasurementBasis.capture(copy.toTige()),
                copiedFrom = ObservationQualificationCopySource(source.id, record.revision, record.updatedAt, record.updatedBy),
            )
            qualifications.insert(ObservationQualificationEntity(
                tigeId = copy.tigeId, revision = 1, payloadJson = ObservationQualificationCodec.encode(payload),
                createdAt = now, createdBy = actor, updatedAt = now, updatedBy = actor,
            ))
        }
    }

    /** Une correction reprend le gel source, avec auteur de correction et filiation propres. */
    private suspend fun copySealedSurveyContext(sourceId: String, targetId: String, actor: String, now: Long) {
        val contexts = surveyContextDao ?: return
        val source = contexts.getBySession(sourceId) ?: return
        check(source.sealedPayloadJson != null) { "SURVEY_CONTEXT_UNSEALED_CLOSED" }
        source.toRecord()
        contexts.insert(SurveyContextEntity(
            sessionId = targetId, revision = 1, payloadJson = checkNotNull(source.sealedPayloadJson),
            createdAt = now, createdBy = actor, updatedAt = now, updatedBy = actor,
            copiedFromSessionId = sourceId,
        ))
    }

    private suspend fun authorizeParent(parcelleId: String, actor: String?) {
        val currentAccount = metadataService.currentAccountId()
        check(actor == (currentAccount ?: "anonymous")) { "MARTELAGE_ACCOUNT_CHANGED" }
        check(dao.getLiveParent(parcelleId) != null) { "MARTELAGE_PARENT_UNAVAILABLE" }
        check(dao.getLinkedAccounts(parcelleId).all { currentAccount != null && it == currentAccount }) {
            "MARTELAGE_LINKED_TO_ANOTHER_ACCOUNT"
        }
        readAccess?.let { access ->
            val snapshot = access.capture()
            check(access.canReadParcelle(parcelleId, snapshot) && access.revalidate(snapshot)) {
                "MARTELAGE_LINKED_TO_ANOTHER_ACCOUNT"
            }
        }
    }

    private fun capturedActor(): String = metadataService.currentAccountId() ?: "anonymous"
    private fun checkRequestScope(requestGuard: (() -> Boolean)?) {
        check(requestGuard?.invoke() != false) { "SURVEY_CONTEXT_REQUEST_SCOPE_CHANGED" }
    }

    private suspend fun withAuthorizedMutation(requestGuard: (() -> Boolean)? = null, block: suspend () -> Unit) {
        val account = metadataService.currentAccountId()
        val guard = mutationGuardFactory?.invoke(account) ?: { metadataService.currentAccountId() == account }
        dao.withMartelageTransaction {
            check(guard() && metadataService.currentAccountId() == account) { "MARTELAGE_ACCOUNT_CHANGED" }
            checkRequestScope(requestGuard)
            val accessSnapshot = readAccess?.capture()
            block()
            check(guard() && metadataService.currentAccountId() == account) { "MARTELAGE_ACCOUNT_CHANGED" }
            if (accessSnapshot != null) {
                check(checkNotNull(readAccess).revalidate(accessSnapshot)) { "MARTELAGE_ACCESS_CHANGED" }
            }
            // La lecture des droits peut suspendre : recontrôler ensuite le brouillon et son identité.
            if (requestGuard != null) {
                checkRequestScope(requestGuard)
                check(guard() && metadataService.currentAccountId() == account) { "MARTELAGE_ACCOUNT_CHANGED" }
            }
        }
    }
    private fun recovered(session: InventaireSession) = session.copy(
        martelageState = MartelageStates.PAUSED, segmentStartedElapsedMs = null,
        segmentBootCount = null, recoveryRequired = true, version = session.version + 1,
    )

}
