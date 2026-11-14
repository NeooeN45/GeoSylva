package com.forestry.counter.presentation.screens.forestry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forestry.counter.R

/** Host-owned route. This file never reads a repository, a file or a device service. */
internal enum class ManualMeasureAuxiliary { NONE, SEGMENTS, COLOR, CLEAR, HELP, SAVED, DETAIL, SHARE }

/** Stable scope for the auxiliary sheet toolbar, distinct from the map toolbar. */
internal const val MANUAL_MEASURE_AUXILIARY_HEADER_TAG = "manual_measure_auxiliary_header"

/** Only rows from the current, authorised repository snapshot belong in this model. */
internal data class ManualSavedMeasureRowUi(
    val id: String,
    val title: String,
    val valueText: String,
    val detailText: String,
    val isArea: Boolean = false,
)

/** Values are formatted by the same host formatter as the editor and share preview. */
internal data class ManualMeasureDetailUi(
    val id: String,
    val title: String,
    val valueText: String,
    val pointCountText: String,
    val conversionText: String? = null,
    val methodText: String? = null,
    val sourceText: String? = null,
    val recordedAtText: String? = null,
    val crsText: String? = null,
    val precisionText: String? = null,
)

internal data class ManualMeasureAuxiliaryState(
    val panel: ManualMeasureAuxiliary = ManualMeasureAuxiliary.NONE,
    val segments: List<String> = emptyList(),
    val selectedColor: Color = Color(0xFF176BDF),
    val saved: List<ManualSavedMeasureRowUi> = emptyList(),
    val loading: Boolean = false,
    val errorText: String? = null,
    val detail: ManualMeasureDetailUi? = null,
    val shareText: String? = null,
    val actionsEnabled: Boolean = true,
    val showLegacyNotice: Boolean = false,
    val helpTitle: String? = null,
    val helpText: String? = null,
)

/**
 * Stateless adapters for the manual-measure editor. Every mutation, copy,
 * clipboard write, share and authorisation revalidation belongs to the host.
 * Opening DETAIL cannot turn a saved record into an editable draft.
 */
@Composable
internal fun ManualMeasureAuxiliaryPanels(
    state: ManualMeasureAuxiliaryState,
    onClose: () -> Unit,
    onClear: () -> Unit,
    onColor: (Color) -> Unit,
    onSelectSaved: (String) -> Unit,
    onRequestCopy: (String) -> Unit,
    onRequestShare: (String) -> Unit,
    onRequestDetails: (String) -> Unit,
    onCopyShareText: () -> Unit,
    onShareText: () -> Unit,
    onRetrySaved: () -> Unit,
    onNewMeasure: (() -> Unit)? = null,
    onPanelHeightChanged: (Dp) -> Unit = {},
) {
    val original = MaterialTheme.colorScheme
    MaterialTheme(colorScheme = original.copy(primary = AuxiliaryStyle.green, onPrimary = Color.White,
        surface = AuxiliaryStyle.paper, onSurface = AuxiliaryStyle.ink,
        onSurfaceVariant = AuxiliaryStyle.muted, secondaryContainer = AuxiliaryStyle.lavender)) {
    when (state.panel) {
        ManualMeasureAuxiliary.NONE -> Unit
        ManualMeasureAuxiliary.CLEAR -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(R.string.manual_aux_clear_title)) },
            text = { Text(stringResource(R.string.manual_aux_clear_body)) },
            confirmButton = {
                TextButton(onClick = onClear, enabled = state.actionsEnabled) {
                    Text(stringResource(R.string.manual_aux_clear_action))
                }
            },
            dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.manual_aux_cancel)) } },
        )
        ManualMeasureAuxiliary.COLOR -> ColorDialog(state, onClose, onColor)
        else -> AuxiliarySheet(title = auxiliaryTitle(state), onClose = onClose, onPanelHeightChanged = onPanelHeightChanged,
            back = state.panel in setOf(ManualMeasureAuxiliary.DETAIL, ManualMeasureAuxiliary.HELP, ManualMeasureAuxiliary.SHARE)) {
            when (state.panel) {
                ManualMeasureAuxiliary.SEGMENTS -> {
                    if (state.segments.isEmpty()) Text(stringResource(R.string.manual_aux_no_segments))
                    state.segments.forEach { segment ->
                        Text(segment, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        HorizontalDivider()
                    }
                }
                ManualMeasureAuxiliary.HELP -> {
                    if (!MeasurementReadStatus(state, onRetrySaved)) {
                        when {
                            state.helpText != null -> Text(state.helpText, style = MaterialTheme.typography.bodyLarge)
                            state.detail != null -> {
                                ManualMeasureStoredCalculation(state.detail)
                                Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(20.dp))
                                    Text(stringResource(R.string.manual_aux_return_to_measure), Modifier.padding(start = 8.dp))
                                }
                            }
                            else -> DefaultCalculationHelp()
                        }
                    }
                }
                ManualMeasureAuxiliary.SAVED -> SavedMeasurements(state, onSelectSaved, onRetrySaved, onNewMeasure)
                ManualMeasureAuxiliary.DETAIL -> {
                    val detail = state.detail
                    if (!MeasurementReadStatus(state, onRetrySaved)) {
                        if (detail == null) Text(stringResource(R.string.manual_aux_record_unavailable))
                        else {
                            OnPhoneLabel()
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(detail.valueText, Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge,
                                    color = AuxiliaryStyle.ink, fontWeight = FontWeight.Bold)
                                Surface(shape = RoundedCornerShape(24.dp), color = AuxiliaryStyle.lavender) {
                                    Text(detail.pointCountText, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = AuxiliaryStyle.ink)
                                }
                            }
                            AuxiliaryLink(stringResource(R.string.manual_aux_calculation_details), state.actionsEnabled) {
                                onRequestDetails(detail.id)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { onRequestShare(detail.id) }, enabled = state.actionsEnabled,
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                    Icon(Icons.Outlined.Share, null, Modifier.size(20.dp))
                                    Text(stringResource(R.string.manual_aux_share_action), Modifier.padding(start = 6.dp))
                                }
                                Button(onClick = { onRequestCopy(detail.id) }, enabled = state.actionsEnabled,
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                    Icon(Icons.Outlined.Edit, null, Modifier.size(20.dp))
                                    Text(stringResource(R.string.manual_aux_resume_copy), Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                    }
                }
                ManualMeasureAuxiliary.SHARE -> {
                    if (!MeasurementReadStatus(state, onRetrySaved)) {
                        Text(stringResource(R.string.manual_aux_text_summary), style = MaterialTheme.typography.labelLarge)
                        val preview = state.shareText
                        if (preview.isNullOrBlank()) Text(stringResource(R.string.manual_aux_share_unavailable))
                        else Surface(shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                            Text(preview, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onCopyShareText,
                                enabled = state.actionsEnabled && !preview.isNullOrBlank(),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(20.dp))
                                Text(stringResource(R.string.manual_aux_copy_text), Modifier.padding(start = 6.dp))
                            }
                            Button(onClick = onShareText, enabled = state.actionsEnabled && !preview.isNullOrBlank(),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                Icon(Icons.Outlined.Share, null, Modifier.size(20.dp))
                                Text(stringResource(R.string.manual_aux_share_action), Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
    }
    }
}

@Composable
private fun auxiliaryTitle(state: ManualMeasureAuxiliaryState): String = when (state.panel) {
    ManualMeasureAuxiliary.SEGMENTS -> stringResource(R.string.manual_aux_segments_title)
    ManualMeasureAuxiliary.COLOR -> stringResource(R.string.manual_aux_color_title)
    ManualMeasureAuxiliary.CLEAR -> stringResource(R.string.manual_aux_clear_title)
    ManualMeasureAuxiliary.HELP -> state.helpTitle ?: stringResource(R.string.manual_aux_help_title)
    ManualMeasureAuxiliary.SAVED -> stringResource(R.string.manual_aux_saved_title)
    ManualMeasureAuxiliary.DETAIL -> state.detail?.title ?: stringResource(R.string.manual_aux_detail_title)
    ManualMeasureAuxiliary.SHARE -> stringResource(R.string.manual_aux_share_title)
    ManualMeasureAuxiliary.NONE -> ""
}

@Composable
private fun AuxiliarySheet(title: String, onClose: () -> Unit, onPanelHeightChanged: (Dp) -> Unit,
    back: Boolean, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    var measuredHeight by remember { mutableFloatStateOf(0f) }
    val latestHeight by rememberUpdatedState(onPanelHeightChanged)
    LaunchedEffect(measuredHeight, density) { if (measuredHeight > 0) latestHeight(with(density) { measuredHeight.toDp() }) }
    BackHandler(onBack = onClose)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maximum = maxHeight * if (density.fontScale > 1.1f || maxHeight < 600.dp) 0.82f else 0.56f
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .onSizeChanged { measuredHeight = it.height.toFloat() }
            .navigationBarsPadding().padding(12.dp)) {
            Surface(shape = RoundedCornerShape(28.dp), color = AuxiliaryStyle.paper, shadowElevation = 5.dp,
                modifier = Modifier.fillMaxWidth().heightIn(max = maximum)
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }) {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).width(36.dp).height(4.dp)
                        .clip(CircleShape).background(AuxiliaryStyle.muted.copy(alpha = .45f)))
                    Row(Modifier.fillMaxWidth().testTag(MANUAL_MEASURE_AUXILIARY_HEADER_TAG)
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (back) IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = AuxiliaryStyle.ink)
                        } else Surface(shape = RoundedCornerShape(14.dp), color = AuxiliaryStyle.lavender,
                            modifier = Modifier.padding(start = 8.dp).size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Description, null, tint = AuxiliaryStyle.ink) }
                        }
                        Text(title, Modifier.weight(1f).padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = AuxiliaryStyle.ink)
                        if (!back) IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.manual_aux_close), tint = AuxiliaryStyle.ink)
                        }
                    }
                    Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
                }
            }
        }
    }
}

/** Returns true while data is loading or unavailable; never exposes stale fields beneath it. */
@Composable
private fun MeasurementReadStatus(state: ManualMeasureAuxiliaryState, onRetry: () -> Unit): Boolean {
    if (state.loading) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.manual_aux_loading))
        }
        return true
    }
    if (state.errorText != null) {
        Text(state.errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.manual_aux_retry))
        }
        return true
    }
    return false
}

@Composable
private fun SavedMeasurements(state: ManualMeasureAuxiliaryState, onSelect: (String) -> Unit,
    onRetry: () -> Unit, onNewMeasure: (() -> Unit)?) {
    val expandedText = LocalDensity.current.fontScale > 1.15f
    OnPhoneLabel()
    HorizontalDivider()
    if (!MeasurementReadStatus(state, onRetry)) {
        if (state.saved.isEmpty()) Text(stringResource(R.string.manual_aux_empty))
        else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.saved, key = { it.id }) { row ->
                Surface(onClick = { onSelect(row.id) }, enabled = state.actionsEnabled,
                    shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SavedMeasureGlyph(row.isArea, Modifier.size(32.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(row.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(row.detailText, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (expandedText) Text(row.valueText, style = MaterialTheme.typography.titleMedium,
                                color = AuxiliaryStyle.ink, fontWeight = FontWeight.Bold)
                        }
                        if (!expandedText) Text(row.valueText, style = MaterialTheme.typography.titleMedium,
                            color = AuxiliaryStyle.ink, fontWeight = FontWeight.Bold)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                }
            }
        }
    }
    if (state.showLegacyNotice) Text(stringResource(R.string.manual_aux_legacy_notice),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (onNewMeasure != null) Button(onClick = onNewMeasure, enabled = state.actionsEnabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(20.dp)) {
        Icon(Icons.Outlined.Add, null)
        Text(stringResource(R.string.manual_aux_new), Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ColorDialog(state: ManualMeasureAuxiliaryState, onClose: () -> Unit, onColor: (Color) -> Unit) {
    val choices = listOf(
        Color(0xFF075B3D) to R.string.manual_aux_color_forest,
        Color(0xFF176BDF) to R.string.manual_aux_color_blue,
        Color(0xFF77567F) to R.string.manual_aux_color_plum,
        Color(0xFF986548) to R.string.manual_aux_color_earth,
        Color(0xFF556774) to R.string.manual_aux_color_slate,
        Color(0xFF7B7E44) to R.string.manual_aux_color_olive,
        Color(0xFF237F82) to R.string.manual_aux_color_teal,
        Color(0xFFAD4242) to R.string.manual_aux_color_red,
    )
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.manual_aux_color_title)) },
        text = {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                choices.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (color, nameResource) ->
                            val name = stringResource(nameResource)
                            val selected = state.selectedColor == color
                            Column(Modifier.weight(1f).heightIn(min = 72.dp)
                                .selectable(selected = selected, enabled = state.actionsEnabled, role = Role.RadioButton,
                                    onClick = { onColor(color) }).padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(36.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                                    if (selected) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                                Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.manual_aux_close)) } },
    )
}

@Composable
private fun DefaultCalculationHelp() {
    AuxiliaryFact(stringResource(R.string.manual_aux_source_label), stringResource(R.string.manual_aux_manual_source))
    AuxiliaryFact(stringResource(R.string.manual_aux_distance_label), stringResource(R.string.manual_aux_distance_method))
    AuxiliaryFact(stringResource(R.string.manual_aux_area_label), stringResource(R.string.manual_aux_area_method))
    AuxiliaryFact(stringResource(R.string.manual_aux_limits_label), stringResource(R.string.manual_aux_limits))
}

@Composable
internal fun ManualMeasureStoredCalculation(detail: ManualMeasureDetailUi) {
    Text(detail.valueText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
        color = AuxiliaryStyle.ink)
    detail.conversionText?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    AuxiliaryFact(stringResource(R.string.manual_aux_points_label), detail.pointCountText)
    AuxiliaryFact(stringResource(R.string.manual_aux_method_label), detail.methodText ?: stringResource(R.string.manual_aux_unknown))
    AuxiliaryFact(stringResource(R.string.manual_aux_source_label), detail.sourceText ?: stringResource(R.string.manual_aux_unknown))
    AuxiliaryFact(stringResource(R.string.manual_aux_recorded_label), detail.recordedAtText ?: stringResource(R.string.manual_aux_unknown))
    AuxiliaryFact(stringResource(R.string.manual_aux_crs_label), detail.crsText ?: stringResource(R.string.manual_aux_unknown))
    AuxiliaryFact(stringResource(R.string.manual_aux_precision_label), detail.precisionText ?: stringResource(R.string.manual_aux_precision_unknown))
    Surface(shape = RoundedCornerShape(16.dp), color = AuxiliaryStyle.lavender, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.manual_aux_limits_label), style = MaterialTheme.typography.labelLarge)
            // Same qualified caveat as the share preview, for both distances and areas.
            Text(stringResource(R.string.manual_host_share_limits), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AuxiliaryFact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
    HorizontalDivider()
}

@Composable
private fun AuxiliaryLink(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(label, Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
}

@Composable
private fun OnPhoneLabel() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Smartphone, null, tint = AuxiliaryStyle.ink, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.manual_aux_on_phone), style = MaterialTheme.typography.bodyMedium, color = AuxiliaryStyle.ink)
    }
}

/** Generic measurement symbol; it never claims to reproduce a stored geometry. */
@Composable
private fun SavedMeasureGlyph(isArea: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val first = Offset(size.width * .15f, size.height * .8f)
        val second = Offset(size.width * .48f, size.height * if (isArea) .2f else .5f)
        val third = Offset(size.width * .85f, size.height * if (isArea) .7f else .2f)
        val stroke = 1.5.dp.toPx()
        drawLine(AuxiliaryStyle.ink, first, second, stroke)
        drawLine(AuxiliaryStyle.ink, second, third, stroke)
        if (isArea) drawLine(AuxiliaryStyle.ink, third, first, stroke)
        listOf(first, second, third).forEach {
            drawCircle(AuxiliaryStyle.paper, 3.dp.toPx(), it)
            drawCircle(AuxiliaryStyle.ink, 3.dp.toPx(), it, style = Stroke(stroke))
        }
    }
}

private object AuxiliaryStyle {
    val paper = Color(0xFFFFFEFF)
    val ink = Color(0xFF0B286B)
    val green = Color(0xFF00652C)
    val muted = Color(0xFF6663AC)
    val lavender = Color(0xFFE7E4F7)
}
