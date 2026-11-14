package com.quintessences.geosylva.cartography.ui.source

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quintessences.geosylva.cartography.ui.MapPreviewKey
import com.quintessences.geosylva.cartography.ui.R

/** Host values describe the current authorized render, independently of its profession or storage. */
data class MapSourceLayerUi(
    val id: String,
    val title: String,
    val previewKey: MapPreviewKey,
    val visible: Boolean,
    val opacity: Float? = null,
    val legendLabel: String,
    val legendColorHex: String,
    val origin: String? = null,
    val distribution: String? = null,
    val edition: String? = null,
    val licenceLabel: String? = null,
    val licenceUrl: String? = null,
    val officialUrl: String? = null,
    val note: String? = null,
)

data class MapSourcePanelState(
    val title: String,
    val subtitle: String? = null,
    val previewKey: MapPreviewKey? = null,
    val layer: MapSourceLayerUi? = null,
    val showSource: Boolean = true,
    val showProvenance: Boolean = true,
    val origin: String? = null,
    val distribution: String? = null,
    val edition: String? = null,
    val licenceLabel: String? = null,
    val licenceUrl: String? = null,
    val officialUrl: String? = null,
    val facts: List<Pair<String, String>> = emptyList(),
    val notes: List<String> = emptyList(),
    val previewDetails: String? = null,
    val linkUnavailable: Boolean = false,
)

private val SourceInk = Color(0xFF164D31)
private val SourceText = Color(0xFF262C2B)
private val SourceMuted = Color(0xFF737A7D)
private val SourceBorder = Color(0xFFE0DED8)

/** Shared non-modal 19/20: only the measured lower panel consumes gestures; the map remains live. */
@Composable
fun MapLayerSourcePanel(
    state: MapSourcePanelState,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onSource: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    onPanelHeightChanged: (Dp) -> Unit = {},
    onVisibilityChanged: ((Boolean) -> Unit)? = null,
    onOpacityChanged: ((Float) -> Unit)? = null,
    thumbnail: @Composable (MapPreviewKey, Modifier) -> Unit = { _, _ -> },
) {
    val density = LocalDensity.current
    val heightChanged by rememberUpdatedState(onPanelHeightChanged)
    val pane = if (state.showSource) stringResource(R.string.gs_source_panel_details) else state.title
    BackHandler(onBack = onBack)
    BoxWithConstraints(modifier.fillMaxSize()) {
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * 0.54f)
            .onGloballyPositioned { heightChanged(with(density) { it.size.height.toDp() }) }
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            .semantics { paneTitle = pane },
            color = Color(0xFFFFFCF7), contentColor = SourceText,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), shadowElevation = 5.dp) {
            Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 18.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).size(34.dp, 4.dp).clip(CircleShape).background(Color(0xFF9B9D9B)))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onBack, Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.gs_source_panel_back), tint = SourceInk) }
                    state.previewKey?.let { key ->
                        Box(Modifier.width(64.dp).height(42.dp).clip(RoundedCornerShape(8.dp))) { thumbnail(key, Modifier.fillMaxSize()) }
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(state.title, color = SourceInk, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.semantics { heading() })
                        state.subtitle?.let { Text(it, color = SourceMuted, fontSize = 12.sp) }
                    }
                    if (!state.showSource) IconButton(onSource, Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Info, stringResource(R.string.gs_source_panel_details), Modifier.size(20.dp), tint = SourceInk)
                    }
                    IconButton(onClose, Modifier.size(48.dp)) { Icon(Icons.Outlined.Close, stringResource(R.string.gs_source_panel_close), tint = SourceInk) }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.showSource && state.layer != null) LayerControls(state.layer, onVisibilityChanged, onOpacityChanged, onSource)
                    else SourceDetails(state, onOpenLink)
                }
            }
        }
    }
}

@Composable
private fun LayerControls(layer: MapSourceLayerUi, visibility: ((Boolean) -> Unit)?, opacity: ((Float) -> Unit)?, source: () -> Unit) {
    HorizontalDivider(color = SourceBorder)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.gs_source_panel_show), Modifier.weight(1f), fontSize = 16.sp)
        val description = stringResource(R.string.gs_source_panel_show)
        Switch(layer.visible, visibility, enabled = visibility != null,
            modifier = Modifier.semantics { contentDescription = description }, colors = SwitchDefaults.colors(checkedTrackColor = SourceInk))
    }
    HorizontalDivider(color = SourceBorder)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.gs_source_panel_opacity), fontSize = 16.sp)
        Text(layer.opacity?.takeIf { it.isFinite() && it in 0f..1f }?.let { stringResource(R.string.gs_source_panel_percent, (it * 100).toInt()) }
            ?: stringResource(R.string.gs_source_panel_unknown), color = SourceMuted)
    }
    layer.opacity?.takeIf { it.isFinite() && it in 0f..1f }?.let { value ->
        val description = stringResource(R.string.gs_source_panel_opacity)
        Slider(value, opacity ?: {}, enabled = opacity != null, modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
            colors = SliderDefaults.colors(thumbColor = SourceInk, activeTrackColor = SourceInk))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.gs_source_panel_zero), fontSize = 12.sp, color = SourceMuted)
            Text(stringResource(R.string.gs_source_panel_full), fontSize = 12.sp, color = SourceMuted)
        }
    }
    HorizontalDivider(color = SourceBorder)
    Text(stringResource(R.string.gs_source_panel_legend), fontSize = 15.sp, fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth().heightIn(min = 38.dp), verticalAlignment = Alignment.CenterVertically) {
        val color = layer.legendColorHex.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) }?.removePrefix("#")?.toLongOrNull(16)
            ?.let { Color(0xFF000000 or it) }
        if (color != null) Surface(Modifier.size(30.dp, 18.dp), shape = RoundedCornerShape(2.dp), color = Color.Transparent, border = BorderStroke(2.dp, color)) {}
        else Text(stringResource(R.string.gs_source_panel_unknown), color = SourceMuted, fontSize = 11.sp)
        Text(layer.legendLabel, Modifier.padding(start = 12.dp), fontSize = 14.sp)
    }
    HorizontalDivider(color = SourceBorder)
    SourceLink(stringResource(R.string.gs_source_panel_details), source, external = false)
}

@Composable
private fun SourceDetails(state: MapSourcePanelState, onOpen: (String) -> Unit) {
    var previewExpanded by remember(state.title) { mutableStateOf(false) }
    val unknown = stringResource(R.string.gs_source_panel_unknown)
    Text(stringResource(R.string.gs_source_panel_details), color = SourceMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    HorizontalDivider(color = SourceBorder)
    if (state.showProvenance) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SourceFact(stringResource(R.string.gs_source_panel_origin), state.origin ?: unknown, Modifier.weight(1f))
            SourceFact(stringResource(R.string.gs_source_panel_distribution), state.distribution ?: unknown, Modifier.weight(1f))
        }
        HorizontalDivider(color = SourceBorder)
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.gs_source_panel_edition), Modifier.weight(1f), fontSize = 14.sp)
            Text(state.edition ?: unknown, color = SourceMuted, fontSize = 14.sp)
        }
        HorizontalDivider(color = SourceBorder)
    }
    state.facts.forEach { (label, value) -> SourceFact(label, value, Modifier.fillMaxWidth()) }
    if (state.licenceUrl != null && state.licenceLabel != null) SourceLink(state.licenceLabel, { onOpen(state.licenceUrl) })
    else if (state.showProvenance) Row(Modifier.fillMaxWidth().heightIn(min = 38.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.gs_source_panel_terms), Modifier.weight(1f), fontSize = 14.sp)
        Text(state.licenceLabel ?: unknown, color = SourceMuted, fontSize = 13.sp)
    }
    state.officialUrl?.let { url -> SourceLink(stringResource(R.string.gs_source_panel_official), { onOpen(url) }) }
    state.notes.forEach { Text(it, color = SourceMuted, fontSize = 12.sp) }
    state.previewDetails?.let { details ->
        TextButton({ previewExpanded = !previewExpanded }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Info, null, Modifier.size(18.dp)); Text(stringResource(R.string.gs_source_panel_previews), Modifier.padding(start = 8.dp), fontSize = 12.sp)
        }
        if (previewExpanded) Text(details, color = SourceMuted, fontSize = 11.sp)
    }
    if (state.linkUnavailable) Text(stringResource(R.string.gs_source_panel_link_unavailable), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
}
@Composable private fun SourceFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = SourceMuted, fontSize = 12.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
@Composable private fun SourceLink(label: String, click: () -> Unit, external: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = click), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (external) Icons.Outlined.OpenInNew else Icons.Outlined.Description, null, Modifier.size(24.dp), tint = SourceInk)
        Text(label, Modifier.weight(1f).padding(horizontal = 12.dp), fontSize = 15.sp)
        Icon(if (external) Icons.Outlined.OpenInNew else Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(21.dp), tint = SourceInk)
    }
    HorizontalDivider(color = SourceBorder)
}
