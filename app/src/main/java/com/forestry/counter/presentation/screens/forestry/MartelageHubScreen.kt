package com.forestry.counter.presentation.screens.forestry

import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.forestry.counter.R
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.domain.calculation.*
import com.forestry.counter.domain.calculation.tarifs.TarifMethod
import com.forestry.counter.domain.calculation.tarifs.TarifSelection
import com.forestry.counter.domain.model.Essence
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.usecase.export.PdfSynthesisExporter
import com.forestry.counter.domain.usecase.export.QgisExportHelper
import com.forestry.counter.domain.usecase.export.ShapefileExporter
import com.forestry.counter.presentation.components.AppMiniDialog
import com.forestry.counter.presentation.components.AutomatedDataDisclaimer
import com.forestry.counter.presentation.theme.*
import com.forestry.counter.presentation.utils.ColorUtils
import com.forestry.counter.presentation.utils.parseHeightInputMean
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Écran Hub du martelage V2.
 *
 * Contient tout le rendu visuel de l'état Hub : fond, Scaffold avec TopAppBar riche,
 * panneau paramètres animé, chips essences, résumé exécutif et grille 2×2 de navigation.
 * Tous les dialogues liés au Hub (paramètres, hauteurs, cubage, export) sont ici.
 *
 * L'état est entièrement fourni par [MartelageScreen] — ce composable ne contient
 * aucun calcul métier propre.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MartelageHubScreen(
    // ── État affiché ──────────────────────────────────────────────────────────
    stats: MartelageStats?,
    scopeLabel: String,
    parcellesInScope: List<Parcelle>,
    parcelle: Parcelle?,
    surfaceHa: Double,
    gsieSyncState: GsieSyncState,
    tigesInScope: List<Tige>,
    essences: List<Essence>,
    availableEssences: List<Essence>,
    selectedEssenceCodes: Set<String>,
    viewScope: MartelageViewScope,
    parcelleId: String?,
    placetteId: String?,
    scope: String,
    missingParams: Boolean,
    missingHeightEssenceCodesForPrompt: List<String>,
    missingHeightsForPrompt: Boolean,
    // ── Saisies paramètres ────────────────────────────────────────────────────
    surfaceInput: String,
    surfaceInputValueM2: Double?,
    hoInput: String,
    nHaAvantInput: String,
    gHaAvantInput: String,
    // ── État UI ───────────────────────────────────────────────────────────────
    showParamPanel: Boolean,
    showParamDialog: Boolean,
    showHeightPromptDialog: Boolean,
    showHeightSnoozeDialog: Boolean,
    heightSnoozeHours: Int,
    showTarifMethodDialog: Boolean,
    showExportDialog: Boolean,
    editingHeightsEssenceCode: String?,
    currentTarifMethod: TarifMethod,
    currentTarifNumero: Int?,
    // ── Heights & synthèse ────────────────────────────────────────────────────
    martelageHeights: Map<String, Map<Int, Double>>,
    martelageHeightsLocal: Map<String, Map<Int, Double>>,
    martelageHeightsForest: Map<String, Map<Int, Double>>,
    forestScopeKeyForHeights: String?,
    synthesisParams: ForestrySynthesisParams?,
    diameterClasses: List<Int>,
    breakdownByEssence: Map<String, List<ProductBreakdownRow>>,
    // ── Préférences ───────────────────────────────────────────────────────────
    animationsEnabled: Boolean,
    backgroundImageEnabled: Boolean,
    backgroundImageUri: String?,
    // ── Callbacks ─────────────────────────────────────────────────────────────
    onNavigateTo: (MartelageSubScreen) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToMap: ((String) -> Unit)?,
    onSurfaceInputChange: (String) -> Unit,
    onHoInputChange: (String) -> Unit,
    onNHaAvantInputChange: (String) -> Unit,
    onGHaAvantInputChange: (String) -> Unit,
    onSelectedEssenceCodesChange: (Set<String>) -> Unit,
    onViewScopeChange: (MartelageViewScope) -> Unit,
    onShowParamPanelChange: (Boolean) -> Unit,
    onShowParamDialogChange: (Boolean) -> Unit,
    onShowHeightPromptDialogChange: (Boolean) -> Unit,
    onShowHeightSnoozeDialogChange: (Boolean) -> Unit,
    onHeightSnoozeHoursChange: (Int) -> Unit,
    onShowTarifMethodDialogChange: (Boolean) -> Unit,
    onShowExportDialogChange: (Boolean) -> Unit,
    onEditingHeightsEssenceCodeChange: (String?) -> Unit,
    onPersistParams: () -> Unit,
    onTarifChanged: (TarifMethod, Int?) -> Unit,
    onPlayClick: () -> Unit,
    userPreferences: UserPreferencesManager,
    forestryCalculator: ForestryCalculator,
    scopeKey: String,
    ellipsis: String,
    placeholderDash: String,
    euroSymbol: String,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun normalizeCode(code: String) = code.trim().uppercase(Locale.getDefault())

    // ── Launchers d'export ────────────────────────────────────────────────────

    val exportGeoJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/geo+json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val (geojson, count) = QgisExportHelper.buildGeoJson(tigesInScope, essences)
            if (count == 0) { snackbar.showSnackbar(context.getString(R.string.export_qgis_no_gps_stems)); return@launch }
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(geojson) }
            }.onSuccess { snackbar.showSnackbar(context.getString(R.string.export_qgis_geojson_done, count)) }
             .onFailure { snackbar.showSnackbar(context.getString(R.string.export_failed_format, it.message ?: "")) }
        }
    }
    val exportCsvXyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val (csv, count) = QgisExportHelper.buildCsvXY(tigesInScope, essences)
            if (count == 0) { snackbar.showSnackbar(context.getString(R.string.export_qgis_no_gps_stems)); return@launch }
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
            }.onSuccess { snackbar.showSnackbar(context.getString(R.string.export_qgis_csv_xy_done, count)) }
             .onFailure { snackbar.showSnackbar(context.getString(R.string.export_failed_format, it.message ?: "")) }
        }
    }
    val exportShapefileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    ShapefileExporter.exportToZip(tigesInScope, essences, os)
                }
            }.onSuccess { result ->
                val count = result?.stemCount ?: 0
                if (count == 0) snackbar.showSnackbar(context.getString(R.string.export_qgis_no_gps_stems))
                else snackbar.showSnackbar(context.getString(R.string.export_shapefile_done, count))
            }.onFailure { snackbar.showSnackbar(context.getString(R.string.export_failed_format, it.message ?: "")) }
        }
    }
    val exportMartelageCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val s = stats ?: run { snackbar.showSnackbar(context.getString(R.string.martelage_stats_unavailable_error)); return@launch }
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
                    val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                    w.write("martelage_export_at;$now\n")
                    w.write("scope;view;surface_m2;v_total_m3;v_per_ha_m3;g_total_m2;g_per_ha_m2;revenue_total_eur;revenue_per_ha_eur\n")
                    w.write(listOf(
                        scope, viewScope.name,
                        "%.1f".format(surfaceHa * 10_000.0),
                        "%.3f".format(s.vTotal), "%.3f".format(s.vPerHa),
                        "%.3f".format(s.gTotal), "%.3f".format(s.gPerHa),
                        s.revenueTotal?.let { "%.2f".format(it) } ?: "",
                        s.revenuePerHa?.let { "%.2f".format(it) } ?: "",
                    ).joinToString(";") + "\n")
                    w.write("\nper_essence\nessence_code;essence_name;n;v_total_m3;revenue_total_eur\n")
                    s.perEssence.forEach { row ->
                        w.write("${row.essenceCode};${row.essenceName};${row.n};${"%.3f".format(row.vTotal)};${row.revenueTotal?.let { "%.2f".format(it) } ?: ""}\n")
                    }
                }
            }.onSuccess { snackbar.showSnackbar(context.getString(R.string.result_exported)) }
             .onFailure { snackbar.showSnackbar(context.getString(R.string.export_failed_format, it.message ?: "")) }
        }
    }
    val exportPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val s = stats ?: run { snackbar.showSnackbar(context.getString(R.string.martelage_stats_unavailable_error)); return@launch }
            runCatching {
                PdfSynthesisExporter.export(
                    context = context,
                    uri = uri,
                    stats = s,
                    scopeLabel = scopeKey,
                    surfaceM2 = surfaceHa * 10_000.0,
                    productBreakdown = breakdownByEssence,
                )
            }.onSuccess { snackbar.showSnackbar(context.getString(R.string.pdf_exported)) }
             .onFailure { snackbar.showSnackbar(context.getString(R.string.export_failed_format, it.message ?: "")) }
        }
    }

    // ── Fond d'écran ──────────────────────────────────────────────────────────

    Box(modifier = Modifier.fillMaxSize()) {
        if (backgroundImageEnabled) {
            val uriStr = backgroundImageUri
            if (uriStr != null) {
                val uri = remember(uriStr) { Uri.parse(uriStr) }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                    update = { it.setImageURI(uri) },
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.forest_background),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        // ── Scaffold ─────────────────────────────────────────────────────────

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbar) },
            containerColor = Color.Transparent,
            topBar = {
                val topBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                val topFg = ColorUtils.getContrastingTextColor(topBg)
                TopAppBar(
                    title = { Text(stringResource(R.string.martelage_before_cut_title)) },
                    navigationIcon = {
                        IconButton(onClick = { onPlayClick(); onNavigateBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        if (parcelleId != null && onNavigateToMap != null) {
                            IconButton(onClick = { onPlayClick(); onNavigateToMap(parcelleId) }) {
                                Icon(Icons.Default.Map, contentDescription = stringResource(R.string.map_view))
                            }
                        }
                        IconButton(onClick = {
                            onPlayClick()
                            val s = stats
                            if (s != null) {
                                val text = buildString {
                                    appendLine(context.getString(R.string.share_synthesis_header))
                                    appendLine("${context.getString(R.string.share_scope)}: $scopeKey")
                                    if (surfaceHa > 0) appendLine("${context.getString(R.string.share_surface)}: ${"%.2f".format(surfaceHa)} ha")
                                    appendLine("N: ${s.nTotal} ${context.getString(R.string.stems)} (${"%.0f".format(s.nPerHa)} /ha)")
                                    appendLine("G: ${"%.2f".format(s.gTotal)} m² (${"%.2f".format(s.gPerHa)} m²/ha)")
                                    if (s.vTotal > 0) appendLine("V: ${"%.1f".format(s.vTotal)} m³ (${"%.1f".format(s.vPerHa)} m³/ha)")
                                    s.dg?.let { appendLine("Dg: ${"%.1f".format(it)} cm") }
                                    s.revenueTotal?.let { appendLine("${context.getString(R.string.share_revenue)}: ${"%.0f".format(it)} ${context.getString(R.string.euro_symbol)}") }
                                    appendLine(context.getString(R.string.share_footer))
                                }
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_synthesis_header)))
                            } else {
                                coroutineScope.launch { snackbar.showSnackbar(context.getString(R.string.martelage_stats_unavailable_error)) }
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                        }
                        IconButton(onClick = { onPlayClick(); onShowExportDialogChange(true) }) {
                            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.export))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = topBg,
                        titleContentColor = topFg,
                        navigationIconContentColor = topFg,
                        actionIconContentColor = topFg,
                    ),
                )
            },
        ) { padding ->

            val pageBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
            val pageText = ColorUtils.getContrastingTextColor(pageBg)

            CompositionLocalProvider(LocalContentColor provides pageText) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(pageBg),
                    contentPadding = PaddingValues(horizontal = Space.screenHField, vertical = Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {

                    // ── En-tête : portée, vue, méthode ──────────────────────

                    item {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = GsShape.md,
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(Space.sm),
                                verticalArrangement = Arrangement.spacedBy(Space.xs),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.martelage_summary_title),
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    AssistChip(
                                        onClick = { onPlayClick(); onShowParamPanelChange(!showParamPanel) },
                                        label = { Text(stringResource(R.string.martelage_parameters)) },
                                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                                    )
                                }
                                Text(
                                    text = scopeLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (parcellesInScope.isNotEmpty()) {
                                    Text(
                                        text = stringResource(R.string.martelage_included_parcelles_format, parcellesInScope.joinToString { it.name }),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                // Sélecteur de vue
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (placetteId != null) FilterChip(
                                        selected = viewScope == MartelageViewScope.PLACETTE,
                                        onClick = { onViewScopeChange(MartelageViewScope.PLACETTE) },
                                        label = { Text(stringResource(R.string.martelage_view_placette)) },
                                    )
                                    if (parcelleId != null) FilterChip(
                                        selected = viewScope == MartelageViewScope.PARCELLE,
                                        onClick = { onViewScopeChange(MartelageViewScope.PARCELLE) },
                                        label = { Text(stringResource(R.string.martelage_view_parcelle)) },
                                    )
                                    FilterChip(
                                        selected = viewScope == MartelageViewScope.GLOBAL,
                                        onClick = { onViewScopeChange(MartelageViewScope.GLOBAL) },
                                        label = { Text(stringResource(R.string.martelage_view_global)) },
                                    )
                                }
                                // Chips méthode + GPS
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                                    AssistChip(
                                        onClick = { onShowTarifMethodDialogChange(true) },
                                        label = { Text(stringResource(R.string.martelage_cubage_method_current_format, currentTarifMethod.label), style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    )
                                    val gpsCount = remember(tigesInScope) { tigesInScope.count { !it.gpsWkt.isNullOrBlank() } }
                                    if (gpsCount > 0) AssistChip(
                                        onClick = { onShowExportDialogChange(true) },
                                        label = { Text(stringResource(R.string.gps_count_label, gpsCount), style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = { Icon(Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    )
                                }
                            }
                        }
                    }

                    // ── Panneau paramètres (animé) ───────────────────────────

                    item {
                        AnimatedVisibility(
                            visible = showParamPanel,
                            enter = fadeIn(tween(if (animationsEnabled) 200 else 0)) + expandVertically(),
                            exit = fadeOut(tween(if (animationsEnabled) 200 else 0)) + shrinkVertically(),
                        ) {
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = GsShape.md,
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(Space.sm),
                                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                                ) {
                                    // En-tête du panneau
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Space.xs),
                                        ) {
                                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                            Text(stringResource(R.string.martelage_calc_parameters_title), style = MaterialTheme.typography.titleMedium)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
                                            FilledTonalButton(onClick = {
                                                onPlayClick()
                                                if (surfaceInputValueM2 == null || surfaceInputValueM2 <= 0.0) {
                                                    coroutineScope.launch { snackbar.showSnackbar(context.getString(R.string.martelage_sample_area_missing_error)) }
                                                } else {
                                                    onPersistParams()
                                                    if (!missingHeightsForPrompt) onShowParamPanelChange(false)
                                                }
                                            }) { Text(stringResource(R.string.validate)) }
                                            IconButton(onClick = { onPlayClick(); onShowParamPanelChange(false) }) {
                                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
                                            }
                                        }
                                    }

                                    // Champ surface
                                    OutlinedTextField(
                                        value = surfaceInput,
                                        onValueChange = onSurfaceInputChange,
                                        label = { Text(stringResource(R.string.martelage_sample_area_m2)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                        placeholder = { Text(stringResource(R.string.placeholder_surface)) },
                                        suffix = { Text(stringResource(R.string.unit_m2)) },
                                        singleLine = true,
                                    )
                                    if (surfaceInputValueM2 != null && surfaceInputValueM2 > 0) {
                                        Text(
                                            "≈ ${"%.4f".format(surfaceInputValueM2 / 10_000.0)} ha",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        )
                                    }

                                    // Champ Ho
                                    OutlinedTextField(
                                        value = hoInput,
                                        onValueChange = onHoInputChange,
                                        label = { Text(stringResource(R.string.martelage_ho_dominant_optional)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                        placeholder = { Text(stringResource(R.string.placeholder_height)) },
                                        suffix = { Text(stringResource(R.string.unit_m)) },
                                        singleLine = true,
                                    )

                                    HorizontalDivider(modifier = Modifier.padding(vertical = Space.xxs))

                                    // Peuplement avant coupe
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                                        Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
                                        Text(stringResource(R.string.martelage_avant_coupe_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                                        OutlinedTextField(
                                            value = nHaAvantInput, onValueChange = onNHaAvantInputChange,
                                            label = { Text(stringResource(R.string.martelage_nha_avant_label)) },
                                            modifier = Modifier.weight(1f),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                            placeholder = { Text(stringResource(R.string.placeholder_nha)) },
                                            singleLine = true,
                                        )
                                        OutlinedTextField(
                                            value = gHaAvantInput, onValueChange = onGHaAvantInputChange,
                                            label = { Text(stringResource(R.string.martelage_gha_avant_label)) },
                                            modifier = Modifier.weight(1f),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                            placeholder = { Text(stringResource(R.string.placeholder_gha)) },
                                            suffix = { Text(stringResource(R.string.unit_m2)) },
                                            singleLine = true,
                                        )
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(vertical = Space.xxs))

                                    // Méthode de cubage
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                                                Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                                Text(stringResource(R.string.martelage_cubage_method), style = MaterialTheme.typography.titleSmall)
                                            }
                                            Text(stringResource(R.string.martelage_cubage_method_current_format, currentTarifMethod.label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            currentTarifNumero?.let { Text(stringResource(R.string.martelage_cubage_method_numero_format, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        }
                                        FilledTonalButton(onClick = { onPlayClick(); onShowTarifMethodDialogChange(true) }) {
                                            Text(stringResource(R.string.martelage_cubage_method_change))
                                        }
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(vertical = Space.xxs))

                                    // Hauteurs par essence
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                                        Icon(Icons.Default.Height, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text(stringResource(R.string.martelage_heights_by_species_or_ho_desc), style = MaterialTheme.typography.titleMedium)
                                    }
                                    val heightModes = synthesisParams?.heightModes.orEmpty()
                                    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                                        availableEssences.forEach { ess ->
                                            val hasCustom = (martelageHeights[normalizeCode(ess.code)] != null && martelageHeights[normalizeCode(ess.code)]!!.isNotEmpty()) ||
                                                heightModes.any { it.essence.equals(ess.code, true) && it.mode.equals("FIXED", true) && (it.fixed ?: 0.0) > 0.0 }
                                            val missing = missingHeightEssenceCodesForPrompt.contains(normalizeCode(ess.code))
                                            val cardColor by animateColorAsState(
                                                targetValue = when {
                                                    missing  -> MaterialTheme.colorScheme.errorContainer.copy(0.25f)
                                                    hasCustom -> MaterialTheme.colorScheme.primaryContainer.copy(0.18f)
                                                    else     -> MaterialTheme.colorScheme.surface
                                                },
                                                animationSpec = tween(if (animationsEnabled) 220 else 0, easing = FastOutSlowInEasing),
                                                label = "heightCardColor",
                                            )
                                            Card(
                                                onClick = { onPlayClick(); onEditingHeightsEssenceCodeChange(ess.code) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = cardColor),
                                            ) {
                                                Column(modifier = Modifier.padding(Space.sm)) {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
                                                        Text(ess.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                                        AnimatedVisibility(visible = missing, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                                                            HubToCompleteBadge()
                                                        }
                                                    }
                                                    Text(
                                                        stringResource(if (hasCustom) R.string.martelage_custom_heights_defined else R.string.martelage_default_height_tables_used),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (missingParams) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                                            Text(stringResource(R.string.martelage_sample_area_missing_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                            TextButton(onClick = { onShowParamDialogChange(true) }) { Text(stringResource(R.string.martelage_fill_in)) }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── État vide ────────────────────────────────────────────

                    if (tigesInScope.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.martelage_no_tiges_error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {

                        // ── Chips essences ───────────────────────────────────

                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                                Text(stringResource(R.string.martelage_available_species), style = MaterialTheme.typography.titleSmall)
                                if (availableEssences.isEmpty()) {
                                    Text(stringResource(R.string.martelage_no_resinous_species_detected), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                                        availableEssences.forEach { e ->
                                            val normalized = normalizeCode(e.code)
                                            val selected = selectedEssenceCodes.contains(normalized)
                                            FilterChip(
                                                selected = selected,
                                                onClick = {
                                                    onPlayClick()
                                                    onSelectedEssenceCodesChange(
                                                        if (selected) (selectedEssenceCodes - normalized).ifEmpty { setOf(normalized) }
                                                        else selectedEssenceCodes + normalized
                                                    )
                                                },
                                                label = { Text(e.name) },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── Alerte hauteurs manquantes ───────────────────────

                        val s = stats
                        if (s != null && s.missingHeightEssenceCodes.isNotEmpty()) {
                            item {
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn(tween(if (animationsEnabled) 180 else 0)) + expandVertically(tween(if (animationsEnabled) 220 else 0)),
                                ) {
                                    val names = s.missingHeightEssenceNames.take(3).joinToString(", ") +
                                        if (s.missingHeightEssenceNames.size > 3) ellipsis else ""
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    ) {
                                        Column(modifier = Modifier.padding(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                                                Icon(Icons.Default.Warning, contentDescription = null)
                                                Text(stringResource(R.string.martelage_missing_heights_title), style = MaterialTheme.typography.titleSmall)
                                            }
                                            Text(stringResource(R.string.martelage_missing_heights_desc, names), style = MaterialTheme.typography.bodyMedium)
                                            TextButton(onClick = { onPlayClick(); onShowParamPanelChange(true); onEditingHeightsEssenceCodeChange(s.missingHeightEssenceCodes.firstOrNull()) }) {
                                                Text(stringResource(R.string.martelage_fill_heights))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ── Résumé exécutif ──────────────────────────────────

                        if (!missingParams && s != null) {
                            item { HubSummaryCard(stats = s, scopeLabel = scopeLabel, parcelle = parcelle, surfaceHa = surfaceHa, gsieSyncState = gsieSyncState, onOpenParams = { onShowParamPanelChange(true) }) }

                            // ── Grille de navigation 2×2 ────────────────────

                            item {
                                Text(
                                    text = stringResource(R.string.martelage_hub_open_section),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            item {
                                val limitingFactors = s.sanityWarnings.size
                                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                        HubNavCard(
                                            modifier = Modifier.weight(1f),
                                            icon = Icons.Default.Summarize,
                                            title = stringResource(R.string.martelage_hub_synthese_title),
                                            description = stringResource(R.string.martelage_hub_synthese_desc),
                                            badge = if (limitingFactors > 0) "$limitingFactors facteur${if (limitingFactors > 1) "s" else ""}" else null,
                                            onClick = { onPlayClick(); onNavigateTo(MartelageSubScreen.SyntheseDetaillee) },
                                        )
                                        HubNavCard(
                                            modifier = Modifier.weight(1f),
                                            icon = Icons.Default.Calculate,
                                            title = stringResource(R.string.martelage_hub_calcul_title),
                                            description = stringResource(R.string.martelage_hub_calcul_desc),
                                            badge = null,
                                            onClick = { onPlayClick(); onNavigateTo(MartelageSubScreen.CalculFin) },
                                        )
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                        HubNavCard(
                                            modifier = Modifier.weight(1f),
                                            icon = Icons.Default.PieChart,
                                            title = stringResource(R.string.martelage_hub_schemas_title),
                                            description = stringResource(R.string.martelage_hub_schemas_desc),
                                            badge = null,
                                            onClick = { onPlayClick(); onNavigateTo(MartelageSubScreen.Schemas) },
                                        )
                                        HubNavCard(
                                            modifier = Modifier.weight(1f),
                                            icon = Icons.Default.Map,
                                            title = stringResource(R.string.martelage_hub_carte_title),
                                            description = stringResource(R.string.martelage_hub_carte_desc),
                                            badge = null,
                                            onClick = { onPlayClick(); onNavigateTo(MartelageSubScreen.Carte) },
                                        )
                                    }
                                }
                            }

                            item { AutomatedDataDisclaimer() }
                        }

                        // Calcul en cours
                        if (!missingParams && s == null) {
                            item {
                                Text(
                                    stringResource(R.string.martelage_stats_unavailable_error),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    item { Spacer(Modifier.height(Space.xxl)) }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Dialogues
    // ═══════════════════════════════════════════════════════════════════════════

    if (showParamDialog) {
        AppMiniDialog(
            onDismissRequest = { onShowParamDialogChange(false) },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Tune,
            title = stringResource(R.string.martelage_calc_parameters_title),
            description = stringResource(R.string.martelage_params_dialog_desc),
            confirmText = stringResource(R.string.validate),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { onPersistParams(); onShowParamDialogChange(false) },
        ) {
            OutlinedTextField(
                value = surfaceInput, onValueChange = onSurfaceInputChange,
                label = { Text(stringResource(R.string.martelage_sample_area_m2)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                placeholder = { Text(stringResource(R.string.placeholder_surface)) },
                suffix = { Text(stringResource(R.string.unit_m2)) },
                singleLine = true,
            )
            Spacer(Modifier.height(Space.xs))
            OutlinedTextField(
                value = hoInput, onValueChange = onHoInputChange,
                label = { Text(stringResource(R.string.martelage_ho_dominant_optional)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                placeholder = { Text(stringResource(R.string.placeholder_height)) },
                suffix = { Text(stringResource(R.string.unit_m)) },
                singleLine = true,
            )
            Text(stringResource(R.string.martelage_params_dialog_hint), style = MaterialTheme.typography.bodySmall)
        }
    }

    if (showHeightPromptDialog) {
        val names = missingHeightEssenceCodesForPrompt
            .map { code -> availableEssences.firstOrNull { normalizeCode(it.code) == normalizeCode(code) }?.name ?: code }
            .joinToString(", ")
        AppMiniDialog(
            onDismissRequest = { onShowHeightPromptDialogChange(false) },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Warning,
            title = stringResource(R.string.martelage_missing_heights_title),
            description = stringResource(R.string.martelage_missing_heights_desc, names.ifBlank { "—" }),
            confirmText = stringResource(R.string.martelage_fill_heights),
            dismissText = stringResource(R.string.mandatory_heights_skip),
            neutralText = stringResource(R.string.mandatory_heights_snooze_title),
            onDismiss = { onShowHeightPromptDialogChange(false) },
            onNeutral = { onShowHeightPromptDialogChange(false); onHeightSnoozeHoursChange(1); onShowHeightSnoozeDialogChange(true) },
            onConfirm = { onShowHeightPromptDialogChange(false); onEditingHeightsEssenceCodeChange(missingHeightEssenceCodesForPrompt.firstOrNull()) },
        )
    }

    if (showHeightSnoozeDialog) {
        AppMiniDialog(
            onDismissRequest = { onShowHeightSnoozeDialogChange(false) },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Warning,
            title = stringResource(R.string.mandatory_heights_snooze_title),
            description = stringResource(R.string.mandatory_heights_desc),
            confirmText = stringResource(R.string.validate),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                val hours = heightSnoozeHours.coerceAtLeast(1)
                onShowHeightSnoozeDialogChange(false)
                coroutineScope.launch {
                    userPreferences.snoozeHeightPromptForHours(hours)
                    snackbar.showSnackbar(context.getString(R.string.mandatory_heights_snoozed_format, hours))
                }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                listOf(1, 4, 24).forEach { hours ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        RadioButton(selected = heightSnoozeHours == hours, onClick = { onHeightSnoozeHoursChange(hours) })
                        Text(stringResource(R.string.mandatory_heights_snooze_format, hours))
                    }
                }
            }
        }
    }

    // Dialogue édition hauteurs par essence/classe
    val editingCode = editingHeightsEssenceCode
    if (editingCode != null) {
        val normalizedEditingCode = remember(editingCode) { normalizeCode(editingCode) }
        val tigesEssence = remember(tigesInScope, editingCode) {
            tigesInScope.filter { normalizeCode(it.essenceCode) == normalizeCode(editingCode) }
        }
        val classesForDialog = remember(diameterClasses) {
            if (diameterClasses.isNotEmpty()) diameterClasses else (5..120 step 5).toList()
        }
        val byClass = remember(tigesEssence, classesForDialog) {
            tigesEssence.groupBy { forestryCalculator.diameterClassFor(it.diamCm, classesForDialog) }
        }
        val presentClasses = remember(byClass) {
            val counts = byClass.mapValues { it.value.size }
            byClass.keys.sortedWith(compareByDescending<Int> { counts[it] ?: 0 }.thenBy { it })
        }
        val existingForEssence = martelageHeights[normalizedEditingCode] ?: emptyMap()
        val fixedForEssence = remember(synthesisParams, editingCode) {
            synthesisParams?.heightModes.orEmpty()
                .filter { it.essence.trim().equals(normalizedEditingCode, true) && it.mode.equals("FIXED", true) && (it.fixed ?: 0.0) > 0.0 }
                .associate { it.diamClass to (it.fixed ?: 0.0) }
        }
        var localInputs by remember(editingCode, presentClasses, fixedForEssence) {
            mutableStateOf(presentClasses.associateWith { cls ->
                (existingForEssence[cls] ?: fixedForEssence[cls])?.let { "%.1f".format(it) } ?: ""
            })
        }
        val essenceName = availableEssences.firstOrNull { normalizeCode(it.code) == normalizedEditingCode }?.name ?: editingCode

        AppMiniDialog(
            onDismissRequest = { onEditingHeightsEssenceCodeChange(null) },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Height,
            title = stringResource(R.string.martelage_heights_dialog_title_format, essenceName),
            description = if (presentClasses.isEmpty()) stringResource(R.string.martelage_no_stem_for_species) else stringResource(R.string.martelage_height_by_class_hint),
            confirmText = stringResource(R.string.validate),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                val cleaned = localInputs.mapNotNull { (cls, str) ->
                    val (mean, _) = parseHeightInputMean(str)
                    if (mean != null && mean > 0.0) cls to mean else null
                }.toMap()
                val newMap = martelageHeightsLocal.toMutableMap().apply {
                    if (cleaned.isEmpty()) remove(normalizedEditingCode) else put(normalizedEditingCode, cleaned)
                    if (editingCode != normalizedEditingCode) remove(editingCode)
                }
                coroutineScope.launch {
                    userPreferences.setMartelageHeights(scopeKey, newMap)
                    val fk = forestScopeKeyForHeights
                    if (!fk.isNullOrBlank() && fk != scopeKey) {
                        val forestMap = martelageHeightsForest.toMutableMap().apply {
                            if (cleaned.isEmpty()) remove(normalizedEditingCode) else put(normalizedEditingCode, cleaned)
                        }
                        userPreferences.setMartelageHeights(fk, forestMap)
                    }
                }
                onEditingHeightsEssenceCodeChange(null)
            },
        ) {
            if (presentClasses.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    items(presentClasses, key = { it }) { cls ->
                        val count = byClass[cls]?.size ?: 0
                        val missingCount = byClass[cls]?.count { it.hauteurM == null } ?: 0
                        val (meanVal, meanCount) = parseHeightInputMean(localInputs[cls] ?: "")
                        val hasValue = meanVal != null && meanVal > 0.0
                        val needsValue = missingCount > 0 && !hasValue
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = if (needsValue) MaterialTheme.colorScheme.errorContainer.copy(0.35f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(0.35f)
                            ),
                        ) {
                            Column(modifier = Modifier.padding(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.martelage_diameter_class_cm_format, cls), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    AnimatedVisibility(visible = needsValue, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) { HubToCompleteBadge() }
                                }
                                OutlinedTextField(
                                    value = localInputs[cls] ?: "",
                                    onValueChange = { newVal -> localInputs = localInputs.toMutableMap().also { it[cls] = newVal } },
                                    modifier = Modifier.fillMaxWidth(),
                                    isError = needsValue,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                    placeholder = { Text(stringResource(R.string.placeholder_height_short)) },
                                    suffix = { Text(stringResource(R.string.unit_m)) },
                                    supportingText = {
                                        if (count > 0) {
                                            val label = if (missingCount > 0) "N=$count · manquantes=$missingCount" else "N=$count"
                                            val suffix = if (meanCount > 1 && meanVal != null) " · moy=${"%.1f".format(meanVal)}" else ""
                                            Text(label + suffix, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    },
                                    singleLine = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTarifMethodDialog) {
        TarifMethodDialog(
            currentMethod = currentTarifMethod,
            currentNumero = currentTarifNumero,
            onConfirm = { method, numero ->
                onShowTarifMethodDialogChange(false)
                coroutineScope.launch {
                    forestryCalculator.saveTarifSelection(
                        TarifSelection(
                            method = method.code,
                            schaefferNumero = if (method == TarifMethod.SCHAEFFER_1E || method == TarifMethod.SCHAEFFER_2E) numero else null,
                            ifnNumero = if (method == TarifMethod.IFN_RAPIDE || method == TarifMethod.IFN_LENT) numero else null,
                        )
                    )
                    onTarifChanged(method, numero)
                    snackbar.showSnackbar(context.getString(R.string.martelage_cubage_method_saved))
                }
            },
            onDismiss = { onShowTarifMethodDialogChange(false) },
        )
    }

    if (showExportDialog) {
        ExportQgisDialog(
            tigesInScope = tigesInScope,
            scopeKey = scopeKey,
            onDismiss = { onShowExportDialogChange(false) },
            onPlayClick = onPlayClick,
            exportGeoJsonLauncher = exportGeoJsonLauncher,
            exportCsvXyLauncher = exportCsvXyLauncher,
            exportShapefileLauncher = exportShapefileLauncher,
            exportCsvMartelageLauncher = exportMartelageCsvLauncher,
            exportPdfLauncher = exportPdfLauncher,
            viewScopeName = viewScope.name,
        )
    }
}

// ─── Résumé exécutif ──────────────────────────────────────────────────────────

@Composable
internal fun HubSummaryCard(
    stats: MartelageStats?,
    scopeLabel: String,
    parcelle: Parcelle?,
    surfaceHa: Double,
    gsieSyncState: GsieSyncState,
    onOpenParams: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GsShape.lg,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Elevation.card,
    ) {
        Column(modifier = Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(scopeLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    parcelle?.let { Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                GsieStateBadge(state = gsieSyncState)
            }
            HorizontalDivider()
            if (stats != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    HubMetric("Tiges", stats.nTotal.toString())
                    HubMetric("Volume", if (stats.vTotal > 0.0) "%.1f m³".format(stats.vTotal) else "—")
                    HubMetric("G/ha", if (stats.gPerHa > 0.0) "%.1f m²".format(stats.gPerHa) else "—")
                    HubMetric("Surface", if (surfaceHa > 0.0) "%.2f ha".format(surfaceHa) else "—")
                }
                HubCoverageBadge(cubagePct = stats.volumeCompletenessPct.toInt())
            } else {
                Text("Calcul en cours…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun HubMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun HubCoverageBadge(cubagePct: Int) {
    val (bg, fg) = when {
        cubagePct >= 90 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        cubagePct >= 70 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else            -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(shape = GsShape.sm, color = bg) {
        Text(
            "Cubage couvert : $cubagePct %",
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xxs),
        )
    }
}

// ─── Carte de navigation ──────────────────────────────────────────────────────

@Composable
internal fun HubNavCard(
    icon: ImageVector,
    title: String,
    description: String,
    badge: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .height(Space.xxl * 3)
            .clickable(onClick = onClick),
        shape = GsShape.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Space.sm),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Space.lg), tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                if (badge != null) {
                    Surface(shape = GsShape.sm, color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xxs))
                    }
                }
            }
        }
    }
}

// ─── Badge "À compléter" ──────────────────────────────────────────────────────

@Composable
private fun HubToCompleteBadge(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = GsShape.pill) {
        Text(stringResource(R.string.badge_to_complete), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xxs))
    }
}

// ─── Formulaire typologique rapide (accessible depuis MartelageSchemasScreen) ─

@Composable
internal fun TypelogiqueRapideCard(
    prefilledGPerHa: Double = 0.0,
    modifier: Modifier = Modifier,
) {
    // Stub — sera implémenté avec Vico dans la prochaine itération.
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Formulaire typologique — disponible prochainement",
            modifier = Modifier.padding(Space.md),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
