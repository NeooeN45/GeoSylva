package com.forestry.counter.presentation.screens.formulas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.domain.model.Formula
import com.forestry.counter.domain.repository.FormulaRepository
import com.forestry.counter.presentation.components.AppMiniDialog
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FormulasScreen(
    groupId: String,
    formulaRepository: FormulaRepository,
    preferencesManager: UserPreferencesManager,
    onNavigateToCalculator: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val formulas by formulaRepository.getFormulasByGroup(groupId).collectAsStateWithLifecycle(initialValue = emptyList())

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingFormula by remember { mutableStateOf<Formula?>(null) }
    var evaluatingId by remember { mutableStateOf<String?>(null) }
    var evalResult by remember { mutableStateOf<Double?>(null) }

    val animationsEnabled by preferencesManager.animationsEnabled.collectAsStateWithLifecycle(initialValue = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.formulas)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editingFormula = null
                        showCreateDialog = true
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add))
                    }
                    IconButton(onClick = onNavigateToCalculator) {
                        Icon(Icons.Filled.Functions, contentDescription = stringResource(R.string.calculator))
                    }
                }
            )
        }
    ) { paddingValues ->
        Crossfade(
            targetState = formulas.isEmpty(),
            animationSpec = if (animationsEnabled) {
                tween(durationMillis = 220, easing = FastOutSlowInEasing)
            } else {
                tween(durationMillis = 0)
            },
            label = "formulasCrossfade"
        ) { isEmpty ->
            if (isEmpty) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.formulas_empty_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.formulas_empty_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(formulas, key = { it.id }) { formula ->
                        Column(
                            modifier = if (animationsEnabled) Modifier.animateItemPlacement() else Modifier
                        ) {
                            ListItem(
                                headlineContent = { Text(formula.name) },
                                supportingContent = { Text(formula.expression) },
                                trailingContent = {
                                    IconButton(onClick = {
                                        scope.launch {
                                            evaluatingId = formula.id
                                            evalResult = formulaRepository.evaluateFormula(formula.id)
                                        }
                                    }) {
                                        Icon(Icons.Filled.Calculate, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.clickable {
                                    editingFormula = formula
                                    showCreateDialog = true
                                }
                            )

                            AnimatedVisibility(
                                visible = evaluatingId == formula.id && evalResult != null,
                                enter = fadeIn(animationSpec = tween(durationMillis = if (animationsEnabled) 160 else 0, easing = FastOutSlowInEasing)) +
                                    expandVertically(animationSpec = tween(durationMillis = if (animationsEnabled) 200 else 0, easing = FastOutSlowInEasing)),
                                exit = fadeOut(animationSpec = tween(durationMillis = if (animationsEnabled) 160 else 0, easing = FastOutSlowInEasing)) +
                                    shrinkVertically(animationSpec = tween(durationMillis = if (animationsEnabled) 200 else 0, easing = FastOutSlowInEasing))
                            ) {
                                Text(
                                    stringResource(R.string.result_value_format, evalResult ?: 0.0),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }

                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        val existing = editingFormula
        var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
        var expr by remember(existing) { mutableStateOf(existing?.expression ?: "") }
        val canSave = name.isNotBlank() && expr.isNotBlank()
        AppMiniDialog(
            onDismissRequest = {
                showCreateDialog = false
                editingFormula = null
            },
            animationsEnabled = animationsEnabled,
            icon = if (existing == null) Icons.Filled.Add else Icons.Filled.Edit,
            title = stringResource(
                if (existing == null) R.string.formulas_create_title else R.string.formulas_edit_title
            ),
            confirmText = stringResource(if (existing == null) R.string.create else R.string.save),
            dismissText = stringResource(R.string.cancel),
            confirmEnabled = canSave,
            onConfirm = {
                scope.launch {
                    if (!canSave) return@launch
                    val trimmedName = name.trim()
                    val trimmedExpr = expr.trim()
                    if (existing == null) {
                        formulaRepository.insertFormula(
                            Formula(
                                id = UUID.randomUUID().toString(),
                                groupId = groupId,
                                name = trimmedName,
                                expression = trimmedExpr,
                                description = null
                            )
                        )
                    } else {
                        formulaRepository.updateFormula(
                            existing.copy(
                                name = trimmedName,
                                expression = trimmedExpr
                            )
                        )
                    }
                    showCreateDialog = false
                    editingFormula = null
                }
            }
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = expr,
                onValueChange = { expr = it },
                label = { Text(stringResource(R.string.expression)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}
