package com.forestry.counter.presentation.screens.forestry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.R
import com.forestry.counter.domain.location.SampleFootprintShape
import com.forestry.counter.domain.location.PlotSamplingGeometry
import com.quintessences.geosylva.cartography.ui.forestry.ForestryMapLayout
import com.quintessences.geosylva.cartography.ui.forestry.ForestryMetric
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sqrt

private enum class PlotPanel { OVERVIEW, PARAMETERS, DISTRIBUTION, LIST, DETAIL, HELP, BOUNDARY_REVIEW }

/** References 57–63, all operating on the existing repository-backed batch draft. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlacetteMapCreationScreen(
    viewModel: CreatePlacetteViewModel,
    onNavigateBack: () -> Unit,
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
    onMapRenderStatusChanged: (com.quintessences.geosylva.cartography.mapbox.MapRenderStatus) -> Unit = {},
    onMapOverlayStatusChanged: (com.quintessences.geosylva.cartography.mapbox.MapOverlayStatus) -> Unit = {},
    onOutsidePreviewChanged: (com.forestry.counter.domain.location.PlotOutsidePreview) -> Unit = {},
) {
    val form by viewModel.formState.collectAsStateWithLifecycle()
    val saved by viewModel.isSaved.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var panel by rememberSaveable { mutableStateOf(PlotPanel.OVERVIEW) }
    var parameters by rememberSaveable(stateSaver = parameterSaver) { mutableStateOf(PlotParameterDraft.from(form)) }
    var distribution by rememberSaveable(stateSaver = distributionSaver) { mutableStateOf(PlotDistributionDraft.from(form)) }
    val selectedIndex = form.pointIds.indexOf(form.selectedPointId)
    val selectedFootprint = form.footprints.getOrNull(selectedIndex)
    val editable = !form.isSaving && !form.isGenerating
    fun openParameters() { parameters = PlotParameterDraft.from(form); panel = PlotPanel.PARAMETERS }
    fun openDistribution() { distribution = PlotDistributionDraft.from(form); panel = PlotPanel.DISTRIBUTION }
    fun back() { if (panel == PlotPanel.OVERVIEW) onNavigateBack() else panel = PlotPanel.OVERVIEW }
    fun create() {
        if (form.requiresBoundaryReview) panel = PlotPanel.BOUNDARY_REVIEW
        else viewModel.save(context.getString(R.string.plot_default_prefix))
    }
    BackHandler(enabled = form.isSaving || panel != PlotPanel.OVERVIEW) { if (!form.isSaving) back() }
    LaunchedEffect(saved) { if (saved) onCreated() }
    if (saved) return
    ForestryMapLayout(
        title = stringResource(when (panel) {
            PlotPanel.PARAMETERS -> R.string.fc_plot_parameters
            PlotPanel.DISTRIBUTION -> R.string.fc_plot_distribution
            PlotPanel.LIST -> R.string.fc_plot_list
            else -> R.string.fc_plot_place
        }),
        subtitle = form.parent?.name, backLabel = stringResource(R.string.back), onBack = ::back,
        enabled = !form.isSaving, modifier = modifier,
        helpLabel = stringResource(R.string.fc_plot_info, if (selectedIndex >= 0) selectedIndex + 1 else 1),
        onHelp = { panel = PlotPanel.HELP },
        expandedPanel = panel in setOf(PlotPanel.PARAMETERS, PlotPanel.DISTRIBUTION, PlotPanel.LIST),
        headerControls = {
            if (panel == PlotPanel.OVERVIEW) {
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    FilledTonalButton(onClick = { menuExpanded = true }, enabled = editable) {
                        Text(stringResource(scopeLabel(form.scope)))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        PlotScope.entries.forEach { scope ->
                            DropdownMenuItem(text = { Text(stringResource(scopeLabel(scope))) }, onClick = {
                                viewModel.updateScope(scope); menuExpanded = false
                            })
                        }
                    }
                }
            }
        },
        map = { mapModifier, controlsTop ->
            GeographicSelectionMap(
                featureCollectionJson = EMPTY_SELECTION,
                selectedFeatureId = null,
                initialBounds = if (panel == PlotPanel.DETAIL) selectedFootprint?.geometry?.bounds ?: form.geometry?.bounds else form.geometry?.bounds,
                onMapClick = { if (panel == PlotPanel.OVERVIEW && editable) viewModel.addPoint(it) },
                onViewportChanged = { _, _ -> }, modifier = mapModifier,
                plotPoints = form.points, parcelGeometry = form.geometry,
                plotFootprints = form.footprints, gridCells = form.gridCells,
                plotPointIds = form.pointIds, selectedPlotId = form.selectedPointId,
                onPlotSelected = { id -> viewModel.selectPoint(id); if (panel == PlotPanel.OVERVIEW) panel = PlotPanel.DETAIL },
                controlsTopPadding = controlsTop,
                onMapRenderStatusChanged = onMapRenderStatusChanged,
                onMapOverlayStatusChanged = onMapOverlayStatusChanged,
                onOutsidePreviewChanged = onOutsidePreviewChanged,
            )
        },
        panel = {
            if (form.isLoading || form.isGenerating || form.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
            form.error?.let { Text(stringResource(errorLabel(it)), color = MaterialTheme.colorScheme.error) }
            if (!form.isLoading && form.geometry == null) Text(stringResource(R.string.plot_missing_boundary), color = MaterialTheme.colorScheme.error)
            when (panel) {
                PlotPanel.PARAMETERS -> PlotParameterEditor(parameters, { parameters = it }, editable) {
                    viewModel.applyParameters(parameters); panel = PlotPanel.OVERVIEW
                }
                PlotPanel.DISTRIBUTION -> PlotDistributionEditor(distribution, { distribution = it }, editable) {
                    viewModel.applyDistribution(distribution); viewModel.generateGrid(); panel = PlotPanel.OVERVIEW
                }
                PlotPanel.LIST -> {
                    PlotCountHeader(form) { panel = PlotPanel.OVERVIEW }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 200.dp)) {
                        itemsIndexed(form.pointIds, key = { _, id -> id }) { index, id ->
                            Surface(color = if (id == form.selectedPointId) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
                                Row(Modifier.fillMaxWidth().clickable(enabled = editable, role = Role.Button,
                                    onClick = { viewModel.selectPoint(id) }).padding(start = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text((index + 1).toString(), style = MaterialTheme.typography.titleMedium)
                                    Text(plotName(form, index), Modifier.weight(1f).padding(horizontal = 12.dp))
                                    IconButton(onClick = { viewModel.selectPoint(id); panel = PlotPanel.DETAIL }, enabled = editable) {
                                        Icon(Icons.Default.Info, stringResource(R.string.fc_plot_info, index + 1))
                                    }
                                    IconButton(onClick = { viewModel.removePoint(index) }, enabled = editable) {
                                        Icon(Icons.Default.RemoveCircleOutline, stringResource(R.string.fc_plot_remove, index + 1))
                                    }
                                }
                            }
                        }
                    }
                    Text(stringResource(R.string.fc_removed_candidate_hint), style = MaterialTheme.typography.bodySmall)
                    CreatePlotButton(form, editable, ::create)
                }
                PlotPanel.DETAIL -> {
                    if (selectedFootprint == null) Text(stringResource(R.string.fc_boundary_unknown))
                    else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(plotName(form, selectedIndex), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                            SuggestionChip(onClick = { panel = PlotPanel.HELP }, label = { Text(stringResource(R.string.fc_planned_single)) })
                        }
                        Text(stringResource(shapeLabel(form.footprintShape)) + " · " + stringResource(scopeLabel(form.scope)))
                        PlotTargetMetrics(form)
                        Text(stringResource(if (selectedFootprint.crossesBoundary) R.string.fc_boundary_edge else R.string.fc_boundary_inside),
                            color = if (selectedFootprint.crossesBoundary) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.fc_effective_unknown), style = MaterialTheme.typography.bodySmall)
                        if (selectedFootprint.crossesBoundary) Text(stringResource(R.string.fc_boundary_review_help), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = ::openParameters, enabled = editable) { Text(stringResource(R.string.fc_batch_parameters)) }
                        TextButton(onClick = { panel = PlotPanel.LIST }) { Text(stringResource(R.string.fc_plot_list)) }
                    }
                }
                PlotPanel.HELP -> {
                    Text(stringResource(R.string.fc_planning_help), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.fc_boundary_review_help), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { panel = PlotPanel.OVERVIEW }) { Text(stringResource(R.string.fc_return_map)) }
                }
                PlotPanel.BOUNDARY_REVIEW -> {
                    Text(stringResource(R.string.sampling_boundary_warning, form.edgeWarningCount), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.fc_boundary_review_help))
                    Text(stringResource(R.string.fc_effective_unknown), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { panel = PlotPanel.LIST }) { Text(stringResource(R.string.fc_plot_list)) }
                        if (form.scope == PlotScope.AUTOMATIC_GRID) TextButton(onClick = ::openDistribution) { Text(stringResource(R.string.fc_plot_distribution)) }
                    }
                    Button(onClick = { viewModel.confirmBoundaryReview(); panel = PlotPanel.OVERVIEW },
                        enabled = editable, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.fc_confirm_boundary)) }
                }
                PlotPanel.OVERVIEW -> {
                    if (form.scope == PlotScope.ENTIRE_PARCEL) {
                        Text(stringResource(R.string.plot_whole_summary), style = MaterialTheme.typography.titleMedium)
                        Text(form.wholeAreaM2?.let { stringResource(R.string.plot_whole_area, number(it / 10_000.0, 4)) }
                            ?: stringResource(R.string.plot_missing_area))
                        OutlinedTextField(value = form.name, onValueChange = viewModel::updateName,
                            label = { Text(stringResource(R.string.plot_name_prefix)) }, enabled = editable,
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                    } else {
                        PlotCountHeader(form) { panel = PlotPanel.LIST }
                        if (form.scope == PlotScope.AUTOMATIC_GRID) {
                            TextButton(onClick = ::openDistribution, enabled = editable, modifier = Modifier.fillMaxWidth()) {
                                Text(form.cellAreaHa?.let { stringResource(R.string.fc_grid_cell, number(it, 2)) }
                                    ?: stringResource(R.string.fc_plot_distribution))
                            }
                            if (!form.gridPreviewReady) Text(stringResource(R.string.fc_grid_not_ready), style = MaterialTheme.typography.bodySmall)
                            if (!form.gridPreviewReady) Button(onClick = viewModel::generateGrid, enabled = form.canGenerate && editable,
                                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.fc_preview)) }
                            if (form.gridPreviewReady) form.neighborDistanceM?.let { Text(stringResource(R.string.sampling_grid_distance, number(it, 1)), style = MaterialTheme.typography.bodySmall) }
                            if (form.excludedBoundaryCount > 0) Text(stringResource(R.string.sampling_grid_excluded, form.excludedBoundaryCount), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = ::openParameters, enabled = editable, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(shapeLabel(form.footprintShape)) + " · " +
                                (form.footprintAreaM2?.let { stringResource(R.string.fc_area_m2, number(it, 1)) }
                                    ?: stringResource(R.string.fc_plot_parameters)))
                        }
                        if (form.scope == PlotScope.MAP_POINTS) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.plot_points_hint), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { viewModel.removePoint(form.points.lastIndex) }, enabled = editable && form.points.isNotEmpty()) {
                                Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.fc_undo_point))
                            }
                        }
                        if (form.edgeWarningCount > 0) TextButton(onClick = { panel = PlotPanel.BOUNDARY_REVIEW }, enabled = editable) {
                            Text(stringResource(R.string.sampling_boundary_warning, form.edgeWarningCount))
                        }
                    }
                    CreatePlotButton(form, editable, ::create)
                }
            }
        })
}

@Composable
private fun PlotCountHeader(form: CreatePlacetteFormState, onList: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.fc_plot_count, form.points.size), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.fc_planned), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        IconButton(onClick = onList) { Icon(Icons.AutoMirrored.Filled.List, stringResource(R.string.fc_plot_list)) }
    }
}

@Composable
private fun CreatePlotButton(form: CreatePlacetteFormState, editable: Boolean, onCreate: () -> Unit) {
    Button(onClick = onCreate, enabled = form.isValid && editable, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Text(if (form.requiresBoundaryReview) stringResource(R.string.fc_review_boundary) else stringResource(
            if (form.scope == PlotScope.ENTIRE_PARCEL) R.string.plot_create_whole else R.string.plot_create_points,
            if (form.scope == PlotScope.ENTIRE_PARCEL) 1 else form.points.size))
    }
}

@Composable
private fun PlotTargetMetrics(form: CreatePlacetteFormState) {
    val area = form.footprintAreaM2
    area?.let {
        if (form.footprintShape == SampleFootprintShape.CIRCLE)
            Text(stringResource(R.string.fc_radius_m, number(sqrt(it / PI), 2)))
        ForestryMetric(stringResource(R.string.fc_target_area), stringResource(R.string.fc_area_m2, number(it, 1)))
    }
}

@Composable
private fun PlotParameterEditor(draft: PlotParameterDraft, onChange: (PlotParameterDraft) -> Unit,
    editable: Boolean, onApply: () -> Unit) {
    Text(stringResource(R.string.sampling_footprint_title), style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SampleFootprintShape.entries.forEach { shape ->
            FilterChip(selected = draft.shape == shape, enabled = editable, onClick = {
                val converted = if (shape == SampleFootprintShape.HEXAGON) draft.withMeasure(FootprintSizeMeasure.AREA) else draft
                onChange(converted.copy(shape = shape))
            }, label = { Text(stringResource(shapeLabel(shape))) })
        }
    }
    if (draft.shape == SampleFootprintShape.CIRCLE) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FootprintSizeMeasure.entries.forEach { measure ->
            FilterChip(selected = draft.measure == measure, enabled = editable,
                onClick = { onChange(draft.withMeasure(measure)) }, label = {
                    Text(stringResource(if (measure == FootprintSizeMeasure.RADIUS) R.string.sampling_measure_radius else R.string.sampling_measure_area))
                })
        }
    }
    val radiusMode = draft.shape == SampleFootprintShape.CIRCLE && draft.measure == FootprintSizeMeasure.RADIUS
    OutlinedTextField(value = if (radiusMode) draft.radiusInput else draft.areaInput,
        onValueChange = { onChange(if (radiusMode) draft.copy(radiusInput = it) else draft.copy(areaInput = it)) },
        label = { Text(stringResource(if (radiusMode) R.string.plot_common_radius else R.string.sampling_footprint_area)) },
        suffix = { Text(if (radiusMode) "m" else "m²") }, enabled = editable, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
    PlotTargetMetrics(draft.asForm())
    if (draft.shape == SampleFootprintShape.HEXAGON) OutlinedTextField(value = draft.rotationInput,
        onValueChange = { onChange(draft.copy(rotationInput = it)) }, label = { Text(stringResource(R.string.sampling_hexagon_rotation)) },
        suffix = { Text("°") }, enabled = editable, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = draft.name, onValueChange = { onChange(draft.copy(name = it)) },
        label = { Text(stringResource(R.string.plot_name_prefix)) }, placeholder = { Text(stringResource(R.string.plot_default_prefix)) },
        enabled = editable, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text(stringResource(R.string.fc_planning_help), style = MaterialTheme.typography.bodySmall)
    Button(onClick = onApply, enabled = editable && draft.asForm().footprintAreaM2 != null && draft.asForm().rotationDeg != null,
        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.fc_apply)) }
}

@Composable
private fun PlotDistributionEditor(draft: PlotDistributionDraft, onChange: (PlotDistributionDraft) -> Unit,
    editable: Boolean, onPreview: () -> Unit) {
    Text(stringResource(R.string.sampling_grid_density_title), style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(value = draft.cellAreaInput, onValueChange = { onChange(draft.copy(cellAreaInput = it)) },
        label = { Text(stringResource(R.string.sampling_grid_cell_area)) }, suffix = { Text("ha") }, enabled = editable,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
    Text(stringResource(R.string.sampling_grid_cell_hint), style = MaterialTheme.typography.bodySmall)
    draft.asForm().cellAreaHa?.let { cellArea ->
        Text(stringResource(R.string.sampling_grid_distance, number(PlotSamplingGeometry.neighborDistanceM(cellArea), 1)),
            style = MaterialTheme.typography.bodySmall)
    }
    OutlinedTextField(value = draft.rotationInput, onValueChange = { onChange(draft.copy(rotationInput = it)) },
        label = { Text(stringResource(R.string.sampling_grid_rotation)) }, suffix = { Text("°") }, enabled = editable,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.sampling_boundary_exclude), Modifier.weight(1f))
        Switch(checked = draft.excludeBoundary, onCheckedChange = { onChange(draft.copy(excludeBoundary = it)) }, enabled = editable)
    }
    Text(stringResource(R.string.fc_boundary_review_help), style = MaterialTheme.typography.bodySmall)
    Button(onClick = onPreview, enabled = editable && draft.asForm().cellAreaHa != null && draft.asForm().rotationDeg != null,
        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.fc_preview)) }
}

@Composable
private fun plotName(form: CreatePlacetteFormState, index: Int): String =
    (form.name.trim().ifBlank { stringResource(R.string.plot_default_prefix) }) + " ${index + 1}"

private fun number(value: Double, decimals: Int) = String.format(Locale.getDefault(), "%.${decimals}f", value)
private fun scopeLabel(scope: PlotScope) = when (scope) {
    PlotScope.ENTIRE_PARCEL -> R.string.plot_scope_whole
    PlotScope.MAP_POINTS -> R.string.plot_scope_points
    PlotScope.AUTOMATIC_GRID -> R.string.sampling_grid_title
}
private fun shapeLabel(shape: SampleFootprintShape) = if (shape == SampleFootprintShape.CIRCLE) R.string.sampling_shape_circle else R.string.sampling_shape_hexagon
private fun errorLabel(error: PlotCreationError) = when (error) {
    PlotCreationError.PARENT_UNAVAILABLE -> R.string.plot_parent_unavailable
    PlotCreationError.POINT_OUTSIDE -> R.string.plot_point_outside
    PlotCreationError.POINT_LIMIT -> R.string.plot_point_limit
    PlotCreationError.SAVE_FAILED -> R.string.plot_save_failed
    PlotCreationError.GRID_UNSUPPORTED -> R.string.sampling_grid_unsupported
    PlotCreationError.EMPTY_GRID -> R.string.sampling_grid_empty
}
private val parameterSaver = listSaver<PlotParameterDraft, String>(
    save = { listOf(it.name, it.radiusInput, it.areaInput, it.shape.name, it.measure.name, it.rotationInput) },
    restore = { PlotParameterDraft(it[0], it[1], it[2], SampleFootprintShape.valueOf(it[3]), FootprintSizeMeasure.valueOf(it[4]), it[5]) })
private val distributionSaver = listSaver<PlotDistributionDraft, String>(
    save = { listOf(it.cellAreaInput, it.rotationInput, it.excludeBoundary.toString()) },
    restore = { PlotDistributionDraft(it[0], it[1], it[2].toBoolean()) })
private const val EMPTY_SELECTION = "{\"type\":\"FeatureCollection\",\"features\":[]}"
