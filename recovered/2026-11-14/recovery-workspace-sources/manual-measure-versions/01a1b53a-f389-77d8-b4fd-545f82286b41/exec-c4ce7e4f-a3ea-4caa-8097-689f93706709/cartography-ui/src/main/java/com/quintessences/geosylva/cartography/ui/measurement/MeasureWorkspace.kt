package com.quintessences.geosylva.cartography.ui.measurement

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quintessences.geosylva.cartography.ui.R

/**
 * Non-modal editor shared by all map hosts. The host alone interprets map gestures,
 * validates geometry and saves it. Only EDITOR allows new points; folding preserves
 * its draft and leaves the exposed map available for browsing.
 */
@Composable
fun MeasureWorkspace(
    state: MeasureWorkspaceState,
    actions: MeasureWorkspaceActions,
    modifier: Modifier = Modifier,
    /** Total bottom occlusion, including the current navigation/keyboard inset and margin. */
    onPanelHeightChanged: (Dp) -> Unit = {},
    /** Top of the reserved native compass slot, relative to the workspace. */
    onCompassTopChanged: (Dp) -> Unit = {},
    canvas: @Composable BoxScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    var workspaceTop by remember { mutableFloatStateOf(Float.NaN) }
    var workspaceHeight by remember { mutableFloatStateOf(0f) }
    var panelTop by remember { mutableFloatStateOf(Float.NaN) }
    var compassTop by remember { mutableFloatStateOf(Float.NaN) }
    val notifyPanel by rememberUpdatedState(onPanelHeightChanged)
    val notifyCompass by rememberUpdatedState(onCompassTopChanged)
    LaunchedEffect(workspaceTop, workspaceHeight, panelTop, density) {
        if (workspaceTop.isFinite() && panelTop.isFinite() && workspaceHeight > 0f) {
            notifyPanel(with(density) { (workspaceTop + workspaceHeight - panelTop).coerceAtLeast(0f).toDp() })
        }
    }
    LaunchedEffect(workspaceTop, compassTop, density) {
        if (workspaceTop.isFinite() && compassTop.isFinite()) {
            notifyCompass(with(density) { (compassTop - workspaceTop).coerceAtLeast(0f).toDp() })
        }
    }
    BackHandler(enabled = state.panelVisible) {
        when (state.panel) {
            MeasurePanel.SAVE -> if (!state.saving) actions.onCancelSave()
            MeasurePanel.UNITS -> actions.onPanelChange(MeasurePanel.EDITOR)
            MeasurePanel.EDITOR, MeasurePanel.COLLAPSED -> actions.onBack()
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned {
        workspaceTop = it.positionInRoot().y
        workspaceHeight = it.size.height.toFloat()
    }) {
        canvas()
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding()
            .padding(horizontal = MeasureUi.edge, vertical = MeasureUi.edge),
            verticalArrangement = Arrangement.spacedBy(MeasureUi.gap)) {
            MeasureContext(state, actions)
            Spacer(Modifier.align(Alignment.End).size(MeasureUi.touch)
                .onGloballyPositioned { compassTop = it.positionInRoot().y })
            MapCommand(Icons.Outlined.Layers, stringResource(R.string.gs_measure_layers),
                enabled = !state.saving, modifier = Modifier.align(Alignment.End), onClick = actions::onLayers)
            MapCommand(Icons.Outlined.MyLocation, stringResource(R.string.gs_measure_location),
                enabled = state.locationEnabled && !state.saving,
                modifier = Modifier.align(Alignment.End), onClick = actions::onLocate)
        }
        // The sheet wraps its content. On a compact window or at large font sizes,
        // the body scrolls while its title and dismiss control remain reachable.
        val maximumHeight = maxHeight * if (density.fontScale > 1.1f || maxHeight < 600.dp) 0.82f else 0.56f
        if (state.panelVisible) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding()
            .padding(horizontal = MeasureUi.edge, vertical = MeasureUi.edge)) {
            val panelModifier = Modifier.fillMaxWidth().heightIn(max = maximumHeight)
                .onGloballyPositioned { panelTop = it.positionInRoot().y }
            if (state.panel == MeasurePanel.COLLAPSED) {
                ResumeMeasurement(state, actions, panelModifier)
            } else {
                MeasureSheet(state, actions, panelModifier)
            }
        }
    }
}

@Composable
private fun MeasureContext(state: MeasureWorkspaceState, actions: MeasureWorkspaceActions) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(onClick = actions::onBack, enabled = !state.saving, shape = CircleShape,
            color = MeasureUi.paper, contentColor = MeasureUi.ink,
            shadowElevation = 2.dp, modifier = Modifier.size(MeasureUi.touch)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.gs_measure_back))
            }
        }
        Surface(shape = MeasureUi.capsule, color = MeasureUi.paper, shadowElevation = 2.dp,
            modifier = Modifier.weight(1f).blockMapTouches()) {
            Row(Modifier.heightIn(min = 56.dp).padding(start = 16.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(stringResource(R.string.gs_measure_on_map), color = MeasureUi.ink,
                        fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    state.contextLabel?.takeIf(String::isNotBlank)?.let {
                        Text(it, color = MeasureUi.secondary, fontSize = 14.sp)
                    }
                }
                MeasureInfo(stringResource(R.string.gs_measure_context_help)) { actions.onExplain(MeasureHelp.CONTEXT) }
            }
        }
    }
}

@Composable
private fun ResumeMeasurement(state: MeasureWorkspaceState, actions: MeasureWorkspaceActions, modifier: Modifier) {
    Surface(onClick = { actions.onPanelChange(MeasurePanel.EDITOR) }, modifier = modifier,
        shape = MeasureUi.capsule, color = MeasureUi.paper, contentColor = MeasureUi.ink,
        shadowElevation = 3.dp) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MeasurementGlyph(Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.gs_measure_resume), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                state.valueText?.let {
                    Text("$it ${state.unitText}", color = MeasureUi.secondary, fontSize = 14.sp)
                }
            }
        }
    }
}
