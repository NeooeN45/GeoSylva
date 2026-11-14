package com.quintessences.geosylva.cartography.ui.trees

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quintessences.geosylva.cartography.ui.R
import java.text.NumberFormat
import kotlin.math.cos
import kotlin.math.sin

private object TreeUi {
    val paper = Color(0xFFFFFEFF)
    val ink = Color(0xFF0B286B)
    val secondary = Color(0xFF707AB0)
    val green = Color(0xFF00652C)
    val paleGreen = Color(0xFFD6E8D5)
    val lavender = Color(0xFFEAE7F8)
    val border = Color(0xFFDEDEE5)
    val stop = Color(0xFFCA705B)
    val touch = 48.dp
}

/** Shared nonmodal sheet for references 64–71. No SDK, repository or sensor acquisition. */
@Composable
fun TreeWorkspacePanel(
    state: TreeWorkspaceUiState,
    onAction: (TreeWorkspaceAction) -> Unit,
    modifier: Modifier = Modifier,
    onHeightChanged: (Dp) -> Unit = {},
) {
    val panel = state.panel ?: return
    BackHandler { onAction(TreeWorkspaceAction.Close) }
    val density = LocalDensity.current
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.58f).dp
    Surface(modifier.fillMaxWidth().heightIn(max = maxHeight)
        .onSizeChanged { onHeightChanged(with(density) { it.height.toDp() }) }
        .pointerInput(Unit) { detectTapGestures(onTap = {}) },
        shape = RoundedCornerShape(28.dp), color = TreeUi.paper, contentColor = TreeUi.ink,
        shadowElevation = 6.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(32.dp, 4.dp)
                .clip(CircleShape).background(Color(0xFFCCCCCC)))
            if (panel != TreeWorkspacePanel.HELP && state.readStatus != TreeReadStatus.READY) {
                PanelTitle(stringResource(if (state.readStatus == TreeReadStatus.LOADING) R.string.gs_tree_loading else R.string.gs_tree_unavailable),
                    onClose = { onAction(TreeWorkspaceAction.Close) })
                if (state.readStatus == TreeReadStatus.UNAVAILABLE) TreeButton(stringResource(R.string.gs_tree_retry)) {
                    onAction(TreeWorkspaceAction.RetryRead)
                }
                return@Column
            }
            when (panel) {
                TreeWorkspacePanel.RECORD -> state.record?.let { Record(it, state.guidance.active, onAction) }
                TreeWorkspacePanel.GUIDANCE -> state.record?.let { Guidance(it, state.guidance, onAction) }
                TreeWorkspacePanel.SPECIES -> Species(state, onAction)
                TreeWorkspacePanel.ACCURACY -> Accuracy(state, onAction)
                TreeWorkspacePanel.EMPTY -> Empty(state, onAction)
                TreeWorkspacePanel.RECORDS -> Records(state, onAction)
                TreeWorkspacePanel.HELP -> Help(state.help, onAction)
            }
        }
    }
}

@Composable
private fun PanelTitle(label: String, help: TreeHelp? = null, onAction: (TreeWorkspaceAction) -> Unit = {}, onClose: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.Bold)
        help?.let { Info(it, onAction) }
        onClose?.let { Close(it) }
    }
}

@Composable
private fun Identity(record: TreeRecordUi, onAction: (TreeWorkspaceAction) -> Unit, showSpeciesFirst: Boolean = false, close: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(TreeUi.paleGreen), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Eco, null, Modifier.size(32.dp), tint = TreeUi.green)
        }
        Column(Modifier.weight(1f)) {
            Text(if (showSpeciesFirst) record.speciesName else treeName(record), fontWeight = FontWeight.Bold, fontSize = 21.sp)
            Text(if (showSpeciesFirst) treeName(record) else record.speciesName, fontSize = 16.sp, color = TreeUi.secondary)
            record.parentLabel?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 14.sp, color = TreeUi.secondary) }
        }
        Info(TreeHelp.RECORD, onAction)
        if (close) Close { onAction(TreeWorkspaceAction.Close) }
    }
}

@Composable
private fun Record(record: TreeRecordUi, active: Boolean, onAction: (TreeWorkspaceAction) -> Unit) {
    Identity(record, onAction, showSpeciesFirst = true, close = true)
    Divider()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            LabelWithInfo(stringResource(R.string.gs_tree_diameter), TreeHelp.DIAMETER, onAction)
            Text(record.diameterCm?.let { stringResource(R.string.gs_tree_cm, number(it)) } ?: stringResource(R.string.gs_tree_unrecorded),
                fontSize = 25.sp, fontWeight = FontWeight.Bold)
            record.diameterClass?.let { Text(stringResource(R.string.gs_tree_diameter_class, it), fontSize = 13.sp, color = TreeUi.secondary) }
        }
        Column(Modifier.weight(1f)) {
            LabelWithInfo(stringResource(R.string.gs_tree_height), TreeHelp.HEIGHT, onAction)
            Text(record.heightM?.let { metres(it) } ?: stringResource(R.string.gs_tree_unrecorded), fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
    }
    Divider()
    LabelWithInfo(stringResource(R.string.gs_tree_recorded_accuracy_value, accuracy(record.recordedAccuracyM)), TreeHelp.RECORDED_ACCURACY, onAction)
    if (!record.hasCoordinates) Text(stringResource(R.string.gs_tree_no_coordinates, 1), color = TreeUi.secondary)
    TreeButton(stringResource(if (active) R.string.gs_tree_back_guidance else R.string.gs_tree_start), enabled = record.hasCoordinates,
        icon = { Icon(Icons.Outlined.Explore, null) }) { onAction(TreeWorkspaceAction.StartGuidance) }
}

@Composable
private fun Guidance(record: TreeRecordUi, guidance: TreeGuidanceUi, onAction: (TreeWorkspaceAction) -> Unit) {
    Identity(record, onAction)
    val fresh = guidance.gps == TreeGpsStatus.FRESH && guidance.distanceM != null
    if (fresh) {
        if (guidance.nearRecordedPoint) {
            Surface(shape = CircleShape, color = TreeUi.paleGreen) {
                Text(stringResource(R.string.gs_tree_near), Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = TreeUi.green, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (!guidance.nearRecordedPoint && guidance.relativeBearingDeg != null)
                DirectionCompass(guidance.relativeBearingDeg, Modifier.size(90.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(distance(guidance.distanceM), fontSize = 38.sp, fontWeight = FontWeight.Bold)
                    Info(TreeHelp.GUIDANCE, onAction)
                }
                Text(stringResource(if (guidance.nearRecordedPoint) R.string.gs_tree_near_distance else R.string.gs_tree_bird_distance),
                    fontSize = 16.sp, color = TreeUi.secondary)
            }
        }
        if (guidance.nearRecordedPoint) Text(stringResource(R.string.gs_tree_identify), color = TreeUi.secondary)
        if (guidance.relativeBearingDeg == null) {
            Chip(stringResource(R.string.gs_tree_gps_fresh))
            StatusCard(compassLabel(guidance.heading), stringResource(R.string.gs_tree_direction_hidden), TreeHelp.COMPASS, onAction)
        }
        Divider()
        Text(stringResource(R.string.gs_tree_accuracy_announced), color = TreeUi.secondary, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AccuracyValue(stringResource(R.string.gs_tree_phone), guidance.phoneAccuracyM, TreeHelp.PHONE_ACCURACY, onAction, Modifier.weight(1f))
            AccuracyValue(stringResource(R.string.gs_tree_recorded_tree), record.recordedAccuracyM, TreeHelp.RECORDED_ACCURACY, onAction, Modifier.weight(1f))
        }
    } else {
        val subtitle = stringResource(when (guidance.gps) {
            TreeGpsStatus.PERMISSION_REQUIRED -> R.string.gs_tree_permission_body
            TreeGpsStatus.PAUSED -> R.string.gs_tree_paused
            TreeGpsStatus.STALE -> R.string.gs_tree_gps_stale
            TreeGpsStatus.UNAVAILABLE -> R.string.gs_tree_gps_unavailable
            else -> R.string.gs_tree_gps_searching
        })
        StatusCard(stringResource(R.string.gs_tree_gps_unavailable), subtitle, TreeHelp.GPS, onAction)
        guidance.lastFixAgeSeconds?.let { age ->
            Text(if (age >= 60) stringResource(R.string.gs_tree_last_fix_minutes, age / 60)
                else stringResource(R.string.gs_tree_last_fix_seconds, age), color = TreeUi.secondary, fontSize = 14.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                LabelWithInfo(stringResource(R.string.gs_tree_distance), TreeHelp.GPS, onAction)
                Text(stringResource(R.string.gs_tree_distance_unavailable), fontWeight = FontWeight.Bold)
            }
            AccuracyValue(stringResource(R.string.gs_tree_recorded_tree), record.recordedAccuracyM, TreeHelp.RECORDED_ACCURACY, onAction, Modifier.weight(1f))
        }
        if (guidance.gps == TreeGpsStatus.PERMISSION_REQUIRED) TreeButton(stringResource(R.string.gs_tree_permission)) {
            onAction(TreeWorkspaceAction.RequestLocation)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TreeButton(stringResource(R.string.gs_tree_view_record), Modifier.weight(1f), icon = { Icon(Icons.Outlined.Assignment, null) }) {
            onAction(TreeWorkspaceAction.ViewRecord)
        }
        TreeButton(stringResource(R.string.gs_tree_stop), Modifier.weight(0.8f), stop = true, icon = { Icon(Icons.Outlined.StopCircle, null) }) {
            onAction(TreeWorkspaceAction.StopGuidance)
        }
    }
}

@Composable
private fun Species(state: TreeWorkspaceUiState, onAction: (TreeWorkspaceAction) -> Unit) {
    PanelTitle(stringResource(R.string.gs_tree_species), TreeHelp.SPECIES, onAction)
    TextButton(onClick = { onAction(TreeWorkspaceAction.ShowAllSpecies) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.gs_tree_show_all), color = TreeUi.secondary)
    }
    state.species.forEach { species ->
        Row(Modifier.fillMaxWidth().toggleable(value = species.selected, role = Role.Checkbox,
            onValueChange = { onAction(TreeWorkspaceAction.ToggleSpecies(species.code, it)) })
            .heightIn(min = TreeUi.touch).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Color(species.colorArgb)))
            Text(species.name, Modifier.weight(1f), fontSize = 18.sp)
            Text(number(species.locatedCount.toDouble()), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Checkbox(checked = species.selected, onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = TreeUi.green))
        }
        Divider()
    }
    Text(stringResource(R.string.gs_tree_located_count, state.counts.shown, state.counts.located), fontSize = 17.sp, fontWeight = FontWeight.Medium)
    Text(stringResource(R.string.gs_tree_no_coordinates, state.counts.withoutCoordinates), color = TreeUi.secondary)
    if (state.accuracyFilterEnabled) Text(stringResource(R.string.gs_tree_active_filter, number(state.accuracyThresholdM.toDouble())), color = TreeUi.secondary)
    TreeButton(stringResource(R.string.gs_tree_close)) { onAction(TreeWorkspaceAction.Close) }
}

@Composable
private fun Accuracy(state: TreeWorkspaceUiState, onAction: (TreeWorkspaceAction) -> Unit) {
    PanelTitle(stringResource(R.string.gs_tree_filter_title), TreeHelp.FILTER, onAction)
    Row(Modifier.fillMaxWidth().heightIn(min = TreeUi.touch), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.gs_tree_filter_enabled), Modifier.weight(1f), fontSize = 18.sp)
        Switch(state.accuracyFilterEnabled, onCheckedChange = { onAction(TreeWorkspaceAction.SetAccuracyFilter(it)) },
            colors = SwitchDefaults.colors(checkedTrackColor = TreeUi.green))
    }
    Divider()
    LabelWithInfo(stringResource(R.string.gs_tree_filter_max), TreeHelp.RECORDED_ACCURACY, onAction)
    Surface(shape = CircleShape, color = TreeUi.paper, border = androidx.compose.foundation.BorderStroke(1.dp, TreeUi.border)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            ThresholdButton(false, state.accuracyThresholdM > 3f) {
                onAction(TreeWorkspaceAction.SetAccuracyThreshold((state.accuracyThresholdM - 1f).coerceAtLeast(3f)))
            }
            Text(metres(state.accuracyThresholdM.toDouble()), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 30.sp, fontWeight = FontWeight.Bold)
            ThresholdButton(true, state.accuracyThresholdM < 100f) {
                onAction(TreeWorkspaceAction.SetAccuracyThreshold((state.accuracyThresholdM + 1f).coerceAtMost(100f)))
            }
        }
    }
    Divider()
    FilterCounts(state)
    TreeButton(stringResource(R.string.gs_tree_close)) { onAction(TreeWorkspaceAction.Close) }
}

@Composable
private fun Empty(state: TreeWorkspaceUiState, onAction: (TreeWorkspaceAction) -> Unit) {
    PanelTitle(stringResource(R.string.gs_tree_empty_title), TreeHelp.FILTER, onAction, onClose = { onAction(TreeWorkspaceAction.Close) })
    Chip(stringResource(R.string.gs_tree_active_filter, number(state.accuracyThresholdM.toDouble())))
    Divider()
    FilterCounts(state)
    TreeButton(stringResource(R.string.gs_tree_disable_filter)) { onAction(TreeWorkspaceAction.SetAccuracyFilter(false)) }
    TextButton(onClick = { onAction(TreeWorkspaceAction.AdjustAccuracy) }, modifier = Modifier.fillMaxWidth().heightIn(min = TreeUi.touch)) {
        Text(stringResource(R.string.gs_tree_adjust_threshold), color = TreeUi.ink, fontSize = 18.sp)
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = TreeUi.ink)
    }
}

@Composable
private fun FilterCounts(state: TreeWorkspaceUiState) {
    Text(stringResource(R.string.gs_tree_selected_species), color = TreeUi.secondary, fontWeight = FontWeight.Medium)
    Text(stringResource(R.string.gs_tree_shown_count, state.counts.shown, state.counts.selectedLocated), fontSize = 19.sp, fontWeight = FontWeight.Medium)
    // Separate rows remain readable with large font and three-digit counts.
    Chip(stringResource(R.string.gs_tree_above_threshold, state.counts.aboveThreshold))
    Chip(stringResource(R.string.gs_tree_unknown_accuracy, state.counts.unknownAccuracy))
    if (state.counts.withoutCoordinates > 0) Text(stringResource(R.string.gs_tree_no_coordinates, state.counts.withoutCoordinates), color = TreeUi.secondary, fontSize = 14.sp)
    if (state.counts.speciesHidden > 0) Text(stringResource(R.string.gs_tree_hidden_species, state.counts.speciesHidden), color = TreeUi.secondary, fontSize = 14.sp)
}

@Composable
private fun Records(state: TreeWorkspaceUiState, onAction: (TreeWorkspaceAction) -> Unit) {
    PanelTitle(stringResource(R.string.gs_tree_records), TreeHelp.SPECIES, onAction, onClose = { onAction(TreeWorkspaceAction.Close) })
    Text(stringResource(R.string.gs_tree_located_count, state.counts.shown, state.counts.located), color = TreeUi.secondary)
    if (state.records.isEmpty()) Text(stringResource(R.string.gs_tree_no_records))
    state.records.forEach { record ->
        Surface(onClick = { onAction(TreeWorkspaceAction.SelectRecord(record.treeId, record.parcelId)) },
            color = TreeUi.paper, contentColor = TreeUi.ink, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.heightIn(min = TreeUi.touch).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(treeName(record), fontWeight = FontWeight.Bold)
                    Text(record.speciesName, color = TreeUi.secondary)
                }
                Text(accuracy(record.recordedAccuracyM), color = TreeUi.secondary)
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
            }
        }
        Divider()
    }
    if (state.counts.withoutCoordinates > 0) Text(stringResource(R.string.gs_tree_no_coordinates, state.counts.withoutCoordinates), color = TreeUi.secondary)
    TreeButton(stringResource(R.string.gs_tree_close)) { onAction(TreeWorkspaceAction.Close) }
}

@Composable
private fun Help(topic: TreeHelp, onAction: (TreeWorkspaceAction) -> Unit) {
    val (title, body) = when (topic) {
        TreeHelp.RECORD -> R.string.gs_tree_title to R.string.gs_tree_help_record
        TreeHelp.DIAMETER -> R.string.gs_tree_diameter to R.string.gs_tree_help_diameter
        TreeHelp.HEIGHT -> R.string.gs_tree_height to R.string.gs_tree_help_height
        TreeHelp.RECORDED_ACCURACY -> R.string.gs_tree_recorded_accuracy to R.string.gs_tree_help_recorded_accuracy
        TreeHelp.GUIDANCE -> R.string.gs_tree_start to R.string.gs_tree_help_guidance
        TreeHelp.PHONE_ACCURACY -> R.string.gs_tree_phone to R.string.gs_tree_help_phone
        TreeHelp.COMPASS -> R.string.gs_tree_compass_perturbed to R.string.gs_tree_help_compass
        TreeHelp.GPS -> R.string.gs_tree_gps_unavailable to R.string.gs_tree_help_gps
        TreeHelp.SPECIES -> R.string.gs_tree_species to R.string.gs_tree_help_species
        TreeHelp.FILTER -> R.string.gs_tree_filter_title to R.string.gs_tree_help_filter
    }
    PanelTitle(stringResource(title), onClose = { onAction(TreeWorkspaceAction.Close) })
    Text(stringResource(body), fontSize = 17.sp)
    TreeButton(stringResource(R.string.gs_tree_close)) { onAction(TreeWorkspaceAction.Close) }
}

@Composable
private fun DirectionCompass(bearing: Float, modifier: Modifier) {
    val description = stringResource(R.string.gs_tree_indicative_direction)
    Canvas(modifier.semantics { contentDescription = description }) {
        val radius = size.minDimension / 2f - 3.dp.toPx()
        drawCircle(TreeUi.ink, radius, style = Stroke(1.4.dp.toPx()))
        repeat(8) { index ->
            val angle = Math.toRadians(index * 45.0)
            val outer = Offset(center.x + radius * sin(angle).toFloat(), center.y - radius * cos(angle).toFloat())
            val inner = Offset(center.x + radius * 0.86f * sin(angle).toFloat(), center.y - radius * 0.86f * cos(angle).toFloat())
            drawLine(TreeUi.ink, outer, inner, 1.dp.toPx())
        }
        rotate(bearing) {
            val path = Path().apply {
                moveTo(center.x, center.y - radius * 0.65f)
                lineTo(center.x + radius * 0.23f, center.y + radius * 0.4f)
                lineTo(center.x, center.y + radius * 0.15f)
                lineTo(center.x - radius * 0.23f, center.y + radius * 0.4f)
                close()
            }
            drawPath(path, TreeUi.green)
        }
    }
}
@Composable
private fun LabelWithInfo(label: String, topic: TreeHelp, onAction: (TreeWorkspaceAction) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f, fill = false), color = TreeUi.secondary, fontSize = 15.sp)
        Info(topic, onAction)
    }
}
@Composable
private fun AccuracyValue(label: String, value: Double?, topic: TreeHelp, onAction: (TreeWorkspaceAction) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        LabelWithInfo(label, topic, onAction)
        Text(accuracy(value), fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

// Reconstructed from the current UI state and existing resources; no missing sensor data is inferred.
@Composable
private fun StatusCard(title: String, subtitle: String, topic: TreeHelp, onAction: (TreeWorkspaceAction) -> Unit) {
    Surface(onClick = { onAction(TreeWorkspaceAction.Help(topic)) }, color = TreeUi.lavender,
        shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TreeUi.secondary)
            }
            Icon(Icons.Outlined.Info, null, tint = TreeUi.ink, modifier = Modifier.size(20.dp))
        }
    }
}
@Composable
private fun Chip(label: String) {
    Surface(color = TreeUi.lavender, shape = CircleShape) {
        Text(label, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = TreeUi.ink, fontSize = 16.sp)
    }
}
@Composable
private fun compassLabel(status: TreeHeadingStatus): String = stringResource(when (status) {
    TreeHeadingStatus.WAITING -> R.string.gs_tree_compass_waiting
    TreeHeadingStatus.READY -> R.string.gs_tree_indicative_direction
    TreeHeadingStatus.UNKNOWN -> R.string.gs_tree_compass_unknown
    TreeHeadingStatus.UNRELIABLE -> R.string.gs_tree_compass_perturbed
    TreeHeadingStatus.STALE -> R.string.gs_tree_compass_stale
    TreeHeadingStatus.UNAVAILABLE -> R.string.gs_tree_compass_unavailable
})
@Composable
private fun Info(topic: TreeHelp, onAction: (TreeWorkspaceAction) -> Unit) {
    IconButton(onClick = { onAction(TreeWorkspaceAction.Help(topic)) }, modifier = Modifier.size(TreeUi.touch)) {
        Icon(Icons.Outlined.Info, stringResource(R.string.gs_tree_title), Modifier.size(18.dp), tint = TreeUi.ink)
    }
}
@Composable
private fun Close(onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(TreeUi.touch)) { Icon(Icons.Outlined.Close, stringResource(R.string.gs_tree_close), tint = TreeUi.ink) }
}
@Composable
private fun ThresholdButton(increase: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(16.dp), color = TreeUi.paleGreen, modifier = Modifier.size(TreeUi.touch)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(if (increase) Icons.Outlined.Add else Icons.Outlined.Remove,
                stringResource(if (increase) R.string.gs_tree_increase else R.string.gs_tree_decrease), tint = TreeUi.ink)
        }
    }
}
@Composable
private fun TreeButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, stop: Boolean = false,
    icon: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = TreeUi.touch), enabled = enabled,
        shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = if (stop) TreeUi.stop else TreeUi.green, contentColor = Color.White)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            icon?.invoke()
            Text(label, fontSize = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
@Composable private fun Divider() = HorizontalDivider(color = TreeUi.border)
@Composable private fun treeName(record: TreeRecordUi): String = record.number?.let { stringResource(R.string.gs_tree_number, it) } ?: stringResource(R.string.gs_tree_unnumbered)
@Composable private fun metres(value: Double): String = stringResource(R.string.gs_tree_m, number(value))
@Composable private fun distance(value: Double?): String = if (value == null) stringResource(R.string.gs_tree_distance_unavailable)
    else if (value >= 1000.0) stringResource(R.string.gs_tree_km, number(value / 1000.0)) else metres(value)
@Composable private fun accuracy(value: Double?): String = value?.takeIf { it.isFinite() && it > 0 }?.let { metres(it) } ?: stringResource(R.string.gs_tree_unknown)
private fun number(value: Double): String = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(value)