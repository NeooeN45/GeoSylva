package com.forestry.counter.presentation.screens.forestry

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.model.IbpEvaluation
import com.forestry.counter.domain.model.IbpLevel
import com.forestry.counter.domain.repository.IbpRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import com.forestry.counter.domain.usecase.export.IbpCsvExporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IbpHistoryScreen(
    parcelleId: String,
    placetteId: String?,
    ibpRepository: IbpRepository,
    placetteRepository: PlacetteRepository? = null,
    onNavigateBack: () -> Unit,
    onOpenEvaluation: (parcelleId: String, placetteId: String, evalId: String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val evaluationsFlow = remember(ibpRepository, parcelleId, placetteId) {
        if (placetteId != null) ibpRepository.getByPlacette(placetteId)
        else ibpRepository.getByParcelle(parcelleId)
    }
    val evaluations by evaluationsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val sorted = remember(evaluations) { evaluations.sortedByDescending { it.observationDate } }

    val placettesFlow = remember(placetteRepository, parcelleId) {
        placetteRepository?.getPlacettesByParcelle(parcelleId) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }
    val placettes by placettesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val placetteNames = remember(placettes) { placettes.associate { it.id to (it.name ?: "") } }

    var showDeleteDialog by remember { mutableStateOf<IbpEvaluation?>(null) }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        IbpCsvExporter.export(sorted, placetteNames, out)
                    }
                }.onSuccess { snackbar.showSnackbar(context.getString(R.string.pdf_exported)) }
                  .onFailure { snackbar.showSnackbar(context.getString(R.string.export_error)) }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ibp_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (sorted.isNotEmpty()) {
                        IconButton(onClick = {
                            val ts = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                            csvLauncher.launch("IBP_export_$ts.csv")
                        }) {
                            Icon(Icons.Default.TableChart, contentDescription = stringResource(R.string.cd_export))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                )
            )
        },
        floatingActionButton = {
            if (placetteId != null) {
                FloatingActionButton(onClick = { onOpenEvaluation(parcelleId, placetteId, null) }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add))
                }
            }
        }
    ) { padding ->
        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.EmojiNature, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Text(stringResource(R.string.ibp_no_evaluation), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (sorted.size >= 2) {
                    item {
                        IbpEvolutionCard(evaluations = sorted)
                    }
                }
                items(sorted, key = { it.id }) { eval ->
                    IbpHistoryItem(
                        eval = eval,
                        placetteName = placetteNames[eval.placetteId],
                        showPlacette = placetteId == null,
                        onEdit = { onOpenEvaluation(parcelleId, eval.placetteId, eval.id) },
                        onDelete = { showDeleteDialog = eval }
                    )
                }
            }
        }
    }

    showDeleteDialog?.let { eval ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.ibp_delete)) },
            text = { Text(stringResource(R.string.ibp_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = eval.id
                        showDeleteDialog = null
                        scope.launch { ibpRepository.delete(toDelete) }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun IbpEvolutionCard(evaluations: List<IbpEvaluation>) {
    val complete = evaluations.filter { it.isComplete }
    if (complete.size < 2) return
    val latest = complete.first()
    val previous = complete.drop(1).first()
    val delta = latest.scoreTotal - previous.scoreTotal
    val deltaColor = when {
        delta > 0 -> Color(0xFF2E7D32)
        delta < 0 -> Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val deltaIcon = when {
        delta > 0 -> Icons.AutoMirrored.Filled.TrendingUp
        delta < 0 -> Icons.AutoMirrored.Filled.TrendingDown
        else -> Icons.AutoMirrored.Filled.TrendingFlat
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = deltaColor.copy(alpha = 0.08f)),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(deltaIcon, contentDescription = null, tint = deltaColor, modifier = Modifier.size(32.dp))
            Column {
                Text(stringResource(R.string.ibp_evolution_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (delta > 0) "+$delta pts depuis l'évaluation précédente"
                    else if (delta < 0) "$delta pts depuis l'évaluation précédente"
                    else "Score stable par rapport à l'évaluation précédente",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = deltaColor
                )
            }
        }
    }
}

@Composable
private fun IbpHistoryItem(
    eval: IbpEvaluation,
    placetteName: String?,
    showPlacette: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(eval.observationDate))
    val level = IbpLevel.fromScore(eval.scoreTotal)
    val levelColor = ibpLevelColor(level)
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(dateStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (showPlacette && placetteName != null) {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                                Text(placetteName, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (eval.evaluatorName.isNotBlank()) {
                        Text(eval.evaluatorName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (eval.isComplete) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${eval.scoreTotal}/20", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = levelColor)
                        IbpLevelBadge(level = level, color = levelColor)
                    }
                } else {
                    val pct = (eval.answers.answeredCount * 10)
                    Text("$pct%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(48.dp)) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) stringResource(R.string.cd_collapse) else stringResource(R.string.cd_expand))
                }
            }

            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    if (eval.isComplete) {
                        IbpRadarChart(answers = eval.answers, color = levelColor, modifier = Modifier.fillMaxWidth().height(160.dp))
                        Spacer(Modifier.height(8.dp))
                    }
                    if (eval.globalNote.isNotBlank()) {
                        Text(eval.globalNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.delete))
                        }
                        Spacer(Modifier.width(4.dp))
                        Button(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.ibp_edit))
                        }
                    }
                }
            }
        }
    }
}
