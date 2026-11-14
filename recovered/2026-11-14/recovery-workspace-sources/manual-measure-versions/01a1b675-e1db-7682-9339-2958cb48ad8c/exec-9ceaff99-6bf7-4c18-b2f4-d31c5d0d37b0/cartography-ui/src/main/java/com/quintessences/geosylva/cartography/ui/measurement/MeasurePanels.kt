package com.quintessences.geosylva.cartography.ui.measurement

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quintessences.geosylva.cartography.ui.R

@Composable
internal fun MeasureSheet(state: MeasureWorkspaceState, actions: MeasureWorkspaceActions, modifier: Modifier) {
    Surface(modifier = modifier.blockMapTouches(), shape = MeasureUi.panel, color = MeasureUi.paper,
        contentColor = MeasureUi.ink, shadowElevation = 4.dp) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                .size(32.dp, 4.dp).clip(CircleShape).background(MeasureUi.border))
            MeasurePanelHeader(state, actions)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(bottom = if (state.panel == MeasurePanel.UNITS) 8.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(if (state.panel == MeasurePanel.UNITS) 2.dp else 6.dp)) {
                when (state.panel) {
                    MeasurePanel.EDITOR -> EditorContent(state, actions)
                    MeasurePanel.UNITS -> UnitsContent(state, actions)
                    MeasurePanel.SAVE -> SaveContent(state, actions)
                    MeasurePanel.COLLAPSED -> Unit
                }
            }
        }
    }
}

@Composable
private fun MeasurePanelHeader(state: MeasureWorkspaceState, actions: MeasureWorkspaceActions) {
    Row(Modifier.fillMaxWidth().padding(vertical = if (state.panel == MeasurePanel.UNITS) 2.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MeasureUi.lavender),
            contentAlignment = Alignment.Center) {
            when (state.panel) {
                MeasurePanel.UNITS -> Icon(Icons.Outlined.Tune, null, Modifier.size(28.dp))
                MeasurePanel.SAVE -> Icon(Icons.Outlined.Description, null, Modifier.size(28.dp))
                else -> MeasurementGlyph(Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(stringResource(when (state.panel) {
            MeasurePanel.UNITS -> R.string.gs_measure_units
            MeasurePanel.SAVE -> R.string.gs_measure_save_title
            else -> R.string.gs_measure_title
        }), Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 20.sp)
        MeasureInfo(stringResource(when (state.panel) {
            MeasurePanel.UNITS -> R.string.gs_measure_units_help
            MeasurePanel.SAVE -> R.string.gs_measure_save_help
            else -> R.string.gs_measure_method_help
        })) { actions.onExplain(when (state.panel) {
            MeasurePanel.UNITS -> MeasureHelp.UNITS
            MeasurePanel.SAVE -> MeasureHelp.SAVE
            else -> MeasureHelp.METHOD
        }) }
        if (state.panel == MeasurePanel.EDITOR) MeasureMenu(state, actions)
        IconButton(onClick = {
            when (state.panel) {
                MeasurePanel.SAVE -> actions.onCancelSave()
                MeasurePanel.UNITS -> actions.onPanelChange(MeasurePanel.EDITOR)
                else -> actions.onPanelChange(MeasurePanel.COLLAPSED)
            }
        }, enabled = !state.saving, modifier = Modifier.size(MeasureUi.touch)) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(MeasureUi.lavender),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, stringResource(if (state.panel == MeasurePanel.EDITOR)
                    R.string.gs_measure_fold else R.string.gs_measure_close), Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun MeasureMenu(state: MeasureWorkspaceState, actions: MeasureWorkspaceActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(MeasureUi.touch)) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(MeasureUi.lavender),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.MoreVert, stringResource(R.string.gs_measure_menu), Modifier.size(22.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MeasureMenuAction.entries.filter { it in state.availableMenuActions }.forEach { action ->
                DropdownMenuItem(text = { Text(stringResource(when (action) {
                    MeasureMenuAction.UNITS -> R.string.gs_measure_units
                    MeasureMenuAction.SEGMENTS -> R.string.gs_measure_segments
                    MeasureMenuAction.COLOR -> R.string.gs_measure_color
                    MeasureMenuAction.CLEAR -> R.string.gs_measure_clear
                    MeasureMenuAction.SHARE -> R.string.gs_measure_share
                    MeasureMenuAction.SAVED_MEASURES -> R.string.gs_measure_saved
                })) }, leadingIcon = { Icon(when (action) {
                    MeasureMenuAction.UNITS -> Icons.Outlined.Tune
                    MeasureMenuAction.SEGMENTS -> Icons.Outlined.FormatListBulleted
                    MeasureMenuAction.COLOR -> Icons.Outlined.Palette
                    MeasureMenuAction.CLEAR -> Icons.Outlined.DeleteOutline
                    MeasureMenuAction.SHARE -> Icons.Outlined.Share
                    MeasureMenuAction.SAVED_MEASURES -> Icons.Outlined.FolderOpen
                }, null) }, onClick = { expanded = false; actions.onMenuAction(action) },
                    modifier = Modifier.heightIn(min = MeasureUi.touch))
            }
        }
    }
}

@Composable
private fun ColumnScope.EditorContent(state: MeasureWorkspaceState, actions: MeasureWorkspaceActions) {
    MeasureChoices(listOf(MeasureMode.DISTANCE to stringResource(R.string.gs_measure_distance),
        MeasureMode.AREA to stringResource(R.string.gs_measure_area)), state.mode,
        enabled = state.geometryEditable && !state.saving, onSelected = actions::onModeChange)
    ResultSummary(state, actions, includeMode = false)
    Text(stringResource(if (state.mode == MeasureMode.DISTANCE) R.string.gs_measure_distance_on_map
        else R.string.gs_measure_area_on_map), color = MeasureUi.secondary, fontSize = 14.sp)
    HorizontalDivider(color = MeasureUi.border)
    Text(state.validationMessage ?: stringResource(R.string.gs_measure_touch_to_add),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), textAlign = TextAlign.Center,
        color = MeasureUi.ink, fontSize = 14.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = actions::onUndo, enabled = state.canUndo, shape = MeasureUi.command,
            color = MeasureUi.paleGreen, contentColor = MeasureUi.green.copy(alpha = if (state.canUndo) 1f else 0.45f),
            modifier = Modifier.size(width = 56.dp, height = MeasureUi.touch)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.gs_measure_undo), Modifier.size(26.dp))
            }
        }
        MeasureAction(stringResource(R.string.gs_measure_save), Modifier.weight(1f), Icons.Outlined.Description,
            enabled = state.canSave, onClick = actions::onSaveRequested)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ResultSummary(state: MeasureWorkspaceState, actions: MeasureWorkspaceActions, includeMode: Boolean) {
    // Let the actual formatted value and badge decide whether they share a row.
    // A weighted numeric field would squeeze and split significant digits.
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ValueLine(state, actions)
        PointCount(state, actions, includeMode)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ValueLine(state: MeasureWorkspaceState, actions: MeasureWorkspaceActions, modifier: Modifier = Modifier) {
    val missing = stringResource(R.string.gs_measure_no_result)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // An unusually long number remains one complete, horizontally scrollable
        // value even on a small window; its precision and typography are preserved.
        Text(state.valueText ?: "—", Modifier.align(Alignment.CenterVertically)
            .horizontalScroll(rememberScrollState())
            .then(if (state.valueText == null) Modifier.semantics { contentDescription = missing } else Modifier),
            fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, softWrap = false)
        Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
            if (state.valueText != null && state.unitText.isNotBlank()) Text(state.unitText,
                fontSize = 30.sp, fontWeight = FontWeight.Bold, softWrap = false)
            MeasureInfo(stringResource(R.string.gs_measure_result_help)) { actions.onExplain(MeasureHelp.RESULT) }
        }
    }
}

@Composable
private fun PointCount(state: MeasureWorkspaceState, actions: MeasureWorkspaceActions, includeMode: Boolean,
    modifier: Modifier = Modifier) {
    val count = pluralStringResource(R.plurals.gs_measure_point_count, state.pointCount, state.pointCount)
    val label = if (includeMode) stringResource(if (state.mode == MeasureMode.DISTANCE)
        R.string.gs_measure_distance else R.string.gs_measure_area) + " · " + count else count
    Surface(onClick = { actions.onExplain(MeasureHelp.POINTS) }, shape = CircleShape,
        color = MeasureUi.lavender, modifier = modifier.heightIn(min = MeasureUi.touch)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = MeasureUi.ink, fontSize = 14.sp)
            Icon(Icons.Outlined.Info, stringResource(R.string.gs_measure_points_help),
                modifier = Modifier.size(18.dp), tint = MeasureUi.ink)
        }
    }
}

@Composable
private fun ColumnScope.UnitsContent(state: MeasureWorkspaceState, actions: MeasureWorkspaceActions) {
    UnitHeading(stringResource(R.string.gs_measure_distance), stringResource(R.string.gs_measure_distance_unit_help)) {
        actions.onExplain(MeasureHelp.DISTANCE_UNIT)
    }
    MeasureChoices(listOf(MeasureDistanceUnit.METRES to "m", MeasureDistanceUnit.KILOMETRES to "km"),
        state.distanceUnit, onSelected = actions::onDistanceUnitChange)
    UnitHeading(stringResource(R.string.gs_measure_area), stringResource(R.string.gs_measure_area_unit_help)) {
        actions.onExplain(MeasureHelp.AREA_UNIT)
    }
    MeasureChoices(listOf(MeasureAreaUnit.SQUARE_METRES to "m²", MeasureAreaUnit.ARES to stringResource(R.string.gs_measure_ares),
        MeasureAreaUnit.HECTARES to "ha"), state.areaUnit, onSelected = actions::onAreaUnitChange)
    HorizontalDivider(Modifier.padding(top = 2.dp), color = MeasureUi.border)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(if (state.mode == MeasureMode.DISTANCE) R.string.gs_measure_displayed_distance
            else R.string.gs_measure_displayed_area), Modifier.weight(1f), color = MeasureUi.secondary, fontSize = 14.sp)
        Text(state.valueText?.let { "$it ${state.unitText}" } ?: stringResource(R.string.gs_measure_no_result),
            fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        MeasureInfo(stringResource(R.string.gs_measure_result_help)) { actions.onExplain(MeasureHelp.RESULT) }
    }
    MeasureAction(stringResource(R.string.gs_measure_close), Modifier.fillMaxWidth()) {
        actions.onPanelChange(MeasurePanel.EDITOR)
    }
}

@Composable
private fun UnitHeading(label: String, help: String, onHelp: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontWeight = FontWeight.Medium, fontSize = 16.sp)
        MeasureInfo(help, onClick = onHelp)
    }
}

@Composable
private fun ColumnScope.SaveContent(state: MeasureWorkspaceState, actions: MeasureWorkspaceActions) {
    ResultSummary(state, actions, includeMode = true)
    HorizontalDivider(color = MeasureUi.border)
    OutlinedTextField(value = state.saveName, onValueChange = actions::onSaveNameChange,
        enabled = !state.saving && state.saveNameEditable, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.gs_measure_name_optional)) }, shape = RoundedCornerShape(14.dp),
        trailingIcon = { MeasureInfo(stringResource(R.string.gs_measure_name_help)) { actions.onExplain(MeasureHelp.NAME) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (state.canSave && !state.saving) actions.onConfirmSave() }))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Smartphone, null, Modifier.size(20.dp))
        Text(stringResource(R.string.gs_measure_local_save), Modifier.weight(1f), fontSize = 14.sp)
        MeasureInfo(stringResource(R.string.gs_measure_local_help)) { actions.onExplain(MeasureHelp.LOCAL_STORAGE) }
    }
    state.saveError?.let {
        Text(it, color = MeasureUi.error, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive })
    }
    if (state.saving) Row(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp), color = MeasureUi.green, strokeWidth = 2.dp)
        Text(stringResource(R.string.gs_measure_saving), fontSize = 14.sp)
    }
    val largeText = LocalDensity.current.fontScale > 1.25f
    if (largeText) {
        MeasureAction(stringResource(R.string.gs_measure_save), Modifier.fillMaxWidth(), Icons.Outlined.Description,
            enabled = state.canSave && !state.saving, onClick = actions::onConfirmSave)
        MeasureAction(stringResource(R.string.gs_measure_cancel), Modifier.fillMaxWidth(),
            enabled = !state.saving, primary = false, onClick = actions::onCancelSave)
    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MeasureAction(stringResource(R.string.gs_measure_cancel), Modifier.weight(1f),
            enabled = !state.saving, primary = false, onClick = actions::onCancelSave)
        MeasureAction(stringResource(R.string.gs_measure_save), Modifier.weight(1.2f), Icons.Outlined.Description,
            enabled = state.canSave && !state.saving, onClick = actions::onConfirmSave)
    }
}
