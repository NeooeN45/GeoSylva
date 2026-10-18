package com.forestry.counter.data.repository

import com.forestry.counter.data.local.dao.InventaireSessionDao
import com.forestry.counter.data.local.dao.ObservationQualificationDao
import com.forestry.counter.data.local.dao.TigeDao
import com.forestry.counter.data.local.entity.ObservationQualificationEntity
import com.forestry.counter.data.local.entity.TigeEntity
import com.forestry.counter.data.mapper.toTige
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.model.MartelageStates
import com.forestry.counter.domain.model.ObservationMeasurementBasis
import com.forestry.counter.domain.model.ObservationQualificationCodec
import com.forestry.counter.domain.model.ObservationQualificationDraft
import com.forestry.counter.domain.model.ObservationQualificationPayload
import com.forestry.counter.domain.model.ObservationQualificationRead
import com.forestry.counter.domain.model.ObservationQualificationStatus
import com.forestry.counter.domain.repository.ObservationQualificationRepository

/** Déclarations locales liées aux valeurs exactes ; aucune mesure ancienne requalifiée par défaut. */
class ObservationQualificationRepositoryImpl(
    private val tigeDao: TigeDao,
    private val sessionDao: InventaireSessionDao,
    private val qualificationDao: ObservationQualificationDao,
    private val metadataService: MetadataService,
    private val readAccess: BusinessReadAccess,
    private val mutationGuardFactory: ((String?) -> (() -> Boolean))? = null,
) : ObservationQualificationRepository {
    override suspend fun getQualification(tigeId: String): ObservationQualificationRead? {
        var result: ObservationQualificationRead? = null
        tigeDao.withTigeMutationTransaction {
            val access = readAccess.capture()
            val tree = tigeDao.getTigeById(tigeId) ?: return@withTigeMutationTransaction
            if (!hasLiveScope(tree) || !readAccess.canReadParcelle(tree.parcelleOwnerId, access)) {
                return@withTigeMutationTransaction
            }
            val session = sessionDao.getById(checkNotNull(tree.sessionId)) ?: return@withTigeMutationTransaction
            val stored = qualificationDao.getByTige(tigeId) ?: return@withTigeMutationTransaction
            val record = stored.toRecord()
            if (!basisScopeMatches(record.payload.basis, tree)) return@withTigeMutationTransaction
            check((session.martelageState == MartelageStates.CLOSED) == record.isSealed) {
                "OBSERVATION_QUALIFICATION_SEAL_STATE_MISMATCH"
            }
            val status = if (record.payload.basis.matches(tree.toTige())) ObservationQualificationStatus.CURRENT
                else ObservationQualificationStatus.STALE
            if (readAccess.revalidate(access)) result = ObservationQualificationRead(record, status)
        }
        return result
    }

    override suspend fun saveQualification(
        tigeId: String,
        draft: ObservationQualificationDraft,
        expectedObservationVersion: Int,
        expectedRevision: Int?,
    ): ObservationQualificationRead {
        require(expectedObservationVersion > 0 && (expectedRevision == null || expectedRevision > 0)) {
            "OBSERVATION_QUALIFICATION_INVALID_REVISION"
        }
        val account = metadataService.currentAccountId()
        val actor = account ?: "anonymous"
        val guard = mutationGuardFactory?.invoke(account) ?: { metadataService.currentAccountId() == account }
        var saved: ObservationQualificationRead? = null
        tigeDao.withTigeMutationTransaction {
            check(guard() && metadataService.currentAccountId() == account) { "OBSERVATION_QUALIFICATION_ACCOUNT_CHANGED" }
            val access = readAccess.capture()
            check(access.accountId == account) { "OBSERVATION_QUALIFICATION_ACCOUNT_CHANGED" }
            val tree = checkNotNull(tigeDao.getTigeById(tigeId)) { "OBSERVATION_UNAVAILABLE" }
            check(hasLiveScope(tree)) { "OBSERVATION_QUALIFICATION_SCOPE_UNAVAILABLE" }
            check(readAccess.canReadParcelle(tree.parcelleOwnerId, access)) { "OBSERVATION_QUALIFICATION_ACCESS_DENIED" }
            val session = checkNotNull(sessionDao.getById(checkNotNull(tree.sessionId))) { "SESSION_UNAVAILABLE" }
            check(session.martelageState in MartelageStates.ACTIVE && session.closedSnapshotJson == null) {
                "OBSERVATION_QUALIFICATION_READ_ONLY"
            }
            check(tree.version == expectedObservationVersion) { "OBSERVATION_QUALIFICATION_OBSERVATION_CONFLICT" }
            val previous = qualificationDao.getByTige(tigeId)
            val previousRecord = previous?.toRecord()
            check(previousRecord == null || basisScopeMatches(previousRecord.payload.basis, tree)) {
                "OBSERVATION_QUALIFICATION_SCOPE_CONFLICT"
            }
            val payload = ObservationQualificationPayload(
                basis = ObservationMeasurementBasis.capture(tree.toTige()), draft = draft,
                copiedFrom = previousRecord?.payload?.copiedFrom,
            )
            val json = ObservationQualificationCodec.encode(payload)
            val now = System.currentTimeMillis()
            if (expectedRevision == null) {
                check(previous == null) { "OBSERVATION_QUALIFICATION_REVISION_CONFLICT" }
                qualificationDao.insert(ObservationQualificationEntity(
                    tigeId = tigeId, revision = 1, payloadJson = json,
                    createdAt = now, createdBy = actor, updatedAt = now, updatedBy = actor,
                ))
            } else {
                check(previous != null && previous.revision == expectedRevision && previous.sealedPayloadJson == null) {
                    "OBSERVATION_QUALIFICATION_REVISION_CONFLICT"
                }
                check(qualificationDao.compareAndSet(tigeId, expectedRevision, json, now, actor) == 1) {
                    "OBSERVATION_QUALIFICATION_REVISION_CONFLICT"
                }
            }
            val record = checkNotNull(qualificationDao.getByTige(tigeId)).toRecord()
            val current = checkNotNull(tigeDao.getTigeById(tigeId)) { "OBSERVATION_UNAVAILABLE" }
            check(payload.basis.matches(current.toTige()) && hasLiveScope(current)) {
                "OBSERVATION_QUALIFICATION_OBSERVATION_CONFLICT"
            }
            check(guard() && metadataService.currentAccountId() == account && readAccess.revalidate(access)) {
                "OBSERVATION_QUALIFICATION_ACCESS_CHANGED"
            }
            saved = ObservationQualificationRead(record, ObservationQualificationStatus.CURRENT)
        }
        return checkNotNull(saved)
    }

    /** Une lecture différée conserve l'identité et l'état brut présentés au formulaire. */
    override suspend fun getQualificationForObservation(
        expectedBasis: ObservationMeasurementBasis,
        requestGuard: () -> Boolean,
    ): ObservationQualificationRead? {
        checkRequestGuard(requestGuard)
        requireManagedRequest(expectedBasis)
        var result: ObservationQualificationRead? = null
        tigeDao.withTigeMutationTransaction {
            checkRequestGuard(requestGuard)
            val access = readAccess.capture()
            checkRequestGuard(requestGuard)
            val tree = requireExpectedObservation(expectedBasis, access)
            val session = checkNotNull(sessionDao.getById(checkNotNull(tree.sessionId))) {
                "SESSION_UNAVAILABLE"
            }
            val stored = qualificationDao.getByTige(expectedBasis.tigeId)
            val record = stored?.toRecord()
            if (record != null) {
                check(basisScopeMatches(record.payload.basis, tree)) {
                    "OBSERVATION_QUALIFICATION_SCOPE_CONFLICT"
                }
                check((session.martelageState == MartelageStates.CLOSED) == record.isSealed) {
                    "OBSERVATION_QUALIFICATION_SEAL_STATE_MISMATCH"
                }
                result = ObservationQualificationRead(record,
                    if (record.payload.basis.matches(tree.toTige())) ObservationQualificationStatus.CURRENT
                    else ObservationQualificationStatus.STALE)
            }
            requireExpectedObservation(expectedBasis, access)
            checkRequestGuard(requestGuard)
            check(readAccess.revalidate(access)) { "OBSERVATION_QUALIFICATION_ACCESS_CHANGED" }
            checkRequestGuard(requestGuard)
        }
        checkRequestGuard(requestGuard)
        return result
    }

    /**
     * Les refus survenant dans la transaction englobante annulent ses écritures ;
     * la garde de sortie masque seulement un résultat devenu inaccessible après commit.
     */
    override suspend fun saveQualificationForObservation(
        expectedBasis: ObservationMeasurementBasis,
        draft: ObservationQualificationDraft,
        expectedRevision: Int?,
        requestGuard: () -> Boolean,
    ): ObservationQualificationRead {
        checkRequestGuard(requestGuard)
        requireManagedRequest(expectedBasis)
        var saved: ObservationQualificationRead? = null
        tigeDao.withTigeMutationTransaction {
            checkRequestGuard(requestGuard)
            val access = readAccess.capture()
            checkRequestGuard(requestGuard)
            requireExpectedObservation(expectedBasis, access)
            checkRequestGuard(requestGuard)
            // Le chemin existant conserve auteur capturé, version attendue, CAS et droits canoniques.
            saved = saveQualification(expectedBasis.tigeId, draft, expectedBasis.observationVersion,
                expectedRevision)
            requireExpectedObservation(expectedBasis, access)
            val session = checkNotNull(sessionDao.getById(checkNotNull(expectedBasis.sessionId))) {
                "SESSION_UNAVAILABLE"
            }
            check(session.martelageState in MartelageStates.ACTIVE && session.closedSnapshotJson == null) {
                "OBSERVATION_QUALIFICATION_READ_ONLY"
            }
            checkRequestGuard(requestGuard)
            check(readAccess.revalidate(access)) { "OBSERVATION_QUALIFICATION_ACCESS_CHANGED" }
            checkRequestGuard(requestGuard)
        }
        checkRequestGuard(requestGuard)
        return checkNotNull(saved)
    }

    private fun checkRequestGuard(requestGuard: () -> Boolean) {
        check(requestGuard()) { "OBSERVATION_QUALIFICATION_REQUEST_SCOPE_CHANGED" }
    }

    private fun requireManagedRequest(expectedBasis: ObservationMeasurementBasis) {
        require(!expectedBasis.placetteId.isNullOrBlank() && !expectedBasis.sessionId.isNullOrBlank()) {
            "OBSERVATION_QUALIFICATION_SCOPE_UNAVAILABLE"
        }
    }

    private suspend fun requireExpectedObservation(
        expectedBasis: ObservationMeasurementBasis,
        access: BusinessReadAccess.AccessSnapshot,
    ): TigeEntity {
        check(readAccess.canReadParcelle(expectedBasis.parcelleId, access)) {
            "OBSERVATION_QUALIFICATION_ACCESS_DENIED"
        }
        val tree = checkNotNull(tigeDao.getTigeById(expectedBasis.tigeId)) { "OBSERVATION_UNAVAILABLE" }
        check(basisScopeMatches(expectedBasis, tree)) { "OBSERVATION_QUALIFICATION_SCOPE_CONFLICT" }
        check(hasLiveScope(tree)) { "OBSERVATION_QUALIFICATION_SCOPE_UNAVAILABLE" }
        check(expectedBasis.matches(tree.toTige())) { "OBSERVATION_QUALIFICATION_OBSERVATION_CONFLICT" }
        return tree
    }

    /** Une réaffectation par import ne doit ni exposer ni réancrer une déclaration d'une autre portée. */
    private fun basisScopeMatches(basis: ObservationMeasurementBasis, tree: TigeEntity): Boolean =
        basis.tigeId == tree.tigeId && basis.parcelleId == tree.parcelleOwnerId &&
            basis.placetteId == tree.placetteOwnerId && basis.sessionId == tree.sessionId

    /** La portée doit être une session gérée et attribuée, avec parents encore accessibles. */
    private suspend fun hasLiveScope(tree: TigeEntity): Boolean {
        if (tree.deletedAt != null || tigeDao.countLiveParentForMutation(tree.parcelleOwnerId) != 1) return false
        val plotId = tree.placetteOwnerId ?: return false
        if (tigeDao.countLivePlotForMutation(tree.parcelleOwnerId, plotId) != 1) return false
        val session = tree.sessionId?.let { sessionDao.getById(it) } ?: return false
        return session.parcelleId == tree.parcelleOwnerId && session.placetteId == plotId &&
            session.typeSession == MartelageStates.TYPE &&
            (session.martelageState in MartelageStates.ACTIVE || session.martelageState == MartelageStates.CLOSED)
    }
}
