package com.forestry.counter.presentation.screens.forestry

import com.forestry.counter.domain.model.InventaireSession
import com.forestry.counter.domain.model.MartelageStates
import com.forestry.counter.domain.model.ObservationMeasurementBasis
import com.forestry.counter.domain.model.ObservationQualificationDraft
import com.forestry.counter.domain.model.ObservationQualificationRead
import com.forestry.counter.domain.model.ObservationQualificationStatus
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.repository.InventaireSessionRepository
import com.forestry.counter.domain.repository.ObservationQualificationRepository
import com.forestry.counter.domain.repository.TigeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

enum class ObservationQualificationEditorError { CONFLICT, UNAVAILABLE, SAVE_FAILED }

data class ObservationQualificationEditorState(
    val basis: ObservationMeasurementBasis? = null,
    val read: ObservationQualificationRead? = null,
    val readOnly: Boolean = true,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: ObservationQualificationEditorError? = null,
    val reloadCount: Int = 0,
)

/**
 * Déclaration seulement : aucun appel d'écriture de mesure brute ni brouillon de formulaire.
 * Les contrôles UI restreignent les dépôts canoniques ; ils ne remplacent pas leurs transactions.
 */
class ObservationQualificationEditorController(
    private val tigeRepository: TigeRepository,
    private val qualificationRepository: ObservationQualificationRepository,
    private val sessionRepository: InventaireSessionRepository,
    private val expectedParcelleId: String,
    private val expectedPlacetteId: String,
    private val expectedSessionId: String,
    private val expectedEssenceCode: String,
    private val requestGuard: () -> Boolean,
) {
    private val monitor = Any()
    private val mutableState = MutableStateFlow(ObservationQualificationEditorState())
    val state: StateFlow<ObservationQualificationEditorState> = mutableState.asStateFlow()
    private var selectedTigeId: String? = null
    private var operationSequence = 0L
    private var revoked = false

    init {
        require(listOf(expectedParcelleId, expectedPlacetteId, expectedSessionId, expectedEssenceCode)
            .all { it.isNotBlank() }) { "OBSERVATION_EDITOR_SCOPE_UNAVAILABLE" }
    }

    /** L'objet doit provenir de la liste déjà autorisée ; les treize valeurs sont ensuite revérifiées. */
    suspend fun open(treeFromAuthorizedList: Tige): Boolean {
        if (!ensureAccess()) return false
        val operation = begin(loading = true) ?: return false
        if (!hasExpectedScope(treeFromAuthorizedList, treeFromAuthorizedList.id)) {
            fail(operation, ObservationQualificationEditorError.UNAVAILABLE, clearPrivate = true)
            return false
        }
        synchronized(monitor) {
            if (!isCurrent(operation)) return false
            selectedTigeId = treeFromAuthorizedList.id
        }
        return try {
            val basis = ObservationMeasurementBasis.capture(treeFromAuthorizedList)
            load(operation, basis.tigeId, basis, isReload = false)
        } catch (cancelled: CancellationException) {
            finishCancellation(operation)
            throw cancelled
        } catch (failure: Exception) {
            handleFailure(operation, failure, saving = false)
            false
        }
    }

    /** Recharge le même identifiant et la même portée, sans fusion de la saisie de l'utilisateur. */
    suspend fun reload(): Boolean {
        if (!ensureAccess()) return false
        val id = synchronized(monitor) { selectedTigeId } ?: return false
        val operation = begin(loading = true) ?: return false
        return try {
            load(operation, id, expectedBasis = null, isReload = true)
        } catch (cancelled: CancellationException) {
            finishCancellation(operation)
            throw cancelled
        } catch (failure: Exception) {
            handleFailure(operation, failure, saving = false)
            false
        }
    }

    /** Réutilise uniquement l'ancrage et la révision affichés ; aucun réancrage implicite. */
    suspend fun save(draft: ObservationQualificationDraft): Boolean {
        if (!ensureAccess()) return false
        val displayed = state.value
        val basis = displayed.basis ?: return false
        if (displayed.readOnly || displayed.error == ObservationQualificationEditorError.CONFLICT) return false
        val operation = begin(loading = false, expectedState = displayed) ?: return false
        return try {
            val before = readTree(operation, basis.tigeId) ?: return false
            if (!checkTree(operation, basis, before)) return false
            val session = readSession(operation) ?: return false
            if (!checkSession(operation, session)) return false
            if (session.martelageState == MartelageStates.CLOSED) {
                fail(operation, ObservationQualificationEditorError.UNAVAILABLE, readOnly = true)
                return false
            }
            if (!isCurrent(operation)) return false
            val saved = qualificationRepository.saveQualificationForObservation(
                basis, draft, displayed.read?.record?.revision, operationGuard(operation),
            )
            if (!isCurrent(operation)) return false
            val after = readTree(operation, basis.tigeId) ?: return false
            if (!checkTree(operation, basis, after)) return false
            val currentSession = readSession(operation) ?: return false
            if (!checkSession(operation, currentSession)) return false
            if (currentSession.martelageState == MartelageStates.CLOSED) {
                fail(operation, ObservationQualificationEditorError.UNAVAILABLE, readOnly = true)
                return false
            }
            if (!readBelongsToScope(saved, basis, currentSession) ||
                saved.status != ObservationQualificationStatus.CURRENT || saved.record.payload.basis != basis
            ) {
                fail(operation, ObservationQualificationEditorError.CONFLICT)
                return false
            }
            publish(operation) { it.copy(read = saved, isSaving = false, error = null, readOnly = false) }
        } catch (cancelled: CancellationException) {
            finishCancellation(operation)
            throw cancelled
        } catch (failure: Exception) {
            handleFailure(operation, failure, saving = true)
            false
        }
    }

    /** Une fermeture ou révocation empêche toute adoption tardive, même si la garde redevient vraie. */
    fun invalidate() = synchronized(monitor) {
        revoked = true
        operationSequence++
        selectedTigeId = null
        mutableState.value = ObservationQualificationEditorState(
            error = ObservationQualificationEditorError.UNAVAILABLE,
        )
    }

    private suspend fun load(
        operation: Long,
        id: String,
        expectedBasis: ObservationMeasurementBasis?,
        isReload: Boolean,
    ): Boolean {
        val current = readTree(operation, id) ?: return false
        if (!hasExpectedScope(current, id)) {
            fail(operation, ObservationQualificationEditorError.UNAVAILABLE, clearPrivate = true)
            return false
        }
        val basis = expectedBasis ?: ObservationMeasurementBasis.capture(current)
        if (!checkTree(operation, basis, current)) return false
        val session = readSession(operation) ?: return false
        if (!checkSession(operation, session)) return false
        if (!isCurrent(operation)) return false
        val read = qualificationRepository.getQualificationForObservation(basis, operationGuard(operation))
        if (!isCurrent(operation)) return false
        val verified = readTree(operation, id) ?: return false
        if (!checkTree(operation, basis, verified)) return false
        val verifiedSession = readSession(operation) ?: return false
        if (!checkSession(operation, verifiedSession)) return false
        if (read != null && !readBelongsToScope(read, basis, verifiedSession)) {
            fail(operation, ObservationQualificationEditorError.UNAVAILABLE, clearPrivate = true)
            return false
        }
        return publish(operation) {
            it.copy(
                basis = basis, read = read, isLoading = false, isSaving = false, error = null,
                readOnly = verifiedSession.martelageState == MartelageStates.CLOSED,
                reloadCount = it.reloadCount + if (isReload) 1 else 0,
            )
        }
    }

    private suspend fun readTree(operation: Long, id: String): Tige? {
        if (!isCurrent(operation)) return null
        val tree = tigeRepository.getTigeById(id).first()
        if (!isCurrent(operation)) return null
        if (tree == null) fail(operation, ObservationQualificationEditorError.UNAVAILABLE, clearPrivate = true)
        return tree
    }

    private suspend fun readSession(operation: Long): InventaireSession? {
        if (!isCurrent(operation)) return null
        val session = sessionRepository.getById(expectedSessionId)
        if (!isCurrent(operation)) return null
        if (session == null) fail(operation, ObservationQualificationEditorError.UNAVAILABLE, clearPrivate = true)
        return session
    }

    private fun hasExpectedScope(tree: Tige, id: String): Boolean =
        tree.id == id && tree.deletedAt == null && tree.parcelleId == expectedParcelleId &&
            tree.placetteId == expectedPlacetteId && tree.sessionId == expectedSessionId &&
            tree.essenceCode == expectedEssenceCode

    private fun checkTree(operation: Long, basis: ObservationMeasurementBasis, tree: Tige): Boolean {
        if (!hasExpectedScope(tree, basis.tigeId)) {
            fail(operation, ObservationQualificationEditorError.UNAVAILABLE, clearPrivate = true)
            return false
        }
        if (!basis.matches(tree)) {
            fail(operation, ObservationQualificationEditorError.CONFLICT)
            return false
        }
        return isCurrent(operation)
    }

    private fun checkSession(operation: Long, session: InventaireSession): Boolean {
        if (session.deletedAt != null || session.sessionId != expectedSessionId ||
            session.parcelleId != expectedParcelleId || session.placetteId != expectedPlacetteId ||
            session.typeSession != MartelageStates.TYPE ||
            (session.martelageState !in MartelageStates.ACTIVE && session.martelageState != MartelageStates.CLOSED) ||
            (session.martelageState in MartelageStates.ACTIVE && session.closedSnapshotJson != null)
        ) {
            fail(operation, ObservationQualificationEditorError.UNAVAILABLE, clearPrivate = true)
            return false
        }
        return isCurrent(operation)
    }

    private fun readBelongsToScope(
        read: ObservationQualificationRead,
        basis: ObservationMeasurementBasis,
        session: InventaireSession,
    ): Boolean {
        val stored = read.record.payload.basis
        return read.record.tigeId == basis.tigeId && stored.tigeId == basis.tigeId &&
            stored.parcelleId == basis.parcelleId && stored.placetteId == basis.placetteId &&
            stored.sessionId == basis.sessionId &&
            read.record.isSealed == (session.martelageState == MartelageStates.CLOSED) &&
            (read.status != ObservationQualificationStatus.CURRENT || stored == basis)
    }

    private fun begin(
        loading: Boolean,
        expectedState: ObservationQualificationEditorState? = null,
    ): Long? = synchronized(monitor) {
        if (!ensureAccess() || state.value.isLoading || state.value.isSaving) return@synchronized null
        if (expectedState != null && state.value !== expectedState) return@synchronized null
        val operation = ++operationSequence
        mutableState.value = state.value.copy(isLoading = loading, isSaving = !loading, error = null)
        operation
    }

    private fun operationGuard(operation: Long): () -> Boolean = {
        synchronized(monitor) { !revoked && operationSequence == operation && requestGuard() }
    }

    private fun ensureAccess(): Boolean = synchronized(monitor) {
        if (revoked) return@synchronized false
        if (!requestGuard()) {
            invalidate()
            return@synchronized false
        }
        true
    }

    private fun isCurrent(operation: Long): Boolean = synchronized(monitor) {
        ensureAccess() && operationSequence == operation
    }

    private fun publish(
        operation: Long,
        update: (ObservationQualificationEditorState) -> ObservationQualificationEditorState,
    ): Boolean = synchronized(monitor) {
        if (!isCurrent(operation)) return@synchronized false
        mutableState.value = update(state.value)
        true
    }

    private fun fail(
        operation: Long,
        error: ObservationQualificationEditorError,
        clearPrivate: Boolean = false,
        readOnly: Boolean? = null,
    ) {
        publish(operation) {
            it.copy(
                basis = it.basis.takeUnless { clearPrivate }, read = it.read.takeUnless { clearPrivate },
                readOnly = if (clearPrivate) true else readOnly ?: it.readOnly,
                isLoading = false, isSaving = false, error = error,
            )
        }
    }

    private fun handleFailure(operation: Long, failure: Exception, saving: Boolean) {
        if (!isCurrent(operation)) return
        when (failure.message) {
            "OBSERVATION_QUALIFICATION_OBSERVATION_CONFLICT", "OBSERVATION_QUALIFICATION_REVISION_CONFLICT" ->
                fail(operation, ObservationQualificationEditorError.CONFLICT)
            "OBSERVATION_QUALIFICATION_READ_ONLY" ->
                fail(operation, ObservationQualificationEditorError.UNAVAILABLE, readOnly = true)
            "OBSERVATION_QUALIFICATION_REQUEST_SCOPE_CHANGED", "OBSERVATION_QUALIFICATION_ACCESS_CHANGED",
            "OBSERVATION_QUALIFICATION_ACCESS_DENIED", "OBSERVATION_QUALIFICATION_ACCOUNT_CHANGED",
            "OBSERVATION_QUALIFICATION_SCOPE_CONFLICT", "OBSERVATION_QUALIFICATION_SCOPE_UNAVAILABLE",
            "OBSERVATION_QUALIFICATION_SEAL_STATE_MISMATCH", "OBSERVATION_UNAVAILABLE", "SESSION_UNAVAILABLE" ->
                fail(operation, ObservationQualificationEditorError.UNAVAILABLE, clearPrivate = true)
            else -> fail(operation,
                if (saving) ObservationQualificationEditorError.SAVE_FAILED else ObservationQualificationEditorError.UNAVAILABLE,
                clearPrivate = !saving,
            )
        }
    }

    private fun finishCancellation(operation: Long) {
        publish(operation) { it.copy(isLoading = false, isSaving = false) }
    }
}
