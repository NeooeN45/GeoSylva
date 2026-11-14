package com.quintessences.geosylva.cartography.ui.layers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.sp
import com.quintessences.geosylva.cartography.ui.R
import com.quintessences.geosylva.cartography.ui.forestry.ForestrySourceDetails

private val Ink = Color(0xFF102C70)
private val Green = Color(0xFF006B32)
private val Lavender = Color(0xFFEEEDF8)
private val Border = Color(0xFFDADCE5)
private val Secondary = Color(0xFF74798A)

/** Passive shared panel; the host retains the map, authority, stage, filter and drafts. */
@Composable
fun LayerWorkspace(state: LayerWorkspaceState, actions: LayerWorkspaceActions, modifier: Modifier = Modifier,
    onPanelHeightChanged: (Dp) -> Unit = {}) {
    val density = LocalDensity.current
    val panelHeightChanged by rememberUpdatedState(onPanelHeightChanged)
    var measuredPanelHeight by remember { mutableStateOf(0.dp) }
    var workspaceTop by remember { mutableFloatStateOf(Float.NaN) }
    var workspaceHeight by remember { mutableFloatStateOf(0f) }
    var panelTop by remember { mutableFloatStateOf(Float.NaN) }
    val fixedFooter = state.panel in setOf(LayerPanel.IMPORT,LayerPanel.FORMATS,LayerPanel.COLOR)
    LaunchedEffect(workspaceTop,workspaceHeight,panelTop,density) {
        if (workspaceTop.isFinite() && panelTop.isFinite() && workspaceHeight > 0f) {
            // Full physical occlusion, including navigation/IME insets and the actual outer margins.
            val height = with(density) { (workspaceTop+workspaceHeight-panelTop).coerceIn(0f,workspaceHeight).toDp() }
            measuredPanelHeight = height
            panelHeightChanged(height)
        }
    }
    BackHandler { if (!state.busy) actions.onBack() }
    val pane = if (state.panel == LayerPanel.MANAGE) state.layerName else stringResource(title(state.panel))
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned {
        workspaceTop = it.positionInRoot().y
        workspaceHeight = it.size.height.toFloat()
    }) {
        if (state.preview || state.filterActive) Surface(
            Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = measuredPanelHeight + 12.dp),
            shape = RoundedCornerShape(24.dp), color = Color.White, contentColor = Ink, shadowElevation = 2.dp) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.filterActive) Icon(Icons.Outlined.FilterList, null, Modifier.size(18.dp))
                Text(stringResource(if (state.filterActive) R.string.layer_filter_active else R.string.layer_preview_badge),
                    Modifier.padding(start = if (state.filterActive) 5.dp else 0.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(8.dp)
            .fillMaxWidth().heightIn(max = maxHeight * if (state.panel == LayerPanel.IMPORT) 0.42f else 0.55f)
            .onGloballyPositioned { panelTop = it.positionInRoot().y }
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }.semantics { paneTitle = pane },
            shape = RoundedCornerShape(28.dp), color = Color.White, contentColor = Ink, shadowElevation = 5.dp) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 7.dp).size(32.dp, 4.dp)
                    .clip(CircleShape).background(Color(0xFFBFC1C5)))
                LayerHeader(state, actions)
                Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()).padding(bottom = if (fixedFooter) 4.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.busy) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.layer_busy), Modifier.padding(start = 10.dp))
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    when (state.panel) {
                        LayerPanel.LIBRARY -> Library(state, actions)
                        LayerPanel.IMPORT -> Import(actions, state.busy)
                        LayerPanel.FORMATS -> Formats()
                        LayerPanel.PREVIEW -> Preview(state, actions)
                        LayerPanel.DATA -> Data(state, actions)
                        LayerPanel.ATTRIBUTES -> Attributes(state, actions)
                        LayerPanel.SOURCE -> Source(state)
                        LayerPanel.MANAGE -> Manage(state, actions)
                        LayerPanel.EDIT -> Edit(state, actions)
                        LayerPanel.FILTER -> Filter(state, actions)
                        LayerPanel.APPEARANCE -> Appearance(state, actions)
                        LayerPanel.COLOR -> CustomLayerColorPanel(state.colorCurrent, state.colorInput,
                            state.colorValid, actions::onColorInput, actions::onCancelColor, actions::onUseColor, showActions = false)
                    }
                }
                if (fixedFooter) Column(Modifier.fillMaxWidth().padding(top = 8.dp,bottom = 12.dp)) {
                    when (state.panel) {
                        LayerPanel.IMPORT,LayerPanel.FORMATS -> LayerButton(stringResource(R.string.layer_choose_file),
                            actions::onChooseFile,enabled = !state.busy)
                        LayerPanel.COLOR -> TwoActions(stringResource(R.string.layer_cancel),actions::onCancelColor,
                            stringResource(R.string.layer_use),actions::onUseColor,
                            !state.busy && state.colorValid && parseLayerColor(state.colorInput) != null,firstEnabled = !state.busy)
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun LayerHeader(state: LayerWorkspaceState, actions: LayerWorkspaceActions) {
    var help by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        val back = state.panel !in setOf(LayerPanel.IMPORT, LayerPanel.LIBRARY, LayerPanel.EDIT, LayerPanel.FILTER, LayerPanel.COLOR)
        if (back) IconButton(actions::onBack, enabled = !state.busy, modifier = Modifier.size(48.dp).background(Lavender, CircleShape)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.layer_back))
        } else if (state.panel == LayerPanel.IMPORT) Icon(Icons.Outlined.NoteAdd, null, Modifier.padding(end = 10.dp).size(30.dp))
        Column(Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 6.dp)) {
            Text(if (state.panel == LayerPanel.MANAGE) state.layerName else stringResource(title(state.panel)), fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (state.panel in setOf(LayerPanel.DATA, LayerPanel.ATTRIBUTES, LayerPanel.EDIT, LayerPanel.FILTER, LayerPanel.APPEARANCE))
                Text(if (state.panel == LayerPanel.DATA) state.filename else state.layerName,
                    color = Color(0xFF8581D7), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton({ help = true }, modifier = Modifier.size(42.dp)) { Icon(Icons.Outlined.Info, stringResource(R.string.layer_information), Modifier.size(21.dp)) }
        if (state.panel == LayerPanel.MANAGE && state.editable) Box {
            IconButton({ menu = true }, enabled = !state.busy) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.layer_more_actions)) }
            DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text(stringResource(R.string.layer_delete)) }, onClick = { menu = false; confirmDelete = true }) }
        }
        if (!back) IconButton(onClick = {
            when (state.panel) {
                LayerPanel.COLOR -> actions.onCancelColor()
                LayerPanel.EDIT, LayerPanel.FILTER -> actions.onBack()
                else -> actions.onClose()
            }
        }, enabled = !state.busy, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Close, stringResource(R.string.layer_close))
        }
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text(stringResource(title(state.panel))) },
        text = { Text(stringResource(when (state.panel) {
            LayerPanel.IMPORT, LayerPanel.FORMATS, LayerPanel.PREVIEW -> R.string.layer_import_limit
            LayerPanel.APPEARANCE -> R.string.layer_size_caution
            LayerPanel.COLOR -> R.string.layer_color_hint
            LayerPanel.FILTER, LayerPanel.DATA -> R.string.layer_filter_help
            else -> R.string.layer_source_preserved
        })) }, confirmButton = { TextButton({ help = false }) { Text(stringResource(R.string.layer_close)) } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.layer_delete)) },
        text = { Text(stringResource(R.string.layer_delete_confirm)) }, confirmButton = { TextButton({ confirmDelete = false; actions.onDeleteLayer() }) { Text(stringResource(R.string.layer_delete)) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.layer_cancel)) } })
}
private fun title(panel: LayerPanel): Int = when (panel) {
    LayerPanel.LIBRARY -> R.string.layer_library
    LayerPanel.IMPORT -> R.string.layer_import
    LayerPanel.FORMATS -> R.string.layer_formats
    LayerPanel.PREVIEW -> R.string.layer_preview
    LayerPanel.DATA -> R.string.layer_data
    LayerPanel.ATTRIBUTES -> R.string.layer_attributes
    LayerPanel.SOURCE -> R.string.layer_source
    LayerPanel.MANAGE -> R.string.layer_library
    LayerPanel.EDIT -> R.string.layer_edit
    LayerPanel.FILTER -> R.string.layer_filter
    LayerPanel.APPEARANCE -> R.string.layer_appearance
    LayerPanel.COLOR -> R.string.layer_custom_color
}

@Composable private fun Import(actions: LayerWorkspaceActions, busy: Boolean) {
    Surface(shape = RoundedCornerShape(20.dp), color = Lavender) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FolderOpen, null); Text(stringResource(R.string.layer_no_file), Modifier.padding(start = 10.dp))
        }
    }
    LayerNav(stringResource(R.string.layer_formats), Icons.Outlined.Description, { actions.onPanel(LayerPanel.FORMATS) }, !busy)
    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Visibility, null); Text(stringResource(R.string.layer_before_add), Modifier.padding(start = 10.dp), fontSize = 14.sp) }
}
@Composable private fun Formats() {
    listOf(R.string.layer_format_geojson, R.string.layer_format_kml, R.string.layer_format_gpx, R.string.layer_format_csv, R.string.layer_format_gpkg).forEach {
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Description, null, Modifier.size(26.dp)); Text(stringResource(it), Modifier.padding(start = 10.dp), fontSize = 14.sp)
        }
        HorizontalDivider(color = Border)
    }
    Text(stringResource(R.string.layer_format_shapefile), color = Secondary, fontSize = 12.sp)
    Text(stringResource(R.string.layer_import_limit), color = Secondary, fontSize = 12.sp)
}
@Composable private fun Library(state: LayerWorkspaceState, actions: LayerWorkspaceActions) {
    if (state.layers.isEmpty()) Text(stringResource(R.string.layer_no_layers), color = Secondary)
    state.layers.forEach { layer ->
        LayerNav(layer.name, Icons.Outlined.Layers, { actions.onOpenLayer(layer.id) }, !state.busy,
            stringResource(R.string.layer_objects_count, layer.count))
    }
    LayerButton(stringResource(R.string.layer_import), { actions.onPanel(LayerPanel.IMPORT) }, enabled = !state.busy)
}
@Composable private fun Preview(state: LayerWorkspaceState, actions: LayerWorkspaceActions) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Description, null, Modifier.size(32.dp))
        Column(Modifier.padding(start = 12.dp)) {
            Text(state.filename, fontWeight = FontWeight.SemiBold)
            Text("${state.format} · ${stringResource(R.string.layer_objects_count, state.totalCount)}", color = Secondary, fontSize = 14.sp)
        }
    }
    LayerNav(stringResource(R.string.layer_view_data), Icons.Outlined.TableChart, { actions.onPanel(LayerPanel.DATA) }, !state.busy)
    LayerNav(stringResource(R.string.layer_controls_source), Icons.Outlined.Info, { actions.onPanel(LayerPanel.SOURCE) }, !state.busy)
    Text(stringResource(R.string.layer_source_not_certified), color = Secondary, fontSize = 12.sp)
    TwoActions(stringResource(R.string.layer_cancel), actions::onDiscardPreview, stringResource(R.string.layer_add), actions::onCommit, !state.busy)
}
@Composable private fun Data(state: LayerWorkspaceState, actions: LayerWorkspaceActions) {
    OutlinedTextField(state.query, actions::onQuery, Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text(stringResource(R.string.layer_search)) }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = { IconButton({ actions.onPanel(LayerPanel.FILTER) }, enabled = !state.busy) { Icon(Icons.Outlined.FilterList, stringResource(R.string.layer_filter)) } },
        enabled = !state.busy, shape = RoundedCornerShape(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.layer_result_count, state.visibleCount, state.totalCount), fontSize = 13.sp)
        Text(stringResource(if (state.editable) R.string.layer_local_edits else R.string.layer_read_only), color = Secondary, fontSize = 12.sp)
    }
    if (state.objects.isEmpty()) Text(stringResource(R.string.layer_no_objects), color = Secondary)
    state.objects.forEach { feature ->
        Surface(Modifier.fillMaxWidth().clickable(enabled = !state.busy) { actions.onSelectObject(feature.id) },
            shape = RoundedCornerShape(12.dp), color = if (feature.id == state.selectedId) Color(0xFFE7F2DF) else Color.White,
            border = if (feature.id == state.selectedId) BorderStroke(1.dp, Green) else null) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(Color(state.appearance.colorArgb), CircleShape))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(feature.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (feature.subtitle.isNotEmpty()) Text(feature.subtitle, color = Secondary, fontSize = 13.sp)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
        }
        HorizontalDivider(color = Border.copy(alpha = 0.6f))
    }
    if (state.canLoadMore) TextButton(actions::onLoadMore) { Text(stringResource(R.string.layer_more)) }
}
@Composable private fun Attributes(state: LayerWorkspaceState, actions: LayerWorkspaceActions) {
    state.selected?.let { selected ->
        Text(selected.title, fontWeight = FontWeight.SemiBold)
        Text(selected.geometry ?: stringResource(R.string.layer_unknown), color = Secondary, fontSize = 13.sp)
        selected.attributes.forEach { AttributeRow(it) }
    }
    LayerNav(stringResource(R.string.layer_source), Icons.Outlined.Description, { actions.onPanel(LayerPanel.SOURCE) }, !state.busy)
    if (state.editable) LayerButton(stringResource(R.string.layer_edit), { actions.onPanel(LayerPanel.EDIT) }, enabled = !state.busy)
}
@Composable private fun Source(state: LayerWorkspaceState) {
    val unknown = stringResource(R.string.layer_unknown)
    var details by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Border)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Description, null); Text(state.filename, Modifier.padding(start = 10.dp), fontSize = 15.sp)
        }
    }
    SourceRow(stringResource(if (state.sourceCrsFromFormat) R.string.layer_format_crs else R.string.layer_source_crs), state.sourceCrs ?: unknown)
    SourceRow(stringResource(R.string.layer_producer), state.sourceProducer ?: unknown)
    if (state.sourceFields.isNotEmpty()) {
        Text(stringResource(R.string.layer_observed_fields), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        state.sourceFields.forEach { attribute ->
            SourceRow(attribute.key, stringResource(kindLabel(attribute.kind)))
        }
    }
    TextButton({ details = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Info, null, Modifier.size(19.dp))
        Text(stringResource(R.string.layer_source_details), Modifier.padding(start = 8.dp))
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text(stringResource(R.string.layer_source)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ForestrySourceDetails(title = state.filename,
                    attribution = stringResource(R.string.layer_source_preserved),
                    rows = listOf(
                        stringResource(if (state.sourceCrsFromFormat) R.string.layer_format_crs else R.string.layer_source_crs) to (state.sourceCrs ?: unknown),
                        stringResource(R.string.layer_source_order) to sourceOrderLabel(state.sourceOrder),
                        stringResource(R.string.layer_producer) to (state.sourceProducer ?: unknown),
                        stringResource(R.string.layer_source_version) to (state.sourceVersion ?: unknown)),
                    caution = stringResource(R.string.layer_source_not_certified))
                Text(stringResource(R.string.layer_normalized_crs), fontSize = 13.sp)
                Text(stringResource(R.string.layer_sha), fontSize = 12.sp, color = Secondary)
                Text(state.sourceHash, fontSize = 11.sp)
                if (state.sourceBasicAttributes) Text(stringResource(R.string.layer_basic_attributes), fontSize = 12.sp, color = Secondary)
            }
        }, confirmButton = { TextButton({ details = false }) { Text(stringResource(R.string.layer_close)) } })
}
@Composable private fun SourceRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(0.55f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(value, Modifier.weight(0.45f), fontSize = 13.sp, color = Secondary)
    }
    HorizontalDivider(color = Border)
}
@Composable private fun Manage(state: LayerWorkspaceState, actions: LayerWorkspaceActions) {
    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Smartphone, null); Text(stringResource(R.string.layer_on_phone), Modifier.padding(start = 10.dp)) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.layer_visible), Modifier.weight(1f))
        Switch(state.appearance.visible, { actions.onAppearance(state.appearance.copy(visible = it)); actions.onSaveAppearance() }, enabled = !state.busy)
    }
    LayerNav(stringResource(R.string.layer_data), Icons.Outlined.TableChart, { actions.onPanel(LayerPanel.DATA) }, !state.busy, stringResource(R.string.layer_objects_count, state.totalCount))
    LayerNav(stringResource(R.string.layer_appearance), Icons.Outlined.Palette, { actions.onPanel(LayerPanel.APPEARANCE) }, !state.busy,
        swatchArgb = state.appearance.colorArgb)
    LayerNav(stringResource(R.string.layer_source), Icons.Outlined.Description, { actions.onPanel(LayerPanel.SOURCE) }, !state.busy)
    LayerButton(stringResource(R.string.layer_close), actions::onClose, enabled = !state.busy)
}
@Composable private fun Edit(state: LayerWorkspaceState, actions: LayerWorkspaceActions) {
    state.editFields.forEach { field ->
        if (field.editable) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(0.35f)) { Text(field.key, fontWeight = FontWeight.SemiBold); Text(stringResource(kindLabel(field.kind)), fontSize = 11.sp, color = Secondary) }
            OutlinedTextField(field.input, { actions.onAttribute(field.key, it) }, Modifier.weight(0.65f),
                singleLine = true, enabled = !state.busy, shape = RoundedCornerShape(12.dp),
                placeholder = if (field.kind == LayerValueKind.NULL) ({ Text(stringResource(R.string.layer_null), fontSize = 12.sp) }) else null,
                trailingIcon = { IconButton({ actions.onNullAttribute(field.key) }, enabled = !state.busy) { Icon(Icons.Outlined.Block, stringResource(R.string.layer_set_null), Modifier.size(20.dp)) } })
        } else AttributeRow(field)
    }
    Text(stringResource(R.string.layer_local_edits), color = Secondary, fontSize = 13.sp)
    Text(stringResource(R.string.layer_source_preserved), color = Secondary, fontSize = 12.sp)
    TwoActions(stringResource(R.string.layer_cancel), actions::onBack, stringResource(R.string.layer_save), actions::onSaveAttributes, !state.busy && state.editValid)
}
@Composable private fun Filter(state: LayerWorkspaceState, actions: LayerWorkspaceActions) {
    FieldChoice(stringResource(R.string.layer_field), state.filterField ?: stringResource(R.string.layer_unknown),
        state.fields.map { it to it }, { actions.onFilterField(it) }, !state.busy)
    FieldChoice(stringResource(R.string.layer_condition), stringResource(conditionLabel(state.filterCondition)),
        state.filterConditions.map { it.name to stringResource(conditionLabel(it)) }, { actions.onFilterCondition(LayerCondition.valueOf(it)) }, !state.busy)
    if (state.filterCondition == LayerCondition.CONTAINS) {
        OutlinedTextField(state.filterText, actions::onFilterText, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.layer_value)) }, enabled = !state.busy, shape = RoundedCornerShape(12.dp))
    } else if (state.filterCondition == LayerCondition.EQUALS) {
        val value = state.filterValues.firstOrNull { it.key == state.filterValueKey }
        FieldChoice(stringResource(R.string.layer_value), value?.let { displayValue(it.value, it.kind) } ?: stringResource(R.string.layer_unknown),
            state.filterValues.filter { state.filterCondition != LayerCondition.CONTAINS || it.kind == LayerValueKind.TEXT }
                .map { it.key to displayValue(it.value, it.kind) }, actions::onFilterValue, !state.busy)
    }
    HorizontalDivider(color = Border)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.layer_result), fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.layer_result_count, state.visibleCount, state.totalCount))
    }
    TwoActions(stringResource(R.string.layer_reset), actions::onResetFilter, stringResource(R.string.layer_close), actions::onBack, !state.busy)
}
@Composable private fun Appearance(state: LayerWorkspaceState, actions: LayerWorkspaceActions) {
    val style = state.appearance
    if (state.geometry in setOf(LayerGeometryKind.POINT, LayerGeometryKind.MIXED)) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.layer_preset), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        FilterChip(!style.pinSymbol, { actions.onAppearance(style.copy(pinSymbol = false)) }, label = { Text(stringResource(R.string.layer_dot)) })
        Spacer(Modifier.width(6.dp))
        FilterChip(style.pinSymbol, { actions.onAppearance(style.copy(pinSymbol = true)) }, label = { Text(stringResource(R.string.layer_pin)) })
    } else Text(stringResource(if (state.geometry == LayerGeometryKind.LINE) R.string.layer_line else R.string.layer_polygon), fontWeight = FontWeight.SemiBold)
    HorizontalDivider(color = Border)
    Text(stringResource(R.string.layer_color), fontWeight = FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        val colors = listOf(0xFF087C96L, 0xFF206C32L, 0xFF815E7AL, 0xFF626D7BL, 0xFFAE7059L)
        val names = listOf(R.string.layer_color_petrol, R.string.layer_color_fir, R.string.layer_color_plum, R.string.layer_color_slate, R.string.layer_color_earth)
        colors.forEachIndexed { index, color ->
            val label = stringResource(names[index])
            Column(Modifier.weight(1f).clickable(enabled = !state.busy, role = Role.RadioButton) { actions.onAppearance(style.copy(colorArgb = color)) }
                .heightIn(min = 54.dp).semantics { contentDescription = label; selected = style.colorArgb == color }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(30.dp).background(Color(color), CircleShape), contentAlignment = Alignment.Center) {
                    if (style.colorArgb == color) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Text(label, fontSize = 10.sp)
            }
        }
        IconButton({ actions.onPanel(LayerPanel.COLOR) }, enabled = !state.busy) { Icon(Icons.Outlined.Palette, stringResource(R.string.layer_custom_color)) }
    }
    HorizontalDivider(color = Border)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.layer_size), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        listOf(R.string.layer_size_small, R.string.layer_size_medium, R.string.layer_size_large).forEachIndexed { index, label ->
            FilterChip(style.sizeIndex == index, { actions.onAppearance(style.copy(sizeIndex = index)) }, label = { Text(stringResource(label)) }, enabled = !state.busy)
        }
    }
    Text(stringResource(R.string.layer_size_caution), color = Secondary, fontSize = 11.sp)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.layer_labels), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Switch(style.labels, { actions.onAppearance(style.copy(labels = it)) }, enabled = !state.busy)
    }
    if (style.labels) FieldChoice(stringResource(R.string.layer_label_field), style.labelField ?: stringResource(R.string.layer_unknown),
        state.labelFields.map { it to it }, { actions.onAppearance(style.copy(labelField = it)) }, !state.busy)
    LayerButton(stringResource(R.string.layer_close), actions::onSaveAppearance, enabled = !state.busy)
}
@Composable private fun AttributeRow(attribute: LayerAttributeUi) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(attribute.key, Modifier.weight(0.4f), fontWeight = FontWeight.SemiBold)
        Text(displayValue(attribute.value, attribute.kind), Modifier.weight(0.6f), color = if (attribute.kind in setOf(LayerValueKind.NULL, LayerValueKind.ABSENT)) Secondary else Ink)
    }
    HorizontalDivider(color = Border.copy(alpha = 0.7f))
}
@Composable private fun displayValue(value: String, kind: LayerValueKind): String = when {
    kind == LayerValueKind.NULL -> stringResource(R.string.layer_null)
    kind == LayerValueKind.ABSENT -> stringResource(R.string.layer_absent)
    kind == LayerValueKind.TEXT && value.isEmpty() -> stringResource(R.string.layer_empty)
    else -> value
}
private fun kindLabel(kind: LayerValueKind): Int = when (kind) {
    LayerValueKind.TEXT -> R.string.layer_text
    LayerValueKind.NUMBER -> R.string.layer_number
    LayerValueKind.BOOLEAN -> R.string.layer_boolean
    LayerValueKind.NULL -> R.string.layer_null
    LayerValueKind.ABSENT -> R.string.layer_absent
    LayerValueKind.OBJECT -> R.string.layer_object
    LayerValueKind.ARRAY -> R.string.layer_array
}
private fun conditionLabel(condition: LayerCondition): Int = when (condition) {
    LayerCondition.EQUALS -> R.string.layer_equals
    LayerCondition.CONTAINS -> R.string.layer_contains
    LayerCondition.IS_NULL -> R.string.layer_is_null
    LayerCondition.IS_ABSENT -> R.string.layer_is_absent
    LayerCondition.IS_EMPTY -> R.string.layer_is_empty
}
@Composable private fun FieldChoice(label: String, value: String, choices: List<Pair<String, String>>, onChoose: (String) -> Unit, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(0.35f), fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(0.65f)) {
            OutlinedButton({ open = true }, enabled = enabled && choices.isNotEmpty(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(value, Modifier.weight(1f), color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Ink)
            }
            DropdownMenu(open, { open = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                choices.forEach { (key, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onChoose(key) }) }
            }
        }
    }
}
@Composable private fun LayerNav(label: String, icon: ImageVector, onClick: () -> Unit, enabled: Boolean, suffix: String? = null,
    swatchArgb: Long? = null) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).heightIn(min = 52.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp)); Text(label, Modifier.weight(1f).padding(start = 12.dp), fontSize = 16.sp)
        suffix?.let { Text(it, color = Secondary, fontSize = 12.sp) }
        swatchArgb?.let { argb ->
            val description = "${stringResource(R.string.layer_color)} #${"%08X".format(argb and 0xFFFFFFFFL)}"
            Surface(Modifier.padding(horizontal = 8.dp).size(20.dp).semantics { contentDescription = description },
                shape = CircleShape, color = Color(argb), border = BorderStroke(1.dp, Ink)) {}
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
    HorizontalDivider(color = Border)
}
@Composable internal fun LayerButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, secondary: Boolean = false) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled,
        shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(containerColor = if (secondary) Color(0xFFE0EED8) else Green,
            contentColor = if (secondary) Green else Color.White)) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}
@Composable internal fun TwoActions(first: String, onFirst: () -> Unit, second: String, onSecond: () -> Unit, enabled: Boolean, firstEnabled: Boolean = enabled) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LayerButton(first, onFirst, Modifier.weight(1f), firstEnabled, secondary = true)
        LayerButton(second, onSecond, Modifier.weight(1f), enabled)
    }
}

@Composable private fun sourceOrderLabel(order: String?): String = when (order) {
    "longitude_latitude" -> stringResource(R.string.layer_order_lon_lat)
    "easting_northing" -> stringResource(R.string.layer_order_easting_northing)
    "named_latitude_longitude_columns" -> stringResource(R.string.layer_order_named_columns)
    "named_latitude_longitude_attributes" -> stringResource(R.string.layer_order_named_attributes)
    else -> stringResource(R.string.layer_unknown)
}
