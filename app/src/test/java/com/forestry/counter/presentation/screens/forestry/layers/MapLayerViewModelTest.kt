package com.forestry.counter.presentation.screens.forestry.layers

import androidx.lifecycle.ViewModelStore
import com.forestry.counter.domain.model.cartography.*
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.usecase.maps.MapDataScope
import com.quintessences.geosylva.cartography.ui.layers.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class MapLayerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var nextModel = 0
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    @Test fun `preview cancel does not commit and a file response for an old scope is discarded`() = runTest {
        val repo = Fake(); val vm = vm(repo)
        vm.open(MapDataScope.Free); runCurrent()
        val ticket = requireNotNull(vm.captureFileSelectionTicket())
        vm.importFile(byteArrayOf(1), "TEST.geojson", LayerImportFormat.GEOJSON, ticket); runCurrent()
        assertTrue(vm.state.value.ui.preview); assertEquals(1, vm.state.value.renders.size); assertEquals(0, repo.commits)
        vm.onDiscardPreview(); assertTrue(vm.state.value.renders.isEmpty()); assertEquals(0, repo.commits)
        vm.open(MapDataScope.AllForests); runCurrent()
        vm.importFile(byteArrayOf(1), "TEST.geojson", LayerImportFormat.GEOJSON, ticket); runCurrent()
        assertFalse(vm.state.value.ui.preview); assertEquals(1, repo.stageCalls)
    }
    @Test fun `filter drives map list counters and deselection and survives colour cancellation`() = runTest {
        val repo = Fake(saved = listOf(layer())); val vm = vm(repo)
        vm.open(MapDataScope.Free); runCurrent(); vm.onOpenLayer("layer"); runCurrent()
        vm.onPanel(LayerPanel.DATA); vm.onSelectObject("f-1")
        vm.onPanel(LayerPanel.FILTER); vm.onFilterField("nature"); vm.onFilterValue(JsonPrimitive("Accès").toString())
        val state = vm.state.value
        assertNull(state.ui.selectedId); assertEquals(1, state.ui.visibleCount); assertEquals(listOf("f-0"), state.ui.objects.map { it.id })
        assertEquals(1, Json.parseToJsonElement(state.renders.single().geoJson).jsonObject["features"]!!.jsonArray.size)
        vm.onBack(); vm.onPanel(LayerPanel.APPEARANCE)
        val initial = vm.state.value.ui.appearance.colorArgb
        vm.onPanel(LayerPanel.COLOR); vm.onColorInput("#3B7D72")
        assertEquals(0xFF3B7D72L, vm.state.value.renders.single().style.colorArgb)
        vm.onCancelColor()
        assertEquals(initial, vm.state.value.renders.single().style.colorArgb)
        assertTrue(vm.state.value.ui.filterActive); assertEquals(1, vm.state.value.ui.visibleCount); assertEquals(0, repo.styleWrites)
    }
    @Test fun `untouched nullable attributes are omitted and an explicit empty string stays text`() = runTest {
        val repo = Fake(saved = listOf(layer())); val vm = vm(repo)
        vm.open(MapDataScope.Free); runCurrent(); vm.onOpenLayer("layer"); runCurrent(); vm.onSelectObject("f-0")
        vm.onPanel(LayerPanel.EDIT); vm.onAttribute("nom", ""); vm.onSaveAttributes(); runCurrent()
        assertEquals(listOf(LayerAttributeChange("nom", JsonPrimitive(""))), repo.patch)
        assertEquals(JsonNull, vm.state.value.ui.selected?.attributes?.first { it.key == "note" }?.let { if (it.kind == LayerValueKind.NULL) JsonNull else null })
    }
    @Test fun `common visibility checkbox preserves unrelated layer panel filter and selection`() = runTest {
        val repo = Fake(saved = listOf(layer(), layer().copy(id = "other", name = "TEST other"))); val vm = vm(repo)
        vm.open(MapDataScope.Free); runCurrent(); vm.onOpenLayer("layer"); runCurrent()
        vm.onPanel(LayerPanel.FILTER); vm.onFilterField("nature"); vm.onFilterValue(JsonPrimitive("Dépôt").toString())
        // Establish a selection that actually passes the final filter before changing another layer.
        vm.onSelectObject("f-1"); vm.onPanel(LayerPanel.APPEARANCE)
        assertEquals("f-1", vm.state.value.ui.selectedId)
        assertTrue(vm.state.value.ui.filterActive)
        vm.setLayerVisible("other", false); runCurrent()
        assertEquals(LayerPanel.APPEARANCE, vm.state.value.ui.panel)
        assertEquals("f-1", vm.state.value.ui.selectedId); assertTrue(vm.state.value.ui.filterActive)
        assertEquals(1, vm.state.value.ui.visibleCount)
        assertFalse(vm.state.value.ui.layers.first { it.id == "other" }.visible)
        assertTrue(vm.state.value.ui.layers.first { it.id == "layer" }.visible)
    }
    @Test fun `a filtered out selection is cleared immediately and later criteria never resurrect it`() = runTest {
        val repo = Fake(saved = listOf(layer())); val vm = vm(repo)
        vm.open(MapDataScope.Free); runCurrent(); vm.onOpenLayer("layer"); runCurrent(); vm.onSelectObject("f-1")
        assertEquals("f-1", vm.state.value.ui.selectedId)
        vm.onPanel(LayerPanel.FILTER); vm.onFilterField("nature")
        // The first observed value is Accès, so the selected Dépôt object is now excluded.
        assertEquals(JsonPrimitive("Accès").toString(), vm.state.value.ui.filterValueKey)
        assertNull(vm.state.value.ui.selectedId)
        assertEquals(listOf("f-0"), vm.state.value.ui.objects.map { it.id })
        vm.onFilterValue(JsonPrimitive("Dépôt").toString())
        assertEquals(listOf("f-1"), vm.state.value.ui.objects.map { it.id })
        assertNull(vm.state.value.ui.selectedId)
    }
    @Test fun `appearance saves against the version captured before an external update`() = runTest {
        val repo = Fake(saved = listOf(layer())); val vm = vm(repo)
        vm.open(MapDataScope.Free); runCurrent(); vm.onOpenLayer("layer"); runCurrent(); vm.onPanel(LayerPanel.APPEARANCE)
        repo.saved = listOf(layer().copy(version = 2, style = LayerStyle(colorArgb = 0xFF206C32)))
        repo.signals.emit(Unit); runCurrent()
        vm.onAppearance(vm.state.value.ui.appearance.copy(colorArgb = 0xFF3B7D72)); vm.onSaveAppearance(); runCurrent()
        assertEquals(1L, repo.lastStyleExpectedVersion)
        assertEquals(LayerStoreError.CONFLICT, vm.state.value.error)
        assertEquals(0xFF206C32L, repo.saved.single().style.colorArgb)
    }
    @Test fun `access signal clears private overlays and ignores a late stage response`() = runTest {
        val repo = Fake(); val vm = vm(repo)
        vm.open(MapDataScope.Free); runCurrent()
        val ticket = requireNotNull(vm.captureFileSelectionTicket())
        repo.delayStage = CompletableDeferred()
        vm.importFile(byteArrayOf(1), "TEST.geojson", LayerImportFormat.GEOJSON, ticket); runCurrent()
        repo.current = false; repo.signals.emit(Unit); runCurrent()
        repo.delayStage?.complete(Unit); runCurrent()
        assertTrue(vm.state.value.renders.isEmpty()); assertFalse(vm.state.value.ui.preview)
        assertEquals(LayerStoreError.ACCESS_CHANGED, vm.state.value.error)
    }
    private fun vm(repo: Fake) = MapLayerViewModel(repo).also { store.put("vm-${nextModel++}", it) }
    private class Access(override val scope: MapDataScope) : CartographicLayerAccess { override val restorationKey = scope.toString() }
    private class Fake(var saved: List<CartographicLayer> = emptyList()) : CartographicLayerRepository {
        var current = true; val signals = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
        var commits = 0; var stageCalls = 0; var styleWrites = 0; var lastStyleExpectedVersion: Long? = null; var patch = emptyList<LayerAttributeChange>(); var delayStage: CompletableDeferred<Unit>? = null
        override suspend fun captureAccess(scope: MapDataScope) = LayerStoreResult.Success(Access(scope))
        override fun isCurrent(access: CartographicLayerAccess) = current
        override fun accessChanges(): Flow<Unit> = signals
        override suspend fun stage(bytes: ByteArray, filename: String, format: LayerImportFormat, access: CartographicLayerAccess): LayerStoreResult<StagedCartographicLayer> {
            stageCalls++; delayStage?.await(); return LayerStoreResult.Success(StagedCartographicLayer("stage", layer()))
        }
        override fun discard(token: String) = Unit
        override suspend fun commit(token: String, operationId: String, access: CartographicLayerAccess): LayerStoreResult<CartographicLayer> { commits++; saved = listOf(layer()); return LayerStoreResult.Success(saved.single()) }
        override suspend fun list(access: CartographicLayerAccess) = LayerStoreResult.Success(saved)
        override suspend fun read(id: String, access: CartographicLayerAccess): LayerStoreResult<CartographicLayer> = saved.firstOrNull { it.id == id }?.let { LayerStoreResult.Success(it) } ?: LayerStoreResult.Failure(LayerStoreError.UNAVAILABLE)
        override suspend fun updateStyle(id: String, expectedVersion: Long, operationId: String, style: LayerStyle, access: CartographicLayerAccess): LayerStoreResult<CartographicLayer> {
            styleWrites++; lastStyleExpectedVersion = expectedVersion
            val source = saved.first { it.id == id }
            if (source.version != expectedVersion) return LayerStoreResult.Failure(LayerStoreError.CONFLICT)
            val value = source.copy(style = style, version = expectedVersion + 1)
            saved = saved.map { if (it.id == id) value else it }; return LayerStoreResult.Success(value)
        }
        override suspend fun patchAttributes(id: String, featureId: String, expectedVersion: Long, expectedFeatureVersion: Long, operationId: String, changes: List<LayerAttributeChange>, access: CartographicLayerAccess): LayerStoreResult<CartographicLayer> {
            patch = changes; val value = saved.single().let { layer -> layer.copy(version = layer.version + 1, features = layer.features.map { if (it.id == featureId) it.copy(localAttributes = changes.associate { it.field to requireNotNull(it.value) }) else it }) }; saved = listOf(value); return LayerStoreResult.Success(value)
        }
        override suspend fun delete(id: String, expectedVersion: Long, operationId: String, access: CartographicLayerAccess): LayerStoreResult<Unit> { saved = emptyList(); return LayerStoreResult.Success(Unit) }
    }
    companion object {
        private fun layer() = CartographicLayer("layer", "TEST", LayerSource("TEST.geojson", "GEOJSON", "hash", ""), listOf("Accès", "Dépôt").mapIndexed { i, nature ->
            LayerFeature("f-$i", buildJsonObject { put("type", "Feature"); put("geometry", buildJsonObject { put("type", "Point"); put("coordinates", JsonArray(listOf(JsonPrimitive(2.0 + i), JsonPrimitive(45.0)))) }); put("properties", buildJsonObject { put("nom", "TEST $i"); put("nature", nature); put("note", JsonNull) }) })
        }, recordedAt = 10)
    }
}
