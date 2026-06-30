package com.forestry.counter.presentation.screens.calculator

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.domain.calculator.FormulaParser
import com.forestry.counter.domain.model.Counter
import com.forestry.counter.domain.repository.CounterRepository
import com.forestry.counter.domain.repository.FormulaRepository
import com.forestry.counter.domain.model.Formula
import com.forestry.counter.presentation.components.AppMiniDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CalculatorScreen(
    groupId: String,
    counterRepository: CounterRepository,
    formulaRepository: FormulaRepository,
    formulaParser: FormulaParser,
    preferencesManager: UserPreferencesManager,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showSaveFormula by remember { mutableStateOf(false) }
    var formulaName by remember { mutableStateOf("") }
    var showUploadMenu by remember { mutableStateOf(false) }

    val animationsEnabled by preferencesManager.animationsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val pressScale by preferencesManager.pressScale.collectAsStateWithLifecycle(initialValue = 0.96f)
    val animDurationShort by preferencesManager.animDurationShort.collectAsStateWithLifecycle(initialValue = 140)

    // Data
    var counters by remember { mutableStateOf<List<Counter>>(emptyList()) }
    LaunchedEffect(groupId) {
        counterRepository.getCountersByGroup(groupId).collectLatest { list -> counters = list }
    }

    // Calculator state
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Double?>(null) }
    var unit by remember { mutableStateOf("-") } // manual unit selector for now
    val history = remember { mutableStateListOf<Pair<String, Double>>() }

    fun evaluateNow() {
        val nonComputed = counters.filter { !it.isComputed }
        when (val r = formulaParser.evaluate(expression, nonComputed, emptyMap())) {
            is FormulaParser.ParseResult.Success -> {
                result = r.value
            }
            is FormulaParser.ParseResult.Error -> {
                result = null
            }
        }
    }

    // Export result launcher
    val exportResultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && result != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write("${result} ${unit}".toByteArray())
                    }
                    snackbar.showSnackbar(context.getString(R.string.result_exported))
                } catch (e: Exception) {
                    snackbar.showSnackbar(
                        context.getString(
                            R.string.export_failed_format,
                            e.message ?: ""
                        )
                    )
                }
            }
        }
    }

    // Insert counter sheet
    var showInsertSheet by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var insertMode by remember { mutableStateOf("BIND") } // BIND or SNAPSHOT

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calculator)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showInsertSheet = true }) {
                        Icon(Icons.Filled.Functions, contentDescription = stringResource(R.string.insert_counter))
                    }
                    Box {
                        IconButton(onClick = { showUploadMenu = true }) {
                            Icon(Icons.Filled.Upload, contentDescription = stringResource(R.string.cd_upload))
                        }
                        DropdownMenu(
                            expanded = showUploadMenu,
                            onDismissRequest = { showUploadMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.save_as_formula)) },
                                onClick = {
                                    showUploadMenu = false
                                    showSaveFormula = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_result)) },
                                onClick = {
                                    showUploadMenu = false
                                    val ts = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
                                    exportResultLauncher.launch("calc-result-${ts}.txt")
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = expression,
                onValueChange = {
                    expression = it
                    evaluateNow()
                },
                label = { Text(stringResource(R.string.calculator_expression_bind_hint)) },
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.unit), maxLines = 1, overflow = TextOverflow.Ellipsis)
                var unitExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = unitExpanded, onExpandedChange = { unitExpanded = it }, modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.unit)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                        listOf("-", "m", "m²", "m³", "%", "t/ha").forEach { u ->
                            DropdownMenuItem(text = { Text(u) }, onClick = { unit = u; unitExpanded = false })
                        }
                    }
                }

                Button(onClick = {
                    evaluateNow()
                    result?.let { r ->
                        history.add(0, expression to r)
                        if (history.size > 10) history.removeLast()
                    }
                }) { Text(stringResource(R.string.evaluate)) }
            }

            Card(shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.result), style = MaterialTheme.typography.titleMedium)

                    Crossfade(
                        targetState = result,
                        animationSpec = if (animationsEnabled) {
                            tween(durationMillis = 200, easing = FastOutSlowInEasing)
                        } else {
                            tween(durationMillis = 0)
                        },
                        label = "calculatorResultCrossfade"
                    ) { r ->
                        Text(
                            text = r?.let {
                                String.format(Locale.getDefault(), "%.4f", it)
                            } ?: stringResource(R.string.placeholder_dash),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (unit != "-") {
                        Text(unit, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text(stringResource(R.string.history), style = MaterialTheme.typography.titleSmall)
            history.forEach { (expr, res) ->
                ListItem(
                    headlineContent = { Text(expr, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(res.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.clickable { expression = expr; result = res }
                )
                HorizontalDivider()
            }
        }
    }

    if (showSaveFormula) {
        val canSave = formulaName.isNotBlank() && expression.isNotBlank()
        AppMiniDialog(
            onDismissRequest = { showSaveFormula = false },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Save,
            title = stringResource(R.string.save_as_formula),
            confirmText = stringResource(R.string.save),
            dismissText = stringResource(R.string.cancel),
            confirmEnabled = canSave,
            onConfirm = {
                scope.launch {
                    if (!canSave) return@launch
                    formulaRepository.insertFormula(
                        Formula(
                            id = java.util.UUID.randomUUID().toString(),
                            groupId = groupId,
                            name = formulaName.trim(),
                            expression = expression.trim(),
                            description = null
                        )
                    )
                    showSaveFormula = false
                    formulaName = ""
                    snackbar.showSnackbar(context.getString(R.string.formula_saved))
                }
            }
        ) {
            OutlinedTextField(
                value = formulaName,
                onValueChange = { formulaName = it },
                label = { Text(stringResource(R.string.name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(text = expression, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }

    if (showInsertSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(onDismissRequest = { showInsertSheet = false }, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.insert_from_counter), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text(stringResource(R.string.search)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = insertMode == "BIND", onClick = { insertMode = "BIND" }, label = { Text(stringResource(R.string.bind)) })
                    FilterChip(selected = insertMode == "SNAPSHOT", onClick = { insertMode = "SNAPSHOT" }, label = { Text(stringResource(R.string.snapshot)) })
                }

                val filtered = counters.filter { it.name.contains(search, ignoreCase = true) }
                LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { c ->
                        val interactionSource = remember(c.id) { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (animationsEnabled && isPressed) pressScale else 1f,
                            animationSpec = tween(
                                durationMillis = if (animationsEnabled) animDurationShort else 0,
                                easing = FastOutSlowInEasing
                            ),
                            label = "calculatorInsertCardScale"
                        )

                        Card(
                            modifier = (if (animationsEnabled) Modifier.animateItemPlacement() else Modifier)
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .scale(scale)
                                .clickable(interactionSource = interactionSource, indication = null) {
                                    val token = if (insertMode == "BIND") "[${c.name}]" else c.value.toString()
                                    expression = buildString {
                                        append(expression)
                                        if (expression.isNotBlank() && !expression.trim().endsWith("+")) append(" + ")
                                        append(token)
                                    }
                                    evaluateNow()
                                    showInsertSheet = false
                                },
                            colors = CardDefaults.cardColors(containerColor = c.bgColor?.let { try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { MaterialTheme.colorScheme.surface } } ?: MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = String.format(Locale.getDefault(), "%.2f", c.value),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showInsertSheet = false }) { Text(stringResource(R.string.close)) }
                }
            }
        }
    }
}
