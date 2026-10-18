package com.forestry.counter.data.sync

import com.forestry.counter.data.local.dao.ParcelleDao
import com.forestry.counter.data.local.dao.ParcelSyncDao
import com.forestry.counter.data.local.dao.ParcelSyncOperationDao
import com.forestry.counter.data.local.entity.ParcelleEntity
import com.forestry.counter.data.local.entity.ParcelSyncEntity
import com.forestry.counter.data.local.entity.ParcelSyncOperationEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Account binding and immutable-request planning. Real rollback/reopen is exercised by the Room suite. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ParcelMutationJournalTest {
    @Test
    fun unassociated_folder_stays_local_even_when_a_session_exists() = runTest {
        val f = Fixture(bindings = emptyList(), account = A)
        f.mutate("Local only")
        val operation = f.operations.single()
        assertNull(operation.originAccountId)
        assertNull(operation.destinationKind)
        assertNull(operation.expectedServerVersion)
        assertEquals("LOCAL_ONLY", operation.state)
        assertEquals("Local only", ParcelMutationJournal.upsertRequest(operation).parcel.name)
        coVerify(exactly = 0) { f.checkpoints.insertIfAbsent(any()) }
    }

    @Test
    fun logout_refuses_edits_of_previously_consented_dossiers() = runTest {
        val f = Fixture(account = null)
        val failure = runCatching { f.mutate("Logged out change") }.exceptionOrNull()
        assertEquals("PARCEL_LINKED_TO_ANOTHER_ACCOUNT", failure?.message)
        assertEquals("Original", f.parcel.name)
        assertTrue(f.operations.isEmpty())
        assertEquals(0, f.scheduled)
    }

    @Test
    fun offline_network_with_the_same_authenticated_account_retains_its_consented_destination() = runTest {
        val f = Fixture(account = A)
        f.mutate("Offline field change")
        val operation = f.operations.single()
        assertEquals(A, operation.originAccountId)
        assertEquals(4, operation.baseVersion)
        assertEquals(5, operation.expectedServerVersion)
        assertEquals("PENDING", operation.state)
    }

    @Test
    fun hierarchy_only_links_refuse_foreign_and_logged_out_actors_without_creating_a_leaf_journal() = runTest {
        for (actor in listOf(B, null)) {
            val f = Fixture(bindings = emptyList(), account = actor, hierarchyOwners = listOf(A))
            assertEquals("PARCEL_LINKED_TO_ANOTHER_ACCOUNT", runCatching { f.mutate("Refused") }.exceptionOrNull()?.message)
            assertEquals("Original", f.parcel.name)
            assertTrue(f.operations.isEmpty())
        }
        val allowed = Fixture(bindings = emptyList(), account = A, hierarchyOwners = listOf(A))
        allowed.mutate("Own forest")
        assertEquals("LOCAL_ONLY", allowed.operations.single().state)
        assertNull(allowed.operations.single().originAccountId)
    }

    @Test
    fun guest_can_edit_an_unlinked_local_dossier() = runTest {
        val f = Fixture(bindings = emptyList(), account = null)
        f.mutate("Guest field data")
        assertEquals("LOCAL_ONLY", f.operations.single().state)
        assertEquals("Guest field data", f.parcel.name)
    }

    @Test
    fun destination_forest_ownership_is_checked_before_moving_or_creating_a_parcel() = runTest {
        val f = Fixture(bindings = emptyList(), account = A)
        f.destinationOwners["forest-B"] = listOf(B)
        var written = false
        val failure = runCatching {
            f.journal.mutate(ID, "UPSERT", "forest-B") { written = true }
        }.exceptionOrNull()
        assertEquals("PARCEL_LINKED_TO_ANOTHER_ACCOUNT", failure?.message)
        assertFalse(written)
        assertTrue(f.operations.isEmpty())
    }

    @Test
    fun foreign_active_account_is_rejected_before_any_business_write_or_append() = runTest {
        val f = Fixture(account = B)
        var written = false
        val failure = runCatching { f.journal.mutate(ID, "UPSERT") { written = true } }.exceptionOrNull()
        assertEquals("PARCEL_LINKED_TO_ANOTHER_ACCOUNT", failure?.message)
        assertFalse(written)
        assertTrue(f.operations.isEmpty())
        assertEquals(0, f.scheduled)
    }

    @Test
    fun account_switch_while_business_write_is_suspended_never_retargets_to_b() = runTest {
        val f = Fixture(account = A)
        val writing = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val pending = async {
            runCatching { f.journal.mutate(ID, "UPSERT") {
                writing.complete(Unit)
                resume.await()
                f.parcel = f.parcel.copy(name = "Captured by A", updatedAt = 200L)
            } }
        }
        writing.await()
        f.activeAccount = B
        resume.complete(Unit)
        assertEquals("MUTATION_SESSION_CHANGED", pending.await().exceptionOrNull()?.message)
        assertTrue(f.operations.isEmpty())
        assertEquals("Original", f.parcel.name)
        assertEquals(0, f.scheduled)
    }

    @Test
    fun second_change_retains_the_inflight_request_and_plans_a_distinct_successor() = runTest {
        val f = Fixture()
        f.mutate("First")
        val first = f.operations.single().copy(state = "SYNCING", attemptId = "lease-one", lastAttemptAt = 300L)
        f.operations[0] = first
        f.mutate("Second")
        val second = f.operations[1]
        assertEquals(first, f.operations[0])
        assertTrue(first.operationId != second.operationId)
        assertEquals("First", ParcelMutationJournal.upsertRequest(first).parcel.name)
        assertEquals("Second", ParcelMutationJournal.upsertRequest(second).parcel.name)
        assertEquals(5, second.baseVersion)
        assertEquals(6, second.expectedServerVersion)
        assertEquals(first.operationId, second.predecessorOperationId)
    }

    @Test
    fun newer_pull_checkpoint_is_used_after_the_last_journal_operation_was_acknowledged() = runTest {
        val f = Fixture()
        f.operations += ParcelMutationJournal.freeze(f.parcel, "UPSERT", A, ORIGIN, 4, null, "SYNCED", 100L, UUID.randomUUID().toString())
            .copy(sequence = 1, ackVersion = 5, ackAt = 150L)
        f.checkpoint = f.checkpoint.copy(serverVersion = 8)
        f.mutate("After server update")
        val operation = f.operations.last()
        assertEquals(8, operation.baseVersion)
        assertEquals(9, operation.expectedServerVersion)
        assertNull(operation.predecessorOperationId)
    }

    @Test
    fun changed_configuration_does_not_retarget_an_existing_origin_chain() = runTest {
        val f = Fixture(configuredOrigin = "https://other-api.invalid")
        f.operations += ParcelMutationJournal.freeze(f.parcel, "UPSERT", A, ORIGIN, 4, null, "SYNCED", 100L, UUID.randomUUID().toString())
            .copy(sequence = 1, ackVersion = 5, ackAt = 150L)
        f.checkpoint = f.checkpoint.copy(serverVersion = 5)
        f.mutate("Still bound to original API")
        val operation = f.operations.last()
        assertEquals(ORIGIN, operation.apiOrigin)
        assertEquals(5, operation.baseVersion)
        assertEquals(f.operations.first().operationId, operation.predecessorOperationId)
    }

    @Test
    fun legacy_unknown_request_is_preserved_and_new_local_change_is_blocked_for_explicit_recovery() = runTest {
        val f = Fixture()
        val originalLegacy = f.checkpoint.copy(state = "PENDING", operationId = "legacy-original-id")
        f.checkpoint = originalLegacy
        f.mutate("Newer local version")
        val operation = f.operations.single()
        assertEquals("BLOCKED_LEGACY", operation.state)
        assertEquals(originalLegacy, f.checkpoint)
        assertTrue(operation.operationId != originalLegacy.operationId)
        assertEquals("Newer local version", ParcelMutationJournal.upsertRequest(operation).parcel.name)
    }

    @Test
    fun conflict_and_permanent_failure_keep_later_snapshots_without_rebasing_or_sending_them() = runTest {
        for (state in listOf("CONFLICT", "ERROR")) {
            val f = Fixture()
            val blocked = ParcelMutationJournal.freeze(f.parcel, "UPSERT", A, ORIGIN, 4, null, state, 100L, UUID.randomUUID().toString())
                .copy(sequence = 1, nextAttemptAt = Long.MAX_VALUE)
            f.operations += blocked
            f.mutate("Retained after $state")
            assertEquals(blocked, f.operations.first())
            assertEquals("BLOCKED_PREDECESSOR", f.operations.last().state)
            assertEquals(blocked.operationId, f.operations.last().predecessorOperationId)
            assertEquals("Retained after $state", ParcelMutationJournal.upsertRequest(f.operations.last()).parcel.name)
        }
    }

    @Test
    fun deleted_request_never_changes_operation_id_or_becomes_an_upsert() = runTest {
        val f = Fixture(account = A)
        f.journal.mutate(ID, "DELETE") { f.parcel = f.parcel.copy(deletedAt = 200L) }
        val operation = f.operations.single()
        val request = ParcelMutationJournal.deleteRequest(operation)
        assertEquals("DELETE", operation.operation)
        assertEquals(operation.operationId, request.operationId)
        assertEquals(4, request.baseVersion)
        assertEquals(A, operation.originAccountId)
        val savedJson = operation.requestJson
        f.parcel = f.parcel.copy(name = "Restored afterward", deletedAt = null)
        assertEquals(savedJson, operation.requestJson)
        assertEquals(request, ParcelMutationJournal.deleteRequest(operation))
    }

    @Test
    fun append_failure_propagates_and_never_schedules_postcommit_work() = runTest {
        val f = Fixture()
        coEvery { f.operationDao.append(any()) } throws IllegalStateException("journal-full")
        val failure = runCatching { f.mutate("Cannot commit") }.exceptionOrNull()
        assertEquals("journal-full", failure?.message)
        assertEquals(0, f.scheduled)
        assertEquals("Original", f.parcel.name)
        assertTrue(f.operations.isEmpty())
        // The Room suite separately proves this behavior against actual SQLite transactions.
    }

    @Test
    fun cancellation_is_not_swallowed_as_a_successful_business_commit() = runTest {
        val f = Fixture()
        val cancellation = CancellationException("cancel synthetic transaction")
        val failure = runCatching { f.journal.mutate(ID, "UPSERT") { throw cancellation } }.exceptionOrNull()
        assertSame(cancellation, failure)
        assertTrue(f.operations.isEmpty())
        assertEquals(0, f.scheduled)
    }

    private class Fixture(
        val bindings: List<String> = listOf(A),
        account: String? = A,
        configuredOrigin: String = ORIGIN,
        val hierarchyOwners: List<String> = emptyList(),
    ) {
        val parcels = mockk<ParcelleDao>()
        val checkpoints = mockk<ParcelSyncDao>()
        val operationDao = mockk<ParcelSyncOperationDao>()
        val operations = mutableListOf<ParcelSyncOperationEntity>()
        val destinationOwners = mutableMapOf<String, List<String>>()
        var activeAccount = account
        var providerReads = 0
        var scheduled = 0
        var parcel = newParcel()
        var checkpoint = ParcelSyncEntity(A, ID, "UPSERT", "old-checkpoint", "SYNCED", 4, 0, 100L, null, 100L, 100L, null)
        val journal = ParcelMutationJournal(
            parcels, checkpoints, operationDao, configuredOrigin,
            { providerReads += 1; activeAccount }, { scheduled += 1 }, { 300L }, { UUID.randomUUID().toString() },
        )

        init {
            coEvery { operationDao.runJournalTransaction(any()) } coAnswers {
                val beforeParcel = parcel
                val beforeOperations = operations.toList()
                try { firstArg<suspend () -> Unit>().invoke() } catch (error: Throwable) {
                    parcel = beforeParcel
                    operations.clear(); operations.addAll(beforeOperations)
                    throw error
                }
            }
            coEvery { checkpoints.getAccountIdsForParcel(any()) } returns bindings
            coEvery { checkpoints.getHierarchyAccountsForParcel(any()) } returns hierarchyOwners
            coEvery { checkpoints.getHierarchyAccountsForForest(any()) } coAnswers { destinationOwners[firstArg<String>()].orEmpty() }
            coEvery { checkpoints.get(any(), any()) } coAnswers { checkpoint }
            coEvery { parcels.getParcelleByIdAny(any()) } coAnswers { parcel }
            coEvery { operationDao.getLastActiveForParcel(any(), any()) } coAnswers {
                operations.lastOrNull { it.originAccountId == firstArg<String>() && it.state !in setOf("LOCAL_ONLY", "SUPERSEDED") }
            }
            coEvery { operationDao.append(any()) } coAnswers {
                val operation = firstArg<ParcelSyncOperationEntity>().copy(sequence = operations.size.toLong() + 1)
                operations += operation
                operation.sequence
            }
        }

        suspend fun mutate(name: String) = journal.mutate(ID, "UPSERT") {
            parcel = parcel.copy(name = name, updatedAt = parcel.updatedAt + 1)
        }
    }

    private companion object {
        const val A = "account-A"
        const val B = "account-B"
        const val ID = "parcel-1"
        const val ORIGIN = "https://gsie.invalid"
        fun newParcel() = ParcelleEntity(
            parcelleId = ID, forestOwnerId = null, foretId = null, name = "Original", surfaceHa = 2.4,
            shape = null, slopePct = null, aspect = null, access = null, altitudeM = null,
            objectifType = null, objectifVal = null, tolerancePct = null, samplingMode = null,
            sampleAreaM2 = null, targetSpeciesCsv = null, srid = null, remarks = "Private field data",
            codeInseeCommune = null, nomCommune = null, sectionCadastrale = null, numeroCadastral = null,
            contenanceCadastraleHa = null, geometrieIgnWkt = null, natureCadastraleCode = null,
            localisationMode = null, codeSer = null, nomSer = null, createdAt = 100L, updatedAt = 100L,
        )
    }
}
