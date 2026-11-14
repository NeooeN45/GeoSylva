package com.forestry.counter.presentation.screens.forestry.layers

import androidx.lifecycle.ViewModelStore
import com.forestry.counter.domain.model.cartography.*
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.usecase.maps.MapDataScope
import com.quintessences.geosylva.cartography.ui.layers.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapLayerViewModelReconstructionTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var models = 0

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test fun old_file_ticket_cannot_stage_or_publish_an_error_in_another_scope() = runTest(dispatcher) {
        val repo = Fake()
        val vm = model(repo)
        vm.open(MapDataScope.Free)
        runCurrent()
        val ticket = requireNotNull(vm.captureFileSelectionTicket())
        vm.open(MapDataScope.AllForests)
        runCurrent()
        vm.importFile(byteArrayOf(1), "old.geojson", LayerImportFormat.GEOJSON, ticket)
        vm.reportFileError(LayerStoreError.UNSAFE_ARCHIVE, ticket)
        runCurrent()
        assertEquals(0, repo.stageCalls)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.ui.preview)
    }

    @Test fun a_noncooperative_late_stage_is_discarded_after_scope_rebinding() = runTest(dispatcher) {
        val repo = Fake()
        val vm = model(repo)
        vm.open(MapDataScope.Free)
        runCurrent()
        val ticket = requireNotNull(vm.captureFileSelectionTicket())
        val gate = CompletableDeferred<Unit>()
        repo.stageGate = gate
        vm.importFile(byteArrayOf(1), "late.geojson", LayerImportFormat.GEOJSON, ticket)
        runCurrent()
        vm.open(MapDataScope.AllForests)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertTrue(repo.staged.isEmpty())
        assertEquals(1, repo.discards.size)
        assertTrue(vm.state.value.renders.isEmpty())
        assertFalse(vm.state.value.ui.preview)
        assertFalse(vm.state.value.ui.busy)
    }

    @Test fun a_noncooperative_old_list_cannot_replace_a_new_scope_snapshot() = runTest(dispatcher) {
        val repo = Fake()
        repo.scoped[MapDataScope.Free] = listOf(layer("old"))
        repo.scoped[MapDataScope.AllForests] = listOf(layer("new"))
        val gate = CompletableDeferred<Unit>()
        repo.listGate = MapDataScope.Free to gate
        val vm = model(repo)
        vm.bind(MapDataScope.Free)
        runCurrent()
        vm.open(MapDataScope.AllForests)
        runCurrent()
        assertEquals(listOf("new"), vm.state.value.renders.map { it.id })
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("new"), vm.state.value.renders.map { it.id })
        assertTrue(vm.state.value.visible)
    }

    @Test fun opening_during_the_initial_list_keeps_the_user_requested_panel_visible() = runTest(dispatcher) {
        val repo = Fake()
        val gate = CompletableDeferred<Unit>()
        repo.listGate = MapDataScope.Free to gate
        val vm = model(repo)
        vm.bind(MapDataScope.Free)
        runCurrent()
        vm.open(MapDataScope.Free)
        gate.complete(Unit)
        runCurrent()
        assertTrue(vm.state.value.visible)
        assertFalse(vm.state.value.ui.busy)
    }

    @Test fun uncertain_commit_keeps_preview_and_retries_the_same_operation_without_duplicate_storage() = runTest(dispatcher) {
        val repo = Fake()
        repo.uncertainCommitOnce = true
        val vm = model(repo)
        vm.open(MapDataScope.Free)
        runCurrent()
        vm.importFile(byteArrayOf(1), "new.geojson", LayerImportFormat.GEOJSON)
        runCurrent()
        vm.onQuery("oak")
        vm.onCommit()
        runCurrent()
        assertEquals(LayerStoreError.UNKNOWN_RESULT, vm.state.value.error)
        vm.onDiscardPreview()
        vm.onClose()
        assertFalse(vm.state.value.visible)
        assertTrue(vm.state.value.ui.preview)
        assertEquals(0, repo.discards.size)
        vm.open(MapDataScope.Free)
        assertTrue(vm.state.value.visible)
        assertEquals(LayerPanel.PREVIEW, vm.state.value.ui.panel)
        vm.onCommit()
        runCurrent()
        assertEquals(2, repo.commitIds.size)
        assertEquals(repo.commitIds[0], repo.commitIds[1])
        assertEquals(1, repo.saved.size)
        assertFalse(vm.state.value.ui.preview)
        assertEquals(LayerPanel.MANAGE, vm.state.value.ui.panel)
        assertEquals("oak", vm.state.value.ui.query)
        assertNull(vm.state.value.error)
    }

    @Test fun a_write_followed_by_a_failed_refresh_retries_its_frozen_payload_and_expected_version() = runTest(dispatcher) {
        val repo = Fake(listOf(layer()))
        repo.failListAfterStyleOnce = true
        val vm = openLayer(repo)
        vm.onPanel(LayerPanel.APPEARANCE)
        vm.onAppearance(vm.state.value.ui.appearance.copy(colorArgb = 0xFF123456))
        vm.onSaveAppearance()
        runCurrent()
        assertEquals(LayerStoreError.IO, vm.state.value.error)
        assertEquals(2L, repo.saved.single().version)
        vm.onAppearance(vm.state.value.ui.appearance.copy(colorArgb = 0xFFABCDEF))
        vm.onBack()
        vm.onClose()
        assertFalse(vm.state.value.visible)
        vm.open(MapDataScope.Free)
        assertTrue(vm.state.value.visible)
        assertEquals(LayerPanel.APPEARANCE, vm.state.value.ui.panel)
        vm.onSaveAppearance()
        runCurrent()
        assertEquals(2, repo.styleCalls.size)
        assertEquals(repo.styleCalls[0], repo.styleCalls[1])
        assertEquals(1L, repo.styleCalls[1].version)
        assertEquals(0xFF123456L, repo.saved.single().style.colorArgb)
        assertEquals(2L, repo.saved.single().version)
        assertNull(vm.state.value.error)
    }

    @Test fun access_change_after_a_write_clears_overlays_before_any_refreshed_success_is_published() = runTest(dispatcher) {
        val repo = Fake(listOf(layer()))
        repo.invalidateAfterStyleOnce = true
        val vm = openLayer(repo)
        vm.onAppearance(vm.state.value.ui.appearance.copy(colorArgb = 0xFF123456))
        vm.onSaveAppearance()
        runCurrent()
        assertEquals(LayerStoreError.ACCESS_CHANGED, vm.state.value.error)
        assertTrue(vm.state.value.renders.isEmpty())
        assertTrue(vm.state.value.ui.layers.isEmpty())
        assertNull(vm.captureFileSelectionTicket())
    }

    @Test fun invalid_scalar_edits_and_csv_coordinate_edits_do_not_reach_storage() = runTest(dispatcher) {
        val repo = Fake(listOf(layer().copy(source = layer().source.copy(format = "CSV"))))
        val original = repo.saved.single()
        val vm = openLayer(repo)
        vm.onSelectObject("f-0")
        vm.onPanel(LayerPanel.EDIT)
        vm.onAttribute("lat", "12.0")
        vm.onNullAttribute("nested")
        vm.onAttribute("count", "NaN")
        assertFalse(vm.state.value.ui.editValid)
        vm.onSaveAttributes()
        runCurrent()
        assertEquals(0, repo.patchCalls.size)
        assertEquals(original, repo.saved.single())
        vm.onAttribute("count", "2.5")
        vm.onAttribute("enabled", "false")
        vm.onAttribute("name", "")
        vm.onNullAttribute("nullable")
        vm.onSaveAttributes()
        runCurrent()
        val changes = repo.patchCalls.single().changes.associate { it.field to it.value }
        assertFalse(changes.containsKey("lat"))
        assertFalse(changes.containsKey("nested"))
        assertEquals(JsonPrimitive(2.5), changes["count"])
        assertEquals(JsonPrimitive(false), changes["enabled"])
        assertEquals(JsonPrimitive(""), changes["name"])
        assertEquals(JsonNull, changes["nullable"])
        val edited = repo.saved.single()
        assertEquals(original.source, edited.source)
        assertEquals(original.features.map { it.sourceFeature }, edited.features.map { it.sourceFeature })
        assertEquals("oak", vm.state.value.ui.sourceFields.first { it.key == "name" }.value)
    }

    @Test fun external_attribute_updates_do_not_rebase_the_open_edit_draft() = runTest(dispatcher) {
        val repo = Fake(listOf(layer()))
        val vm = openLayer(repo)
        vm.onSelectObject("f-0")
        vm.onPanel(LayerPanel.EDIT)
        vm.onAttribute("name", "my draft")
        repo.saved = repo.saved.map { it.copy(version = 2, features = it.features.map { f -> f.copy(version = 2) }) }
        repo.signals.emit(Unit)
        runCurrent()
        vm.onSaveAttributes()
        runCurrent()
        assertEquals(1L, repo.patchCalls.single().version)
        assertEquals(1L, repo.patchCalls.single().featureVersion)
        assertEquals(LayerStoreError.CONFLICT, vm.state.value.error)
        assertEquals("my draft", vm.state.value.ui.editFields.first { it.key == "name" }.input)
        assertEquals(2L, repo.saved.single().version)
    }

    @Test fun null_absent_empty_zero_and_false_filters_share_the_same_map_and_list_predicate() = runTest(dispatcher) {
        val values = listOf<JsonElement?>(JsonNull, null, JsonPrimitive(""), JsonPrimitive(0), JsonPrimitive(false))
        val source = layer().copy(features = values.mapIndexed { i, value ->
            point("f-$i", buildJsonObject { value?.let { put("value", it) } })
        })
        val repo = Fake(listOf(source))
        val vm = openLayer(repo)
        vm.onSelectObject("f-0")
        vm.onPanel(LayerPanel.FILTER)
        vm.onFilterField("value")
        vm.onFilterCondition(LayerCondition.IS_ABSENT)
        assertEquals(listOf("f-1"), vm.state.value.ui.objects.map { it.id })
        assertNull(vm.state.value.ui.selectedId)
        assertEquals(listOf("f-1"), renderedIds(vm))
        vm.onFilterCondition(LayerCondition.IS_EMPTY)
        assertEquals(listOf("f-2"), renderedIds(vm))
        vm.onFilterCondition(LayerCondition.EQUALS)
        vm.onFilterValue(JsonPrimitive(0).toString())
        assertEquals(listOf("f-3"), renderedIds(vm))
        vm.onFilterValue(JsonPrimitive(false).toString())
        assertEquals(listOf("f-4"), renderedIds(vm))
        vm.onResetFilter()
        assertEquals(5, vm.state.value.ui.visibleCount)
        assertNull(vm.state.value.ui.selectedId)
    }

    @Test fun active_visibility_save_preserves_the_unsaved_appearance_then_saves_against_its_new_version() = runTest(dispatcher) {
        val repo = Fake(listOf(layer()))
        val vm = openLayer(repo)
        vm.onPanel(LayerPanel.APPEARANCE)
        vm.onAppearance(vm.state.value.ui.appearance.copy(colorArgb = 0xFF123456))
        vm.setLayerVisible("layer", false)
        runCurrent()
        assertEquals(0xFF087C96L, repo.saved.single().style.colorArgb)
        assertFalse(repo.saved.single().style.visible)
        assertEquals(0xFF123456L, vm.state.value.ui.appearance.colorArgb)
        vm.onSaveAppearance()
        runCurrent()
        assertEquals(2L, repo.styleCalls.last().version)
        assertEquals(0xFF123456L, repo.saved.single().style.colorArgb)
        assertFalse(repo.saved.single().style.visible)
    }

    @Test fun deleting_a_layer_requires_repository_confirmation_and_removes_its_render_and_selection() = runTest(dispatcher) {
        val repo = Fake(listOf(layer()))
        val vm = openLayer(repo)
        vm.onSelectObject("f-0")
        vm.onDeleteLayer()
        runCurrent()
        assertEquals(1, repo.deletes)
        assertTrue(repo.saved.isEmpty())
        assertTrue(vm.state.value.renders.isEmpty())
        assertNull(vm.state.value.ui.selectedId)
        assertEquals(LayerPanel.LIBRARY, vm.state.value.ui.panel)
        assertNull(vm.state.value.error)
    }

    @Test fun back_closes_an_idle_library_and_clearing_the_viewmodel_discards_only_its_staged_source() = runTest(dispatcher) {
        val repo = Fake(listOf(layer()))
        val vm = model(repo)
        vm.open(MapDataScope.Free)
        vm.onBack()
        assertTrue(vm.state.value.visible)
        runCurrent()
        vm.onBack()
        assertFalse(vm.state.value.visible)
        assertEquals(listOf("layer"), vm.state.value.renders.map { it.id })
        vm.open(MapDataScope.Free)
        vm.importFile(byteArrayOf(1), "new.geojson", LayerImportFormat.GEOJSON)
        runCurrent()
        store.clear()
        assertEquals(1, repo.discards.size)
        assertTrue(repo.staged.isEmpty())
        assertEquals(1, repo.saved.size)
        assertTrue(repo.commitIds.isEmpty())
    }

    @Test fun a_failed_access_capture_can_close_its_error_panel_without_retaining_private_snapshots() = runTest(dispatcher) {
        val repo = Fake(listOf(layer()))
        repo.captureFailure = LayerStoreError.UNAVAILABLE
        val vm = model(repo)
        vm.open(MapDataScope.Free)
        runCurrent()
        assertEquals(LayerStoreError.UNAVAILABLE, vm.state.value.error)
        assertTrue(vm.state.value.visible)
        assertTrue(vm.state.value.renders.isEmpty())
        vm.onBack()
        assertFalse(vm.state.value.visible)
        assertTrue(vm.state.value.renders.isEmpty())
        assertNull(vm.captureFileSelectionTicket())
    }

    private fun model(repo: Fake) = MapLayerViewModel(repo).also { store.put("layer-" + models++, it) }
    private suspend fun TestScope.openLayer(repo: Fake): MapLayerViewModel = model(repo).also {
        it.open(MapDataScope.Free)
        runCurrent()
        it.onOpenLayer("layer")
        runCurrent()
    }
    private fun renderedIds(vm: MapLayerViewModel): List<String> =
        Json.parseToJsonElement(vm.state.value.renders.single().geoJson).jsonObject["features"]!!.jsonArray
            .map { it.jsonObject["id"]!!.jsonPrimitive.content }

    private data class Access(override val scope: MapDataScope, val epoch: Int) : CartographicLayerAccess {
        override val restorationKey get() = scope.toString() + ":" + epoch
    }
    private data class StyleCall(val id: String, val version: Long, val operationId: String, val style: LayerStyle)
    private data class PatchCall(
        val id: String, val version: Long, val featureId: String, val featureVersion: Long,
        val operationId: String, val changes: List<LayerAttributeChange>,
    )
    private class Fake(var saved: List<CartographicLayer> = emptyList()) : CartographicLayerRepository {
        var epoch = 0
        val signals = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        val scoped = mutableMapOf<MapDataScope, List<CartographicLayer>>()
        val staged = mutableMapOf<String, CartographicLayer>()
        val discards = mutableListOf<String>()
        val commitIds = mutableListOf<String>()
        val styleCalls = mutableListOf<StyleCall>()
        val patchCalls = mutableListOf<PatchCall>()
        private val receipts = mutableSetOf<String>()
        var stageCalls = 0
        var deletes = 0
        var stageGate: CompletableDeferred<Unit>? = null
        var listGate: Pair<MapDataScope, CompletableDeferred<Unit>>? = null
        var uncertainCommitOnce = false
        var failListAfterStyleOnce = false
        var invalidateAfterStyleOnce = false
        var captureFailure: LayerStoreError? = null
        private var failedLists = 0
        override suspend fun captureAccess(scope: MapDataScope): LayerStoreResult<CartographicLayerAccess> =
            captureFailure?.let { LayerStoreResult.Failure(it) } ?: LayerStoreResult.Success(Access(scope, epoch))
        override fun isCurrent(access: CartographicLayerAccess) = (access as? Access)?.epoch == epoch
        override fun accessChanges() = signals
        private fun rows(a: CartographicLayerAccess) = scoped[a.scope] ?: saved
        private fun rows(a: CartographicLayerAccess, next: List<CartographicLayer>) {
            if (scoped.containsKey(a.scope)) scoped[a.scope] = next else saved = next
        }
        override suspend fun stage(bytes: ByteArray, filename: String, format: LayerImportFormat, access: CartographicLayerAccess): LayerStoreResult<StagedCartographicLayer> {
            stageCalls++
            stageGate?.let { withContext(NonCancellable) { it.await() } }
            val token = UUID.randomUUID().toString()
            val value = layer(token)
            staged[token] = value
            return LayerStoreResult.Success(StagedCartographicLayer(token, value))
        }
        override fun discard(token: String) { discards += token; staged.remove(token) }
        override suspend fun commit(token: String, operationId: String, access: CartographicLayerAccess): LayerStoreResult<CartographicLayer> {
            commitIds += operationId
            if (!isCurrent(access)) return LayerStoreResult.Failure(LayerStoreError.ACCESS_CHANGED)
            val existing = rows(access).firstOrNull { it.id == token }
            if (operationId in receipts) return existing?.let { LayerStoreResult.Success(it) } ?: LayerStoreResult.Failure(LayerStoreError.UNAVAILABLE)
            val value = staged[token] ?: return LayerStoreResult.Failure(LayerStoreError.UNAVAILABLE)
            rows(access, rows(access).filterNot { it.id == token } + value)
            receipts += operationId
            if (uncertainCommitOnce) {
                uncertainCommitOnce = false
                return LayerStoreResult.Failure(LayerStoreError.UNKNOWN_RESULT)
            }
            return LayerStoreResult.Success(value)
        }
        override suspend fun list(access: CartographicLayerAccess): LayerStoreResult<List<CartographicLayer>> {
            if (failedLists > 0) { failedLists--; return LayerStoreResult.Failure(LayerStoreError.IO) }
            val snapshot = rows(access)
            listGate?.takeIf { it.first == access.scope }?.let { withContext(NonCancellable) { it.second.await() } }
            return LayerStoreResult.Success(snapshot)
        }
        override suspend fun read(id: String, access: CartographicLayerAccess): LayerStoreResult<CartographicLayer> =
            rows(access).firstOrNull { it.id == id }?.let { LayerStoreResult.Success(it) } ?: LayerStoreResult.Failure(LayerStoreError.UNAVAILABLE)
        override suspend fun updateStyle(id: String, expectedVersion: Long, operationId: String, style: LayerStyle, access: CartographicLayerAccess): LayerStoreResult<CartographicLayer> {
            styleCalls += StyleCall(id, expectedVersion, operationId, style)
            val old = rows(access).firstOrNull { it.id == id } ?: return LayerStoreResult.Failure(LayerStoreError.UNAVAILABLE)
            if (operationId in receipts) return LayerStoreResult.Success(old)
            if (old.version != expectedVersion) return LayerStoreResult.Failure(LayerStoreError.CONFLICT)
            val next = old.copy(style = style, version = old.version + 1)
            rows(access, rows(access).map { if (it.id == id) next else it })
            receipts += operationId
            if (failListAfterStyleOnce) { failedLists++; failListAfterStyleOnce = false }
            if (invalidateAfterStyleOnce) { epoch++; invalidateAfterStyleOnce = false }
            return LayerStoreResult.Success(next)
        }
        override suspend fun patchAttributes(id: String, featureId: String, expectedVersion: Long, expectedFeatureVersion: Long, operationId: String, changes: List<LayerAttributeChange>, access: CartographicLayerAccess): LayerStoreResult<CartographicLayer> {
            patchCalls += PatchCall(id, expectedVersion, featureId, expectedFeatureVersion, operationId, changes)
            val old = rows(access).first { it.id == id }
            if (operationId in receipts) return LayerStoreResult.Success(old)
            val feature = old.features.first { it.id == featureId }
            if (old.version != expectedVersion || feature.version != expectedFeatureVersion) return LayerStoreResult.Failure(LayerStoreError.CONFLICT)
            if (!LayerAttributePolicy.validate(old, feature, changes)) return LayerStoreResult.Failure(LayerStoreError.INVALID_INPUT)
            val attributes = feature.localAttributes.toMutableMap()
            val removed = feature.removedAttributes.toMutableSet()
            for (change in changes) {
                val value = change.value
                if (value == null) { attributes.remove(change.field); removed += change.field }
                else { attributes[change.field] = value; removed -= change.field }
            }
            val next = old.copy(version = old.version + 1, features = old.features.map {
                if (it.id == featureId) it.copy(version = it.version + 1, localAttributes = attributes, removedAttributes = removed) else it
            })
            rows(access, rows(access).map { if (it.id == id) next else it })
            receipts += operationId
            return LayerStoreResult.Success(next)
        }
        override suspend fun delete(id: String, expectedVersion: Long, operationId: String, access: CartographicLayerAccess): LayerStoreResult<Unit> {
            deletes++
            if (operationId in receipts) return LayerStoreResult.Success(Unit)
            val old = rows(access).firstOrNull { it.id == id } ?: return LayerStoreResult.Failure(LayerStoreError.UNAVAILABLE)
            if (old.version != expectedVersion) return LayerStoreResult.Failure(LayerStoreError.CONFLICT)
            rows(access, rows(access).filterNot { it.id == id })
            receipts += operationId
            return LayerStoreResult.Success(Unit)
        }
    }

    companion object {
        private fun layer(id: String = "layer") = CartographicLayer(
            id, "Imported oaks", LayerSource("oaks.geojson", "GEOJSON", "unchanged-source-hash", "source-bytes"),
            listOf(point("f-0", buildJsonObject {
                put("name", "oak"); put("count", 1); put("enabled", true); put("nullable", JsonNull)
                put("lat", 45.0); put("nested", buildJsonObject { put("original", true) })
            })), recordedAt = 100,
        )
        private fun point(id: String, attributes: JsonObject) = LayerFeature(id, buildJsonObject {
            put("type", "Feature")
            put("geometry", buildJsonObject {
                put("type", "Point"); put("coordinates", JsonArray(listOf(JsonPrimitive(2.0), JsonPrimitive(45.0))))
            })
            put("properties", attributes)
        })
    }
}
