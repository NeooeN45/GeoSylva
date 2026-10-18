package com.forestry.counter.presentation.screens.forestry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.forestry.counter.R
import com.forestry.counter.domain.location.ForestSearchResult

/** Three stable steps backed by the ViewModel: identity, optional owner and management. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateForestWizard(
    viewModel: CreateForestViewModel,
    onNavigateBack: () -> Unit,
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form by viewModel.formState.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    val lookup by viewModel.lookupState.collectAsStateWithLifecycle()

    // One event outside composition: recomposition must not pop the next screen.
    LaunchedEffect(isSaved) {
        if (isSaved) onCreated()
    }
    if (isSaved) return

    BackHandler {
        if (!isSaving) {
            if (form.currentStep > 0) viewModel.previousStep() else onNavigateBack()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.forest_creation_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !isSaving) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 4.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (saveError) {
                        Text(
                            text = stringResource(R.string.forest_creation_save_error),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (form.currentStep > 0) viewModel.previousStep() else onNavigateBack()
                            },
                            enabled = !isSaving,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(stringResource(if (form.currentStep > 0) R.string.previous else R.string.cancel))
                        }
                        Button(
                            onClick = {
                                if (form.currentStep < 2) viewModel.nextStep() else viewModel.save()
                            },
                            enabled = !isSaving && form.canAdvance,
                            modifier = Modifier.weight(1.4f),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                stringResource(
                                    when {
                                        isSaving -> R.string.forest_creation_saving
                                        form.currentStep == 2 -> R.string.forest_creation_save
                                        else -> R.string.next
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (form.currentStep == 0) ForestCreationHero()
            ForestCreationProgress(form.currentStep)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (form.currentStep) {
                        0 -> StepIdentity(
                            form,
                            lookup,
                            !isSaving,
                            viewModel::updateNom,
                            viewModel::updateDepartement,
                            viewModel::selectForestSuggestion,
                        )
                        1 -> StepOwner(
                            form,
                            !isSaving,
                            viewModel::updateProprietaireNom,
                            viewModel::updateProprietaireEmail,
                            viewModel::updateGestionnaireNom,
                        )
                        2 -> StepManagement(
                            form,
                            !isSaving,
                            viewModel::updateTypeForet,
                            viewModel::updateObjectifGestion,
                            viewModel::updatePsgNumero,
                            viewModel::updateRemarques,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForestCreationHero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 174.dp)
            .clip(RoundedCornerShape(28.dp)),
    ) {
        // Qualified, unaltered CC0 original. Illustration only, never the entered forest.
        AsyncImage(
            model = R.drawable.profession_autre,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.8f)))),
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Forest, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Text(
                stringResource(R.string.forest_creation_intro),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(stringResource(R.string.forest_creation_intro_detail), style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Text(stringResource(R.string.forest_creation_photo_credit), style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}

@Composable
private fun ForestCreationProgress(currentStep: Int) {
    val titles = listOf(
        stringResource(R.string.create_forest_step_identity),
        stringResource(R.string.create_forest_step_owner),
        stringResource(R.string.create_forest_step_management),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.forest_creation_step_count, currentStep + 1, titles.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            titles.forEachIndexed { index, title ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (index <= currentStep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (index <= currentStep) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            if (index < currentStep) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            } else {
                                Text((index + 1).toString(), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (index == currentStep) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == currentStep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepHeading(title: String, help: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() },
    )
    Text(help, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StepIdentity(
    form: CreateForestFormState,
    lookup: CreateForestLookupState,
    enabled: Boolean,
    onNomChange: (String) -> Unit,
    onDepartementChange: (String) -> Unit,
    onSelectForest: (ForestSearchResult) -> Unit,
) {
    StepHeading(stringResource(R.string.create_forest_step_identity), stringResource(R.string.forest_creation_identity_help))
    ForestTextField(
        form.nom,
        onNomChange,
        stringResource(R.string.forest_creation_name_required),
        enabled,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
    )
    ForestLookupSuggestions(lookup, enabled, onSelectForest)
    ForestTextField(
        form.departement.orEmpty(),
        onDepartementChange,
        stringResource(R.string.forest_creation_department_optional),
        enabled,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForestLookupSuggestions(
    lookup: CreateForestLookupState,
    enabled: Boolean,
    onSelectForest: (ForestSearchResult) -> Unit,
) {
    when {
        lookup.selected != null -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.forest_creation_lookup_selected), style = MaterialTheme.typography.labelLarge)
                        Text(lookup.selected.sourceLabel, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        lookup.isLoading -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.forest_creation_lookup_loading), style = MaterialTheme.typography.bodySmall)
            }
        }
        lookup.hasError -> {
            Text(
                stringResource(R.string.forest_creation_lookup_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        lookup.suggestions.isNotEmpty() -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.forest_creation_lookup_help), style = MaterialTheme.typography.bodySmall)
                lookup.suggestions.forEach { candidate ->
                    OutlinedCard(
                        onClick = { onSelectForest(candidate) },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(candidate.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            if (!candidate.department.isNullOrBlank()) {
                                Text(
                                    stringResource(R.string.forest_creation_lookup_department, candidate.department),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(candidate.sourceLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        lookup.hasSearched -> {
            Text(
                stringResource(R.string.forest_creation_lookup_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepOwner(
    form: CreateForestFormState,
    enabled: Boolean,
    onProprietaireNomChange: (String) -> Unit,
    onProprietaireEmailChange: (String) -> Unit,
    onGestionnaireNomChange: (String) -> Unit,
) {
    StepHeading(stringResource(R.string.create_forest_step_owner), stringResource(R.string.forest_creation_owner_help))
    ForestTextField(
        form.proprietaireNom,
        onProprietaireNomChange,
        stringResource(R.string.forest_creation_owner_name_optional),
        enabled,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrect = false, imeAction = ImeAction.Next),
    )
    ForestTextField(
        form.proprietaireEmail.orEmpty(),
        onProprietaireEmailChange,
        stringResource(R.string.forest_creation_owner_email_optional),
        enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrect = false, imeAction = ImeAction.Next),
        error = if (!form.ownerEmailValid) stringResource(R.string.forest_creation_email_invalid) else null,
    )
    ForestTextField(
        form.gestionnaireNom.orEmpty(),
        onGestionnaireNomChange,
        stringResource(R.string.forest_creation_manager_optional),
        enabled,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrect = false, imeAction = ImeAction.Done),
    )
}

@Composable
private fun StepManagement(
    form: CreateForestFormState,
    enabled: Boolean,
    onTypeForetChange: (String) -> Unit,
    onObjectifGestionChange: (String) -> Unit,
    onPsgNumeroChange: (String) -> Unit,
    onRemarquesChange: (String) -> Unit,
) {
    StepHeading(stringResource(R.string.create_forest_step_management), stringResource(R.string.forest_creation_management_help))
    Text(
        stringResource(R.string.forest_creation_management_optional),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ForestTextField(form.typeForet.orEmpty(), onTypeForetChange, stringResource(R.string.create_forest_type), enabled)
    ForestTextField(
        form.objectifGestion.orEmpty(),
        onObjectifGestionChange,
        stringResource(R.string.create_forest_objective),
        enabled,
        minLines = 2,
    )
    ForestTextField(
        form.psgNumero.orEmpty(),
        onPsgNumeroChange,
        stringResource(R.string.forest_creation_plan_optional),
        enabled,
        keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Next),
    )
    ForestTextField(form.remarques.orEmpty(), onRemarquesChange, stringResource(R.string.create_forest_remarks), enabled, minLines = 3)
}

@Composable
private fun ForestTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
    minLines: Int = 1,
    error: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = minLines == 1,
        minLines = minLines,
        maxLines = if (minLines == 1) 1 else 6,
        keyboardOptions = keyboardOptions,
        isError = error != null,
        supportingText = if (error != null) ({ Text(error) }) else null,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
