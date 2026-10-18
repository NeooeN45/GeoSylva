package com.forestry.counter.presentation.screens.forestry

import com.forestry.counter.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.RadioButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sqrt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import com.forestry.counter.domain.location.SampleFootprintShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_parcelle_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Annuler")
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
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.surfaceHa?.toString() ?: "",
                onValueChange = viewModel::updateSurfaceHa,
                label = { Text(stringResource(R.string.create_parcelle_surface_ha)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.slopePct?.toString() ?: "",
                onValueChange = viewModel::updateSlopePct,
                label = { Text(stringResource(R.string.create_parcelle_slope_pct)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.altitudeM?.toString() ?: "",
                onValueChange = viewModel::updateAltitudeM,
                label = { Text(stringResource(R.string.create_parcelle_altitude_m)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.remarks ?: "",
                onValueChange = viewModel::updateRemarks,
                label = { Text(stringResource(R.string.create_forest_remarks)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onNavigateBack) { Text(stringResource(R.string.cancel)) }
                Button(
                    onClick = viewModel::save,
                    enabled = form.isValid,
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
    val form by viewModel.formState.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler(enabled = form.isSaving) { /* Finish or roll back the local transaction first. */ }

    LaunchedEffect(isSaved) {
        if (isSaved) onCreated()
    }
    if (isSaved) return

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_placette_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !form.isSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onNavigateBack, enabled = !form.isSaving) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = { viewModel.save(context.getString(R.string.plot_default_prefix)) },
                        enabled = form.isValid && !form.isSaving,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(stringResource(
                            if (form.scope != PlotScope.ENTIRE_PARCEL) R.string.plot_create_points else R.string.plot_create_whole,
                            if (form.scope != PlotScope.ENTIRE_PARCEL) form.points.size else 1,
                        ))
                    }
                }
            }
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
            Text(
                form.parent?.name ?: stringResource(R.string.plot_choose_scope),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.plot_choose_scope), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (form.isLoading) {
                CircularProgressIndicator()
            }

            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlotScopeOption(
                    title = stringResource(R.string.plot_scope_whole),
                    description = stringResource(R.string.plot_scope_whole_description),
                    selected = form.scope == PlotScope.ENTIRE_PARCEL,
                    enabled = !form.isSaving,
                    onClick = { viewModel.updateScope(PlotScope.ENTIRE_PARCEL) },
                )
                PlotScopeOption(
                    title = stringResource(R.string.plot_scope_points),
                    description = stringResource(R.string.plot_scope_points_description),
                    selected = form.scope == PlotScope.MAP_POINTS,
                    enabled = !form.isSaving,
                    onClick = { viewModel.updateScope(PlotScope.MAP_POINTS) },
                )
                PlotScopeOption(
                    title = stringResource(R.string.sampling_grid_title),
                    description = stringResource(R.string.sampling_grid_description),
                    selected = form.scope == PlotScope.AUTOMATIC_GRID,
                    enabled = !form.isSaving,
                    onClick = { viewModel.updateScope(PlotScope.AUTOMATIC_GRID) },
                )
            }

            if (!form.isLoading && form.geometry == null) {
                Text(stringResource(R.string.plot_missing_boundary), color = MaterialTheme.colorScheme.error)
            } else if (form.scope == PlotScope.ENTIRE_PARCEL) {
                val area = form.wholeAreaM2
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.plot_whole_summary), style = MaterialTheme.typography.titleMedium)
                        Text(if (area != null) stringResource(R.string.plot_whole_area,
                            String.format(Locale.getDefault(), "%.4f", area / 10_000.0))
                            else stringResource(R.string.plot_missing_area))
                    }
                }
            } else {
                PlotSamplingParameters(form, viewModel)
                form.geometry?.let { geometry ->
                    Surface(shape = RoundedCornerShape(24.dp)) {
                        GeographicSelectionMap(
                            featureCollectionJson = "{\"type\":\"FeatureCollection\",\"features\":[]}",
                            selectedFeatureId = null, initialBounds = geometry.bounds,
                            onMapClick = viewModel::addPoint, onViewportChanged = { _, _ -> },
                            plotPoints = form.points, parcelGeometry = geometry,
                            plotFootprints = form.footprints, gridCells = form.gridCells,
                            modifier = Modifier.fillMaxWidth().height(380.dp),
                        )
                    }
                }
                Text(stringResource(R.string.plot_points_selected, form.points.size), style = MaterialTheme.typography.titleMedium)
                if (form.scope == PlotScope.MAP_POINTS) {
                    Text(stringResource(R.string.plot_points_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.sampling_grid_preview_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    form.neighborDistanceM?.takeIf { form.gridPreviewReady }?.let { distance ->
                        Text(stringResource(R.string.sampling_grid_distance, String.format(Locale.getDefault(), "%.1f", distance)))
                    }
                    if (form.excludedBoundaryCount > 0 && form.gridPreviewReady) {
                        Text(stringResource(R.string.sampling_grid_excluded, form.excludedBoundaryCount), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (form.edgeWarningCount > 0) {
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(stringResource(R.string.sampling_boundary_warning, form.edgeWarningCount),
                            modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
                form.points.forEachIndexed { index, _ ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.plot_number, index + 1))
                        TextButton(onClick = { viewModel.removePoint(index) }, enabled = !form.isSaving && !form.isGenerating) {
                            Text(stringResource(R.string.plot_remove_point))
                        }
                    }
                }
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::updateName,
                label = { Text(stringResource(R.string.plot_name_prefix)) },
                placeholder = { Text(stringResource(R.string.plot_default_prefix)) },
                enabled = !form.isSaving,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            form.error?.let { error ->
                Text(stringResource(when (error) {
                    PlotCreationError.PARENT_UNAVAILABLE -> R.string.plot_parent_unavailable
                    PlotCreationError.POINT_OUTSIDE -> R.string.plot_point_outside
                    PlotCreationError.POINT_LIMIT -> R.string.plot_point_limit
                    PlotCreationError.SAVE_FAILED -> R.string.plot_save_failed
                    PlotCreationError.GRID_UNSUPPORTED -> R.string.sampling_grid_unsupported
                    PlotCreationError.EMPTY_GRID -> R.string.sampling_grid_empty
                }), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PlotSamplingParameters(form: CreatePlacetteFormState, viewModel: CreatePlacetteViewModel) {
    val editable = !form.isSaving && !form.isGenerating
    if (form.scope == PlotScope.AUTOMATIC_GRID) {
        Text(stringResource(R.string.sampling_grid_density_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = form.cellAreaInput, onValueChange = viewModel::updateCellAreaHa,
            label = { Text(stringResource(R.string.sampling_grid_cell_area)) }, suffix = { Text("ha") },
            supportingText = { Text(stringResource(R.string.sampling_grid_cell_hint)) },
            enabled = editable, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (form.scope == PlotScope.AUTOMATIC_GRID || form.footprintShape == SampleFootprintShape.HEXAGON) {
        OutlinedTextField(
            value = form.rotationInput, onValueChange = viewModel::updateRotationDeg,
            label = { Text(stringResource(if (form.scope == PlotScope.AUTOMATIC_GRID) R.string.sampling_grid_rotation else R.string.sampling_hexagon_rotation)) }, suffix = { Text("°") },
            supportingText = { Text(stringResource(if (form.scope == PlotScope.AUTOMATIC_GRID) R.string.sampling_grid_rotation_hint else R.string.sampling_hexagon_rotation_hint)) },
            enabled = editable, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Text(stringResource(R.string.sampling_footprint_title), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SampleFootprintShape.entries.forEach { shape ->
            FilterChip(selected = form.footprintShape == shape, enabled = editable,
                onClick = { viewModel.updateFootprintShape(shape) },
                label = { Text(stringResource(if (shape == SampleFootprintShape.CIRCLE) R.string.sampling_shape_circle else R.string.sampling_shape_hexagon)) })
        }
    }
    if (form.footprintShape == SampleFootprintShape.CIRCLE) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FootprintSizeMeasure.entries.forEach { measure ->
                FilterChip(selected = form.sizeMeasure == measure, enabled = editable,
                    onClick = { viewModel.updateSizeMeasure(measure) },
                    label = { Text(stringResource(if (measure == FootprintSizeMeasure.RADIUS) R.string.sampling_measure_radius else R.string.sampling_measure_area)) })
            }
        }
    }
    val radiusMode = form.footprintShape == SampleFootprintShape.CIRCLE && form.sizeMeasure == FootprintSizeMeasure.RADIUS
    OutlinedTextField(
        value = if (radiusMode) form.radiusInput else form.areaInput,
        onValueChange = if (radiusMode) viewModel::updateRayonM else viewModel::updateFootprintArea,
        label = { Text(stringResource(if (radiusMode) R.string.plot_common_radius else R.string.sampling_footprint_area)) },
        suffix = { Text(if (radiusMode) "m" else "m²") },
        supportingText = {
            Text(form.footprintAreaM2?.let { area ->
                if (form.footprintShape == SampleFootprintShape.CIRCLE) stringResource(R.string.sampling_circle_dimensions,
                    String.format(Locale.getDefault(), "%.1f", area), String.format(Locale.getDefault(), "%.2f", sqrt(area / PI)))
                else stringResource(R.string.sampling_hexagon_dimensions, String.format(Locale.getDefault(), "%.1f", area),
                    String.format(Locale.getDefault(), "%.2f", sqrt(2.0 * area / (3.0 * sqrt(3.0)))))
            } ?: stringResource(R.string.sampling_footprint_hint))
        },
        enabled = editable, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    if (form.scope == PlotScope.AUTOMATIC_GRID) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sampling_boundary_exclude))
                Text(stringResource(R.string.sampling_boundary_exclude_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = form.excludeBoundary, onCheckedChange = viewModel::updateExcludeBoundary, enabled = editable)
        }
        Button(onClick = viewModel::generateGrid, enabled = form.canGenerate && !form.isGenerating,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            if (form.isGenerating) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
            Text(stringResource(if (form.gridPreviewReady) R.string.sampling_grid_regenerate else R.string.sampling_grid_generate))
        }
    }
    Text(stringResource(R.string.sampling_planned_location_notice), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PlotScopeOption(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled,
            role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
