package com.forestry.counter.presentation.screens.forestry

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forestry.counter.R
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.calculation.ForestryCalculator
import com.forestry.counter.domain.calculation.SynthesisReport
import com.forestry.counter.domain.calculation.RecordedDendrometryStatus
import com.forestry.counter.domain.calculation.synthesisDiameterClass
import com.forestry.counter.domain.calculation.tarifs.TarifCalculator
import com.forestry.counter.domain.calculation.tarifs.TarifMethod
import com.forestry.counter.domain.calculation.tarifs.TarifSelection
import com.forestry.counter.domain.model.MartelageStates
import com.forestry.counter.domain.repository.EssenceRepository
import com.forestry.counter.domain.repository.IbpRepository
import com.forestry.counter.domain.repository.InventaireSessionRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.domain.usecase.export.PdfSynthesisExporter
import com.forestry.counter.domain.usecase.export.IllustratedSynthesisDocument
import com.forestry.counter.domain.usecase.export.IllustratedSynthesisExporter
import com.forestry.counter.domain.usecase.export.SpecializedReportType
import com.forestry.counter.domain.usecase.export.SpecializedSynthesisDocument
import com.forestry.counter.domain.usecase.export.SpecializedSynthesisExporter
import com.forestry.counter.domain.usecase.export.captureIllustratedSynthesisDocument
import com.forestry.counter.domain.usecase.export.buildSynthesisGeoJson
import com.forestry.counter.domain.usecase.export.decodedSynthesisTrace
import com.forestry.counter.domain.usecase.export.SynthesisDocument
import com.forestry.counter.domain.usecase.export.SynthesisReadGuard
import com.forestry.counter.domain.usecase.export.guardedSynthesisOutput
import com.forestry.counter.domain.usecase.export.synthesisVolumeReasonResource
import com.forestry.counter.domain.usecase.export.currentSynthesisVolumeReasons
import com.forestry.counter.domain.usecase.export.synthesisEngineDescriptionResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

private data class CapturedSynthesisExport(
    val document: SynthesisDocument,
    val guard: SynthesisReadGuard,
    val illustrated: IllustratedSynthesisDocument? = null,
    val specialized: SpecializedSynthesisDocument? = null,
)

private data class CapturedSpecializedDialog(
    val illustrated: IllustratedSynthesisDocument,
    val guard: SynthesisReadGuard,
)

/** A transient report form cannot adopt a later selection, revision or access generation. */
private fun sameSpecializedScope(current: SynthesisUiState, captured: SynthesisUiState): Boolean =
    current.accessSnapshot == captured.accessSnapshot && current.selection == captured.selection &&
        current.session == captured.session && current.report == captured.report && current.snapshot == captured.snapshot &&
        current.sealedCalculation && captured.sealedCalculation

/** Existing route retained; this screen presents a single observation campaign/revision. */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MartelageScreen(
    scope: String,
    forestId: String?,
    parcelleId: String?,
    placetteId: String?,
    essenceRepository: EssenceRepository,
    tigeRepository: TigeRepository,
    parcelleRepository: ParcelleRepository,
    forestryCalculator: ForestryCalculator,
    userPreferences: UserPreferencesManager,
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToPriceTablesEditor: (() -> Unit)? = null,
    onNavigateToMap: ((String) -> Unit)? = null,
    ibpRepository: IbpRepository? = null,
    onNavigateToIbp: ((String, String) -> Unit)? = null,
    onNavigateToIbpHistory: ((String, String?) -> Unit)? = null,
    onNavigateToSuperCorrelateur: ((String) -> Unit)? = null,
    onNavigateToStandClassification: ((String) -> Unit)? = null,
    placetteRepository: PlacetteRepository? = null,
    sessionRepository: InventaireSessionRepository? = null,
    initialSessionId: String? = null,
    businessReadAccess: BusinessReadAccess? = null,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val factory = remember(scope, forestId, parcelleId, placetteId, businessReadAccess) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MartelageViewModel(
                scope, forestId, parcelleId, placetteId, essenceRepository, tigeRepository,
                parcelleRepository, placetteRepository, sessionRepository, forestryCalculator, businessReadAccess,
            ) as T
        }
    }
    val vm: MartelageViewModel = viewModel(key = "synthesis:$scope:$forestId:$parcelleId:$placetteId", factory = factory)
    val capturedState by vm.uiState.collectAsStateWithLifecycle()
    val state = if (vm.isReadable(capturedState)) capturedState else SynthesisUiState(loading = false)
    LaunchedEffect(initialSessionId) { initialSessionId?.let(vm::chooseSession) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val coroutines = rememberCoroutineScope()
    var sectionName by rememberSaveable { mutableStateOf(SynthesisSection.MANAGEMENT.name) }
    val section = SynthesisSection.entries.firstOrNull { it.name == sectionName } ?: SynthesisSection.MANAGEMENT
    var showSources by remember(state.report?.parcelId, state.report?.sessionId,
        state.report?.selectedPlotId, state.report?.revisionNumber, state.accessSnapshot) { mutableStateOf(false) }
    var showHeightHelp by remember(state.report?.parcelId, state.report?.sessionId,
        state.report?.revisionNumber, state.accessSnapshot) { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var closeConfirmation by remember { mutableStateOf(false) }
    var heightDialog by remember { mutableStateOf(false) }
    var pendingDocument by remember { mutableStateOf<CapturedSynthesisExport?>(null) }
    var specializedDialog by remember { mutableStateOf<CapturedSpecializedDialog?>(null) }
    var archiveSuccess by remember(state.session?.sessionId) { mutableStateOf(false) }
    val traceJson = if (state.closed) state.snapshot?.traceJson else state.session?.traceJson
    val trace = remember(traceJson) { decodedSynthesisTrace(traceJson) }
    val document = remember(state.report, state.snapshot, state.parameters, traceJson) {
        state.report?.let { report -> SynthesisDocument(
            report = report,
            closed = state.sealedCalculation,
            closedAt = state.snapshot?.closedAt,
            sourceFingerprint = state.snapshot?.calculation?.sourceFingerprint,
            parameters = state.snapshot?.calculation?.parameters ?: Json { encodeDefaults = true }.encodeToJsonElement(state.parameters).jsonObject,
            observations = state.observations.filter { report.selectedPlotId == null || it.placetteId == report.selectedPlotId },
            traceJson = traceJson,
        ) }
    }
    val illustratedDocument = remember(document, state.snapshot, state.parcel, state.plots) {
        document?.let { input -> runCatching {
            captureIllustratedSynthesisDocument(input, state.snapshot, state.parcel, state.plots)
        }.getOrNull() }
    }
    suspend fun export(uri: Uri?, kind: String) {
        val captured = pendingDocument ?: return
        pendingDocument = null
        if (uri == null) return
        try {
            withContext(Dispatchers.IO) {
                val guard = captured.guard
                val input = captured.document
                when (kind) {
                    "pdf" -> PdfSynthesisExporter.exportGuarded(context, uri, input, guard)
                    "illustrated_pdf" -> IllustratedSynthesisExporter.exportGuarded(context, uri,
                        requireNotNull(captured.illustrated), guard)
                    "specialized_pdf" -> SpecializedSynthesisExporter.exportGuarded(context, uri,
                        requireNotNull(captured.specialized), guard)
                    else -> guardedSynthesisOutput(guard, produce = {
                        if (kind == "json") Json { encodeDefaults = true; prettyPrint = true }.encodeToString(input).toByteArray(Charsets.UTF_8)
                        else {
                            val (geojson, count) = buildSynthesisGeoJson(input)
                            require(count > 0)
                            geojson.toByteArray(Charsets.UTF_8)
                        }
                    }, output = { bytes ->
                        guard.requireRevalidated()
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            var offset = 0
                            while (offset < bytes.size) {
                                guard.requireCurrent()
                                val length = minOf(8_192, bytes.size - offset)
                                output.write(bytes, offset, length)
                                offset += length
                            }
                            guard.requireCurrent()
                        } ?: error("OUTPUT_UNAVAILABLE")
                    })
                }
            }
            captured.guard.requireCurrent()
            snackbar.showSnackbar(context.getString(R.string.synthesis_export_done))
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            snackbar.showSnackbar(context.getString(R.string.synthesis_error))
        }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> coroutines.launch { export(uri, "pdf") } }
    val illustratedPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> coroutines.launch { export(uri, "illustrated_pdf") } }
    val specializedPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> coroutines.launch { export(uri, "specialized_pdf") } }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> coroutines.launch { export(uri, "json") } }
    val geoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/geo+json")) { uri -> coroutines.launch { export(uri, "geojson") } }
    fun requestExport(kind: String) {
        val input = document ?: return
        val capturedIllustrated = if (kind == "illustrated_pdf") illustratedDocument ?: return else null
        val originalState = state
        coroutines.launch {
            val guard = vm.captureExportGuard(originalState) ?: return@launch
            guard.requireCurrent()
            pendingDocument = CapturedSynthesisExport(input, guard, capturedIllustrated)
            when (kind) {
                "pdf" -> pdfLauncher.launch("GeoSylva-synthese-r${input.report.revisionNumber ?: 0}.pdf")
                "illustrated_pdf" -> illustratedPdfLauncher.launch("GeoSylva-rapport-illustre-r${input.report.revisionNumber ?: 0}.pdf")
                "json" -> jsonLauncher.launch("GeoSylva-synthese.json")
                else -> geoLauncher.launch("GeoSylva-tiges.geojson")
            }
        }
    }
    fun requestSpecializedReports() {
        val input = illustratedDocument?.takeIf { it.document.closed } ?: return
        val originalState = state
        coroutines.launch {
            val sourceGuard = vm.captureExportGuard(originalState) ?: return@launch
            val guard = SynthesisReadGuard(
                current = { sourceGuard.isCurrent() && sameSpecializedScope(vm.uiState.value, originalState) },
                validated = {
                    sourceGuard.requireRevalidated()
                    sameSpecializedScope(vm.uiState.value, originalState)
                },
            )
            if (!guard.isCurrent()) return@launch
            specializedDialog = CapturedSpecializedDialog(input, guard)
        }
    }
    LaunchedEffect(capturedState.accessSnapshot, capturedState.report, capturedState.session, capturedState.selection, capturedState.snapshot) {
        if (pendingDocument?.guard?.isCurrent() == false) pendingDocument = null
        if (specializedDialog?.guard?.isCurrent() == false) specializedDialog = null
        if (!vm.isReadable(capturedState)) { showExport = false; showSources = false; showHeightHelp = false; closeConfirmation = false; heightDialog = false; archiveSuccess = false; specializedDialog = null }
    }
    LaunchedEffect(document?.closed, document?.report?.sessionId, document?.sourceFingerprint, state.accessSnapshot) {
        val closedDocument = document?.takeIf { it.closed } ?: return@LaunchedEffect
        val originalState = state
        archiveSuccess = false
        try {
            val guard = vm.captureExportGuard(originalState) ?: return@LaunchedEffect
            withContext(Dispatchers.IO) { PdfSynthesisExporter.archiveClosedGuarded(context, closedDocument, guard) }
            guard.requireRevalidated()
            archiveSuccess = true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (vm.isReadable(originalState)) snackbar.showSnackbar(context.getString(R.string.synthesis_archive_failed))
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(stringResource(R.string.martelage_synthesis_title), fontWeight = FontWeight.Bold)
                    state.parcel?.let { Text(it.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    state.parcel?.let { parcel -> onNavigateToMap?.let { nav -> IconButton(onClick = { nav(parcel.id) }) { Icon(Icons.Default.Map, stringResource(R.string.map_view)) } } }
                    IconButton(enabled = state.report != null, onClick = { showSources = true }) {
                        Icon(Icons.Default.Description, stringResource(R.string.synthesis_sources))
                    }
                    IconButton(enabled = document != null, onClick = { showExport = true }) { Icon(Icons.Default.Download, stringResource(R.string.export)) }
                    DropdownMenu(expanded = showExport, onDismissRequest = { showExport = false }) {
                        DropdownMenuItem(text = { Column {
                            Text(stringResource(R.string.specialized_form_export_menu))
                            if (illustratedDocument?.document?.closed != true) Text(
                                stringResource(R.string.specialized_form_closed_required),
                                style = MaterialTheme.typography.bodySmall)
                        } }, enabled = illustratedDocument?.document?.closed == true, onClick = {
                            showExport = false; requestSpecializedReports()
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.illustrated_pdf_export)) },
                            enabled = illustratedDocument != null, onClick = {
                                showExport = false; requestExport("illustrated_pdf")
                            })
                        DropdownMenuItem(text = { Text(stringResource(R.string.synthesis_export_pdf)) }, onClick = {
                            showExport = false; requestExport("pdf")
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.synthesis_export_json)) }, onClick = {
                            showExport = false; requestExport("json")
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.synthesis_export_geojson)) }, onClick = {
                            showExport = false
                            if ((state.report?.locatedCount ?: 0) <= 0 && trace?.points.isNullOrEmpty()) coroutines.launch { snackbar.showSnackbar(context.getString(R.string.synthesis_export_empty_geo)) }
                            else requestExport("geojson")
                        })
                    }
                })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.synthesis_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (parcelleId == null) SelectionButton(
                label = state.parcel?.name ?: stringResource(R.string.synthesis_choose_parcel),
                choices = state.parcels.map { it.id to it.name }, selected = state.parcel?.id, onChoose = vm::chooseParcel,
            )
            if (state.parcel == null) {
                SummaryCard(stringResource(R.string.synthesis_choose_parcel)) { Text(stringResource(R.string.synthesis_no_parcel)) }
            } else {
                CampaignCard(state, vm)
                if (state.loading) CircularProgressIndicator()
                if (state.error) SummaryCard(stringResource(R.string.martelage_synthesis_title)) {
                    Text(stringResource(R.string.synthesis_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = vm::reloadParameters) { Text(stringResource(R.string.synthesis_refresh)) }
                }
                if (state.closed && !state.sealedCalculation) SummaryCard(stringResource(R.string.synthesis_closed)) {
                    Text(stringResource(R.string.synthesis_closed_no_calculation))
                    SummaryLine(stringResource(R.string.synthesis_stems), state.observations.size.toString())
                }
                if (!state.closed && state.session?.placetteId == null && placetteId == null && state.plots.isNotEmpty()) SelectionButton(
                    label = state.plots.firstOrNull { it.id == state.selection.plotId }?.name ?: stringResource(R.string.synthesis_plot_all),
                    choices = listOf("" to stringResource(R.string.synthesis_plot_all)) + state.plots.map { it.id to (it.name ?: it.id.take(8)) },
                    selected = state.selection.plotId ?: "", onChoose = { vm.choosePlot(it.takeIf(String::isNotEmpty)) },
                )
                val report = state.report
                if (report != null) {
                    SynthesisSectionSelector(selected = section, onSelected = { sectionName = it.name })
                    when (section) {
                        SynthesisSection.MANAGEMENT -> {
                            VolumeConditionsCard(report)
                            state.dendrometry?.let { analysis ->
                                key(report.parcelId, report.sessionId, report.selectedPlotId, report.revisionNumber, state.accessSnapshot) {
                                    DendrometryOverviewCard(analysis, onShowSources = { showSources = true })
                                }
                                DiameterDistributionCard(analysis)
                            }
                            UnqualifiedHeightIndicatorsCard(report.measuredHeightCount, report.observationCount,
                                onExplainMissing = { showHeightHelp = true })
                            SynthesisDetails(stringResource(R.string.synthesis_observations), report) {
                                ObservationSummary(report)
                            }
                            SynthesisDetails(stringResource(R.string.synthesis_calculations), report) {
                                CalculationSummary(state, vm, onHeightDialog = { heightDialog = true })
                            }
                        }
                        SynthesisSection.WOOD -> {
                            VolumeConditionsCard(report)
                            SummaryCard(stringResource(R.string.synthesis_volume)) {
                                SummaryLine(stringResource(R.string.synthesis_volume), report.volumeM3?.takeIf {
                                    it.isFinite() && it >= 0.0
                                }?.let {
                                    "${formatSynthesisNumber(it, 3)} m³"
                                } ?: stringResource(R.string.synthesis_unknown))
                                Text(stringResource(if (report.isVolumeComplete) R.string.synthesis_volume_complete
                                    else R.string.synthesis_volume_partial, report.volumeComputedCount, report.observationCount))
                                Text(stringResource(R.string.synthesis_sections_wood_scope),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            WoodQualityCoverageCard(report)
                            EconomySummary(report, onNavigateToPriceTablesEditor ?: onNavigateToSettings)
                        }
                        SynthesisSection.ECOLOGY -> EcologicalObservationCard(report)
                    }
                    if (!state.closed && state.session?.martelageState in MartelageStates.ACTIVE) FilledTonalButton(
                        onClick = { closeConfirmation = true }, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                    ) { Text(stringResource(R.string.synthesis_close), Modifier.padding(vertical = 6.dp)) }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
    if (showSources && state.report != null) ModalBottomSheet(onDismissRequest = { showSources = false }) {
        val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.8f
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.synthesis_sources), style = MaterialTheme.typography.headlineSmall)
            SourcesSummary(state, archiveSuccess)
            Spacer(Modifier.height(16.dp))
        }
    }
    if (showHeightHelp && state.report != null) HeightProtocolHelpDialog(
        recordedHeightCount = state.report.measuredHeightCount,
        observationCount = state.report.observationCount,
        onDismiss = { showHeightHelp = false },
    )
    if (closeConfirmation) AlertDialog(onDismissRequest = { if (!busy) closeConfirmation = false },
        title = { Text(stringResource(R.string.synthesis_close_title)) }, text = { Text(stringResource(
            if (state.session?.placetteId != null) R.string.synthesis_plot_scope_close else R.string.synthesis_close_body)) },
        confirmButton = { TextButton(enabled = !busy, onClick = { closeConfirmation = false; vm.closeSelectedSession() }) { Text(stringResource(R.string.synthesis_close_confirm)) } },
        dismissButton = { TextButton(onClick = { closeConfirmation = false }) { Text(stringResource(R.string.cancel)) } })
    if (heightDialog) ClassHeightDialog(state, onSave = { code, diam, value -> vm.setClassHeight(code, diam, value); heightDialog = false }, onDismiss = { heightDialog = false })
    // Keep the exact captured input and permission across the form and Android document picker.
    val capturedSpecialized = specializedDialog?.takeIf { it.guard.isCurrent() }
    if (capturedSpecialized != null) SpecializedReportDialog(
        illustrated = capturedSpecialized.illustrated,
        onDismiss = { specializedDialog = null },
        onExport = { input ->
            coroutines.launch {
                try {
                    if (specializedDialog !== capturedSpecialized || input.illustrated != capturedSpecialized.illustrated)
                        return@launch
                    capturedSpecialized.guard.requireRevalidated()
                    if (specializedDialog !== capturedSpecialized) return@launch
                    pendingDocument = CapturedSynthesisExport(input.illustrated.document,
                        capturedSpecialized.guard, input.illustrated, input)
                    specializedDialog = null
                    val label = when (input.type) {
                        SpecializedReportType.REVENUE -> "recettes"
                        SpecializedReportType.COSTS -> "couts"
                        SpecializedReportType.ENVIRONMENTAL -> "environnement"
                    }
                    specializedPdfLauncher.launch("GeoSylva-rapport-$label-r${input.illustrated.document.report.revisionNumber ?: 0}-edition${SpecializedSynthesisExporter.TEMPLATE_VERSION}.pdf")
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    specializedDialog = null
                    pendingDocument = null
                    snackbar.showSnackbar(context.getString(R.string.synthesis_error))
                }
            }
        },
    )
}

@Composable
internal fun VolumeConditionsCard(report: SynthesisReport) {
    val affectedCount = currentSynthesisVolumeReasons(report)["UNQUALIFIED_TARIF"]?.takeIf { it > 0 } ?: return
    Card(
        modifier = Modifier.fillMaxWidth().testTag("synthesis_volume_conditions"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Info, contentDescription = null)
                Text(stringResource(R.string.synthesis_volume_conditions_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(stringResource(R.string.synthesis_volume_conditions_body), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.synthesis_volume_conditions_count, affectedCount, report.observationCount),
                style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.synthesis_volume_conditions_commercial),
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun CampaignCard(state: SynthesisUiState, vm: MartelageViewModel) {
    SummaryCard(stringResource(R.string.synthesis_campaign)) {
        val sessionLabel = state.session?.let { "${formatSynthesisDate(it.dateDebut)} · ${stringResource(R.string.synthesis_revision, it.revisionNumber)}" }
            ?: state.selection.legacyDay?.let { stringResource(R.string.synthesis_legacy, it) }
            ?: stringResource(R.string.synthesis_no_campaign)
        val choices = state.sessions.map { it.sessionId to "${formatSynthesisDate(it.dateDebut)} · ${stringResource(R.string.synthesis_revision, it.revisionNumber)}" } +
            state.legacyDays.map { "legacy:$it" to stringResource(R.string.synthesis_legacy, it) }
        SelectionButton(sessionLabel, choices, state.selection.sessionId ?: state.selection.legacyDay?.let { "legacy:$it" }, onChoose = {
            if (it.startsWith("legacy:")) vm.chooseLegacyDay(it.removePrefix("legacy:")) else vm.chooseSession(it)
        })
        val status = when (state.session?.martelageState) {
            MartelageStates.CLOSED -> R.string.synthesis_closed
            MartelageStates.PAUSED -> R.string.synthesis_paused
            MartelageStates.RUNNING -> R.string.synthesis_running
            else -> R.string.synthesis_provisional
        }
        AssistChip(onClick = {}, label = { Text(stringResource(status)) })
        state.session?.placetteId?.let { plotId ->
            Text(stringResource(R.string.synthesis_plot_scope, state.plots.firstOrNull { it.id == plotId }?.name ?: plotId),
                style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(if (state.closed) R.string.synthesis_closed_hint else if (state.session == null) R.string.synthesis_legacy_hint else R.string.synthesis_campaign_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ObservationSummary(report: SynthesisReport) {
    if (report.observationCount == 0) SummaryCard(stringResource(R.string.synthesis_observations)) { Text(stringResource(R.string.synthesis_no_observations)) }
    SummaryCard(stringResource(R.string.synthesis_observations)) {
        SummaryLine(stringResource(R.string.synthesis_stems), report.observationCount.toString())
        SummaryLine(stringResource(R.string.synthesis_sections_recorded_heights), stringResource(R.string.synthesis_coverage, report.measuredHeightCount, report.observationCount))
        SummaryLine(stringResource(R.string.synthesis_locations), stringResource(R.string.synthesis_coverage, report.locatedCount, report.observationCount))
        SummaryLine(stringResource(R.string.synthesis_qualities), stringResource(R.string.synthesis_coverage, report.qualityCount, report.observationCount))
        Text(stringResource(R.string.synthesis_quality_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        report.firstObservationAt?.let { first -> Text("${formatSynthesisDate(first)} — ${formatSynthesisDate(report.lastObservationAt ?: first)}", style = MaterialTheme.typography.bodySmall) }
    }
    SurfaceSummary(report)
    if (report.perEssence.isNotEmpty()) SummaryCard(stringResource(R.string.synthesis_species)) {
        report.perEssence.forEach { row -> SummaryLine(row.name, row.count.toString()) }
    }
    if (report.categories.isNotEmpty()) SummaryCard(stringResource(R.string.synthesis_categories)) {
        report.categories.forEach { (category, count) -> SummaryLine(category, count.toString()) }
    }
}

/** Reading details cannot change the recorded scope or revive a previous revision's UI state. */
@Composable
private fun SynthesisDetails(title: String, report: SynthesisReport, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(report.parcelId, report.sessionId, report.revisionNumber, report.selectedPlotId) {
        mutableStateOf(false)
    }
    val description = stringResource(if (expanded) R.string.synthesis_sections_details_hide else R.string.synthesis_sections_details_show)
    OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()
        .semantics { stateDescription = description }, shape = RoundedCornerShape(20.dp)) {
        Text(title, Modifier.weight(1f).padding(vertical = 6.dp))
        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
    }
    if (expanded) content()
}

@Composable
private fun SurfaceSummary(report: SynthesisReport) {
    SummaryCard(stringResource(R.string.synthesis_surface)) {
        Text(report.surfaceM2?.let { "${formatSynthesisNumber(it, 2)} m² · ${formatSynthesisNumber(it / 10_000.0, 4)} ha" } ?: stringResource(R.string.synthesis_unknown),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(stringResource(when {
            report.surfaceReason in setOf("SAMPLING_BOUNDARY_UNKNOWN", "INVALID_SAMPLING_PLAN") -> R.string.synthesis_surface_boundary_warning
            report.surfaceReason == "CHANGED_PARCEL_AREA" || report.surfaceReason == "INCONSISTENT_PLOT_AREA" -> R.string.synthesis_surface_changed
            report.surfaceM2 == null -> R.string.synthesis_surface_unresolved
            else -> R.string.synthesis_surface_observations
        }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CalculationSummary(state: SynthesisUiState, vm: MartelageViewModel, onHeightDialog: () -> Unit) {
    val report = state.report ?: return
    val verified = state.dendrometry?.takeIf { it.status == RecordedDendrometryStatus.AVAILABLE }
    if (!state.closed) SummaryCard(stringResource(R.string.synthesis_method)) {
        val selection = state.parameters.tarifSelection
        val method = selection?.method?.let { TarifMethod.fromCode(it) }
        SelectionButton(method?.label ?: stringResource(R.string.synthesis_method_missing), TarifMethod.entries.map { it.code to it.label }, method?.code, onChoose = { code ->
            vm.chooseTarif(TarifSelection(method = code))
        })
        val numbers = method?.let(TarifCalculator::availableTarifNumbers)
        if (numbers != null) {
            val selected = when (method) { TarifMethod.SCHAEFFER_1E, TarifMethod.SCHAEFFER_2E -> selection?.schaefferNumero; else -> selection?.ifnNumero }
            SelectionButton(selected?.toString() ?: stringResource(R.string.synthesis_number_missing), numbers.map { it.toString() to it.toString() }, selected?.toString(), onChoose = {
                if (method == TarifMethod.SCHAEFFER_1E || method == TarifMethod.SCHAEFFER_2E) vm.chooseTarif(selection!!.copy(schaefferNumero = it.toInt()))
                else vm.chooseTarif(selection!!.copy(ifnNumero = it.toInt()))
            })
        }
        Text(stringResource(R.string.synthesis_method_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onHeightDialog, enabled = report.observationCount > 0) { Text(stringResource(R.string.synthesis_height_modes)) }
    }
    SummaryCard(stringResource(R.string.synthesis_calculations)) {
        SummaryLine(stringResource(R.string.synthesis_basal), verified?.basalAreaM2?.let {
            "${formatSynthesisNumber(it, 3)} m²"
        } ?: stringResource(R.string.synthesis_unknown))
        SummaryLine(stringResource(R.string.synthesis_volume), report.volumeM3?.takeIf {
            it.isFinite() && it >= 0.0
        }?.let { "${formatSynthesisNumber(it, 3)} m³" } ?: stringResource(R.string.synthesis_unknown))
        Text(stringResource(if (report.isVolumeComplete) R.string.synthesis_volume_complete else R.string.synthesis_volume_partial, report.volumeComputedCount, report.observationCount),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SummaryLine(stringResource(R.string.synthesis_diameter), verified?.meanDiameterCm?.let { "${formatSynthesisNumber(it, 1)} cm" } ?: stringResource(R.string.synthesis_unknown))
        SummaryLine(stringResource(R.string.synthesis_quadratic), verified?.quadraticDiameterCm?.let { "${formatSynthesisNumber(it, 1)} cm" } ?: stringResource(R.string.synthesis_unknown))
        if (report.methods.isNotEmpty()) Text(report.methods.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
    }
    if (report.surfaceM2 != null) SummaryCard(stringResource(R.string.synthesis_surface)) {
        Text(report.surfaceM2.takeIf { it.isFinite() && it > 0.0 }?.let {
            "${formatSynthesisNumber(it, 2)} m²"
        } ?: stringResource(R.string.synthesis_unknown))
        Text(stringResource(R.string.synthesis_sections_surface_protocol), style = MaterialTheme.typography.bodySmall)
    }
    if (report.uncomputedReasons.isNotEmpty()) SummaryCard(stringResource(R.string.synthesis_uncomputed)) {
        report.uncomputedReasons.forEach { (reason, count) -> SummaryLine(stringResource(synthesisReasonResource(reason)), count.toString()) }
    }
    report.perEssence.forEach { row -> SummaryCard(row.name) {
        SummaryLine(stringResource(R.string.synthesis_stems), row.count.toString())
        SummaryLine(stringResource(R.string.synthesis_basal), row.basalAreaM2.takeIf {
            verified != null && it.isFinite() && it >= 0.0
        }?.let { "${formatSynthesisNumber(it, 3)} m²" } ?: stringResource(R.string.synthesis_unknown))
        SummaryLine(stringResource(R.string.synthesis_volume), row.volumeM3?.takeIf {
            it.isFinite() && it >= 0.0
        }?.let { "${formatSynthesisNumber(it, 3)} m³" } ?: stringResource(R.string.synthesis_unknown))
        Text(stringResource(R.string.synthesis_coverage, row.volumeComputedCount, row.count), style = MaterialTheme.typography.bodySmall)
    } }
}

@Composable
private fun EconomySummary(report: SynthesisReport, navigatePrices: (() -> Unit)?) {
    SummaryCard(stringResource(R.string.synthesis_values)) {
        Text(report.enteredValueEur?.let { "${formatSynthesisNumber(it, 2)} €" } ?: stringResource(R.string.synthesis_unknown),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(if (report.enteredValueEur == null) R.string.synthesis_no_values else R.string.synthesis_value_coverage,
            report.enteredValueCount, report.observationCount), style = MaterialTheme.typography.bodySmall)
    }
    SummaryCard(stringResource(R.string.synthesis_costs)) {
        SummaryLine(stringResource(R.string.synthesis_costs), stringResource(R.string.synthesis_unknown))
        SummaryLine(stringResource(R.string.synthesis_net), stringResource(R.string.synthesis_unknown))
        Text(stringResource(R.string.synthesis_cost_hint), style = MaterialTheme.typography.bodySmall)
        navigatePrices?.let { TextButton(onClick = it) { Text(stringResource(R.string.synthesis_configure_prices)) } }
    }
}

@Composable
private fun SourcesSummary(state: SynthesisUiState, archiveSuccess: Boolean) {
    val report = state.report ?: return
    val rawTrace = if (state.closed) state.snapshot?.traceJson else state.session?.traceJson
    val trace = remember(rawTrace) { decodedSynthesisTrace(rawTrace) }
    SummaryCard(stringResource(R.string.synthesis_trace_positions)) {
        if (rawTrace == null) Text(stringResource(R.string.synthesis_trace_absent))
        else if (trace == null) Text(stringResource(R.string.synthesis_trace_invalid))
        else {
            SummaryLine(stringResource(R.string.synthesis_trace_positions), trace.points.size.toString())
            SummaryLine(stringResource(R.string.synthesis_trace_segments), trace.points.map { it.segmentId }.distinct().size.toString())
            Text(stringResource(R.string.synthesis_trace_precision,
                formatSynthesisNumber(trace.points.minOf { it.accuracyM }, 1), formatSynthesisNumber(trace.points.maxOf { it.accuracyM }, 1)))
        }
    }
    SummaryCard(stringResource(R.string.synthesis_limits)) {
        Text(stringResource(R.string.synthesis_limit_stand))
        Text(stringResource(R.string.synthesis_limit_territory))
        Text(stringResource(synthesisEngineDescriptionResource(report), report.engineVersion), style = MaterialTheme.typography.bodySmall)
    }
    SummaryCard(stringResource(R.string.synthesis_source_records)) {
        if (report.sourceReferences.isEmpty()) Text(stringResource(R.string.synthesis_source_unknown))
        else report.sourceReferences.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(stringResource(R.string.synthesis_generated, formatSynthesisDate(report.generatedAt)), style = MaterialTheme.typography.bodySmall)
        state.snapshot?.let { Text(stringResource(R.string.synthesis_closed_at, formatSynthesisDate(it.closedAt)), style = MaterialTheme.typography.bodySmall) }
        state.snapshot?.calculation?.sourceFingerprint?.let { Text("${stringResource(R.string.synthesis_snapshot)} : $it", style = MaterialTheme.typography.bodySmall) }
        if (archiveSuccess) Text(stringResource(R.string.synthesis_pdf_archived), style = MaterialTheme.typography.bodySmall)
    }
    SummaryCard(stringResource(R.string.export)) { Text(stringResource(R.string.synthesis_export_scope), style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun ClassHeightDialog(state: SynthesisUiState, onSave: (String, Int, Double) -> Unit, onDismiss: () -> Unit) {
    val groups = remember(state.observations, state.parameters) {
        state.observations.filter { state.selection.plotId == null || it.placetteId == state.selection.plotId }
            .map { it.essenceCode to synthesisDiameterClass(it.diamCm, state.parameters.diameterClasses) }.distinct().sortedWith(compareBy({ it.first }, { it.second }))
    }
    var selected by remember { mutableStateOf(groups.firstOrNull()) }
    var height by remember(selected) { mutableStateOf("") }
    val parsed = height.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.synthesis_height_modes)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.synthesis_height_mode_hint))
            SelectionButton(selected?.let { stringResource(R.string.synthesis_height_class, it.first, it.second) } ?: stringResource(R.string.synthesis_unknown),
                groups.mapIndexed { index, value -> index.toString() to stringResource(R.string.synthesis_height_class, value.first, value.second) },
                groups.indexOf(selected).toString(), onChoose = { selected = groups[it.toInt()] })
            OutlinedTextField(value = height, onValueChange = { height = it }, label = { Text(stringResource(R.string.synthesis_height_value)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                isError = height.isNotBlank() && parsed == null, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(enabled = parsed != null && selected != null, onClick = { selected?.let { onSave(it.first, it.second, parsed!!) } }) { Text(stringResource(R.string.synthesis_height_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun SelectionButton(label: String, choices: List<Pair<String, String>>, selected: String?, onChoose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, enabled = choices.isNotEmpty(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Text(label, Modifier.padding(vertical = 4.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (id, name) -> DropdownMenuItem(text = { Text(name, fontWeight = if (selected == id) FontWeight.Bold else FontWeight.Normal) }, onClick = { expanded = false; onChoose(id) }) }
        }
    }
}

@Composable
private fun SummaryCard(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

internal fun synthesisReasonResource(reason: String): Int = synthesisVolumeReasonResource(reason)
internal fun formatSynthesisNumber(value: Double?, decimals: Int): String = value?.let { String.format(Locale.getDefault(), "%.${decimals}f", it) } ?: "—"
internal fun formatSynthesisDate(timestamp: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
