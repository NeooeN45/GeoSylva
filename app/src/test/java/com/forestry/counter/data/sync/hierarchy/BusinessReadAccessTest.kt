package com.forestry.counter.data.sync.hierarchy

import com.forestry.counter.data.local.dao.*
import com.forestry.counter.data.local.entity.BusinessReadOwner
import com.forestry.counter.data.local.entity.BusinessReadParcelParent
import com.forestry.counter.data.mapper.toEntity
import com.forestry.counter.data.mapper.toParcelleEntity
import com.forestry.counter.data.repository.ForetRepositoryImpl
import com.forestry.counter.data.repository.ParcelleRepositoryImpl
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.data.sync.ParcelMutationJournal
import com.forestry.counter.domain.model.AccountSession
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.IdentityProvider
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.domain.repository.IdentitySessionGuard
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BusinessReadAccessTest {
    @Test fun explicit_owners_allow_only_the_same_actor_and_never_infer_ownership_from_local_authors() = runTest {
        val f = Fixture()
        f.owners.value = listOf(BusinessReadOwner("FOREST", "forest-A", "A"))
        f.parents.value = listOf(BusinessReadParcelParent("parcel-A", "forest-A"))
        for (actor in listOf("A", "B", null)) {
            f.login(actor)
            val access = f.access.capture()
            assertEquals(actor == "A", f.access.canReadForest("forest-A", access))
            assertEquals(actor == "A", f.access.canReadParcelle("parcel-A", access))
            assertTrue(f.access.canReadForest("unlinked-forest", access))
            assertTrue(f.access.canReadParcelle("unlinked-parcel", access))
            assertTrue(f.access.revalidate(access))
        }
        f.login("A")
        f.owners.value += BusinessReadOwner("FOREST", "forest-A", "B")
        assertFalse(f.access.canReadForest("forest-A"))
        assertFalse(f.access.canReadParcelle("parcel-A"))
    }

    @Test fun reactive_results_clear_on_B_and_logout_without_a_business_table_write() = runTest {
        val f = Fixture()
        f.owners.value = listOf(BusinessReadOwner("PARCEL", "parcel-A", "A"))
        f.login("A")
        val source = MutableStateFlow(listOf("parcel-A", "local"))
        val values = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            f.access.observe(source, emptyList()) { rows, access -> rows.filter { f.access.canReadParcelle(it, access) } }.collect { values += it }
        }
        runCurrent(); assertEquals(listOf("parcel-A", "local"), values.last())
        f.login("B"); runCurrent(); assertEquals(listOf("local"), values.last())
        f.login(null); runCurrent(); assertEquals(listOf("local"), values.last())
        f.login("A"); runCurrent(); assertEquals(listOf("parcel-A", "local"), values.last())
        assertEquals(listOf("parcel-A", "local"), source.value)
    }

    @Test fun association_and_parent_changes_filter_an_unchanged_child_source() = runTest {
        val f = Fixture()
        f.login("B")
        f.access.startObserving(backgroundScope)
        val source = MutableStateFlow(listOf("parcel"))
        val values = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            f.access.observe(source, emptyList()) { rows, access -> rows.filter { f.access.canReadParcelle(it, access) } }.collect { values += it }
        }
        runCurrent(); assertEquals(listOf("parcel"), values.last())
        val before = f.access.capture()
        f.owners.value = listOf(BusinessReadOwner("FOREST", "forest-A", "A"))
        f.parents.value = listOf(BusinessReadParcelParent("parcel", "forest-A"))
        runCurrent(); assertEquals(emptyList<String>(), values.last())
        assertFalse(f.access.isCurrent(before))
        f.parents.value = listOf(BusinessReadParcelParent("parcel", null))
        runCurrent(); assertEquals(listOf("parcel"), values.last())
    }

    @Test fun a_late_filter_result_cannot_republish_A_after_B_enters() = runTest {
        val f = Fixture()
        f.login("A")
        f.owners.value = listOf(BusinessReadOwner("PARCEL", "parcel-A", "A"))
        val waiting = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val values = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            f.access.observe(MutableStateFlow(listOf("parcel-A")), emptyList()) { rows, access ->
                if (access.accountId == "A") { started.complete(Unit); waiting.await() }
                rows.filter { f.access.canReadParcelle(it, access) }
            }.collect { values += it }
        }
        started.await()
        f.login("B"); runCurrent(); waiting.complete(Unit); runCurrent()
        assertTrue(values.all { it.isEmpty() })
    }

    @Test fun authoritative_revalidation_refuses_new_ownership_even_before_the_observer_runs() = runTest {
        val f = Fixture()
        f.login("A")
        val captured = f.access.capture()
        assertTrue(f.access.canReadParcelle("local", captured))
        f.owners.value = listOf(BusinessReadOwner("PARCEL", "local", "B"))
        assertFalse(f.access.revalidate(captured))
    }

    @Test fun same_actor_token_generation_changes_invalidate_old_guards_even_with_equal_session_metadata() = runTest {
        val f = Fixture()
        f.login("A")
        val before = f.access.capture()
        val session = f.sessions.value
        f.login("A")
        assertEquals(session, f.sessions.value)
        assertFalse(f.access.isCurrent(before))
        assertFalse(f.access.revalidate(before))
        assertTrue(f.access.revalidate(f.access.capture()))
    }

    @Test fun equal_A_reauthentication_clears_cached_results_via_the_independent_session_generation() = runTest {
        val f = Fixture()
        f.login("A")
        f.owners.value = listOf(BusinessReadOwner("PARCEL", "parcel-A", "A"))
        val values = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            f.access.observe(MutableStateFlow(listOf("parcel-A")), emptyList()) { rows, access ->
                rows.filter { f.access.canReadParcelle(it, access) }
            }.collect { values += it }
        }
        runCurrent(); assertEquals(listOf("parcel-A"), values.last())
        val count = values.size
        val old = f.access.capture()
        val metadata = f.sessions.value
        f.login("A"); runCurrent()
        assertEquals(metadata, f.sessions.value)
        assertTrue(values.drop(count).any { it.isEmpty() })
        assertEquals(listOf("parcel-A"), values.last())
        assertFalse(f.access.isCurrent(old))
    }

    @Test fun forest_suspended_lookup_rejects_contacts_when_the_actor_changes() = runTest {
        val f = Fixture()
        f.login("A")
        f.owners.value = listOf(BusinessReadOwner("FOREST", "forest-A", "A"))
        val dao = mockk<ForetDao>()
        val response = CompletableDeferred<com.forestry.counter.data.local.entity.ForetEntity?>()
        coEvery { dao.getById("forest-A") } coAnswers { response.await() }
        val repo = ForetRepositoryImpl(dao, MetadataService { f.sessions.value?.accountId }, f.dao,
            mockk<ParcelSyncOperationDao>(), readAccess = f.access)
        val pending = async { repo.getById("forest-A") }
        runCurrent(); f.login("B"); response.complete(forest().toEntity())
        assertNull(pending.await())
    }

    @Test fun parcel_repository_by_id_flow_masks_linked_data_but_keeps_unlinked_local_rows() = runTest {
        val f = Fixture()
        f.login("A")
        f.owners.value = listOf(BusinessReadOwner("PARCEL", "parcel-A", "A"))
        val dao = mockk<ParcelleDao>()
        val source = MutableStateFlow<com.forestry.counter.data.local.entity.ParcelleEntity?>(parcel().toParcelleEntity())
        every { dao.getParcelleByIdFlow("parcel-A") } returns source
        val repo = ParcelleRepositoryImpl(dao, MetadataService { f.sessions.value?.accountId }, mockk<ParcelMutationJournal>(),
            mockk<TigeDao>(), mockk<PlacetteDao>(), f.access)
        val values = mutableListOf<Parcelle?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.getParcelleById("parcel-A").collect { values += it } }
        runCurrent(); assertEquals("Private parcel", values.last()?.name)
        f.login("B"); runCurrent(); assertNull(values.last())
        f.owners.value = emptyList(); runCurrent(); assertEquals("Private parcel", values.last()?.name)
    }

    @Test fun first_on_an_unlinked_local_row_returns_the_row_for_guest_A_and_B() = runTest {
        val f = Fixture()
        for (actor in listOf(null, "A", "B")) {
            f.login(actor)
            val result = f.access.observe(MutableStateFlow<String?>("local-parcel"), null) { row, access ->
                row?.takeIf { f.access.canReadParcelle(it, access) }
            }.first()
            assertEquals("local-parcel", result)
        }
    }

    @Test fun first_on_linked_data_returns_the_owned_value_or_an_actual_masked_result() = runTest {
        val f = Fixture()
        f.owners.value = listOf(BusinessReadOwner("PARCEL", "parcel-A", "A"))
        for (actor in listOf("A", "B", null)) {
            f.login(actor)
            val result = f.access.observe(MutableStateFlow(listOf("parcel-A", "local")), emptyList()) { rows, access ->
                rows.filter { f.access.canReadParcelle(it, access) }
            }.first()
            assertEquals(if (actor == "A") listOf("parcel-A", "local") else listOf("local"), result)
        }
    }

    @Test fun first_on_the_public_parcel_repository_does_not_confuse_loading_with_missing_parent() = runTest {
        val f = Fixture()
        val dao = mockk<ParcelleDao>()
        every { dao.getParcelleByIdFlow("parcel-A") } returns MutableStateFlow(parcel().toParcelleEntity())
        val repo = ParcelleRepositoryImpl(dao, MetadataService { f.sessions.value?.accountId }, mockk<ParcelMutationJournal>(),
            mockk<TigeDao>(), mockk<PlacetteDao>(), f.access)
        assertEquals("Private parcel", repo.getParcelleById("parcel-A").first()?.name)
    }

    @Test fun first_refuses_an_A_result_when_B_enters_during_the_initial_filter() = runTest {
        val f = Fixture()
        f.login("A")
        f.owners.value = listOf(BusinessReadOwner("PARCEL", "parcel-A", "A"))
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val reading = async {
            f.access.observe(MutableStateFlow<String?>("parcel-A"), null) { row, access ->
                if (access.accountId == "A") { started.complete(Unit); resume.await() }
                row?.takeIf { f.access.canReadParcelle(it, access) }
            }.first()
        }
        started.await()
        assertFalse(reading.isCompleted)
        f.login("B"); runCurrent()
        assertNull(reading.await())
        resume.complete(Unit)
    }

    @Test fun first_refuses_the_initial_generation_after_an_equal_A_reauthentication() = runTest {
        val f = Fixture()
        f.login("A")
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val reading = async {
            f.access.observe(MutableStateFlow<String?>("local"), null) { row, _ ->
                started.complete(Unit); resume.await(); row
            }.first()
        }
        started.await()
        val originalSession = f.sessions.value
        f.login("A"); runCurrent()
        assertEquals(originalSession, f.sessions.value)
        assertNull(reading.await())
        resume.complete(Unit)
    }

    @Test fun first_refuses_a_new_foreign_association_during_the_initial_filter() = runTest {
        val f = Fixture()
        f.login("A")
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val reading = async {
            f.access.observe(MutableStateFlow<String?>("local"), null) { row, access ->
                if (access.owners.isEmpty()) { started.complete(Unit); resume.await() }
                row?.takeIf { f.access.canReadParcelle(it, access) }
            }.first()
        }
        started.await()
        f.owners.value = listOf(BusinessReadOwner("PARCEL", "local", "B")); runCurrent()
        assertNull(reading.await())
        resume.complete(Unit)
    }

    @Test fun stale_and_mixed_Room_notifications_never_invalidate_an_authorized_canonical_first_result() = runTest {
        val f = Fixture()
        f.login("A")
        val sqlOwners = listOf(BusinessReadOwner("FOREST", "forest-A", "A"))
        val sqlParents = listOf(BusinessReadParcelParent("parcel-A", "forest-A"))
        coEvery { f.dao.getBusinessReadOwners() } returns sqlOwners
        coEvery { f.dao.getBusinessReadParcelParents() } returns sqlParents
        val stable = f.access.capture()
        // Simulate independently delayed owners and parents query deliveries; SQL is unchanged.
        repeat(4) { index ->
            f.owners.value = if (index % 2 == 0) emptyList() else listOf(BusinessReadOwner("FOREST", "forest-A", "B"))
            f.parents.value = listOf(BusinessReadParcelParent("parcel-A", if (index % 2 == 0) "old-forest" else null))
            val row = f.access.observe(MutableStateFlow<String?>("parcel-A"), null) { value, access ->
                value?.takeIf { f.access.canReadParcelle(it, access) }
            }.first()
            assertEquals("parcel-A", row)
            assertTrue(f.access.isCurrent(stable))
            assertTrue(f.access.revalidate(stable))
        }
    }

    @Test fun canonical_epoch_wakes_a_cached_reader_even_when_its_Room_notification_flows_are_quiet() = runTest {
        val f = Fixture()
        f.login("A")
        var sqlOwners = emptyList<BusinessReadOwner>()
        coEvery { f.dao.getBusinessReadOwners() } coAnswers { sqlOwners }
        every { f.dao.observeBusinessReadOwners() } returns flow { emit(emptyList()); awaitCancellation() }
        every { f.dao.observeBusinessReadParcelParents() } returns flow { emit(emptyList()); awaitCancellation() }
        val values = mutableListOf<String?>()
        val guards = mutableListOf<BusinessReadAccess.AccessSnapshot>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            f.access.observe(MutableStateFlow<String?>("parcel-A"), null) { row, access ->
                guards += access
                row?.takeIf { f.access.canReadParcelle(it, access) }
            }.collect { values += it }
        }
        runCurrent()
        assertEquals("parcel-A", values.last())
        val initial = guards.last()
        sqlOwners = listOf(BusinessReadOwner("PARCEL", "parcel-A", "B"))
        f.access.capture() // Another reader discovers the committed authority transition.
        runCurrent()
        assertNull(values.last())
        assertFalse(f.access.isCurrent(initial))
        sqlOwners = listOf(BusinessReadOwner("PARCEL", "parcel-A", "A"))
        f.access.capture()
        runCurrent()
        assertEquals("parcel-A", values.last())
        assertTrue(f.access.isCurrent(guards.last()))
        assertFalse(f.access.isCurrent(initial))
    }

    @Test fun observed_A_B_A_authority_never_rehabilitates_the_original_snapshot() = runTest {
        val f = Fixture()
        f.login("A")
        f.owners.value = listOf(BusinessReadOwner("PARCEL", "parcel-A", "A"))
        val original = f.access.capture()
        f.owners.value = listOf(BusinessReadOwner("PARCEL", "parcel-A", "B"))
        val revoked = f.access.capture()
        assertFalse(f.access.canReadParcelle("parcel-A", revoked))
        f.owners.value = listOf(BusinessReadOwner("PARCEL", "parcel-A", "A"))
        val restored = f.access.capture()
        assertTrue(f.access.canReadParcelle("parcel-A", restored))
        assertFalse(f.access.isCurrent(original))
        assertFalse(f.access.revalidate(original))
        assertFalse(f.access.isCurrent(revoked))
    }

    @Test fun source_scoped_copy_validation_keeps_the_original_guard_and_ignores_only_unrelated_new_ids() = runTest {
        val f = Fixture()
        f.login("A")
        f.owners.value = listOf(BusinessReadOwner("FOREST", "forest-A", "A"))
        f.parents.value = listOf(BusinessReadParcelParent("parcel-A", "forest-A"))
        val source = f.access.capture()
        f.parents.value += BusinessReadParcelParent("new-unrelated-parcel", "new-unrelated-forest")
        assertTrue(f.access.revalidateSources(source, setOf("forest-A"), setOf("parcel-A")))
        assertTrue(f.access.isCurrent(source))
        f.parents.value = listOf(BusinessReadParcelParent("parcel-A", "foreign-forest"))
        assertFalse(f.access.revalidateSources(source, setOf("forest-A"), setOf("parcel-A")))
        f.login("A")
        assertFalse(f.access.revalidateSources(source, setOf("forest-A"), setOf("parcel-A")))
    }

    private class Fixture {
        val sessions = MutableStateFlow<AccountSession?>(null)
        val revisions = MutableStateFlow(0L)
        val owners = MutableStateFlow<List<BusinessReadOwner>>(emptyList())
        val parents = MutableStateFlow<List<BusinessReadParcelParent>>(emptyList())
        val dao = mockk<ParcelSyncDao>()
        val identity = mockk<IdentityRepository>()
        var generation = 0L
        val access: BusinessReadAccess
        init {
            every { identity.session } returns sessions
            every { identity.sessionGeneration } returns revisions
            every { identity.captureSessionGuard(any()) } answers {
                val id = firstArg<String>()
                if (sessions.value?.accountId == id) Result.success(Guard(id, generation)) else Result.failure(IllegalStateException("SESSION_CHANGED"))
            }
            every { identity.isSessionGuardCurrent(any()) } answers {
                val guard = firstArg<IdentitySessionGuard>() as? Guard
                guard != null && guard.generation == generation && guard.accountId == sessions.value?.accountId
            }
            every { dao.observeBusinessReadOwners() } returns owners
            every { dao.observeBusinessReadParcelParents() } returns parents
            coEvery { dao.getBusinessReadOwners() } coAnswers { owners.value }
            coEvery { dao.getBusinessReadParcelParents() } coAnswers { parents.value }
            coEvery { dao.withParcelMergeTransaction(any()) } coAnswers { firstArg<suspend () -> Unit>().invoke() }
            access = BusinessReadAccess(identity, dao)
        }
        fun login(id: String?) { generation += 1; revisions.value = generation; sessions.value = id?.let { AccountSession(it, IdentityProvider.LOCAL, emptyList(), 4_000_000_000L) } }
    }
    private data class Guard(override val accountId: String, val generation: Long) : IdentitySessionGuard
    private fun forest() = Foret("forest-A", "Private forest", "Owner", "private@example.invalid", null, null, null, null, null, null, null, 1L, 2L)
    private fun parcel() = Parcelle(id = "parcel-A", forestId = null, foretId = null, name = "Private parcel",
        surfaceHa = null, shape = null, slopePct = null, aspect = null, access = null, altitudeM = null,
        objectifType = null, objectifVal = null, tolerancePct = null, samplingMode = null,
        sampleAreaM2 = null, targetSpeciesCsv = null, srid = null, remarks = null, createdAt = 1L, updatedAt = 2L)
}
