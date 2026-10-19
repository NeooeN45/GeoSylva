package com.forestry.counter.presentation.screens.field

import androidx.lifecycle.ViewModelStore
import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.repository.FieldAccess
import com.forestry.counter.domain.repository.FieldRepository
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorksiteExportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var identity: ReceiptsViewModelTest.Fixture
    private lateinit var vm: WorksiteExportViewModel
    private var revokeAfterIo = false
    private val io = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatcher.dispatch(context, Runnable {
                block.run()
                // Identity changes after the last IO check, before its continuation reaches Main.
                if (revokeAfterIo) { revokeAfterIo = false; identity.epoch.value++ }
            })
        }
    }
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        identity = ReceiptsViewModelTest.Fixture()
        val repository = object : FieldRepository by identity {
            override suspend fun exportSnapshot(site: String, access: FieldAccess) = WorksiteExportSnapshot(
                exportId = UUID.randomUUID().toString(), capturedAt = 1L, scope = "LOCAL_DEVICE",
                site = SavedWorksite(identity.site, WorksiteForm("TEST · Export", parcels = listOf(WorksiteParcel("TEST", "TEST-P1", "TEST parcelle"))), 1, false),
                links = emptyList(), depots = emptyList(), objects = emptyList(), receipts = emptyList(),
                entries = emptyList(), photos = emptyList(), balances = emptyList(),
            )
        }
        vm = WorksiteExportViewModel(repository, identity.site, io, dispatcher)
        store.put("export", vm)
    }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun `revocation between IO completion and Main requires destination cleanup`() = runTest(dispatcher) {
        runCurrent(); vm.prepare(); advanceUntilIdle()
        assertTrue(vm.state.value.prepared)
        var writes = 0
        var cleanups = 0
        revokeAfterIo = true
        vm.save(writer = { bytes, current -> assertTrue(current()); assertTrue(bytes.isNotEmpty()); writes++ }, cleanup = { cleanups++; true })
        advanceUntilIdle()
        assertEquals(1, writes)
        assertEquals(1, cleanups)
        assertTrue(vm.state.value.accessLost)
        assertFalse(vm.state.value.saved)
        assertFalse(vm.state.value.cleanupFailed)
    }

    @Test fun `provider cleanup refusal remains visible after access is revoked`() = runTest(dispatcher) {
        runCurrent(); vm.prepare(); advanceUntilIdle()
        revokeAfterIo = true
        vm.save(writer = { _, _ -> }, cleanup = { false })
        advanceUntilIdle()
        assertTrue(vm.state.value.accessLost)
        assertTrue(vm.state.value.cleanupFailed)
        assertFalse(vm.state.value.saved)
    }

    @Test fun `account change while document picker is open rejects writing and cleans created document`() = runTest(dispatcher) {
        runCurrent(); vm.prepare(); advanceUntilIdle()
        identity.epoch.value++; runCurrent()
        var writes = 0
        var cleanups = 0
        vm.save(writer = { _, _ -> writes++ }, cleanup = { cleanups++; true })
        advanceUntilIdle()
        assertEquals(0, writes)
        assertEquals(1, cleanups)
        assertTrue(vm.state.value.accessLost)
    }
}
