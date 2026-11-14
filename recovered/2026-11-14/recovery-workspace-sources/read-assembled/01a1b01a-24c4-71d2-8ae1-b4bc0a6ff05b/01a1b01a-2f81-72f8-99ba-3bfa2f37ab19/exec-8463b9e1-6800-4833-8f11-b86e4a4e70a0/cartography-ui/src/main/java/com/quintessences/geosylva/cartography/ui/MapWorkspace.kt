package com.quintessences.geosylva.cartography.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quintessences.geosylva.cartography.ui.R
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * Shared chrome for Terrain and Libre. The canvas and all durable state belong to
 * the host. This composable never assumes that a visible catalog item is usable.
 */
@Composable
fun MapWorkspace(
    state: MapWorkspaceState,
    actions: MapWorkspaceActions,
    modifier: Modifier = Modifier,
    thumbnail: @Composable (MapPreviewKey, Modifier) -> Unit = { _, imageModifier ->
        MissingPreview(imageModifier)
    },
    canvas: @Composable BoxScope.() -> Unit,
) {
    BackHandler(enabled = state.panel != null) { actions.onPanelChanged(null) }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxSize()) {
        canvas()
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding()
                .padding(horizontal = MapUiTokens.edge, vertical = MapUiTokens.topGap),
            verticalArrangement = Arrangement.spacedBy(MapUiTokens.topGap),
        ) {
            ContextCapsule(state, actions)
            if (state.panel == null) Surface(
                onClick = { actions.onPanelChanged(MapPanel.LAYERS) },
                modifier = Modifier.align(Alignment.End).heightIn(min = MapUiTokens.touch),
                shape = MapUiTokens.commandShape,
                color = MapUiTokens.paper,
                shadowElevation = MapUiTokens.elevation,
            ) {
                Row(Modifier.padding(horizontal = MapUiTokens.commandPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MapUiTokens.iconGap)) {
                    Icon(Icons.Outlined.Layers, null, tint = MapUiTokens.ink)
                    Text(stringResource(R.string.gs_map_layers), fontWeight = FontWeight.SemiBold,
                        color = MapUiTokens.ink, fontSize = 16.sp)
                }
            }
        }
        if (state.panel == null) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = MapUiTokens.edge, vertical = MapUiTokens.bottomGap),
                verticalArrangement = Arrangement.spacedBy(MapUiTokens.commandGap),
            ) {
                Surface(
                    onClick = actions::onLocate,
                    modifier = Modifier.align(Alignment.End).size(MapUiTokens.positionSize),
                    shape = MapUiTokens.commandShape, color = MapUiTokens.paper,
                    shadowElevation = MapUiTokens.elevation,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.MyLocation, stringResource(R.string.gs_map_position),
                            tint = MapUiTokens.ink, modifier = Modifier.size(MapUiTokens.positionIcon))
                    }
                }
                TaskBar(actions)
            }
        } else {
            val panelHeight = maxHeight * if (fontScale > 1.1f) 0.82f else 0.58f
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(panelHeight),
                shape = MapUiTokens.panelShape, color = MapUiTokens.paper,
                shadowElevation = MapUiTokens.panelElevation,
            ) {
                Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(top = MapUiTokens.handleTop)
                        .size(MapUiTokens.handleWidth, MapUiTokens.handleHeight)
                        .clip(CircleShape).background(MapUiTokens.muted.copy(alpha = 0.5f)))
                    Row(Modifier.fillMaxWidth().padding(start = MapUiTokens.panelInset, end = MapUiTokens.closeInset),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.gs_map_layers), Modifier.weight(1f),
                            color = MapUiTokens.ink, fontWeight = FontWeight.Bold, fontSize = 25.sp)
                        IconButton(onClick = { actions.onPanelChanged(null) }, Modifier.size(MapUiTokens.touch)) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.gs_map_close), tint = MapUiTokens.ink)
                        }
                    }
                    PanelTabs(state.panel, actions)
                    when (state.panel) {
                        MapPanel.BASEMAPS -> BasemapPanel(state, actions, thumbnail)
                        MapPanel.LAYERS -> LayerPanel(state, actions, thumbnail)
                        MapPanel.RELIEF -> ReliefPanel(state, actions)
                        null -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextCapsule(state: MapWorkspaceState, actions: MapWorkspaceActions) {
    Surface(shape = CircleShape, color = MapUiTokens.paper,
        shadowElevation = MapUiTokens.elevation, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = MapUiTokens.contextHeight).padding(horizontal = MapUiTokens.contextInset),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions::onBack, modifier = Modifier.size(MapUiTokens.touch)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.gs_map_back), tint = MapUiTokens.ink)
            }
            Row(Modifier.weight(1f).clickable(role = Role.Button, onClick = actions::onChooseContext)
                .heightIn(min = MapUiTokens.touch), verticalAlignment = Alignment.CenterVertically) {
                Text(contextLabel(state), modifier = Modifier.weight(1f), fontSize = 16.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ExpandMore, stringResource(R.string.gs_map_choose_context),
                    tint = MapUiTokens.ink, modifier = Modifier.size(MapUiTokens.icon))
            }
        }
    }
}

private fun contextLabel(state: MapWorkspaceState): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = MapUiTokens.ink, fontWeight = FontWeight.Bold)) { append(state.contextTitle) }
    state.contextSubtitle?.takeIf { it.isNotBlank() }?.let {
        withStyle(SpanStyle(color = MapUiTokens.muted)) { append(" · "); append(it) }
    }
}

@Composable
private fun TaskBar(actions: MapWorkspaceActions) {
    Surface(shape = MapUiTokens.toolbarShape, color = MapUiTokens.paper,
        shadowElevation = MapUiTokens.elevation) {
        Row(Modifier.fillMaxWidth().padding(MapUiTokens.toolbarInset), verticalAlignment = Alignment.CenterVertically) {
            TaskButton(Icons.Outlined.Assignment, stringResource(R.string.gs_map_surveys), Modifier.weight(1f),
                selected = true, onClick = { actions.onTask(MapTask.SURVEYS) })
            ToolbarDivider()
            TaskButton(Icons.Outlined.Straighten, stringResource(R.string.gs_map_measure), Modifier.weight(1f),
                onClick = { actions.onTask(MapTask.MEASURE) })
            ToolbarDivider()
            TaskButton(Icons.Outlined.Tune, stringResource(R.string.gs_map_tools), Modifier.weight(1f),
                onClick = { actions.onTask(MapTask.TOOLS) })
        }
    }
}

@Composable
private fun TaskButton(icon: ImageVector, title: String, modifier: Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Column(modifier.clip(MapUiTokens.commandShape)
        .background(if (selected) MapUiTokens.selected else Color.Transparent)
        .clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = MapUiTokens.taskHeight).padding(vertical = MapUiTokens.taskInset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = MapUiTokens.ink, modifier = Modifier.size(MapUiTokens.taskIcon))
        Text(title, color = MapUiTokens.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun ToolbarDivider() {
    Spacer(Modifier.width(MapUiTokens.dividerWidth).height(MapUiTokens.toolbarDividerHeight)
        .background(MapUiTokens.outline))
}

@Composable
private fun PanelTabs(panel: MapPanel, actions: MapWorkspaceActions) {
    val panels = MapPanel.entries
    TabRow(selectedTabIndex = panels.indexOf(panel), containerColor = Color.Transparent,
        contentColor = MapUiTokens.ink, modifier = Modifier.padding(horizontal = MapUiTokens.panelInset)) {
        panels.forEach { item ->
            Tab(selected = item == panel, onClick = { actions.onPanelChanged(item) },
                text = { Text(stringResource(when(item) {
                    MapPanel.BASEMAPS -> R.string.gs_map_basemap
                    MapPanel.LAYERS -> R.string.gs_map_layers
                    MapPanel.RELIEF -> R.string.gs_map_relief
                }), fontWeight = if (item == panel) FontWeight.Bold else FontWeight.Normal,
                    color = if (item == panel) MapUiTokens.ink else MapUiTokens.muted) })
        }
    }
}

@Composable
private fun BasemapPanel(state: MapWorkspaceState, actions: MapWorkspaceActions,
    thumbnail: @Composable (MapPreviewKey, Modifier) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = MapUiTokens.panelInset)) {
        SearchBox(query, { query = it }, stringResource(R.string.gs_map_search_basemap))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MapUiTokens.rowGap)) {
            val filtered = state.basemaps.filter { matchesQuery(query, it.title, it.provider) }
            if (filtered.isEmpty()) Text(stringResource(R.string.gs_map_no_results),
                Modifier.padding(vertical = MapUiTokens.sectionGap), color = MapUiTokens.muted)
            filtered.forEach { item ->
                val selected = state.selectedBasemapId == item.id
                Surface(onClick = { actions.onSelectBasemap(item.id) },
                    shape = MapUiTokens.rowShape,
                    color = if (selected) MapUiTokens.selected.copy(alpha = 0.5f) else Color.Transparent,
                    border = BorderStroke(MapUiTokens.border, if (selected) MapUiTokens.ink else MapUiTokens.outline),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.heightIn(min = MapUiTokens.basemapHeight).padding(MapUiTokens.rowInset),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MapUiTokens.rowTextGap)) {
                        thumbnail(item.previewKey, Modifier.size(MapUiTokens.previewWidth, MapUiTokens.previewHeight)
                            .clip(MapUiTokens.previewShape))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = MapUiTokens.ink, fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium)
                            Text(item.provider, color = MapUiTokens.muted, style = MaterialTheme.typography.bodyMedium)
                            AvailabilityLabel(item.availability)
                        }
                        Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            if (selected) stringResource(R.string.gs_map_selected) else null,
                            tint = MapUiTokens.ink, modifier = Modifier.size(MapUiTokens.choiceIcon))
                    }
                }
            }
            Spacer(Modifier.height(MapUiTokens.rowGap))
        }
        DetailLink(stringResource(R.string.gs_map_sources), { actions.onExplain(MapHelp.BASEMAP_SOURCE) })
    }
}

@Composable
private fun LayerPanel(state: MapWorkspaceState, actions: MapWorkspaceActions,
    thumbnail: @Composable (MapPreviewKey, Modifier) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = MapUiTokens.panelInset)) {
        SearchBox(query, { query = it }, stringResource(R.string.gs_map_search_layer))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val filtered = state.layers.filter { matchesQuery(query, it.title) }
            val onMap = filtered.filter { it.onMap }
            val catalog = filtered.filterNot { it.onMap }
            if (onMap.isNotEmpty()) {
                SectionLabel(stringResource(R.string.gs_map_on_map))
                onMap.forEach { LayerRow(it, actions, thumbnail) }
            }
            if (catalog.isNotEmpty()) {
                SectionLabel(stringResource(R.string.gs_map_catalog))
                catalog.forEach { LayerRow(it, actions, thumbnail) }
            }
            if (filtered.isEmpty()) Text(stringResource(R.string.gs_map_no_results),
                Modifier.padding(vertical = MapUiTokens.sectionGap), color = MapUiTokens.muted)
            Spacer(Modifier.height(MapUiTokens.sectionGap))
        }
    }
}

@Composable
private fun LayerRow(item: MapLayerChoice, actions: MapWorkspaceActions,
    thumbnail: @Composable (MapPreviewKey, Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = MapUiTokens.layerHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MapUiTokens.rowTextGap)) {
        thumbnail(item.previewKey, Modifier.size(MapUiTokens.layerPreviewWidth, MapUiTokens.layerPreviewHeight)
            .clip(MapUiTokens.previewShape))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = MapUiTokens.ink, style = MaterialTheme.typography.bodyLarge)
            AvailabilityLabel(item.availability)
        }
        if (item.onMap) Switch(checked = item.visible,
            onCheckedChange = { actions.onToggleLayer(item.id, it) },
            enabled = item.availability == MapChoiceAvailability.AVAILABLE,
            colors = SwitchDefaults.colors(checkedThumbColor = MapUiTokens.paper,
                checkedTrackColor = MapUiTokens.ink))
        else IconButton(onClick = { actions.onAddLayer(item.id) }, modifier = Modifier.size(MapUiTokens.touch)) {
            Icon(Icons.Outlined.AddCircleOutline, stringResource(R.string.gs_map_add_layer, item.title),
                tint = MapUiTokens.ink, modifier = Modifier.size(MapUiTokens.choiceIcon))
        }
    }
}

@Composable
private fun ReliefPanel(state: MapWorkspaceState, actions: MapWorkspaceActions) {
    // Keep editing local until release. The label below remains SDK-confirmed;
    // the draft is resynchronized only when that applied value changes.
    var exaggerationDraft by remember(state.verticalExaggeration) {
        mutableFloatStateOf(state.verticalExaggeration.toFloat().coerceIn(1f, 3f))
    }
    Column(Modifier.fillMaxSize().padding(horizontal = MapUiTokens.panelInset).verticalScroll(rememberScrollState())) {
        SectionLabel(stringResource(R.string.gs_map_view))
        Surface(shape = MapUiTokens.rowShape, color = Color.Transparent,
            border = BorderStroke(MapUiTokens.border, MapUiTokens.outline)) {
            Row(Modifier.fillMaxWidth()) {
                ModeButton("2D", state.pitchDegrees <= 0.1, state.controlsEnabled, Modifier.weight(1f)) { actions.onFlatView(true) }
                ModeButton("3D", state.pitchDegrees > 0.1, state.controlsEnabled, Modifier.weight(1f)) { actions.onFlatView(false) }
            }
        }
        if (!state.controlsEnabled) Text(
            state.controlsUnavailableReason ?: stringResource(R.string.gs_map_controls_unavailable),
            color = MapUiTokens.muted, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = MapUiTokens.rowGap))
        HorizontalDivider(Modifier.padding(vertical = MapUiTokens.sectionGap), color = MapUiTokens.outline)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.gs_map_vertical_scale), Modifier.weight(1f),
                color = MapUiTokens.ink, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { actions.onExplain(MapHelp.VERTICAL_EXAGGERATION) }, Modifier.size(MapUiTokens.touch)) {
                Icon(Icons.Outlined.Info, stringResource(R.string.gs_map_vertical_scale_help), tint = MapUiTokens.ink)
            }
            Text(NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0]).apply {
                minimumFractionDigits = 0; maximumFractionDigits = 1
            }.format(state.verticalExaggeration) + "×", color = MapUiTokens.ink,
                fontWeight = FontWeight.Bold)
        }
        Slider(value = exaggerationDraft,
            onValueChange = { exaggerationDraft = it },
            onValueChangeFinished = {
                if (kotlin.math.abs(exaggerationDraft.toDouble() - state.verticalExaggeration) > 0.00001) {
                    actions.onExaggerationChange(exaggerationDraft.toDouble())
                }
            },
            valueRange = 1f..3f,
            enabled = state.controlsEnabled,
            colors = SliderDefaults.colors(thumbColor = MapUiTokens.ink,
                activeTrackColor = MapUiTokens.ink, inactiveTrackColor = MapUiTokens.outline))
        RangeLabels("1×", "3×")
        HorizontalDivider(Modifier.padding(vertical = MapUiTokens.sectionGap), color = MapUiTokens.outline)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.gs_map_pitch), Modifier.weight(1f), color = MapUiTokens.ink,
                fontWeight = FontWeight.SemiBold)
            Text("${state.pitchDegrees.roundToInt()}°", color = MapUiTokens.ink, fontWeight = FontWeight.Bold)
        }
        Slider(value = state.pitchDegrees.toFloat().coerceIn(0f, 60f),
            onValueChange = { actions.onPitchChange(it.toDouble()) }, valueRange = 0f..60f,
            enabled = state.controlsEnabled,
            colors = SliderDefaults.colors(thumbColor = MapUiTokens.ink,
                activeTrackColor = MapUiTokens.ink, inactiveTrackColor = MapUiTokens.outline))
        RangeLabels("0°", "60°")
        Spacer(Modifier.height(MapUiTokens.sectionGap))
        DetailLink(stringResource(R.string.gs_map_terrain_data), { actions.onExplain(MapHelp.RELIEF_SOURCE) })
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.heightIn(min = MapUiTokens.touch).clip(MapUiTokens.rowShape)
        .background(if (selected) MapUiTokens.ink else Color.Transparent)
        .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, fontWeight = FontWeight.Bold,
            color = if (selected) MapUiTokens.paper else MapUiTokens.muted,
            modifier = Modifier.padding(MapUiTokens.rowGap))
    }
}

@Composable
private fun RangeLabels(start: String, end: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(start, Modifier.weight(1f), color = MapUiTokens.muted, style = MaterialTheme.typography.bodySmall)
        Text(end, color = MapUiTokens.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit, placeholder: String) {
    OutlinedTextField(value = query, onValueChange = onQuery,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = MapUiTokens.ink) },
        singleLine = true, shape = CircleShape,
        modifier = Modifier.fillMaxWidth().padding(vertical = MapUiTokens.rowGap))
}

@Composable
private fun AvailabilityLabel(availability: MapChoiceAvailability) {
    if (availability != MapChoiceAvailability.AVAILABLE) Text(stringResource(when (availability) {
        MapChoiceAvailability.CONFIGURATION_REQUIRED -> R.string.gs_map_needs_configuration
        MapChoiceAvailability.DATA_REQUIRED -> R.string.gs_map_needs_data
        else -> R.string.gs_map_needs_qualification
    }), color = MapUiTokens.muted, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun SectionLabel(label: String) {
    Text(label, color = MapUiTokens.muted, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = MapUiTokens.sectionGap, bottom = MapUiTokens.rowGap))
}

@Composable
private fun DetailLink(label: String, onClick: () -> Unit) {
    HorizontalDivider(color = MapUiTokens.outline)
    Row(Modifier.fillMaxWidth().heightIn(min = MapUiTokens.touch).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MapUiTokens.rowTextGap)) {
        Icon(Icons.Outlined.Info, null, tint = MapUiTokens.ink)
        Text(label, Modifier.weight(1f), color = MapUiTokens.ink, style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MapUiTokens.ink)
    }
}

@Composable
private fun MissingPreview(modifier: Modifier) {
    Box(modifier.background(MapUiTokens.selected), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Layers, stringResource(R.string.gs_map_preview_unavailable), tint = MapUiTokens.muted)
    }
}

private fun matchesQuery(query: String, vararg content: String): Boolean =
    query.isBlank() || content.any { it.contains(query.trim(), ignoreCase = true) }

/** One set of dimensions/colors for every workspace context. */
private object MapUiTokens {
    val paper = Color(0xFFFCF9F4)
    val ink = Color(0xFF064D2C)
    val muted = Color(0xFF73757C)
    val selected = Color(0xFFE7EDDD)
    val outline = Color(0xFFDEDBD4)
    val edge = 12.dp
    val topGap = 12.dp
    val bottomGap = 20.dp
    val commandGap = 24.dp
    val commandPadding = 14.dp
    val iconGap = 8.dp
    val touch = 48.dp
    val contextHeight = 48.dp
    val contextInset = 2.dp
    val icon = 24.dp
    val positionSize = 48.dp
    val positionIcon = 28.dp
    val elevation = 2.dp
    val panelElevation = 6.dp
    val taskHeight = 54.dp
    val taskInset = 4.dp
    val taskIcon = 24.dp
    val toolbarInset = 5.dp
    val dividerWidth = 1.dp
    val toolbarDividerHeight = 28.dp
    val handleTop = 8.dp
    val handleWidth = 36.dp
    val handleHeight = 4.dp
    val panelInset = 20.dp
    val closeInset = 8.dp
    val rowGap = 8.dp
    val rowInset = 5.dp
    val rowTextGap = 10.dp
    val basemapHeight = 64.dp
    val previewWidth = 128.dp
    val previewHeight = 52.dp
    val layerPreviewWidth = 72.dp
    val layerPreviewHeight = 32.dp
    val layerHeight = 48.dp
    val choiceIcon = 24.dp
    val border = 1.dp
    val sectionGap = 16.dp
    val commandShape = RoundedCornerShape(16.dp)
    val toolbarShape = RoundedCornerShape(24.dp)
    val panelShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val rowShape = RoundedCornerShape(12.dp)
    val previewShape = RoundedCornerShape(8.dp)
}
