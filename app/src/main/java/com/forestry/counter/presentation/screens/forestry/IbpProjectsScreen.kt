package com.forestry.counter.presentation.screens.forestry

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.forestry.counter.presentation.utils.StaggerEntrance
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.model.IbpEvaluation
import com.forestry.counter.domain.model.IbpLevel
import com.forestry.counter.domain.repository.IbpRepository
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.repository.PlacetteRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.usecase.export.IbpQgisExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IbpProjectsScreen(
    ibpRepository: IbpRepository,
    parcelleRepository: ParcelleRepository? = null,
    placetteRepository: PlacetteRepository? = null,
    onNavigateBack: () -> Unit,
    onOpenEvaluation: (parcelleId: String, placetteId: String, evalId: String?) -> Unit,
    onNavigateToDiagnostic: ((parcelleId: String) -> Unit)? = null,
    onNavigateToCompare: ((parcelleId: String) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    val evaluations by ibpRepository.getAll().collectAsState(initial = emptyList())

    val qgisExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        IbpQgisExporter.export(evaluations, "GeoSylva IBP", out)
                    }
                }
                snackbar.showSnackbar("Export QGIS généré")
            }.onFailure { snackbar.showSnackbar("Erreur export QGIS") }
        }
    }

    val parcelles by remember(parcelleRepository) {
        parcelleRepository?.getAllParcelles() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val parcelleNames = remember(parcelles) { parcelles.associate { it.id to it.name } }

    // Placette names: one-shot per unique parcelleId
    val uniqueParcelleIds = remember(evaluations) { evaluations.map { it.parcelleId }.distinct() }
    val placetteNamesState = remember { mutableStateOf(mapOf<String, String>()) }
    LaunchedEffect(uniqueParcelleIds, placetteRepository) {
        if (placetteRepository == null) return@LaunchedEffect
        val combined = mutableMapOf<String, String>()
        for (pid in uniqueParcelleIds) {
            runCatching {
                placetteRepository.getPlacettesByParcelle(pid).first()
            }.getOrElse { emptyList() }
                .forEach { p -> combined[p.id] = p.name ?: "" }
        }
        placetteNamesState.value = combined
    }
    val placetteNames by placetteNamesState

    var showDeleteDialog by remember { mutableStateOf<IbpEvaluation?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val grouped = remember(evaluations) {
        evaluations
            .sortedByDescending { it.observationDate }
            .groupBy { it.parcelleId }
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.ibp_projects_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (onNavigateToDiagnostic != null && grouped.size == 1) {
                        IconButton(onClick = { onNavigateToDiagnostic(grouped.keys.first()) }) {
                            Icon(Icons.Default.Analytics, contentDescription = "Diagnostic")
                        }
                    }
                    if (onNavigateToCompare != null && grouped.size == 1 && (grouped.values.firstOrNull()?.size ?: 0) >= 2) {
                        IconButton(onClick = { onNavigateToCompare(grouped.keys.first()) }) {
                            Icon(Icons.Default.Timeline, contentDescription = "Comparaison temporelle")
                        }
                    }
                    if (evaluations.isNotEmpty()) {
                        IconButton(onClick = {
                            val dateStr = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
                            qgisExportLauncher.launch("ibp_export_$dateStr.zip")
                        }) {
                            Icon(Icons.Default.Map, contentDescription = "Export QGIS")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (parcelleRepository != null && placetteRepository != null) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ibp_start))
                }
            }
        }
    ) { padding ->
        if (evaluations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.EmojiNature,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Text(
                        stringResource(R.string.ibp_projects_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.ibp_projects_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                grouped.entries.forEachIndexed { groupIdx, (parcelleId, evals) ->
                    item(key = "header_$parcelleId") {
                        val parcelleName = parcelleNames[parcelleId]?.takeIf { it.isNotBlank() }
                            ?: parcelleId.take(8)
                        StaggerEntrance(index = groupIdx * 20, staggerMs = 40) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Forest,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color(0xFF2E7D32)
                            )
                            Text(
                                parcelleName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text(
                                "${evals.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        }
                    }
                    itemsIndexed(evals, key = { _, e -> e.id }) { evalIdx, eval ->
                        StaggerEntrance(index = groupIdx * 20 + evalIdx + 1, staggerMs = 40) {
                        IbpProjectCard(
                            eval = eval,
                            placetteName = placetteNames[eval.placetteId]?.takeIf { it.isNotBlank() }
                                ?: eval.placetteId.take(8),
                            dateFormat = dateFormat,
                            onClick = { onOpenEvaluation(eval.parcelleId, eval.placetteId, eval.id) },
                            onDelete = { showDeleteDialog = eval }
                        )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog && parcelleRepository != null && placetteRepository != null) {
        IbpCreateDialog(
            parcelleRepository = parcelleRepository,
            placetteRepository = placetteRepository,
            onDismiss = { showCreateDialog = false },
            onCreate = { parcelleId, placetteId ->
                showCreateDialog = false
                onOpenEvaluation(parcelleId, placetteId, null)
            }
        )
    }

    showDeleteDialog?.let { eval ->
        AppMiniDialogForIbp(
            onDismissRequest = { showDeleteDialog = null },
            onConfirm = {
                showDeleteDialog = null
                scope.launch { ibpRepository.delete(eval.id) }
            },
            onDismiss = { showDeleteDialog = null }
        )
    }
}

@Composable
private fun AppMiniDialogForIbp(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
        title = { Text(stringResource(R.string.ibp_delete_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun IbpProjectCard(
    eval: IbpEvaluation,
    placetteName: String,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val score = eval.scoreTotal
    val isComplete = score >= 0
    val level = IbpLevel.fromScore(score)
    val levelColor = if (!isComplete) Color(0xFF78909C)
    else when (level) {
        IbpLevel.VERY_LOW  -> Color(0xFFC62828)
        IbpLevel.LOW       -> Color(0xFFE65100)
        IbpLevel.MEDIUM    -> Color(0xFFF57C00)
        IbpLevel.GOOD      -> Color(0xFF558B2F)
        IbpLevel.VERY_GOOD -> Color(0xFF1B5E20)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Score badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = levelColor.copy(alpha = 0.12f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (isComplete) "$score" else "–",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = levelColor
                    )
                    Text(
                        if (isComplete) "/ 20" else "NC",
                        style = MaterialTheme.typography.labelSmall,
                        color = levelColor.copy(alpha = 0.7f)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    placetteName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    dateFormat.format(Date(eval.observationDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (eval.evaluatorName.isNotBlank()) {
                    Text(
                        eval.evaluatorName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                if (eval.globalNote.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        eval.globalNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun IbpCreateDialog(
    parcelleRepository: ParcelleRepository,
    placetteRepository: PlacetteRepository,
    onDismiss: () -> Unit,
    onCreate: (parcelleId: String, placetteId: String) -> Unit
) {
    val parcelles by parcelleRepository.getAllParcelles().collectAsState(initial = emptyList())
    var selectedParcelle by remember { mutableStateOf<Parcelle?>(null) }
    var placettes by remember { mutableStateOf(listOf<Placette>()) }
    var selectedPlacette by remember { mutableStateOf<Placette?>(null) }
    var loadingPlacettes by remember { mutableStateOf(false) }

    LaunchedEffect(selectedParcelle) {
        val p = selectedParcelle ?: return@LaunchedEffect
        loadingPlacettes = true
        placettes = runCatching {
            placetteRepository.getPlacettesByParcelle(p.id).first()
        }.getOrElse { emptyList() }
        selectedPlacette = null
        loadingPlacettes = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.EmojiNature, contentDescription = null, tint = Color(0xFF2E7D32)) },
        title = {
            Text(
                if (selectedParcelle == null) stringResource(R.string.ibp_create_select_parcelle)
                else stringResource(R.string.ibp_create_select_placette)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (selectedParcelle == null) {
                    if (parcelles.isEmpty()) {
                        Text(
                            stringResource(R.string.ibp_create_no_parcelle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        parcelles.forEach { parcelle ->
                            ListItem(
                                headlineContent = { Text(parcelle.name, fontWeight = FontWeight.Medium) },
                                leadingContent = {
                                    Icon(Icons.Default.EmojiNature, contentDescription = null,
                                        tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedParcelle = parcelle }
                            )
                            HorizontalDivider()
                        }
                    }
                } else {
                    TextButton(
                        onClick = { selectedParcelle = null; selectedPlacette = null },
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(selectedParcelle.name, style = MaterialTheme.typography.labelMedium)
                    }
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    if (loadingPlacettes) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else if (placettes.isEmpty()) {
                        Text(
                            stringResource(R.string.ibp_create_no_placette),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        placettes.forEach { placette ->
                            val isSelected = selectedPlacette?.id == placette.id
                            ListItem(
                                headlineContent = {
                                    Text(
                                        placette.name?.takeIf { it.isNotBlank() } ?: placette.id.take(8),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                trailingContent = if (isSelected) ({
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPlacette = placette }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val pid = selectedParcelle?.id ?: return@TextButton
                    val plid = selectedPlacette?.id ?: return@TextButton
                    onCreate(pid, plid)
                },
                enabled = selectedParcelle != null && selectedPlacette != null
            ) {
                Text(stringResource(R.string.ibp_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
