package com.forestry.counter.presentation.screens.packs.offline

import com.forestry.counter.data.cartography.CartographicScopeAuthority
import com.forestry.counter.data.local.entity.OfflineMapPackEntity
import com.forestry.counter.data.packs.offline.*
import com.forestry.counter.domain.repository.CartographicStoreResult
import com.forestry.counter.domain.usecase.maps.MapDataScope
import com.quintessences.geosylva.cartography.mapbox.offline.*
import io.mockk.*
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineMapPackControllerTest {
    private val token = mockk<CartographicScopeAuthority.Token> { every { authorityKey } returns "TEST-authority" }
    private val input = OfflinePackInput(UUID.randomUUID().toString(), "TEST-only",
        OfflineMapDefinition("""{"type":"Polygon","coordinates":[[[5,45],[5.001,45],[5.001,45.001],[5,45.001],[5,45]]]}""", 11, 12, 1f, "mapbox://styles/mapbox/satellite-v9"))
    private val row = OfflineMapPackEntity(id = input.id, authorityKey = "TEST-authority", ownerKey = "LOCAL", scopeKind = "FREE", scopeId = "",
        name = input.name, geometryJson = input.definition.geometryJson, minZoom = 11, maxZoom = 12, pixelRatio = 1f,
        styleUri = input.definition.styleUri, extraTilesetsJson = "[]", datasetsJson = "[]", territoriesJson = "[]",
        definitionHash = input.definition.fingerprint, requestSha256 = "TEST-request", nativeRegionId = "TEST-native", localVersion = "TEST-version",
        createdAt = 1, status = OfflinePackStatus.PAUSED.name)
    private val plan = OfflinePackPlan(input, row.nativeRegionId, OfflineMapEstimate(10, 20, 0.1), 0, 1000, 1)
    private fun repository(changes: MutableSharedFlow<Unit>): OfflineMapPackRepository = mockk(relaxed = true) {
        every { accessChanges() } returns changes
        every { isCurrent(token) } returns true
        every { freeBytes() } returns 1000
        coEvery { capture(MapDataScope.Free) } returns CartographicStoreResult.Success(token)
        coEvery { list(token) } returns emptyList()
        coEvery { prepare(input, token) } returns plan
        coEvery { confirm(plan, token) } returns row
        coEvery { mark(row.id, any(), token) } coAnswers { row.copy(status = secondArg<OfflinePackStatus>().name) }
    }
    @Test fun `manual pause is not automatically resumed by network recovery`() = runTest {
        val changes = MutableSharedFlow<Unit>()
        val repository = repository(changes)
        val network = MutableStateFlow(OfflinePackNetwork(false, false))
        val controller = OfflineMapPackController(repository, backgroundScope, network)
        controller.activate(MapDataScope.Free); runCurrent()
        controller.prepare(input); runCurrent(); controller.confirm(); runCurrent()
        assertEquals(OfflinePackStatus.WAITING_WIFI.name, controller.state.value.packs.single().status)
        controller.pause(row.id); runCurrent()
        network.value = OfflinePackNetwork(true, true); runCurrent()
        coVerify(exactly = 0) { repository.transfer(any(), any(), any(), any()) }
        assertEquals(OfflinePackStatus.PAUSED.name, controller.state.value.packs.single().status)
    }
    @Test fun `a resumed active request waits for WiFi and cancels when WiFi disappears`() = runTest {
        val changes = MutableSharedFlow<Unit>()
        val repository = repository(changes)
        val network = MutableStateFlow(OfflinePackNetwork(false, false))
        val deferred = CompletableDeferred<Unit>()
        coEvery { repository.transfer(row.id, token, true, any()) } coAnswers {
            deferred.await(); row.copy(status = OfflinePackStatus.READY.name)
        }
        val controller = OfflineMapPackController(repository, backgroundScope, network)
        controller.activate(MapDataScope.Free); runCurrent()
        controller.prepare(input); runCurrent(); controller.confirm(); runCurrent()
        network.value = OfflinePackNetwork(true, false); runCurrent()
        coVerify(exactly = 0) { repository.transfer(any(), any(), any(), any()) }
        network.value = OfflinePackNetwork(true, true); runCurrent()
        assertTrue(row.id in controller.state.value.running)
        network.value = OfflinePackNetwork(true, false); runCurrent()
        assertFalse(row.id in controller.state.value.running)
        assertEquals(OfflinePackStatus.WAITING_WIFI.name, controller.state.value.packs.single().status)
        network.value = OfflinePackNetwork(true, true); runCurrent()
        coVerify(exactly = 2) { repository.transfer(row.id, token, true, any()) }
        controller.pause(row.id); runCurrent()
    }
    @Test fun `purge waits for native cancellation cleanup before returning`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = repository(MutableSharedFlow())
            val cancellationStarted = CompletableDeferred<Unit>()
            val cancellationReleased = CompletableDeferred<Unit>()
            coEvery { repository.transfer(row.id, token, true, any()) } coAnswers {
                try { awaitCancellation() }
                finally { withContext(NonCancellable) {
                    cancellationStarted.complete(Unit)
                    cancellationReleased.await()
                } }
            }
            val controller = OfflineMapPackController(repository, backgroundScope,
                MutableStateFlow(OfflinePackNetwork(true, true)))
            controller.activate(MapDataScope.Free); runCurrent()
            controller.prepare(input); runCurrent(); controller.confirm(); runCurrent()
            assertTrue(row.id in controller.state.value.running)
            val stopped = async { controller.stopForPurge() }
            runCurrent()
            assertTrue(cancellationStarted.isCompleted)
            assertFalse(stopped.isCompleted)
            assertTrue(controller.state.value.packs.isEmpty())
            cancellationReleased.complete(Unit)
            runCurrent()
            stopped.await()
            coVerify(exactly = 0) { repository.mark(any(), any(), any()) }
        } finally { Dispatchers.resetMain() }
    }
    @Test fun `parent revocation clears a plan even when the session and purge hint stays current`() = runTest {
        val changes = MutableSharedFlow<Unit>()
        val repository = repository(changes)
        val controller = OfflineMapPackController(repository, backgroundScope,
            MutableStateFlow(OfflinePackNetwork(true, true)))
        controller.activate(MapDataScope.Free); runCurrent(); controller.prepare(input); runCurrent()
        assertNotNull(controller.state.value.plan)
        every { repository.isCurrent(token) } returns true
        coEvery { repository.requireAccess(token) } throws IllegalStateException("TEST-parent-revoked")
        changes.emit(Unit); runCurrent()
        assertNull(controller.state.value.authorityKey)
        assertNull(controller.state.value.plan)
        controller.confirm(); runCurrent()
        coVerify(exactly = 0) { repository.confirm(any(), any()) }
    }
    @Test fun `authority loss clears pending confirmation and late callbacks cannot activate it`() = runTest {
        val changes = MutableSharedFlow<Unit>()
        val repository = repository(changes)
        val network = MutableStateFlow(OfflinePackNetwork(true, true))
        val controller = OfflineMapPackController(repository, backgroundScope, network)
        controller.activate(MapDataScope.Free); runCurrent(); controller.prepare(input); runCurrent()
        assertNotNull(controller.state.value.plan)
        every { repository.isCurrent(token) } returns false
        changes.emit(Unit); runCurrent()
        assertNull(controller.state.value.plan)
        assertNull(controller.state.value.authorityKey)
        assertTrue(controller.state.value.packs.isEmpty())
        controller.confirm(); runCurrent()
        coVerify(exactly = 0) { repository.confirm(any(), any()) }
    }
}
