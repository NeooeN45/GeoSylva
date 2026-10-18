package com.forestry.counter.presentation.screens.forestry

import androidx.lifecycle.ViewModelStore
import com.forestry.counter.domain.calculation.ForestryCalculator
import com.forestry.counter.domain.calculation.ForestrySynthesisParams
import com.forestry.counter.domain.calculation.SynthesisParameters
import com.forestry.counter.domain.calculation.SynthesisReport
import com.forestry.counter.domain.calculation.SynthesisReportCalculator
import com.forestry.counter.domain.calculation.tarifs.TarifSelection
import com.forestry.counter.domain.model.*
import com.forestry.counter.domain.repository.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MartelagePlotSynthesisTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var fixture: Fixture
    @Before fun setup() { Dispatchers.setMain(dispatcher); fixture = Fixture() }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun plot_route_selects_only_its_campaign_and_never_assigns_the_legacy_parcel_campaign() = runTest(dispatcher) {
        val vm = fixture.start(this, "plot-A")
        assertEquals(listOf("session-A"), vm.uiState.value.sessions.map { it.sessionId })
        assertEquals("plot-A", vm.uiState.value.report!!.selectedPlotId)
        assertEquals(listOf("tree-A"), vm.uiState.value.observations.map { it.id })
        assertEquals(1, vm.uiState.value.report!!.observationCount)
        assertEquals(1, vm.uiState.value.dendrometry?.recordedCount)
        assertEquals(listOf("tree-A"), vm.uiState.value.dendrometry?.observationReferences?.map { it.id })
        assertEquals(100.0, vm.uiState.value.report!!.surfaceM2!!, 0.0)
        coVerify(exactly = 0) { fixture.sessions.getByParcelle(any()) }
    }

    @Test fun closing_a_plot_seals_only_its_plot_inputs_and_matches_the_domain_fingerprint() = runTest(dispatcher) {
        val vm = fixture.start(this, "plot-A")
        vm.closeSelectedSession(); runCurrent()
        val captured = fixture.closed.single()
        assertEquals("session-A", captured.first)
        assertEquals(MartelageSnapshotFingerprint.compute(fixture.parcel, listOf(fixture.plotA), listOf(fixture.treeA)), captured.second.sourceFingerprint)
        val report = Json.decodeFromJsonElement(SynthesisReport.serializer(), captured.second.results)
        assertEquals("plot-A", report.selectedPlotId); assertEquals(1, report.observationCount)
        assertEquals(100.0, report.surfaceM2!!, 0.0)
    }

    @Test fun a_parent_route_can_consult_a_specific_plot_campaign_without_summing_other_visits() = runTest(dispatcher) {
        val vm = fixture.start(this, null)
        vm.chooseSession("session-B"); runCurrent()
        assertEquals(listOf("tree-B"), vm.uiState.value.observations.map { it.id })
        assertEquals("plot-B", vm.uiState.value.report!!.selectedPlotId)
        assertEquals(1, vm.uiState.value.report!!.observationCount)
        assertEquals(listOf("tree-B"), vm.uiState.value.dendrometry?.observationReferences?.map { it.id })
    }

    @Test fun changing_the_plot_filter_cannot_move_a_managed_plot_campaign() = runTest(dispatcher) {
        val vm = fixture.start(this, "plot-A")
        vm.choosePlot("plot-B"); runCurrent()
        assertEquals("plot-A", vm.uiState.value.report!!.selectedPlotId)
        assertEquals(listOf("tree-A"), vm.uiState.value.observations.map { it.id })
    }

    @Test fun an_unqualified_tariff_cannot_produce_a_new_preview_volume() = runTest(dispatcher) {
        val vm = fixture.start(this, "plot-A", TarifSelection("FGH"))
        val state = vm.uiState.value
        assertNull(state.report!!.volumeM3)
        assertEquals(0, state.report!!.volumeComputedCount)
        assertEquals(mapOf("UNQUALIFIED_TARIF" to 1), state.report!!.uncomputedReasons)
        assertTrue(state.report!!.methods.isEmpty())
        assertEquals(SynthesisReport.CURRENT_ENGINE_VERSION, state.report!!.engineVersion)
        assertEquals(SynthesisParameters.CURRENT_SOURCE_POLICY, state.parameters.sourcePolicy)
        assertEquals("FGH", state.parameters.tarifSelection!!.method)
    }

    @Test fun closing_records_the_same_unavailable_volume_and_explicit_new_policy() = runTest(dispatcher) {
        val vm = fixture.start(this, "plot-A", TarifSelection("SCHAEFFER_1E", schaefferNumero = 4))
        val preview = vm.uiState.value.report!!
        vm.closeSelectedSession(); runCurrent()
        val calculation = fixture.closed.single().second
        val report = Json.decodeFromJsonElement(SynthesisReport.serializer(), calculation.results)
        val parameters = Json.decodeFromJsonElement(SynthesisParameters.serializer(), calculation.parameters)
        assertNull(report.volumeM3)
        assertEquals(preview.uncomputedReasons, report.uncomputedReasons)
        assertEquals(preview.methods, report.methods)
        assertEquals(SynthesisReport.CURRENT_ENGINE_VERSION, calculation.engineVersion)
        assertEquals(calculation.engineVersion, report.engineVersion)
        assertEquals(SynthesisParameters.CURRENT_SOURCE_POLICY, parameters.sourcePolicy)
        assertEquals("SCHAEFFER_1E", parameters.tarifSelection!!.method)
        assertEquals(4, parameters.tarifSelection!!.schaefferNumero)
    }

    @Test fun a_closed_historical_volume_is_read_without_recalculating_with_new_parameters() = runTest(dispatcher) {
        val archived = fixture.historicalSnapshot()
        val before = MartelageSnapshotCodec.encode(archived)
        val vm = fixture.start(this, "plot-A", TarifSelection("COEF_FORME"), archived)
        assertTrue(vm.uiState.value.closed)
        assertEquals(SynthesisReport.ENGINE_VERSION, vm.uiState.value.report!!.engineVersion)
        assertEquals(0.89123, vm.uiState.value.report!!.volumeM3!!, 0.0)
        assertEquals(0.89123, vm.uiState.value.report!!.perEssence.single().volumeM3!!, 0.0)
        assertEquals(1, vm.uiState.value.report!!.perEssence.single().volumeComputedCount)
        assertEquals(listOf("FGH"), vm.uiState.value.report!!.methods)
        assertEquals(before, MartelageSnapshotCodec.encode(vm.uiState.value.snapshot!!))
        vm.closeSelectedSession(); runCurrent()
        assertTrue(fixture.closed.isEmpty())
    }

    private inner class Fixture {
        val parcel = Parcelle("parcel", null, "canonical-forest", "Parcel", 1.0, null, null, null, null, null,
            null, null, null, null, null, null, null, null, createdAt = 1L, updatedAt = 2L)
        val plotA = Placette("plot-A", "parcel", "Plot A", "AREA", null, 100.0, null, createdAt = 1L, updatedAt = 2L)
        val plotB = plotA.copy(id = "plot-B", name = "Plot B", surfaceM2 = 200.0)
        val sessionA = InventaireSession("session-A", "parcel", MartelageStates.TYPE, 1L, null, null, null, null, null, null,
            martelageState = MartelageStates.PAUSED, campaignId = "campaign-A", placetteId = "plot-A")
        val sessionB = sessionA.copy(sessionId = "session-B", campaignId = "campaign-B", placetteId = "plot-B")
        val legacy = sessionA.copy(sessionId = "legacy-session", campaignId = "legacy-campaign", placetteId = null)
        val treeA = Tige("tree-A", "parcel", "plot-A", "CH_SESSILE", 40.0, 20.0, null, null, null,
            10L, null, null, null, null, sessionId = "session-A")
        val treeB = treeA.copy(id = "tree-B", placetteId = "plot-B", sessionId = "session-B")
        val sessions = mockk<InventaireSessionRepository>()
        val closed = mutableListOf<Pair<String, MartelageCalculationSnapshotV1>>()
        fun historicalSnapshot(): MartelageSnapshotV1 {
            // Explicit TEST fixture of an older sealed result, never a new scientific estimate.
            val parameters = SynthesisParameters(TarifSelection("FGH"), selectedPlotId = "plot-A")
            val report = SynthesisReportCalculator.calculate(parcel, listOf(plotA), listOf(treeA),
                emptyMap(), parameters, sessionA.sessionId, sessionA.campaignId, sessionA.revisionNumber, 2L)
                .let { descriptive ->
                    descriptive.copy(engineVersion = SynthesisReport.ENGINE_VERSION, volumeM3 = 0.89123,
                        volumeComputedCount = 1, methods = listOf("FGH"), uncomputedReasons = emptyMap(),
                        perEssence = descriptive.perEssence.map {
                            it.copy(volumeM3 = 0.89123, volumeComputedCount = 1)
                        })
                }
            val json = Json { encodeDefaults = true }
            val calculation = MartelageCalculationSnapshotV1(2L, report.engineVersion,
                json.encodeToJsonElement(parameters).jsonObject, json.encodeToJsonElement(report).jsonObject,
                MartelageSnapshotFingerprint.compute(parcel, listOf(plotA), listOf(treeA)))
            return MartelageSnapshotV1(2, sessionA.sessionId, parcel.id, requireNotNull(sessionA.campaignId),
                sessionA.revisionNumber, null, 1L, 2L, 0L, "TEST", parcel, listOf(plotA), listOf(treeA),
                calculation, placetteId = plotA.id)
        }

        fun start(scope: TestScope, plotId: String?, tariff: TarifSelection? = null,
                  closedSnapshot: MartelageSnapshotV1? = null): MartelageViewModel {
            val parcels = mockk<ParcelleRepository>(); every { parcels.getAllParcelles() } returns MutableStateFlow(listOf(parcel))
            val plots = mockk<PlacetteRepository>(); every { plots.getPlacettesByParcelle("parcel") } returns MutableStateFlow(listOf(plotA, plotB))
            val trees = mockk<TigeRepository>(); every { trees.getAllTiges() } returns MutableStateFlow(listOf(treeA, treeB))
            val essences = mockk<EssenceRepository>(); every { essences.getAllEssences() } returns MutableStateFlow(emptyList())
            val effectiveSessionA = if (closedSnapshot != null) sessionA.copy(martelageState = MartelageStates.CLOSED,
                dateFin = closedSnapshot.closedAt, activeDurationMs = closedSnapshot.activeDurationMs) else sessionA
            every { sessions.getByParcelle("parcel") } returns MutableStateFlow(listOf(effectiveSessionA, sessionB, legacy))
            every { sessions.getByPlacette("plot-A") } returns MutableStateFlow(listOf(effectiveSessionA))
            coEvery { sessions.getClosedSnapshot("session-A") } returns closedSnapshot
            coEvery { sessions.closeMartelage(any(), any()) } coAnswers {
                closed += firstArg<String>() to requireNotNull(secondArg<MartelageCalculationSnapshotV1?>()); sessionA
            }
            val calculator = mockk<ForestryCalculator>()
            coEvery { calculator.loadSynthesisParams() } returns ForestrySynthesisParams(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), tariff)
            coEvery { calculator.diameterClasses() } returns listOf(35, 40, 45)
            val vm = MartelageViewModel("PLACETTE", "canonical-forest", "parcel", plotId, essences, trees, parcels,
                plots, sessions, calculator, calculationDispatcher = dispatcher)
            store.put("plot-synthesis", vm); scope.runCurrent(); return vm
        }
    }
}
