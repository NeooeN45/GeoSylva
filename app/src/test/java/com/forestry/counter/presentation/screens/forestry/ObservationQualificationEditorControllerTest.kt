package com.forestry.counter.presentation.screens.forestry

import com.forestry.counter.domain.model.InventaireSession
import com.forestry.counter.domain.model.MartelageStates
import com.forestry.counter.domain.model.MeasurementOrigin
import com.forestry.counter.domain.model.DiameterQualificationDraft
import com.forestry.counter.domain.model.ObservationMeasurementBasis
import com.forestry.counter.domain.model.ObservationQualificationDraft
import com.forestry.counter.domain.model.ObservationQualificationPayload
import com.forestry.counter.domain.model.ObservationQualificationRead
import com.forestry.counter.domain.model.ObservationQualificationRecord
import com.forestry.counter.domain.model.ObservationQualificationStatus
import com.forestry.counter.domain.model.ObservationSupport
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.repository.InventaireSessionRepository
import com.forestry.counter.domain.repository.ObservationQualificationRepository
import com.forestry.counter.domain.repository.TigeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Doubles de frontière JVM ; la transaction Room et les droits réels sont vérifiés séparément. */
@OptIn(ExperimentalCoroutinesApi::class)
// Reconstructed on 2026-10-15: missing fixture and test tails rebuilt from the recovered contracts.
class ObservationQualificationEditorControllerTest {
    @Test
    fun should_accept_missing_qualification_only_after_live_scope_and_exact_basis_reads() = runTest {
        val fixture = Fixture()
        val listed = checkNotNull(fixture.tree)

        assertTrue(fixture.controller.open(listed))

        assertEquals(ObservationMeasurementBasis.capture(listed), fixture.controller.state.value.basis)
        assertNull(fixture.controller.state.value.read)
        assertFalse(fixture.controller.state.value.readOnly)
        assertNull(fixture.controller.state.value.error)
        coVerify(exactly = 1) {
            fixture.qualifications.getQualificationForObservation(
                ObservationMeasurementBasis.capture(listed), match { it() },
            )
        }
        fixture.assertNoRawWrites()
    }

    @Test
    fun should_refuse_initial_scope_mismatch_or_deleted_observation_before_repository_calls() = runTest {
        val original = tree()
        listOf(
            original.copy(parcelleId = "another-parcel"), original.copy(placetteId = "another-plot"),
            original.copy(sessionId = "another-session"), original.copy(essenceCode = "another-species"),
            original.copy(deletedAt = 2),
        ).forEach { unauthorized ->
            val fixture = Fixture()
            assertFalse(fixture.controller.open(unauthorized))
            assertEquals(ObservationQualificationEditorError.UNAVAILABLE, fixture.controller.state.value.error)
            assertNull(fixture.controller.state.value.basis)
            verify(exactly = 0) { fixture.tiges.getTigeById(any()) }
            coVerify(exactly = 0) { fixture.qualifications.getQualificationForObservation(any(), any()) }
        }
    }

    @Test
    fun should_refuse_unknown_type_state_deleted_or_reparented_session() = runTest {
        val original = session()
        listOf(
            original.copy(typeSession = "LEGACY"), original.copy(martelageState = null),
            original.copy(parcelleId = "another-parcel"), original.copy(placetteId = "another-plot"),
            original.copy(sessionId = "another-session"), original.copy(deletedAt = 2),
            original.copy(closedSnapshotJson = "{}"),
        ).forEach { invalid ->
            val fixture = Fixture().apply { currentSession = invalid }
            assertFalse(fixture.controller.open(checkNotNull(fixture.tree)))
            assertEquals(ObservationQualificationEditorError.UNAVAILABLE, fixture.controller.state.value.error)
            assertNull(fixture.controller.state.value.basis)
            coVerify(exactly = 0) { fixture.qualifications.getQualificationForObservation(any(), any()) }
        }
    }

    @Test
    fun should_not_start_a_delayed_open_after_request_authorization_is_lost() = runTest {
        val fixture = Fixture()
        val pending = async(start = CoroutineStart.LAZY) { fixture.controller.open(checkNotNull(fixture.tree)) }
        fixture.authorized = false
        pending.start()

        assertFalse(pending.await())
        assertNull(fixture.controller.state.value.basis)
        assertNull(fixture.controller.state.value.read)
        verify(exactly = 0) { fixture.tiges.getTigeById(any()) }
    }

    @Test
    fun should_refuse_measurement_change_without_version_change_before_scoped_read() = runTest {
        val fixture = Fixture()
        val listed = checkNotNull(fixture.tree)
        fixture.tree = listed.copy(hauteurM = checkNotNull(listed.hauteurM) + 1)

        assertFalse(fixture.controller.open(listed))

        assertEquals(ObservationQualificationEditorError.CONFLICT, fixture.controller.state.value.error)
        assertNull(fixture.controller.state.value.read)
        coVerify(exactly = 0) { fixture.qualifications.getQualificationForObservation(any(), any()) }
    }

    @Test
    fun should_recheck_values_and_version_after_waiting_for_qualification() = runTest {
        val fixture = Fixture()
        val listed = checkNotNull(fixture.tree)
        val answer = CompletableDeferred<ObservationQualificationRead?>()
        coEvery { fixture.qualifications.getQualificationForObservation(any(), any()) } coAnswers { answer.await() }
        val pending = async { fixture.controller.open(listed) }
        runCurrent()
        fixture.tree = listed.copy(version = listed.version + 1)
        answer.complete(record(listed))

        assertFalse(pending.await())
        assertEquals(ObservationQualificationEditorError.CONFLICT, fixture.controller.state.value.error)
        assertNull(fixture.controller.state.value.read)
        fixture.assertNoRawWrites()
    }

    @Test
    fun should_erase_private_state_and_not_adopt_a_late_qualification_after_revocation() = runTest {
        val fixture = Fixture().apply { qualification = record(checkNotNull(tree)) }
        assertTrue(fixture.controller.open(checkNotNull(fixture.tree)))
        val answer = CompletableDeferred<ObservationQualificationRead?>()
        coEvery { fixture.qualifications.getQualificationForObservation(any(), any()) } coAnswers { answer.await() }
        val pending = async { fixture.controller.reload() }
        runCurrent()
        fixture.authorized = false
        answer.complete(fixture.qualification)

        assertFalse(pending.await())
        assertNull(fixture.controller.state.value.basis)
        assertNull(fixture.controller.state.value.read)
        assertTrue(fixture.controller.state.value.readOnly)
        assertEquals(0, fixture.controller.state.value.reloadCount)
        fixture.authorized = true
        assertFalse(fixture.controller.reload())
    }

    @Test
    fun should_reject_foreign_or_inconsistent_seal_record_without_exposing_it() = runTest {
        val listed = tree()
        listOf(
            record(listed.copy(parcelleId = "another-parcel")),
            record(listed, sealed = true),
            record(listed.copy(diamCm = listed.diamCm + 1)),
        ).forEach { wrongRecord ->
            val fixture = Fixture().apply { qualification = wrongRecord }
            assertFalse(fixture.controller.open(checkNotNull(fixture.tree)))
            assertNull(fixture.controller.state.value.read)
            assertNull(fixture.controller.state.value.basis)
            assertEquals(ObservationQualificationEditorError.UNAVAILABLE, fixture.controller.state.value.error)
        }
    }

    @Test
    fun should_allow_closed_consultation_without_allowing_save_even_when_qualification_is_absent() = runTest {
        listOf<ObservationQualificationRead?>(null, record(tree(), sealed = true)).forEach { read ->
            val fixture = Fixture().apply {
                currentSession = session().copy(martelageState = MartelageStates.CLOSED, closedSnapshotJson = "{}")
                qualification = read
            }
            assertTrue(fixture.controller.open(checkNotNull(fixture.tree)))
            assertTrue(fixture.controller.state.value.readOnly)
            assertEquals(read, fixture.controller.state.value.read)
            assertFalse(fixture.controller.save(draft()))
            coVerify(exactly = 0) { fixture.qualifications.saveQualificationForObservation(any(), any(), any(), any()) }
        }
    }

    @Test
    fun should_keep_paused_session_editable_and_create_only_declarative_metadata() = runTest {
        val fixture = Fixture().apply { currentSession = session().copy(martelageState = MartelageStates.PAUSED) }
        assertTrue(fixture.controller.open(checkNotNull(fixture.tree)))
        val capturedBasis = checkNotNull(fixture.controller.state.value.basis)
        assertFalse(fixture.controller.state.value.readOnly)

        assertTrue(fixture.controller.save(draft()))

        coVerify(exactly = 1) {
            fixture.qualifications.saveQualificationForObservation(capturedBasis, draft(), null, match { it() })
        }
        assertEquals(capturedBasis, fixture.controller.state.value.basis)
        assertEquals(1, fixture.controller.state.value.read?.record?.revision)
        assertEquals(0, fixture.controller.state.value.reloadCount)
        fixture.assertNoRawWrites()
    }

    @Test
    fun should_reuse_the_displayed_revision_without_a_fresh_qualification_read_on_save() = runTest {
        val fixture = Fixture().apply { qualification = record(checkNotNull(tree), revision = 7) }
        assertTrue(fixture.controller.open(checkNotNull(fixture.tree)))
        val displayedBasis = checkNotNull(fixture.controller.state.value.basis)

        assertTrue(fixture.controller.save(draft()))

        coVerify(exactly = 1) { fixture.qualifications.getQualificationForObservation(any(), any()) }
        coVerify(exactly = 1) {
            fixture.qualifications.saveQualificationForObservation(displayedBasis, draft(), 7, match { it() })
        }
        assertEquals(8, fixture.controller.state.value.read?.record?.revision)
    }

    @Test
    fun should_preserve_the_displayed_basis_and_record_when_raw_values_change_before_save() = runTest {
        val fixture = Fixture().apply { qualification = record(checkNotNull(tree)) }
        assertTrue(fixture.controller.open(checkNotNull(fixture.tree)))
        val displayed = fixture.controller.state.value
        fixture.tree = checkNotNull(fixture.tree).copy(gpsWkt = "POINT(3 4)")

        assertFalse(fixture.controller.save(draft()))

        assertSame(displayed.basis, fixture.controller.state.value.basis)
        assertSame(displayed.read, fixture.controller.state.value.read)
        assertEquals(ObservationQualificationEditorError.CONFLICT, fixture.controller.state.value.error)
        assertFalse(fixture.controller.save(draft()))
        coVerify(exactly = 0) { fixture.qualifications.saveQualificationForObservation(any(), any(), any(), any()) }
    }

    @Test
    fun should_preserve_cas_conflict_until_explicit_reload_without_merging_the_draft() = runTest {
        val fixture = Fixture().apply { qualification = record(checkNotNull(tree), revision = 3) }
        assertTrue(fixture.controller.open(checkNotNull(fixture.tree)))
        val displayed = fixture.controller.state.value
        val userDraft = draft()
        coEvery { fixture.qualifications.saveQualificationForObservation(any(), any(), any(), any()) } throws
            IllegalStateException("OBSERVATION_QUALIFICATION_REVISION_CONFLICT")
        fixture.qualification = record(checkNotNull(fixture.tree), revision = 4,
            draft = ObservationQualificationDraft(support = ObservationSupport.GROUP_REFERENCE))

        assertFalse(fixture.controller.save(userDraft))

        assertSame(displayed.basis, fixture.controller.state.value.basis)
        assertSame(displayed.read, fixture.controller.state.value.read)
        assertEquals(ObservationQualificationEditorError.CONFLICT, fixture.controller.state.value.error)
        assertEquals(0, fixture.controller.state.value.reloadCount)
        coVerify(exactly = 1) { fixture.qualifications.getQualificationForObservation(any(), any()) }
        assertTrue(fixture.controller.reload())
        assertEquals(1, fixture.controller.state.value.reloadCount)
        assertEquals(fixture.qualification, fixture.controller.state.value.read)
        assertNull(fixture.controller.state.value.error)
        assertEquals(ObservationSupport.INDIVIDUAL_TREE, userDraft.support)
    }

    @Test
    fun should_adopt_new_basis_only_after_explicit_reload_of_the_same_scope() = runTest {
        val fixture = Fixture().apply { qualification = record(checkNotNull(tree)) }
        assertTrue(fixture.controller.open(checkNotNull(fixture.tree)))
        val originalRead = checkNotNull(fixture.qualification)
        fixture.tree = checkNotNull(fixture.tree).copy(hauteurM = 31.0, precisionM = 12.0)
        assertFalse(fixture.controller.save(draft()))
        fixture.qualification = originalRead.copy(status = ObservationQualificationStatus.STALE)

        assertTrue(fixture.controller.reload())

        assertEquals(ObservationMeasurementBasis.capture(checkNotNull(fixture.tree)), fixture.controller.state.value.basis)
        assertSame(fixture.qualification, fixture.controller.state.value.read)
        assertEquals(1, fixture.controller.state.value.reloadCount)
        assertNull(fixture.controller.state.value.error)
        coVerify(exactly = 0) { fixture.qualifications.saveQualificationForObservation(any(), any(), any(), any()) }
    }

    @Test
    fun should_never_adopt_a_reparented_observation_or_session_during_reload() = runTest {
        val fixture = Fixture().apply { qualification = record(checkNotNull(tree)) }
        assertTrue(fixture.controller.open(checkNotNull(fixture.tree)))
        fixture.tree = checkNotNull(fixture.tree).copy(sessionId = "another-session")

        assertFalse(fixture.controller.reload())

        assertNull(fixture.controller.state.value.basis)
        assertNull(fixture.controller.state.value.read)
        assertEquals(0, fixture.controller.state.value.reloadCount)
        coVerify(exactly = 1) { fixture.qualifications.getQualificationForObservation(any(), any()) }
    }

    @Test
    fun should_clear_saved_view_state_when_the_observation_becomes_unavailable() = runTest {
        val fixture = Fixture().apply { qualification = record(checkNotNull(tree)) }
        assertTrue(fixture.controller.open(checkNotNull(fixture.tree)))
        fixture.tree = null

        assertFalse(fixture.controller.save(draft()))

        assertNull(fixture.controller.state.value.basis)
        assertNull(fixture.controller.state.value.read)
        assertEquals(ObservationQualificationEditorError.UNAVAILABLE, fixture.controller.state.value.error)
        coVerify(exactly = 0) { fixture.qualifications.saveQualificationForObservation(any(), any(), any(), any()) }
    }

    @Test
    fun should_refuse_a_delayed_save_call_if_the_initial_guard_has_been_revoked() = runTest {
        val fixture = Fixture().apply { qualification = record(checkNotNull(tree)) }
        assertTrue(fixture.controller.open(checkNotNull(fixture.tree)))
        val pending = async(start = CoroutineStart.LAZY) { fixture.controller.save(draft()) }
        fixture.authorized = false
        pending.start()

        assertFalse(pending.await())
        assertNull(fixture.controller.state.value.basis)
        assertNull(fixture.controller.state.value.read)
        coVerify(exactly = 0) { fixture.qualifications.saveQualificationForObservation(any(), any(), any(), any()) }
    }

    @Test
    fun should_recheck_authorization_after_waiting_for_session_before_save() = runTest {
        val fixture = Fixture().apply { qualification = record(checkNotNull(tree)) }
        assertTrue(fixture.controller.open(checkNotNull(fixture.tree)))
        val sessionAnswer = CompletableDeferred<InventaireSession?>()
        coEvery { fixture.sessions.getById(any()) } coAnswers { sessionAnswer.await() }
        val pending = async { fixture.controller.save(draft()) }
        runCurrent()
        fixture.authorized = false
        sessionAnswer.complete(session())

        assertFalse(pending.await())
        assertNull(fixture.controller.state.value.basis)
        assertNull(fixture.controller.state.value.read)
        coVerify(exactly = 0) { fixture.qualifications.saveQualificationForObservation(any(), any(), any(), any()) }
    }

    @Test
    fun should_not_acknowledge_a_late_save_when_request_authorization_changes() = runTest {
        val fixture = Fixture().apply { qualification = record(checkNotNull(tree)) }
        assertTrue(fixture.controller.open(checkNotNull(fixture.tree)))
        val saveAnswer = CompletableDeferred<ObservationQualificationRead>()
        coEvery { fixture.qualifications.saveQualificationForObservation(any(), any(), any(), any()) } coAnswers { saveAnswer.await() }
        val pending = async { fixture.controller.save(draft()) }
        runCurrent()
        fixture.authorized = false
        saveAnswer.complete(record(checkNotNull(fixture.tree), revision = 2, draft = draft()))

        assertFalse(pending.await())
        assertNull(fixture.controller.state.value.basis)
        assertNull(fixture.controller.state.value.read)
        assertFalse(fixture.controller.state.value.isSaving)
    }

    @Test
    fun should_not_acknowledge_a_saved_record_after_raw_mutation_without_version_increment() = runTest {
        val fixture = Fixture().apply { qualification = record(checkNotNull(tree)) }
        val listed = checkNotNull(fixture.tree)
        assertTrue(fixture.controller.open(listed))
        val displayed = fixture.controller.state.value
        val saveAnswer = CompletableDeferred<ObservationQualificationRead>()
        coEvery { fixture.qualifications.saveQualificationForObservation(any(), any(), any(), any()) } coAnswers { saveAnswer.await() }
        val pending = async { fixture.controller.save(draft()) }
        runCurrent()
        fixture.tree = listed.copy(diamCm = listed.diamCm + 1)
        saveAnswer.complete(record(listed, revision = 2, draft = draft()))

        assertFalse(pending.await())
        assertSame(displayed.basis, fixture.controller.state.value.basis)
        assertSame(displayed.read, fixture.controller.state.value.read)
        assertEquals(ObservationQualificationEditorError.CONFLICT, fixture.controller.state.value.error)
    }

    @Test
    fun should_stop_save_when_session_closes_and_require_explicit_reload_for_sealed_record() = runTest {
        val fixture = Fixture().apply { qualification = record(checkNotNull(tree)) }
        assertTrue(fixture.controller.open(checkNotNull(fixture.tree)))
        val displayed = fixture.controller.state.value
        fixture.currentSession = session().copy(martelageState = MartelageStates.CLOSED, closedSnapshotJson = "{}")

        assertFalse(fixture.controller.save(draft()))

        assertTrue(fixture.controller.state.value.readOnly)
        assertSame(displayed.read, fixture.controller.state.value.read)
        assertEquals(ObservationQualificationEditorError.UNAVAILABLE, fixture.controller.state.value.error)
        coVerify(exactly = 0) { fixture.qualifications.saveQualificationForObservation(any(), any(), any(), any()) }
        fixture.qualification = record(checkNotNull(fixture.tree), sealed = true)
        assertTrue(fixture.controller.reload())
        assertTrue(fixture.controller.state.value.readOnly)
        assertTrue(checkNotNull(fixture.controller.state.value.read).record.isSealed)
    }

    @Test
    fun should_not_report_success_if_session_closes_while_save_is_awaited() = runTest {
        val fixture = Fixture().apply { qualification = record(checkNotNull(tree)) }
        assertTrue(fixture.controller.open(checkNotNull(fixture.tree)))
        val answer = CompletableDeferred<ObservationQualificationRead>()
        coEvery { fixture.qualifications.saveQualificationForObservation(any(), any(), any(), any()) } coAnswers { answer.await() }
        val pending = async { fixture.controller.save(draft()) }
        runCurrent()
        fixture.currentSession = session().copy(martelageState = MartelageStates.CLOSED, closedSnapshotJson = "{}")
        answer.complete(record(checkNotNull(fixture.tree), revision = 2, draft = draft()))

        assertFalse(pending.await())
        assertTrue(fixture.controller.state.value.readOnly)
        assertEquals(ObservationQualificationEditorError.UNAVAILABLE, fixture.controller.state.value.error)
        assertEquals(1, checkNotNull(fixture.controller.state.value.read).record.revision)
    }

    @Test
    fun should_preserve_draft_basis_and_record_on_generic_save_failure() = runTest {
        val fixture = Fixture().apply { qualification = record(checkNotNull(tree)) }
        assertTrue(fixture.controller.open(checkNotNull(fixture.tree)))
        val displayed = fixture.controller.state.value
        coEvery { fixture.qualifications.saveQualificationForObservation(any(), any(), any(), any()) } throws IllegalStateException("JVM storage error")

        assertFalse(fixture.controller.save(draft()))

        assertSame(displayed.basis, fixture.controller.state.value.basis)
        assertSame(displayed.read, fixture.controller.state.value.read)
        assertEquals(ObservationQualificationEditorError.SAVE_FAILED, fixture.controller.state.value.error)
        assertFalse(fixture.controller.state.value.isSaving)
        coVerify(exactly = 1) { fixture.qualifications.saveQualificationForObservation(any(), any(), any(), any()) }
        fixture.assertNoRawWrites()
    }

    @Test
    fun should_propagate_cancellation_and_allow_a_new_explicit_open_when_still_authorized() = runTest {
        val fixture = Fixture()
        val answer = CompletableDeferred<ObservationQualificationRead?>()
        coEvery { fixture.qualifications.getQualificationForObservation(any(), any()) } coAnswers { answer.await() }
        val pending = async { fixture.controller.open(checkNotNull(fixture.tree)) }
        runCurrent()
        pending.cancel()
        pending.join()
        assertTrue(pending.isCancelled)
        assertFalse(fixture.controller.state.value.isLoading)
        assertNull(fixture.controller.state.value.read)

        // A cancelled Job alone cannot prove that the controller rethrows cancellation.
        val cancellation = kotlinx.coroutines.CancellationException("TEST repository cancellation")
        coEvery { fixture.qualifications.getQualificationForObservation(any(), any()) } throws cancellation
        try {
            fixture.controller.open(checkNotNull(fixture.tree))
            throw AssertionError("The controller must propagate repository cancellation")
        } catch (caught: kotlinx.coroutines.CancellationException) {
            assertSame(cancellation, caught)
        }
        assertFalse(fixture.controller.state.value.isLoading)
        assertNull(fixture.controller.state.value.read)

        coEvery { fixture.qualifications.getQualificationForObservation(any(), any()) } returns null
        assertTrue(fixture.controller.open(checkNotNull(fixture.tree)))
    }

    @Test
    fun should_propagate_save_cancellation_without_replacing_the_read_record() = runTest {
        val fixture = Fixture().apply { qualification = record(checkNotNull(tree)) }
        assertTrue(fixture.controller.open(checkNotNull(fixture.tree)))
        val displayed = fixture.controller.state.value
        val answer = CompletableDeferred<ObservationQualificationRead>()
        coEvery { fixture.qualifications.saveQualificationForObservation(any(), any(), any(), any()) } coAnswers { answer.await() }
        val pending = async { fixture.controller.save(draft()) }
        runCurrent()
        pending.cancel()
        pending.join()

        assertTrue(pending.isCancelled)
        assertFalse(fixture.controller.state.value.isSaving)
        assertSame(displayed.read, fixture.controller.state.value.read)
        assertNull(fixture.controller.state.value.error)

        val cancellation = kotlinx.coroutines.CancellationException("TEST repository cancellation")
        coEvery { fixture.qualifications.saveQualificationForObservation(any(), any(), any(), any()) } throws cancellation
        try {
            fixture.controller.save(draft())
            throw AssertionError("The controller must propagate repository cancellation")
        } catch (caught: kotlinx.coroutines.CancellationException) {
            assertSame(cancellation, caught)
        }
        assertFalse(fixture.controller.state.value.isSaving)
        assertSame(displayed.read, fixture.controller.state.value.read)
        assertNull(fixture.controller.state.value.error)
    }

    @Test
    fun should_serialize_save_and_reject_double_click_or_reload_while_saving() = runTest {
        val fixture = Fixture().apply { qualification = record(checkNotNull(tree)) }
        assertTrue(fixture.controller.open(checkNotNull(fixture.tree)))
        val answer = CompletableDeferred<ObservationQualificationRead>()
        coEvery { fixture.qualifications.saveQualificationForObservation(any(), any(), any(), any()) } coAnswers { answer.await() }
        val pending = async { fixture.controller.save(draft()) }
        runCurrent()

        assertFalse(fixture.controller.save(draft()))
        assertFalse(fixture.controller.reload())
        answer.complete(record(checkNotNull(fixture.tree), revision = 2, draft = draft()))
        assertTrue(pending.await())
        coVerify(exactly = 1) { fixture.qualifications.saveQualificationForObservation(any(), any(), any(), any()) }
    }

    @Test
    fun should_revoke_repository_request_guard_and_ignore_results_after_explicit_invalidation() = runTest {
        val fixture = Fixture().apply { qualification = record(checkNotNull(tree)) }
        assertTrue(fixture.controller.open(checkNotNull(fixture.tree)))
        val answer = CompletableDeferred<ObservationQualificationRead>()
        var scopedGuard: (() -> Boolean)? = null
        coEvery { fixture.qualifications.saveQualificationForObservation(any(), any(), any(), any()) } coAnswers {
            scopedGuard = arg(3)
            answer.await()
        }
        val pending = async { fixture.controller.save(draft()) }
        runCurrent()
        assertTrue(checkNotNull(scopedGuard).invoke())
        fixture.controller.invalidate()
        assertFalse(checkNotNull(scopedGuard).invoke())
        answer.complete(record(checkNotNull(fixture.tree), revision = 2, draft = draft()))

        assertFalse(pending.await())
        assertNull(fixture.controller.state.value.basis)
        assertNull(fixture.controller.state.value.read)
        assertFalse(fixture.controller.open(checkNotNull(fixture.tree)))
    }

    private class Fixture {
        var authorized = true
        var tree: Tige? = tree()
        var currentSession: InventaireSession? = session()
        var qualification: ObservationQualificationRead? = null
        val tiges = mockk<TigeRepository>()
        val sessions = mockk<InventaireSessionRepository>()
        val qualifications = mockk<ObservationQualificationRepository>()
        val controller = ObservationQualificationEditorController(
            tiges, qualifications, sessions,
            expectedParcelleId = "TEST-PARCEL", expectedPlacetteId = "TEST-PLOT",
            expectedSessionId = "TEST-SESSION", expectedEssenceCode = "TEST-OAK",
            requestGuard = { authorized },
        )

        init {
            every { tiges.getTigeById(any()) } answers {
                val requestedId = firstArg<String>()
                flow { emit(tree?.takeIf { it.id == requestedId }) }
            }
            coEvery { sessions.getById(any()) } coAnswers {
                check(firstArg<String>() == "TEST-SESSION")
                currentSession
            }
            coEvery { qualifications.getQualificationForObservation(any(), any()) } coAnswers { qualification }
            coEvery { qualifications.saveQualificationForObservation(any(), any(), any(), any()) } coAnswers {
                val basis = arg<ObservationMeasurementBasis>(0)
                val draft = arg<ObservationQualificationDraft>(1)
                val expectedRevision = arg<Int?>(2)
                val guard = arg<() -> Boolean>(3)
                check(guard()) { "OBSERVATION_QUALIFICATION_REQUEST_SCOPE_CHANGED" }
                check(basis.matches(checkNotNull(tree))) { "OBSERVATION_QUALIFICATION_OBSERVATION_CONFLICT" }
                check(expectedRevision == qualification?.record?.revision) { "OBSERVATION_QUALIFICATION_REVISION_CONFLICT" }
                record(checkNotNull(tree), (expectedRevision ?: 0) + 1, draft = draft).let {
                    qualification = it
                    it
                }
            }
        }

        fun assertNoRawWrites() {
            coVerify(exactly = 0) { tiges.insertTige(any()) }
            coVerify(exactly = 0) { tiges.insertTiges(any()) }
            coVerify(exactly = 0) { tiges.updateTige(any()) }
            coVerify(exactly = 0) { tiges.deleteTige(any()) }
            coVerify(exactly = 0) { tiges.setTigeHeight(any(), any()) }
            coVerify(exactly = 0) { tiges.setTigeGps(any(), any(), any(), any()) }
            coVerify(exactly = 0) { qualifications.saveQualification(any(), any(), any(), any()) }
            coVerify(exactly = 0) { qualifications.getQualification(any()) }
        }
    }

    companion object {
        private fun tree() = Tige(
            id = "TEST-TREE", parcelleId = "TEST-PARCEL", placetteId = "TEST-PLOT",
            essenceCode = "TEST-OAK", diamCm = 40.0, hauteurM = 20.0,
            gpsWkt = "POINT(0 0)", precisionM = 3.0, altitudeM = 100.0,
            timestamp = 1000L, note = null, produit = null, fCoef = null,
            valueEur = null, sessionId = "TEST-SESSION", version = 1,
        )

        private fun session() = InventaireSession(
            sessionId = "TEST-SESSION", parcelleId = "TEST-PARCEL", placetteId = "TEST-PLOT",
            typeSession = MartelageStates.TYPE, dateDebut = 1000L, dateFin = null,
            operateurNom = "TEST operator", methode = null, intensiteEchantillonnagePct = null,
            objectifSession = null, remarques = null, createdAt = 1000L,
            martelageState = MartelageStates.RUNNING,
        )

        private fun draft() = ObservationQualificationDraft(
            support = ObservationSupport.INDIVIDUAL_TREE,
            diameter = DiameterQualificationDraft(
                origin = MeasurementOrigin.MEASURED,
                conventionReference = "TEST explicit diameter convention",
                sourceReference = "TEST diameter observation",
            ),
        )

        private fun record(
            tree: Tige,
            revision: Int = 1,
            sealed: Boolean = false,
            draft: ObservationQualificationDraft = ObservationQualificationDraft(),
        ) = ObservationQualificationRead(
            record = ObservationQualificationRecord(
                tigeId = tree.id, revision = revision,
                payload = ObservationQualificationPayload(
                    basis = ObservationMeasurementBasis.capture(tree),
                    draft = draft,
                ),
                createdAt = 1001L, createdBy = "TEST-AUTHOR",
                updatedAt = 1002L, updatedBy = "TEST-AUTHOR", isSealed = sealed,
            ),
            status = ObservationQualificationStatus.CURRENT,
        )
    }
}
