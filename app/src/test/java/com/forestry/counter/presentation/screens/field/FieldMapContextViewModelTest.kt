package com.forestry.counter.presentation.screens.field

import androidx.lifecycle.ViewModelStore
import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.repository.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.forestry.counter.domain.journey.JourneyFeature
import com.forestry.counter.presentation.navigation.JourneyNavigation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FieldMapContextViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var fixture: StockJourneyFixture
    private val a = WorksiteParcel("GEOSYLVA", "A", "TEST parcel A", localParcelId = "TEST-local-A")
    private val b = WorksiteParcel("GEOSYLVA", "B", "TEST parcel B", localParcelId = "TEST-local-B")
    @Before fun before() { Dispatchers.setMain(dispatcher); fixture = StockJourneyFixture() }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    private fun model(siteId: String? = fixture.site.id, local: List<WorksiteParcel> = listOf(a, b), allowSites: Boolean = true, allowParcels: Boolean = true): FieldMapContextViewModel {
        val repository = object : FieldRepository by fixture { override suspend fun parcelChoices(access: FieldAccess) = local }
        return FieldMapContextViewModel(repository, siteId, allowSites, allowParcels).also { store.put("context", it) }
    }

    @Test fun `linked local parcels are all offered without selecting the first`() = runTest(dispatcher) {
        fixture.site = fixture.site.copy(form = fixture.site.form.copy(parcels = listOf(a, b)))
        val vm = model(); runCurrent()
        assertEquals(listOf(a.localParcelId, b.localParcelId), vm.state.value.choices.map { it.target.id })
        var selected: FieldTarget? = null
        vm.select(vm.state.value.choices.last().target) { selected = it }; runCurrent()
        assertEquals(b.localParcelId, selected?.id)
        assertTrue(fixture.appendAttempts.isEmpty())
    }

    @Test fun `external or unreadable references never fabricate map coordinates`() = runTest(dispatcher) {
        fixture.site = fixture.site.copy(form = fixture.site.form.copy(parcels = listOf(a, WorksiteParcel("EXTERNAL", "REF", "TEST external"))))
        val vm = model(local = emptyList()); runCurrent()
        assertTrue(vm.state.value.choices.isEmpty())
        assertEquals(2, vm.state.value.unavailableReferences)
        assertNull(vm.state.value.error)
    }

    @Test fun `removed parcel link is checked before navigation`() = runTest(dispatcher) {
        fixture.site = fixture.site.copy(form = fixture.site.form.copy(parcels = listOf(a)))
        val vm = model(); runCurrent()
        val target = vm.state.value.choices.single().target
        fixture.site = fixture.site.copy(form = fixture.site.form.copy(parcels = emptyList()))
        var called = false
        vm.select(target) { called = true }; runCurrent()
        assertFalse(called)
        assertEquals(FieldError.UNAVAILABLE, vm.state.value.error)
    }

    @Test fun `access selector respects allowed journey and skips an archived site`() = runTest(dispatcher) {
        fixture.site = fixture.site.copy(archived = true)
        val vm = model(siteId = null, allowParcels = false); runCurrent()
        assertTrue(vm.state.value.choices.isEmpty())
        assertTrue(fixture.appendAttempts.isEmpty())
    }

    @Test fun `access epoch change clears the previously offered contexts`() = runTest(dispatcher) {
        fixture.site = fixture.site.copy(form = fixture.site.form.copy(parcels = listOf(a)))
        val vm = model(); runCurrent()
        fixture.epoch.value++; runCurrent()
        assertTrue(vm.state.value.accessLost)
        assertTrue(vm.state.value.choices.isEmpty())
        assertNull(vm.state.value.site)
    }

    private fun constraints(code: String?): FieldMapContextConstraints {
        val access = JourneyNavigation.access(code)
        return FieldMapContextConstraints(access.allows(JourneyFeature.WORKSITES), access.allows(JourneyFeature.FORESTS))
    }
    private fun selector(repository: FieldRepository, initial: FieldMapContextConstraints): FieldMapContextViewModel =
        FieldMapContextViewModel(repository, null, initial.allowWorksites, initial.allowParcels).also { store.put("context", it) }
    private fun repositoryWithParcel(): FieldRepository = object : FieldRepository by fixture {
        override suspend fun parcelChoices(access: FieldAccess) = listOf(a)
    }

    @Test fun `initial false false constraints become supervisor choices on the same model`() = runTest(dispatcher) {
        val vm = selector(repositoryWithParcel(), constraints(null)); runCurrent()
        assertTrue(vm.state.value.choices.isEmpty())
        vm.updateConstraints(constraints("conducteur_travaux_forestiers"))
        assertTrue(vm.state.value.busy)
        assertTrue(vm.state.value.choices.isEmpty())
        runCurrent()
        assertEquals(listOf(FieldTarget(FieldTargetKind.WORKSITE, fixture.site.id)), vm.state.value.choices.map { it.target })
        assertNull(vm.state.value.error)
        assertTrue(fixture.appendAttempts.isEmpty())
        assertNull(fixture.savedDraft)
    }

    @Test fun `supervisor to technician clears worksite choices and rejects a delayed old row callback`() = runTest(dispatcher) {
        val operations = constraints("conducteur_travaux_forestiers")
        val vm = selector(repositoryWithParcel(), operations); runCurrent()
        val oldWorksite = vm.state.value.choices.single().target
        vm.updateConstraints(constraints("technicien_forestier"))
        assertTrue(vm.state.value.choices.isEmpty())
        var called = false
        vm.select(oldWorksite, operations) { called = true }; runCurrent()
        assertEquals(listOf(FieldTarget(FieldTargetKind.PARCEL, a.localParcelId!!)), vm.state.value.choices.map { it.target })
        vm.select(oldWorksite, operations) { called = true }; runCurrent()
        assertFalse(called)
        assertNull(vm.state.value.error)
    }

    @Test fun `an old non cooperative load cannot restore previous journey choices`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>(); var reads = 0
        val repository = object : FieldRepository by fixture {
            override suspend fun parcelChoices(access: FieldAccess) = listOf(a)
            override suspend fun sites(access: FieldAccess): List<SavedWorksite> {
                if (++reads == 1) withContext(NonCancellable) { gate.await() }
                return listOf(fixture.site)
            }
        }
        try {
            val vm = selector(repository, constraints("conducteur_travaux_forestiers")); runCurrent()
            assertTrue(vm.state.value.busy)
            vm.updateConstraints(constraints("technicien_forestier")); runCurrent()
            assertEquals(listOf(FieldTargetKind.PARCEL), vm.state.value.choices.map { it.target.kind })
            gate.complete(Unit); runCurrent()
            assertEquals(listOf(FieldTargetKind.PARCEL), vm.state.value.choices.map { it.target.kind })
            assertFalse(vm.state.value.busy)
            assertNull(vm.state.value.error)
        } finally { gate.complete(Unit); runCurrent() }
    }

    @Test fun `late selection revalidation cannot navigate after its constraints were replaced`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>(); var delayed = false
        val repository = object : FieldRepository by fixture {
            override suspend fun parcelChoices(access: FieldAccess) = listOf(a)
            override suspend fun writable(target: FieldTarget, access: FieldAccess): Boolean {
                if (delayed && target.kind == FieldTargetKind.WORKSITE) withContext(NonCancellable) { gate.await() }
                return fixture.writable(target, access)
            }
        }
        try {
            val vm = selector(repository, constraints("conducteur_travaux_forestiers")); runCurrent()
            val target = vm.state.value.choices.single().target
            delayed = true; var called = false
            vm.select(target) { called = true }; runCurrent()
            vm.updateConstraints(constraints("technicien_forestier")); runCurrent()
            gate.complete(Unit); runCurrent()
            assertFalse(called)
            assertEquals(listOf(FieldTargetKind.PARCEL), vm.state.value.choices.map { it.target.kind })
            assertFalse(vm.state.value.busy)
            assertNull(vm.state.value.error)
        } finally { gate.complete(Unit); runCurrent() }
    }

    @Test fun `late failure cannot replace a newer successful context with an IO error`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>(); var reads = 0
        val repository = object : FieldRepository by fixture {
            override suspend fun parcelChoices(access: FieldAccess) = listOf(a)
            override suspend fun sites(access: FieldAccess): List<SavedWorksite> {
                if (++reads == 1) {
                    withContext(NonCancellable) { gate.await() }
                    throw FieldException(FieldError.IO)
                }
                return listOf(fixture.site)
            }
        }
        try {
            val vm = selector(repository, constraints("conducteur_travaux_forestiers")); runCurrent()
            vm.updateConstraints(constraints("technicien_forestier")); runCurrent()
            gate.complete(Unit); runCurrent()
            assertEquals(listOf(FieldTargetKind.PARCEL), vm.state.value.choices.map { it.target.kind })
            assertNull(vm.state.value.error)
            assertFalse(vm.state.value.busy)
        } finally { gate.complete(Unit); runCurrent() }
    }

    @Test fun `journey allows a site only when repository revalidation also allows it`() = runTest(dispatcher) {
        var allowWrite = false
        val repository = object : FieldRepository by fixture {
            override suspend fun writable(target: FieldTarget, access: FieldAccess) = allowWrite
        }
        val vm = selector(repository, constraints("conducteur_travaux_forestiers")); runCurrent()
        assertTrue(vm.state.value.choices.isEmpty())
        allowWrite = true; vm.reload(); runCurrent()
        val target = vm.state.value.choices.single().target
        allowWrite = false; var called = false
        vm.select(target) { called = true }; runCurrent()
        assertFalse(called)
        assertTrue(vm.state.value.choices.isEmpty())
        assertEquals(FieldError.UNAVAILABLE, vm.state.value.error)
        assertTrue(fixture.appendAttempts.isEmpty())
        assertNull(fixture.savedDraft)
    }

    @Test fun `leaving the shared screen invalidates callbacks and reloads the same constraints on return`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>(); var delayed = false
        val repository = object : FieldRepository by fixture {
            override suspend fun writable(target: FieldTarget, access: FieldAccess): Boolean {
                if (delayed) withContext(NonCancellable) { gate.await() }
                return fixture.writable(target, access)
            }
        }
        try {
            val operations = constraints("conducteur_travaux_forestiers")
            val vm = selector(repository, operations); runCurrent()
            val target = vm.state.value.choices.single().target
            delayed = true; var called = false
            vm.select(target) { called = true }; runCurrent()
            vm.deactivate()
            assertTrue(vm.state.value.choices.isEmpty())
            delayed = false; vm.updateConstraints(operations); runCurrent()
            gate.complete(Unit); runCurrent()
            assertFalse(called)
            assertEquals(listOf(target), vm.state.value.choices.map { it.target })
            assertFalse(vm.state.value.busy)
        } finally { gate.complete(Unit); runCurrent() }
    }

    @Test fun `access epoch revocation remains terminal even when journey constraints change`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>(); var delayed = false
        val repository = object : FieldRepository by fixture {
            override suspend fun parcelChoices(access: FieldAccess) = listOf(a)
            override suspend fun writable(target: FieldTarget, access: FieldAccess): Boolean {
                if (delayed) withContext(NonCancellable) { gate.await() }
                return fixture.writable(target, access)
            }
        }
        try {
            val vm = selector(repository, constraints("conducteur_travaux_forestiers")); runCurrent()
            val target = vm.state.value.choices.single().target
            delayed = true; var called = false
            vm.select(target) { called = true }; runCurrent()
            fixture.epoch.value++; runCurrent()
            vm.updateConstraints(constraints("technicien_forestier")); runCurrent()
            gate.complete(Unit); runCurrent()
            assertFalse(called)
            assertTrue(vm.state.value.accessLost)
            assertEquals(FieldError.ACCESS_CHANGED, vm.state.value.error)
            assertTrue(vm.state.value.choices.isEmpty())
            assertFalse(vm.state.value.busy)
            assertTrue(fixture.appendAttempts.isEmpty())
            assertNull(fixture.savedDraft)
        } finally { gate.complete(Unit); runCurrent() }
    }

}
