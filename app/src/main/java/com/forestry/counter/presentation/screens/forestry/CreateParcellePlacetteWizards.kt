package com.forestry.counter.presentation.screens.forestry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.R

/**
 * Wizard de création de parcelle — spec GEOSYLVA-003 §29.7.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateParcelleWizard(
    viewModel: CreateParcelleViewModel,
    onNavigateBack: () -> Unit,
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form by viewModel.formState.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()

    LaunchedEffect(isSaved) {
        if (isSaved) onCreated()
    }
    if (isSaved) return
    BackHandler(enabled = form.isSaving) {}

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_parcelle_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !form.isSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.create_wizard_section_info), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::updateName,
                label = { Text(stringResource(R.string.create_parcelle_name)) },
                singleLine = true,
                enabled = !form.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.surfaceHa?.toString() ?: "",
                onValueChange = viewModel::updateSurfaceHa,
                label = { Text(stringResource(R.string.create_parcelle_surface_ha)) },
                singleLine = true,
                enabled = !form.isSaving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.slopePct?.toString() ?: "",
                onValueChange = viewModel::updateSlopePct,
                label = { Text(stringResource(R.string.create_parcelle_slope_pct)) },
                singleLine = true,
                enabled = !form.isSaving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.altitudeM?.toString() ?: "",
                onValueChange = viewModel::updateAltitudeM,
                label = { Text(stringResource(R.string.create_parcelle_altitude_m)) },
                singleLine = true,
                enabled = !form.isSaving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.remarks ?: "",
                onValueChange = viewModel::updateRemarks,
                label = { Text(stringResource(R.string.create_forest_remarks)) },
                minLines = 3,
                enabled = !form.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )

            if (form.saveError) Text(stringResource(R.string.fc_manual_save_failed), color = MaterialTheme.colorScheme.error)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onNavigateBack, enabled = !form.isSaving) { Text(stringResource(R.string.cancel)) }
                Button(
                    onClick = viewModel::save,
                    enabled = form.isValid && !form.isSaving,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text(stringResource(R.string.create))
                }
            }
        }
    }
}

/**
 * Wizard de création de placette — spec GEOSYLVA-003 §29.8.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlacetteWizard(
    viewModel: CreatePlacetteViewModel,
    onNavigateBack: () -> Unit,
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlacetteMapCreationScreen(viewModel, onNavigateBack, onCreated, modifier)
}
