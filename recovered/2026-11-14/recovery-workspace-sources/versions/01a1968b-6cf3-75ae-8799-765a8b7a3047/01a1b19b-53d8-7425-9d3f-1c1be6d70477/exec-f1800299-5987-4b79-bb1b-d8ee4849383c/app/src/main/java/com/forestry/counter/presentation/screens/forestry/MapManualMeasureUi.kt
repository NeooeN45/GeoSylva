package com.forestry.counter.presentation.screens.forestry

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import com.forestry.counter.R
import com.forestry.counter.domain.calculation.cartography.*
import com.forestry.counter.domain.repository.CartographicStoreError
import com.forestry.counter.domain.repository.SavedCartographicMeasurement
import com.quintessences.geosylva.cartography.ui.measurement.MeasureHelp
import com.quintessences.geosylva.cartography.ui.measurement.MeasureMenuAction
import com.quintessences.geosylva.cartography.ui.measurement.MeasurePanel
import com.quintessences.geosylva.cartography.ui.measurement.MeasureWorkspace
import com.quintessences.geosylva.cartography.ui.measurement.MeasureWorkspaceActions
import com.quintessences.geosylva.cartography.ui.measurement.MeasureWorkspaceState
import com.quintessences.geosylva.cartography.ui.measurement.MeasureMode as DisplayMode
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** App adapter only: shared layout, one controller and the same numeric formatter everywhere. */
@Composable
internal fun MapManualMeasureUi(
    state: ManualMeasureState,
    model: MapManualMeasureViewModel,
    contextLabel: String,
    locationEnabled: Boolean,
    onOpenLayers: () -> Unit,
    onLocateOnMap: () -> Unit,
    onPanelHeightChanged: (Dp) -> Unit,
    onCompassTopChanged: (Dp) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val result = state.result as? CartographicMeasureResult.Available
    val formatted = result?.let { formatManualMeasure(it.valueSi, it.kind, state.distanceUnit, state.areaUnit, locale) }
    val validation = when {
        !state.ready -> storeErrorText(state.error) ?: stringResource(R.string.manual_host_preparing)
        state.saveLocked -> stringResource(R.string.manual_host_unknown)
        state.calculating -> stringResource(R.string.manual_host_calculating)
        state.result is CartographicMeasureResult.Unavailable -> issueText(state.result.issue)
        else -> null
    }
    val actions = object : MeasureWorkspaceActions {
        override fun onBack() = model.leave()
        override fun onPanelChange(panel: MeasurePanel) = model.panel(panel)
        override fun onModeChange(mode: DisplayMode) = model.mode(if (mode == DisplayMode.DISTANCE) CartographicMeasureKind.DISTANCE else CartographicMeasureKind.AREA)
        override fun onUndo() = model.undo()
        override fun onSaveRequested() = model.requestSave()
        override fun onSaveNameChange(name: String) = model.name(name)
        override fun onConfirmSave() = model.confirmSave()
        override fun onCancelSave() = model.cancelSave()
        override fun onDistanceUnitChange(unit: com.quintessences.geosylva.cartography.ui.measurement.MeasureDistanceUnit) = model.distanceUnit(unit)
        override fun onAreaUnitChange(unit: com.quintessences.geosylva.cartography.ui.measurement.MeasureAreaUnit) = model.areaUnit(unit)
        override fun onExplain(help: MeasureHelp) = model.help(help)
        override fun onLayers() = onOpenLayers()
        override fun onLocate() = onLocateOnMap()
        override fun onMenuAction(action: MeasureMenuAction) {
            when (action) {
                MeasureMenuAction.UNITS -> model.panel(MeasurePanel.UNITS)
                MeasureMenuAction.SEGMENTS -> model.auxiliary(ManualMeasureAuxiliary.SEGMENTS)
                MeasureMenuAction.COLOR -> model.auxiliary(ManualMeasureAuxiliary.COLOR)
                MeasureMenuAction.CLEAR -> model.auxiliary(ManualMeasureAuxiliary.CLEAR)
                MeasureMenuAction.SHARE -> model.auxiliary(ManualMeasureAuxiliary.SHARE)
                MeasureMenuAction.SAVED_MEASURES -> model.openSaved()
            }
        }
    }
    val menu = MeasureMenuAction.entries.toMutableSet().apply {
        if (state.mode == CartographicMeasureKind.AREA) remove(MeasureMenuAction.SEGMENTS)
        if (formatted == null) remove(MeasureMenuAction.SHARE)
        if (state.points.isEmpty()) remove(MeasureMenuAction.CLEAR)
        if (state.saveLocked) { remove(MeasureMenuAction.CLEAR); remove(MeasureMenuAction.COLOR) }
    }
    MeasureWorkspace(
        state = MeasureWorkspaceState(
            contextLabel = if (state.sourceId != null) "$contextLabel · ${stringResource(R.string.manual_host_working_copy)}" else contextLabel,
            mode = if (state.mode == CartographicMeasureKind.DISTANCE) DisplayMode.DISTANCE else DisplayMode.AREA,
            valueText = formatted?.number, unitText = formatted?.unit.orEmpty(), pointCount = if (state.ready) state.points.size else 0,
            validationMessage = validation, canSave = state.ready && formatted != null && !state.saving,
            canUndo = state.ready && state.points.isNotEmpty() && !state.saveLocked,
            geometryEditable = state.ready && !state.saveLocked,
            panel = state.panel, distanceUnit = state.distanceUnit, areaUnit = state.areaUnit,
            saveName = state.saveName, saving = state.saving, saveNameEditable = !state.saveLocked,
            saveError = storeErrorText(state.error), locationEnabled = locationEnabled,
            availableMenuActions = menu,
        ), actions = actions, onPanelHeightChanged = onPanelHeightChanged, onCompassTopChanged = onCompassTopChanged,
    )
    LaunchedEffect(state.savedNotice) {
        if (state.savedNotice) {
            Toast.makeText(context, context.getString(R.string.manual_host_saved), Toast.LENGTH_SHORT).show()
            model.consumeSavedNotice()
        }
    }
    fun value(record: SavedCartographicMeasurement): String = formatManualMeasure(record.valueSi, record.kind,
        state.distanceUnit, state.areaUnit, locale)?.let { "${it.number} ${it.unit}" }.orEmpty()
    fun title(record: SavedCartographicMeasurement) = record.name ?: context.getString(
        if (record.kind == CartographicMeasureKind.DISTANCE) R.string.manual_aux_distance_label else R.string.manual_aux_area_label)
    fun summary(record: SavedCartographicMeasurement): String = "${title(record)}\n${value(record)}\n" +
        context.getString(R.string.manual_host_share_points, record.points.size) + "\n" +
        context.getString(R.string.manual_aux_manual_source) + "\n" +
        "${record.methodId} · v${record.methodVersion}\n" + context.getString(R.string.manual_host_share_limits)
    fun draftSummary(snapshot: ManualMeasureState): String {
        val available = snapshot.result as? CartographicMeasureResult.Available ?: return ""
        val display = formatManualMeasure(available.valueSi, available.kind, snapshot.distanceUnit, snapshot.areaUnit, locale) ?: return ""
        return context.getString(R.string.manual_host_draft) + "\n${display.number} ${display.unit}\n" +
            context.getString(R.string.manual_host_share_points, available.pointCount) + "\n" +
            context.getString(R.string.manual_aux_manual_source) + "\n" +
            "${available.methodId} · v${available.methodVersion}\n" + context.getString(R.string.manual_host_share_limits)
    }
    fun externalText(action: (String) -> Unit) {
        if (state.selected != null) model.withSelectedRecord { action(summary(it)) }
        else model.withCurrentDraft { action(draftSummary(it)) }
    }
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
    val selected = state.selected.takeIf { state.ready }
    val detail = selected?.let { record -> ManualMeasureDetailUi(
        id = record.id, title = title(record), valueText = value(record),
        pointCountText = context.getString(R.string.manual_host_share_points, record.points.size),
        methodText = "${record.methodId} · v${record.methodVersion}",
        sourceText = context.getString(R.string.manual_aux_manual_source),
        recordedAtText = "${dateFormat.format(Date(record.recordedAt))} · ${TimeZone.getDefault().id}",
        crsText = record.coordinateReferenceSystem, precisionText = context.getString(R.string.manual_aux_precision_unknown),
    ) }
    val helpText = if (selected != null) null else when (state.help) {
        MeasureHelp.CONTEXT -> stringResource(R.string.manual_host_context_help)
        MeasureHelp.POINTS -> stringResource(R.string.manual_host_points_help)
        MeasureHelp.UNITS, MeasureHelp.DISTANCE_UNIT, MeasureHelp.AREA_UNIT -> stringResource(R.string.manual_host_units_help)
        MeasureHelp.SAVE, MeasureHelp.NAME, MeasureHelp.LOCAL_STORAGE -> stringResource(R.string.manual_host_storage_help)
        else -> null
    }
    ManualMeasureAuxiliaryPanels(
        state = ManualMeasureAuxiliaryState(
            panel = state.auxiliary,
            segments = result?.segmentLengthsM.orEmpty().mapIndexed { index, metres ->
                val segment = formatManualMeasure(metres, CartographicMeasureKind.DISTANCE, state.distanceUnit, state.areaUnit, locale)
                "${index + 1} · ${segment?.number.orEmpty()} ${segment?.unit.orEmpty()}"
            }, selectedColor = Color(state.colorArgb),
            saved = if (state.ready) state.saved.map { record -> ManualSavedMeasureRowUi(record.id, title(record), value(record),
                context.getString(R.string.manual_host_share_points, record.points.size)) } else emptyList(),
            loading = state.loadingSaved || !state.ready,
            errorText = storeErrorText(state.savedError ?: if (!state.ready) state.error else null),
            detail = detail, shareText = selected?.let(::summary) ?: if (state.ready) draftSummary(state) else null,
            actionsEnabled = state.ready && !state.saving, showLegacyNotice = true, helpText = helpText,
        ),
        onClose = { model.auxiliary(ManualMeasureAuxiliary.NONE) },
        onClear = model::clear,
        onColor = { model.color(it.toArgb()) },
        onSelectSaved = model::selectSaved,
        onRequestCopy = { model.requestCopy() },
        onRequestShare = { model.withSelectedRecord { model.auxiliary(ManualMeasureAuxiliary.SHARE) } },
        onRequestDetails = { model.auxiliary(ManualMeasureAuxiliary.HELP) },
        onCopyShareText = { externalText { clipboard.setText(AnnotatedString(it)) } },
        onShareText = { externalText { text ->
            try {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                }, context.getString(R.string.manual_aux_share_title)))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, context.getString(R.string.manual_host_share_unavailable), Toast.LENGTH_SHORT).show()
            }
        } },
        onRetrySaved = { if (state.ready) { if (selected != null) model.selectSaved(selected.id) else model.openSaved() } else model.retryAccess() },
    )
    if (state.copyConfirmation) AlertDialog(
        onDismissRequest = model::cancelCopy,
        title = { Text(stringResource(R.string.manual_host_copy_title)) },
        text = { Text(stringResource(R.string.manual_host_copy_body)) },
        confirmButton = { TextButton(onClick = model::copySelected) { Text(stringResource(R.string.manual_host_copy_confirm)) } },
        dismissButton = { TextButton(onClick = model::cancelCopy) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun storeErrorText(error: CartographicStoreError?): String? = error?.let { stringResource(when (it) {
    CartographicStoreError.ACCESS_CHANGED -> R.string.manual_host_access_changed
    CartographicStoreError.UNAVAILABLE -> R.string.manual_host_unavailable
    CartographicStoreError.INVALID_INPUT -> R.string.manual_host_invalid
    CartographicStoreError.CONFLICT -> R.string.manual_host_conflict
    CartographicStoreError.IO -> R.string.manual_host_io
    CartographicStoreError.CORRUPT -> R.string.manual_host_corrupt
    CartographicStoreError.UNKNOWN_RESULT -> R.string.manual_host_unknown
}) }

@Composable
private fun issueText(issue: CartographicMeasureIssue): String? = when (issue) {
    CartographicMeasureIssue.NOT_ENOUGH_POINTS -> null
    else -> stringResource(when (issue) {
        CartographicMeasureIssue.INVALID_COORDINATE -> R.string.manual_host_invalid_coordinate
        CartographicMeasureIssue.DUPLICATE_VERTEX -> R.string.manual_host_duplicate
        CartographicMeasureIssue.DEGENERATE_GEOMETRY -> R.string.manual_host_degenerate
        CartographicMeasureIssue.SELF_INTERSECTION -> R.string.manual_host_intersection
        CartographicMeasureIssue.OUTSIDE_LOCAL_AREA_DOMAIN -> R.string.manual_host_outside_domain
        CartographicMeasureIssue.TOO_MANY_POINTS -> R.string.manual_host_point_limit
        else -> R.string.manual_host_invalid
    })
}
