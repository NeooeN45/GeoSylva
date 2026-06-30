package com.forestry.counter.presentation.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.data.local.CanonicalEssences
import com.forestry.counter.data.parameters.ParameterDefaults
import com.forestry.counter.data.parameters.RegionalPricePresets
import com.forestry.counter.domain.calculation.PriceEntry
import com.forestry.counter.domain.model.ParameterItem
import com.forestry.counter.domain.parameters.ParameterKeys
import com.forestry.counter.domain.repository.ParameterRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val QUALITY_CODES = listOf("*", "A", "B", "C", "D")

@Composable
private fun qualityLabel(code: String): String = when (code) {
    "*" -> stringResource(R.string.price_quality_all)
    "A" -> stringResource(R.string.price_quality_a)
    "B" -> stringResource(R.string.price_quality_b)
    "C" -> stringResource(R.string.price_quality_c)
    "D" -> stringResource(R.string.price_quality_d)
    else -> code
}

@Composable
private fun productLabel(code: String): String = when (code) {
    "*" -> stringResource(R.string.price_product_all)
    "BO" -> stringResource(R.string.price_product_bo)
    "BI" -> stringResource(R.string.price_product_bi)
    "BCh" -> stringResource(R.string.price_product_bch)
    "BE" -> stringResource(R.string.price_product_be)
    "MERAIN" -> stringResource(R.string.price_product_merain)
    "TRANCHAGE" -> stringResource(R.string.price_product_tranchage)
    "SCIAGE_Q" -> stringResource(R.string.price_product_sciage_q)
    "GRUME_L" -> stringResource(R.string.price_product_grume_l)
    "POTEAU" -> stringResource(R.string.price_product_poteau)
    "SCIAGE_S" -> stringResource(R.string.price_product_sciage_s)
    "PALETTE" -> stringResource(R.string.price_product_palette)
    "PATE" -> stringResource(R.string.price_product_pate)
    else -> code
}

private data class EditablePriceRow(
    val essence: String,
    val product: String,
    val min: String,
    val max: String,
    val eurPerM3: String,
    val quality: String = "*",
    val expanded: Boolean = false
)

private val PRODUCT_CODES = listOf("*", "BO", "BI", "BCh", "BE", "MERAIN", "TRANCHAGE", "SCIAGE_Q", "GRUME_L", "POTEAU", "SCIAGE_S", "PALETTE", "PATE")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceTablesEditorScreen(
    parameterRepository: ParameterRepository,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val json = remember { Json { ignoreUnknownKeys = true } }

    var showResetDialog by remember { mutableStateOf(false) }
    var showPresetDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    val rows = remember { mutableStateListOf<EditablePriceRow>() }

    fun rowsToEntriesOrError(): Pair<List<PriceEntry>?, String?> {
        val result = mutableListOf<PriceEntry>()
        rows.forEachIndexed { idx, r ->
            val essence = r.essence.trim()
            val product = r.product.trim()
            val min = r.min.trim().toIntOrNull()
            val max = r.max.trim().toIntOrNull()
            val eur = r.eurPerM3.trim().replace(',', '.').toDoubleOrNull()

            if (essence.isBlank()) return null to context.getString(R.string.price_tables_invalid_row_format, idx + 1, "essence")
            if (product.isBlank()) return null to context.getString(R.string.price_tables_invalid_row_format, idx + 1, "product")
            if (min == null) return null to context.getString(R.string.price_tables_invalid_row_format, idx + 1, "min")
            if (max == null) return null to context.getString(R.string.price_tables_invalid_row_format, idx + 1, "max")
            if (eur == null) return null to context.getString(R.string.price_tables_invalid_row_format, idx + 1, "eurPerM3")
            if (min > max) return null to context.getString(R.string.price_tables_invalid_row_format, idx + 1, "min>max")

            result += PriceEntry(
                essence = essence,
                product = product,
                min = min,
                max = max,
                eurPerM3 = eur,
                quality = r.quality.trim().takeIf { it != "*" && it.isNotEmpty() }
            )
        }
        return result to null
    }

    suspend fun loadFromJsonString(valueJson: String?) {
        rows.clear()
        val list = runCatching {
            if (valueJson.isNullOrBlank()) emptyList() else json.decodeFromString<List<PriceEntry>>(valueJson)
        }.getOrElse { emptyList() }

        if (list.isEmpty()) {
            rows += EditablePriceRow(essence = "*", product = "*", min = "0", max = "999", eurPerM3 = "0")
        } else {
            list.forEach { e ->
                rows += EditablePriceRow(
                    essence = e.essence,
                    product = e.product,
                    min = e.min.toString(),
                    max = e.max.toString(),
                    eurPerM3 = e.eurPerM3.toString(),
                    quality = e.quality ?: "*"
                )
            }
        }
    }

    LaunchedEffect(parameterRepository) {
        isLoading = true
        val item = parameterRepository.getParameter(ParameterKeys.PRIX_MARCHE).first()
        loadFromJsonString(item?.valueJson)
        isLoading = false
    }

    BackHandler(enabled = true) {
        onNavigateBack()
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val defaultJson = ParameterDefaults.prixMarcheJson
                            parameterRepository.setParameter(ParameterItem(ParameterKeys.PRIX_MARCHE, defaultJson))
                            loadFromJsonString(defaultJson)
                            snackbarHostState.showSnackbar(context.getString(R.string.price_tables_saved))
                            showResetDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.reset))
                }
            },
            dismissButton = {
                FilledTonalButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.reset_price_tables_title)) },
            text = { Text(stringResource(R.string.reset_price_tables_desc)) }
        )
    }

    if (showPresetDialog) {
        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            confirmButton = {},
            title = { Text(stringResource(R.string.price_preset_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.price_preset_dialog_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    RegionalPricePresets.ALL.forEach { preset ->
                        val label = preset.labelFr
                        val count = preset.prices.size
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    val presetJson = json.encodeToString(preset.prices)
                                    parameterRepository.setParameter(ParameterItem(ParameterKeys.PRIX_MARCHE, presetJson))
                                    loadFromJsonString(presetJson)
                                    showPresetDialog = false
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.price_preset_loaded_format, label)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                                Text(label, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "$count ${context.getString(R.string.price_preset_entries_suffix)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.price_tables_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showPresetDialog = true }) {
                        Icon(Icons.Default.Forest, contentDescription = stringResource(R.string.price_preset_dialog_title))
                    }
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                val (entries, err) = rowsToEntriesOrError()
                                if (entries == null) {
                                    snackbarHostState.showSnackbar(err ?: context.getString(R.string.price_error_generic))
                                    return@launch
                                }
                                val outJson = json.encodeToString(entries)
                                parameterRepository.setParameter(ParameterItem(ParameterKeys.PRIX_MARCHE, outJson))
                                snackbarHostState.showSnackbar(context.getString(R.string.price_tables_saved))
                            }
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        val essenceCodes = remember {
            listOf("*") + CanonicalEssences.ALL.map { it.code }
        }
        val essenceLabels = remember {
            mapOf("*" to context.getString(R.string.price_essence_all)) + CanonicalEssences.ALL.associate { it.code to it.name }
        }

        var searchQuery by remember { mutableStateOf("") }

        // Build filtered index list so we keep original indices for editing
        val filteredIndices = remember(searchQuery, rows.size, rows.toList()) {
            if (searchQuery.isBlank()) {
                rows.indices.toList()
            } else {
                val q = searchQuery.lowercase()
                rows.indices.filter { idx ->
                    val r = rows[idx]
                    val essName = essenceLabels[r.essence]?.lowercase() ?: r.essence.lowercase()
                    essName.contains(q) || r.essence.lowercase().contains(q) ||
                            r.product.lowercase().contains(q)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header — compteur + bouton ajouter
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredIndices.size}/${rows.size} ${stringResource(R.string.price_tables_editor_title).lowercase()}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = {
                            rows.add(
                                EditablePriceRow(
                                    essence = "*",
                                    product = "*",
                                    min = "0",
                                    max = "999",
                                    eurPerM3 = "0"
                                )
                            )
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.add_line))
                    }
                }
            }

            // Search bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.price_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            items(filteredIndices.size, key = { filteredIndices[it] }) { i ->
                val index = filteredIndices[i]
                val row = rows[index]
                PriceRowCard(
                    row = row,
                    index = index,
                    essenceCodes = essenceCodes,
                    essenceLabels = essenceLabels,
                    onUpdate = { updated -> rows[index] = updated },
                    onDelete = { if (rows.size > 1) rows.removeAt(index) }
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceRowCard(
    row: EditablePriceRow,
    index: Int,
    essenceCodes: List<String>,
    essenceLabels: Map<String, String>,
    onUpdate: (EditablePriceRow) -> Unit,
    onDelete: () -> Unit
) {
    val essenceLabel = essenceLabels[row.essence] ?: row.essence
    val prodLabel = productLabel(row.product)
    val eurPerM3Label = stringResource(R.string.label_eur_per_m3)
    val priceDisplay = row.eurPerM3.replace(',', '.').toDoubleOrNull()?.let { "%.0f %s".format(it, eurPerM3Label) } ?: row.eurPerM3

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (row.expanded) 4.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header compact — cliquable pour expand
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUpdate(row.copy(expanded = !row.expanded)) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Forest,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = essenceLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    val qualityTag = if (row.quality != "*") " \u00b7 Q.${row.quality}" else ""
                    Text(
                        text = "$prodLabel \u00b7 D ${row.min}\u2013${row.max} cm$qualityTag \u00b7 $priceDisplay",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(priceDisplay, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                IconButton(onClick = { onUpdate(row.copy(expanded = !row.expanded)) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (row.expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Détails expandés
            AnimatedVisibility(
                visible = row.expanded,
                enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 4 },
                exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 4 }
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Dropdown essence
                    var essenceExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = essenceExpanded,
                        onExpandedChange = { essenceExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = essenceLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.essence)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = essenceExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = essenceExpanded,
                            onDismissRequest = { essenceExpanded = false }
                        ) {
                            essenceCodes.forEach { code ->
                                DropdownMenuItem(
                                    text = { Text(essenceLabels[code] ?: code) },
                                    onClick = {
                                        onUpdate(row.copy(essence = code))
                                        essenceExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Dropdown produit
                    var productExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = productExpanded,
                        onExpandedChange = { productExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = prodLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.product)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = productExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = productExpanded,
                            onDismissRequest = { productExpanded = false }
                        ) {
                            PRODUCT_CODES.forEach { code ->
                                DropdownMenuItem(
                                    text = { Text(productLabel(code)) },
                                    onClick = {
                                        onUpdate(row.copy(product = code))
                                        productExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Dropdown qualité
                    var qualityExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = qualityExpanded,
                        onExpandedChange = { qualityExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = qualityLabel(row.quality),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.price_quality_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qualityExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = qualityExpanded,
                            onDismissRequest = { qualityExpanded = false }
                        ) {
                            QUALITY_CODES.forEach { code ->
                                DropdownMenuItem(
                                    text = { Text(qualityLabel(code)) },
                                    onClick = {
                                        onUpdate(row.copy(quality = code))
                                        qualityExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Diamètres min/max + prix
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = row.min,
                            onValueChange = { v -> onUpdate(row.copy(min = v)) },
                            label = { Text(stringResource(R.string.price_dmin_label)) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = row.max,
                            onValueChange = { v -> onUpdate(row.copy(max = v)) },
                            label = { Text(stringResource(R.string.price_dmax_label)) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = row.eurPerM3,
                            onValueChange = { v -> onUpdate(row.copy(eurPerM3 = v)) },
                            label = { Text(stringResource(R.string.label_eur_per_m3)) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }

                    // Bouton supprimer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                            onClick = onDelete,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.delete), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
