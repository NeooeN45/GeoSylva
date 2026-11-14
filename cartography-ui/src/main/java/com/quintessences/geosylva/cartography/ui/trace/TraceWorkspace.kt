package com.quintessences.geosylva.cartography.ui.trace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quintessences.geosylva.cartography.ui.R
import java.text.NumberFormat
import java.util.Locale

/**
 * Shared chrome for approved tracing states92..100. The canvas, capture lifecycle,
 * help content, permissions and durable state belong to the host, never this view.
 * [canvas] receives the actual panel inset so native attributions remain visible.
 */
@Composable
fun TraceWorkspace(
    state: TraceWorkspaceState,
    actions: TraceWorkspaceActions,
    modifier: Modifier = Modifier,
    canvas: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    var panelHeightPx by remember { mutableIntStateOf(0) }
    val backAction = if (state.screen == TraceScreen.SAVE) TraceAction.CANCEL_SAVE else TraceAction.BACK
    BackHandler(enabled = state.saving || backAction in state.allowedActions) {
        if (!state.saving) actions.onAction(backAction)
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        canvas(PaddingValues(start = 12.dp, end = 12.dp, top = 100.dp,
            bottom = with(density) { panelHeightPx.toDp() } + 8.dp))
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp)) {
            WorkspaceHeader(state, actions)
            Column(Modifier.align(Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingCommand(Icons.Outlined.Layers, stringResource(R.string.gs_map_layers),
                    state.enabled(TraceAction.LAYERS)) { actions.onAction(TraceAction.LAYERS) }
                FloatingCommand(Icons.Outlined.CenterFocusStrong, stringResource(R.string.gs_trace_frame),
                    state.enabled(TraceAction.FRAME)) { actions.onAction(TraceAction.FRAME) }
            }
        }
        val maxPanelHeight = maxHeight * when {
            density.fontScale > 1.15f -> 0.78f
            state.screen == TraceScreen.DETAILS -> 0.54f
            else -> 0.49f
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .onSizeChanged { panelHeightPx = it.height }
            .imePadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 18.dp)) {
            TracePanel(state, actions, Modifier.fillMaxWidth().heightIn(max = maxPanelHeight))
        }
    }
}

/** Also usable by a host which already owns its map toolbar and layout. */
@Composable
fun TracePanel(state: TraceWorkspaceState, actions: TraceWorkspaceActions, modifier: Modifier = Modifier) {
    Surface(modifier, shape = TraceTokens.panelShape, color = TraceTokens.paper, shadowElevation = 4.dp) {
        val pinnedFooter = state.screen == TraceScreen.SAVE || state.screen == TraceScreen.DETAILS
        val scroll = key(state.screen) { rememberScrollState() }
        Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val bodyModifier = if (pinnedFooter) Modifier.weight(1f, fill = false) else Modifier
            Column(bodyModifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 7.dp, bottom = 4.dp)
                    .size(30.dp, 4.dp).clip(CircleShape).background(TraceTokens.handle))
                PanelHeading(state, actions)
                state.errorMessage?.takeIf(String::isNotBlank)?.let { message ->
                    Text(message, color = TraceTokens.error, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
                }
                when (state.screen) {
                    TraceScreen.PREPARATION -> PreparationBody(state, actions)
                    TraceScreen.CAPTURE -> CaptureBody(state, actions)
                    TraceScreen.REVIEW -> ReviewBody(state, actions)
                    TraceScreen.SAVE -> SaveBody(state, actions)
                    TraceScreen.SAVED -> SavedBody(state, actions)
                    TraceScreen.DETAILS -> DetailsBody(state, actions)
                }
            }
            when (state.screen) {
                TraceScreen.SAVE -> SaveCommands(state, actions)
                TraceScreen.DETAILS -> DetailsFooter(state, actions)
                else -> Unit
            }
        }
    }
}

@Composable
private fun WorkspaceHeader(state: TraceWorkspaceState, actions: TraceWorkspaceActions) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        val backAction = if (state.screen == TraceScreen.SAVE) TraceAction.CANCEL_SAVE else TraceAction.BACK
        FloatingCommand(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.gs_map_back), state.enabled(backAction)) {
            actions.onAction(backAction)
        }
        Surface(Modifier.weight(1f), shape = RoundedCornerShape(32.dp), color = TraceTokens.paper, shadowElevation = 2.dp) {
            Row(Modifier.padding(start = 15.dp, end = 2.dp).heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(workspaceTitle(state.screen), color = TraceTokens.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(if (state.screen == TraceScreen.DETAILS) state.displayName() else state.contextTitle,
                        color = TraceTokens.subtitle, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                HelpButton(TraceHelp.CONTEXT, stringResource(R.string.gs_trace_context_help), actions)
            }
        }
    }
}

@Composable
private fun PanelHeading(state: TraceWorkspaceState, actions: TraceWorkspaceActions) {
    val details = state.screen == TraceScreen.DETAILS
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (details) {
            IconButton(onClick = { actions.onAction(TraceAction.BACK) }, enabled = state.enabled(TraceAction.BACK),
                modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.gs_map_back), tint = TraceTokens.ink)
            }
        } else Surface(shape = RoundedCornerShape(12.dp), color = TraceTokens.lavender) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Icon(if (state.screen == TraceScreen.CAPTURE && state.captureStatus == TraceCaptureStatus.MANUALLY_PAUSED)
                    Icons.Outlined.Pause else if (state.screen == TraceScreen.SAVE) Icons.Outlined.Description else Icons.Outlined.Timeline,
                    null, tint = TraceTokens.ink, modifier = Modifier.size(27.dp))
            }
        }
        Text(panelTitle(state), Modifier.weight(1f).padding(start = 8.dp), color = TraceTokens.ink,
            fontSize = 20.sp, fontWeight = FontWeight.Bold)
        HelpButton(TraceHelp.CAPTURE, panelTitle(state), actions)
        if (!details) {
            val cancel = state.screen == TraceScreen.SAVE
            val action = if (cancel) TraceAction.CANCEL_SAVE else if (state.screen == TraceScreen.PREPARATION) TraceAction.BACK else TraceAction.OVERFLOW
            IconButton(onClick = { actions.onAction(action) }, enabled = state.enabled(action), modifier = Modifier.size(48.dp)) {
                Icon(if (cancel || state.screen == TraceScreen.PREPARATION) Icons.Outlined.Close else Icons.Outlined.MoreVert,
                    stringResource(if (cancel) R.string.gs_trace_close_save else if (state.screen == TraceScreen.PREPARATION) R.string.gs_map_close else R.string.gs_trace_overflow),
                    tint = TraceTokens.ink)
            }
        }
    }
}

@Composable
private fun PreparationBody(state: TraceWorkspaceState, actions: TraceWorkspaceActions) {
    StatusChip(state.location.unavailableLabel ?: stringResource(if (state.location.available)
        R.string.gs_trace_position_available else R.string.gs_trace_position_unavailable),
        positive = state.location.available, help = TraceHelp.POSITION, actions = actions, expanded = true)
    TwoMetrics(
        stringResource(R.string.gs_trace_accuracy), distance(state.location.reportedAccuracyM), TraceHelp.REPORTED_ACCURACY,
        stringResource(R.string.gs_trace_last_position), state.location.fixAgeSeconds?.takeIf { it >= 0 }?.let {
            stringResource(R.string.gs_trace_age, integer(it)) } ?: unknown(), TraceHelp.FIX_AGE, actions)
    Divider()
    ActionRow(stringResource(R.string.gs_trace_settings), Icons.Outlined.Settings, state.enabled(TraceAction.SETTINGS)) {
        actions.onAction(TraceAction.SETTINGS)
    }
    if (TraceAction.LIBRARY in state.allowedActions) ActionRow(stringResource(R.string.gs_trace_library),
        Icons.Outlined.FolderOpen, state.enabled(TraceAction.LIBRARY)) { actions.onAction(TraceAction.LIBRARY) }
    if (TraceAction.REQUEST_PERMISSION in state.allowedActions) Command(
        stringResource(R.string.gs_trace_request_permission), Icons.Outlined.MyLocation, primary = true,
        enabled = state.enabled(TraceAction.REQUEST_PERMISSION), modifier = Modifier.fillMaxWidth()) {
        actions.onAction(TraceAction.REQUEST_PERMISSION)
    }
    Command(stringResource(R.string.gs_trace_start), Icons.Outlined.RadioButtonChecked, primary = true,
        enabled = state.enabled(TraceAction.START), modifier = Modifier.fillMaxWidth()) { actions.onAction(TraceAction.START) }
}

@Composable
private fun CaptureBody(state: TraceWorkspaceState, actions: TraceWorkspaceActions) {
    val paused = state.captureStatus == TraceCaptureStatus.MANUALLY_PAUSED
    val waiting = state.captureStatus == TraceCaptureStatus.WAITING_FOR_QUALITY
    StatusChip(stringResource(when (state.captureStatus) {
        TraceCaptureStatus.RECORDING -> R.string.gs_trace_recording_status
        TraceCaptureStatus.WAITING_FOR_QUALITY -> R.string.gs_trace_waiting_status
        TraceCaptureStatus.MANUALLY_PAUSED -> R.string.gs_trace_manual_pause
    }), positive = !paused && !waiting, help = TraceHelp.CAPTURE, actions = actions)
    MainMetrics(state, actions, active = !paused && !waiting && !state.resumed)
    Divider()
    if (paused) TwoMetrics(stringResource(R.string.gs_trace_elapsed), duration(state.statistics.elapsedSeconds), TraceHelp.SESSION_DURATION,
        stringResource(R.string.gs_trace_pause_duration), duration(state.statistics.manualPauseSeconds), TraceHelp.PAUSE_DURATION, actions)
    else TwoMetrics(stringResource(R.string.gs_trace_elapsed), duration(state.statistics.elapsedSeconds), TraceHelp.SESSION_DURATION,
        stringResource(if (waiting) R.string.gs_trace_received_accuracy else R.string.gs_trace_accuracy),
        distance(state.location.reportedAccuracyM), TraceHelp.REPORTED_ACCURACY, actions)
    when {
        paused -> Notice(stringResource(R.string.gs_trace_no_points_added), TraceHelp.PAUSE_DURATION, actions)
        waiting -> Notice(stringResource(R.string.gs_trace_new_portion), TraceHelp.NEW_PORTION, actions)
        state.resumed -> Notice(portionsAndGaps(state.statistics, interruption = true), TraceHelp.UNRECORDED_PASSAGES, actions)
    }
    Commands(state, actions, if (paused) TraceAction.RESUME else TraceAction.PAUSE, TraceAction.FINISH, firstPrimary = paused)
}

@Composable
private fun ReviewBody(state: TraceWorkspaceState, actions: TraceWorkspaceActions) {
    StatusChip(stringResource(R.string.gs_trace_draft), false, TraceHelp.CAPTURE, actions)
    MainMetrics(state, actions)
    Divider()
    TwoMetrics(stringResource(R.string.gs_trace_portions), count(state.statistics.portionCount), TraceHelp.PORTIONS,
        stringResource(R.string.gs_trace_session_duration), duration(state.statistics.elapsedSeconds), TraceHelp.SESSION_DURATION, actions)
    Notice(gaps(state.statistics.unrecordedPassageCount), TraceHelp.UNRECORDED_PASSAGES, actions)
    Commands(state, actions, TraceAction.RESUME, TraceAction.CONTINUE)
}

@Composable
private fun SaveBody(state: TraceWorkspaceState, actions: TraceWorkspaceActions) {
    MainMetrics(state, actions, includePortions = true)
    Notice(gaps(state.statistics.unrecordedPassageCount), TraceHelp.UNRECORDED_PASSAGES, actions)
    Divider()
    OutlinedTextField(value = state.name, onValueChange = actions::onNameChanged, enabled = !state.saving && state.nameEditable,
        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TraceTokens.ink),
        label = { Text(stringResource(R.string.gs_trace_name)) },
        trailingIcon = { HelpButton(TraceHelp.NAME, stringResource(R.string.gs_trace_name), actions) },
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TraceTokens.ink, unfocusedBorderColor = TraceTokens.outline))
    Storage(state, actions, destination = true)
    if (state.saving) Row(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp), color = TraceTokens.green, strokeWidth = 2.dp)
        Text(stringResource(R.string.gs_trace_saving), color = TraceTokens.ink)
    }
}

@Composable
private fun SaveCommands(state: TraceWorkspaceState, actions: TraceWorkspaceActions) {
    if (LocalDensity.current.fontScale <= 1.15f) Commands(state, actions, TraceAction.CANCEL_SAVE, TraceAction.SAVE)
    else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Command(actionLabel(TraceAction.CANCEL_SAVE), actionIcon(TraceAction.CANCEL_SAVE), primary = false,
            enabled = state.enabled(TraceAction.CANCEL_SAVE), modifier = Modifier.fillMaxWidth()) { actions.onAction(TraceAction.CANCEL_SAVE) }
        Command(actionLabel(TraceAction.SAVE), actionIcon(TraceAction.SAVE), primary = true,
            enabled = state.enabled(TraceAction.SAVE), modifier = Modifier.fillMaxWidth()) { actions.onAction(TraceAction.SAVE) }
    }
}

@Composable
private fun SavedBody(state: TraceWorkspaceState, actions: TraceWorkspaceActions) {
    Storage(state, actions)
    Divider()
    MainMetrics(state, actions)
    Notice(portionsAndGaps(state.statistics), TraceHelp.UNRECORDED_PASSAGES, actions)
    Divider()
    ActionRow(stringResource(R.string.gs_trace_details), Icons.Outlined.Description, state.enabled(TraceAction.DETAILS)) {
        actions.onAction(TraceAction.DETAILS)
    }
    Divider()
    Commands(state, actions, TraceAction.SHARE, TraceAction.DUPLICATE)
}

@Composable
private fun DetailsBody(state: TraceWorkspaceState, actions: TraceWorkspaceActions) {
    MainMetrics(state, actions)
    if (state.statistics.portions.isEmpty()) Notice(unknown(), TraceHelp.PORTIONS, actions)
    state.statistics.portions.chunked(2).forEach { portions ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            portions.forEach { portion -> PortionTile(portion, portion.id == state.selectedPortionId, actions, Modifier.weight(1f)) }
            if (portions.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    val gapCount = state.statistics.unrecordedPassageCount
    if (gapCount == null || gapCount < 0) Notice(stringResource(R.string.gs_trace_gaps_unknown), TraceHelp.UNRECORDED_PASSAGES, actions)
    else if (gapCount > 0) Notice(stringResource(R.string.gs_trace_gap_excluded), TraceHelp.UNRECORDED_PASSAGES, actions)
    DataRow(stringResource(R.string.gs_trace_session_duration), duration(state.statistics.elapsedSeconds), TraceHelp.SESSION_DURATION, actions)
}

/** Unknown overall quality stays visible together with its method action, outside the scroll body. */
@Composable
private fun DetailsFooter(state: TraceWorkspaceState, actions: TraceWorkspaceActions) {
    Column(Modifier.fillMaxWidth()) {
        Divider()
        DataRow(stringResource(R.string.gs_trace_uncertainty), state.statistics.overallUncertaintyM?.let { distance(it) }
            ?: stringResource(R.string.gs_trace_not_determined), TraceHelp.OVERALL_UNCERTAINTY, actions)
        Divider()
        ActionRow(stringResource(R.string.gs_trace_method_source), Icons.Outlined.Description, state.enabled(TraceAction.METHOD_SOURCE)) {
            actions.onAction(TraceAction.METHOD_SOURCE)
        }
    }
}

@Composable
private fun PortionTile(portion: TracePortionSummary, selected: Boolean, actions: TraceWorkspaceActions, modifier: Modifier) {
    val label = stringResource(R.string.gs_trace_portion, portion.ordinal)
    Surface(onClick = { actions.onSelectPortion(portion.id) }, modifier = modifier,
        shape = RoundedCornerShape(12.dp), color = TraceTokens.sage,
        border = if (selected) BorderStroke(1.dp, TraceTokens.green) else null) {
        Box(Modifier.padding(8.dp)) {
            Column(Modifier.padding(end = 44.dp)) {
                Text(label, color = TraceTokens.ink, fontSize = 14.sp)
                Text(distance(portion.lengthM), color = TraceTokens.green, fontSize = 23.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            }
            Box(Modifier.align(Alignment.TopEnd)) { HelpButton(TraceHelp.PORTIONS, label, actions) }
        }
    }
}

@Composable
private fun MainMetrics(state: TraceWorkspaceState, actions: TraceWorkspaceActions, active: Boolean = false, includePortions: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(distance(state.statistics.acceptedLengthM), color = TraceTokens.ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                HelpButton(TraceHelp.LENGTH, stringResource(R.string.gs_trace_length), actions)
            }
            Text(stringResource(if (active) R.string.gs_trace_active_length else R.string.gs_trace_length),
                color = TraceTokens.subtitle, fontSize = 14.sp)
        }
        Surface(shape = CircleShape, color = TraceTokens.lavender) {
            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val points = points(state.statistics.acceptedPointCount)
                Text(if (includePortions) "$points · ${portions(state.statistics.portionCount)}" else points,
                    color = TraceTokens.ink, fontSize = 14.sp)
                HelpButton(if (includePortions) TraceHelp.PORTIONS else TraceHelp.POINT_COUNT, points, actions)
            }
        }
    }
}

@Composable
private fun Storage(state: TraceWorkspaceState, actions: TraceWorkspaceActions, destination: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Smartphone, null, tint = TraceTokens.ink, modifier = Modifier.size(23.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(state.destinationLabel ?: stringResource(R.string.gs_trace_destination_unknown), color = TraceTokens.ink, fontSize = 15.sp)
            if (destination) Text(stringResource(R.string.gs_trace_destination), color = TraceTokens.muted, fontSize = 12.sp)
        }
        HelpButton(TraceHelp.LOCAL_STORAGE, stringResource(R.string.gs_trace_local), actions)
    }
}

@Composable
private fun TwoMetrics(label1: String, value1: String, help1: TraceHelp, label2: String, value2: String, help2: TraceHelp, actions: TraceWorkspaceActions) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Metric(label1, value1, help1, actions, Modifier.weight(1f))
        Metric(label2, value2, help2, actions, Modifier.weight(1f))
    }
}

@Composable
private fun Metric(label: String, value: String, help: TraceHelp, actions: TraceWorkspaceActions, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f, fill = false), color = TraceTokens.ink, fontSize = 13.sp)
            HelpButton(help, label, actions)
        }
        Text(value, color = TraceTokens.ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DataRow(label: String, value: String, help: TraceHelp, actions: TraceWorkspaceActions) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = TraceTokens.ink, fontSize = 15.sp)
        Text(value, color = TraceTokens.ink, fontSize = 15.sp)
        HelpButton(help, label, actions)
    }
}

@Composable
private fun StatusChip(label: String, positive: Boolean, help: TraceHelp, actions: TraceWorkspaceActions, expanded: Boolean = false) {
    Surface(shape = RoundedCornerShape(18.dp), color = if (positive) TraceTokens.sage else TraceTokens.neutral,
        modifier = if (expanded) Modifier.fillMaxWidth() else Modifier) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (positive) Box(Modifier.size(10.dp).clip(CircleShape).background(TraceTokens.green))
            Text(label, color = TraceTokens.ink, fontSize = 14.sp,
                modifier = Modifier.padding(start = if (positive) 8.dp else 0.dp).semantics { liveRegion = LiveRegionMode.Polite })
            HelpButton(help, label, actions)
        }
    }
}

@Composable
private fun Notice(label: String, help: TraceHelp, actions: TraceWorkspaceActions) {
    Surface(shape = RoundedCornerShape(14.dp), color = TraceTokens.neutral, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HelpButton(help, label, actions)
            Text(label, color = TraceTokens.ink, fontSize = 14.sp, modifier = Modifier.padding(end = 10.dp))
        }
    }
}

@Composable
private fun ActionRow(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = TraceTokens.ink, modifier = Modifier.size(23.dp))
            Text(label, Modifier.weight(1f).padding(start = 10.dp), color = TraceTokens.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TraceTokens.ink)
        }
    }
}

@Composable
private fun Commands(state: TraceWorkspaceState, actions: TraceWorkspaceActions, first: TraceAction, second: TraceAction, firstPrimary: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Command(actionLabel(first), actionIcon(first), primary = firstPrimary, enabled = state.enabled(first), modifier = Modifier.weight(1f)) { actions.onAction(first) }
        Command(actionLabel(second), actionIcon(second), primary = !firstPrimary, enabled = state.enabled(second), modifier = Modifier.weight(1f)) { actions.onAction(second) }
    }
}

@Composable
private fun Command(label: String, icon: ImageVector?, primary: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp).alpha(if (enabled) 1f else 0.45f), shape = RoundedCornerShape(16.dp),
        color = if (primary) TraceTokens.green else TraceTokens.sage, contentColor = if (primary) Color.White else TraceTokens.green) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)) {
            icon?.let { Icon(it, null, modifier = Modifier.size(23.dp)) }
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun HelpButton(help: TraceHelp, label: String, actions: TraceWorkspaceActions) {
    IconButton(onClick = { actions.onExplain(help) }, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Outlined.Info, stringResource(R.string.gs_trace_explain, label), tint = TraceTokens.ink, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun FloatingCommand(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = TraceTokens.paper, shadowElevation = 2.dp,
        modifier = Modifier.size(48.dp).alpha(if (enabled) 1f else 0.45f)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, tint = TraceTokens.ink, modifier = Modifier.size(25.dp)) }
    }
}

@Composable
private fun Divider() { HorizontalDivider(color = TraceTokens.outline) }

private fun TraceWorkspaceState.enabled(action: TraceAction) = !saving && action in allowedActions

@Composable
private fun workspaceTitle(screen: TraceScreen) = stringResource(when (screen) {
    TraceScreen.PREPARATION, TraceScreen.CAPTURE -> R.string.gs_trace_title
    TraceScreen.REVIEW -> R.string.gs_trace_review_title
    TraceScreen.SAVE -> R.string.gs_trace_save
    TraceScreen.SAVED -> R.string.gs_trace_saved
    TraceScreen.DETAILS -> R.string.gs_trace_details
})

@Composable
private fun panelTitle(state: TraceWorkspaceState): String = when (state.screen) {
    TraceScreen.PREPARATION -> stringResource(R.string.gs_trace_prepare)
    TraceScreen.CAPTURE -> stringResource(when (state.captureStatus) {
        TraceCaptureStatus.RECORDING -> if (state.resumed) R.string.gs_trace_resumed else R.string.gs_trace_recording
        TraceCaptureStatus.WAITING_FOR_QUALITY -> R.string.gs_trace_degraded
        TraceCaptureStatus.MANUALLY_PAUSED -> R.string.gs_trace_paused
    })
    // Code nouveau de reconstruction : les 43 lignes historiques manquantes ne sont pas récupérées.
    TraceScreen.REVIEW -> stringResource(R.string.gs_trace_review)
    TraceScreen.SAVE -> stringResource(R.string.gs_trace_save)
    TraceScreen.SAVED -> state.displayName()
    TraceScreen.DETAILS -> stringResource(R.string.gs_trace_details)
}

@Composable
private fun TraceWorkspaceState.displayName(): String =
    name.takeIf(String::isNotBlank) ?: stringResource(R.string.gs_trace_unnamed)

@Composable
private fun actionLabel(action: TraceAction): String = stringResource(when (action) {
    TraceAction.BACK -> R.string.gs_map_back
    TraceAction.LAYERS -> R.string.gs_map_layers
    TraceAction.FRAME -> R.string.gs_trace_frame
    TraceAction.SETTINGS -> R.string.gs_trace_settings
    TraceAction.START -> R.string.gs_trace_start
    TraceAction.PAUSE -> R.string.gs_trace_pause
    TraceAction.RESUME -> R.string.gs_trace_resume
    TraceAction.FINISH -> R.string.gs_trace_finish
    TraceAction.CONTINUE -> R.string.gs_trace_continue
    TraceAction.SAVE -> R.string.gs_trace_save_button
    TraceAction.CANCEL_SAVE -> R.string.gs_trace_cancel
    TraceAction.DETAILS -> R.string.gs_trace_details
    TraceAction.METHOD_SOURCE -> R.string.gs_trace_method_source
    TraceAction.SHARE -> R.string.gs_trace_share
    TraceAction.DUPLICATE -> R.string.gs_trace_duplicate
    TraceAction.OVERFLOW -> R.string.gs_trace_overflow
    TraceAction.REQUEST_PERMISSION -> R.string.gs_trace_request_permission
    TraceAction.LIBRARY -> R.string.gs_trace_library
})

private fun actionIcon(action: TraceAction): ImageVector = when (action) {
    TraceAction.BACK -> Icons.AutoMirrored.Filled.ArrowBack
    TraceAction.LAYERS -> Icons.Outlined.Layers
    TraceAction.FRAME -> Icons.Outlined.CenterFocusStrong
    TraceAction.SETTINGS -> Icons.Outlined.Settings
    TraceAction.START -> Icons.Outlined.RadioButtonChecked
    TraceAction.PAUSE -> Icons.Outlined.Pause
    TraceAction.RESUME -> Icons.Outlined.PlayArrow
    TraceAction.FINISH -> Icons.Outlined.Stop
    TraceAction.CONTINUE -> Icons.AutoMirrored.Filled.KeyboardArrowRight
    TraceAction.SAVE -> Icons.Outlined.Save
    TraceAction.CANCEL_SAVE -> Icons.Outlined.Close
    TraceAction.DETAILS -> Icons.Outlined.Description
    TraceAction.METHOD_SOURCE -> Icons.Outlined.Info
    TraceAction.SHARE -> Icons.Outlined.Share
    TraceAction.DUPLICATE -> Icons.Outlined.ContentCopy
    TraceAction.OVERFLOW -> Icons.Outlined.MoreVert
    TraceAction.REQUEST_PERMISSION -> Icons.Outlined.MyLocation
    TraceAction.LIBRARY -> Icons.Outlined.FolderOpen
}

@Composable
private fun points(value: Int?): String = value?.takeIf { it >= 0 }?.let {
    pluralStringResource(R.plurals.gs_trace_points, it, it)
} ?: unknown()

@Composable
private fun portions(value: Int?): String = value?.takeIf { it >= 0 }?.let {
    pluralStringResource(R.plurals.gs_trace_portion_count, it, it)
} ?: unknown()

@Composable
private fun gaps(value: Int?): String = value?.takeIf { it >= 0 }?.let {
    pluralStringResource(R.plurals.gs_trace_gaps, it, it)
} ?: stringResource(R.string.gs_trace_gaps_unknown)

@Composable
private fun portionsAndGaps(stats: TraceStatistics, interruption: Boolean = false): String {
    val gapCount = stats.unrecordedPassageCount?.takeIf { it >= 0 }
    val gap = if (interruption && gapCount != null) {
        pluralStringResource(R.plurals.gs_trace_interruptions, gapCount, gapCount)
    } else gaps(gapCount)
    // Fin du code nouveau ; reprise de la plage historique suivante.
    return "${portions(stats.portionCount)} · $gap"
}
@Composable
private fun distance(value: Double?): String = value?.takeIf { it.isFinite() && it >= 0 }?.let {
    NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0]).apply { maximumFractionDigits = 1 }.format(it) + " m"
} ?: unknown()
@Composable
private fun integer(value: Long) = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(value)
@Composable
private fun count(value: Int?) = value?.takeIf { it >= 0 }?.let { integer(it.toLong()) } ?: unknown()
@Composable
private fun unknown() = stringResource(R.string.gs_trace_unknown)
@Composable
private fun duration(seconds: Long?): String {
    if (seconds == null || seconds < 0) return unknown()
    val hours = seconds / 3600
    return if (hours > 0) String.format(Locale.ROOT, "%02d:%02d:%02d", hours, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}

/** Shared across all tracing phases; the original map workspace tokens predate this approved palette. */
private object TraceTokens {
    val paper = Color(0xFFFFFFFF)
    val ink = Color(0xFF102B66)
    val green = Color(0xFF006439)
    val sage = Color(0xFFDFEFE1)
    val lavender = Color(0xFFE7E6F7)
    val neutral = Color(0xFFE9ECF3)
    val subtitle = Color(0xFF8D91D1)
    val muted = Color(0xFF737B91)
    val outline = Color(0xFFE1E3EB)
    val handle = Color(0xFFC6C6C6)
    val error = Color(0xFF8A3040)
    val panelShape = RoundedCornerShape(28.dp)
}
