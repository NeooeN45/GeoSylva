package com.forestry.counter.presentation.screens.forestry.trace

import android.content.Intent
import android.location.Location
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.R
import com.forestry.counter.domain.location.trace.*
import com.forestry.counter.domain.repository.CartographicStoreResult
import com.forestry.counter.domain.repository.CartographicTraceDocument
import com.forestry.counter.domain.usecase.maps.MapDataScope
import com.quintessences.geosylva.cartography.ui.trace.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat

/**
 * Application trace UI around the same shared map canvas. The caller opens it through
 * controller.open(scope), supplies an already authorised context title and draws every
 * state.snapshot.portions separately. It must not join their endpoints.
 */
@Composable
fun MapTraceHost(
    controller: MapTraceController,
    scope: MapDataScope,
    contextTitle: String,
    onClose: () -> Unit,
    onLayers: () -> Unit,
    onFrame: () -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    previewLocation: Location? = null,
    canvas: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    val runtime by controller.state.collectAsStateWithLifecycle()
    if (!runtime.visible || runtime.scope != scope) return
    val handleBack: () -> Unit = {
        if (!runtime.busy && !runtime.saveUncertain) when (runtime.screen) {
            TraceScreen.DETAILS -> controller.backDetails()
            TraceScreen.CAPTURE -> controller.finish()
            TraceScreen.SAVE -> controller.cancelSave()
            else -> { controller.hide(); onClose() }
        }
    }
    // Consume Android Back even while a commit is pending; Home/lifecycle changes keep capture running.
    BackHandler(enabled = runtime.visible && runtime.scope == scope, onBack = handleBack)
    val appContext = LocalContext.current
    val work = rememberCoroutineScope()
    var dialog by remember(scope) { mutableStateOf<TraceHostDialog?>(null) }
    var shareDocument by remember(scope) { mutableStateOf<CartographicTraceDocument?>(null) }
    var shareBusy by remember(scope) { mutableStateOf(false) }
    var exportError by remember(scope) { mutableStateOf(false) }
    LaunchedEffect(scope) { controller.contextChanged(scope) }
    LaunchedEffect(previewLocation) { controller.setPreviewLocation(previewLocation) }
    LaunchedEffect(runtime.authorised) { if (!runtime.authorised) { dialog = null; shareDocument = null } }
    val issue = runtime.issue?.let { stringResource(when (it) {
        TraceRuntimeIssue.ACCESS -> R.string.map_trace_access_error
        TraceRuntimeIssue.PERMISSION -> R.string.map_trace_permission_error
        TraceRuntimeIssue.INTERRUPTED -> R.string.map_trace_interrupted
        TraceRuntimeIssue.RECOVERED -> R.string.map_trace_recovered
        TraceRuntimeIssue.STORE -> R.string.map_trace_store_error
        TraceRuntimeIssue.SAVE_UNCERTAIN -> R.string.map_trace_uncertain_save
        TraceRuntimeIssue.INVALID_INPUT -> R.string.map_trace_invalid_input
        TraceRuntimeIssue.NO_POINTS -> R.string.map_trace_no_points
    }) }
    val snapshot = runtime.snapshot
    val fix = snapshot?.lastReceivedFix ?: runtime.previewFix
    val age = fix?.let { if (it.fixElapsedRealtimeNanos in 1..runtime.nowNanos) (runtime.nowNanos - it.fixElapsedRealtimeNanos) / 1_000_000_000L else null }
    val policy = snapshot?.capturePolicy ?: runtime.policy
    val available = fix != null && age != null && runtime.nowNanos - fix.fixElapsedRealtimeNanos <= policy.maximumFixAgeNanos &&
        !fix.isMock && SphericalTraceDistance.isValidCoordinate(fix.latitude, fix.longitude) &&
        fix.horizontalAccuracyM?.let { it.isFinite() && it > 0 && it <= policy.maximumHorizontalAccuracyM } == true &&
        (runtime.screen == TraceScreen.PREPARATION || snapshot?.phase == TraceCapturePhase.RECORDING)
    val locationLabel = if (available) null else stringResource(when {
        fix == null -> R.string.map_trace_fix_waiting
        age == null || runtime.nowNanos - fix.fixElapsedRealtimeNanos > policy.maximumFixAgeNanos -> R.string.map_trace_fix_stale
        else -> R.string.map_trace_fix_quality
    })
    val actionsAllowed = mutableSetOf(TraceAction.BACK)
    if (runtime.authorised && !runtime.busy) {
        actionsAllowed += setOf(TraceAction.LAYERS, TraceAction.FRAME, TraceAction.OVERFLOW)
        when (runtime.screen) {
            TraceScreen.PREPARATION -> {
                actionsAllowed += TraceAction.SETTINGS
                actionsAllowed += TraceAction.LIBRARY
                if (controller.needsLocationPermission()) actionsAllowed += TraceAction.REQUEST_PERMISSION
                // Permission may be requested by pressing Start; source capture still waits for a fresh fix.
                if (available) actionsAllowed += TraceAction.START
            }
            TraceScreen.CAPTURE -> actionsAllowed += setOf(TraceAction.FINISH,
                if (snapshot?.phase == TraceCapturePhase.MANUALLY_PAUSED) TraceAction.RESUME else TraceAction.PAUSE)
            TraceScreen.REVIEW -> {
                actionsAllowed += TraceAction.RESUME
                if (snapshot?.portions?.any { it.points.size >= 2 } == true) actionsAllowed += TraceAction.CONTINUE
            }
            TraceScreen.SAVE -> {
                actionsAllowed += TraceAction.SAVE
                if (!runtime.saveUncertain) actionsAllowed += TraceAction.CANCEL_SAVE
            }
            TraceScreen.SAVED -> actionsAllowed += setOf(TraceAction.SHARE, TraceAction.DUPLICATE, TraceAction.DETAILS)
            TraceScreen.DETAILS -> actionsAllowed += TraceAction.METHOD_SOURCE
        }
    }
    if (runtime.saveUncertain) actionsAllowed -= setOf(TraceAction.BACK, TraceAction.LAYERS, TraceAction.OVERFLOW)
    val ui = TraceWorkspaceState(screen = runtime.screen, contextTitle = contextTitle,
        captureStatus = when (snapshot?.phase) {
            TraceCapturePhase.MANUALLY_PAUSED -> TraceCaptureStatus.MANUALLY_PAUSED
            TraceCapturePhase.WAITING_FOR_QUALITY -> TraceCaptureStatus.WAITING_FOR_QUALITY
            else -> TraceCaptureStatus.RECORDING
        }, resumed = (snapshot?.portions?.size ?: 0) > 1, name = runtime.name,
        destinationLabel = stringResource(R.string.map_trace_local_destination),
        location = TraceLocationSummary(available, fix?.horizontalAccuracyM?.takeIf { it.isFinite() && it > 0 }, age, locationLabel),
        statistics = TraceStatistics(snapshot?.lengthM, snapshot?.acceptedPointCount, snapshot?.portions?.size,
            snapshot?.betweenPortionsGapCount, snapshot?.sessionElapsedNanos?.div(1_000_000_000),
            snapshot?.currentManualPauseElapsedNanos?.div(1_000_000_000), null,
            snapshot?.portions?.mapIndexed { index, portion -> TracePortionSummary(portion.id, index + 1, portion.lengthM) }.orEmpty()),
        allowedActions = actionsAllowed, selectedPortionId = runtime.selectedPortionId,
        saving = runtime.busy, nameEditable = !runtime.saveUncertain, errorMessage = issue)
    val handlers = object : TraceWorkspaceActions {
        override fun onAction(action: TraceAction) {
            if (action !in actionsAllowed || runtime.busy) return
            when (action) {
                TraceAction.BACK -> handleBack()
                TraceAction.LAYERS -> onLayers()
                TraceAction.FRAME -> onFrame()
                TraceAction.SETTINGS -> dialog = TraceHostDialog.SETTINGS
                TraceAction.REQUEST_PERMISSION -> onRequestPermissions()
                TraceAction.LIBRARY -> { controller.refreshLibrary(); dialog = TraceHostDialog.LIBRARY }
                TraceAction.START -> controller.start()
                TraceAction.PAUSE -> controller.pause()
                TraceAction.RESUME -> controller.resume()
                TraceAction.FINISH -> controller.finish()
                TraceAction.CONTINUE -> controller.reviewSave()
                TraceAction.SAVE -> controller.save()
                TraceAction.CANCEL_SAVE -> controller.cancelSave()
                TraceAction.DETAILS -> controller.details()
                TraceAction.METHOD_SOURCE -> dialog = TraceHostDialog.METHOD
                TraceAction.OVERFLOW -> dialog = TraceHostDialog.OPTIONS
                TraceAction.DUPLICATE -> controller.duplicate()
                TraceAction.SHARE -> runtime.selected?.let { selected ->
                    work.launch {
                        shareBusy = true
                        try {
                            if (controller.exportPreview(selected.id, selected.revision) is CartographicStoreResult.Success) {
                                shareDocument = selected
                                dialog = TraceHostDialog.SHARE
                            } else exportError = true
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { exportError = true }
                        finally { shareBusy = false }
                    }
                }
            }
        }
        override fun onNameChanged(name: String) = controller.nameChanged(name)
        override fun onExplain(help: TraceHelp) { dialog = if (help in setOf(TraceHelp.CAPTURE, TraceHelp.PAUSE_DURATION, TraceHelp.NEW_PORTION)) TraceHostDialog.HELP else TraceHostDialog.METHOD }
        override fun onSelectPortion(id: String) { controller.selectPortion(id); onFrame() }
    }
    TraceWorkspace(ui, handlers, modifier, canvas)
    if (runtime.issue == TraceRuntimeIssue.PERMISSION) AlertDialog(onDismissRequest = { controller.dismissIssue() },
        title = { Text(stringResource(R.string.map_trace_permission_error)) },
        confirmButton = { TextButton(onClick = { controller.dismissIssue(); onRequestPermissions() }) { Text(stringResource(R.string.map_trace_apply)) } },
        dismissButton = { TextButton(onClick = { controller.dismissIssue() }) { Text(stringResource(R.string.map_trace_close)) } })
    when (dialog) {
        TraceHostDialog.OPTIONS -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text(stringResource(R.string.map_trace_options)) },
            text = { Column {
                if (runtime.screen == TraceScreen.CAPTURE) TextButton(onClick = { controller.finish(); dialog = null }) { Text(stringResource(R.string.map_trace_notification_finish)) }
                else {
                    TextButton(onClick = { controller.refreshLibrary(); dialog = TraceHostDialog.LIBRARY }) { Text(stringResource(R.string.map_trace_library)) }
                    TextButton(onClick = { controller.newTrace(); dialog = null }) { Text(stringResource(R.string.map_trace_new)) }
                }
                TextButton(onClick = { dialog = TraceHostDialog.METHOD }) { Text(stringResource(R.string.map_trace_method_title)) }
            } }, confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.map_trace_close)) } })
        TraceHostDialog.LIBRARY -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text(stringResource(R.string.map_trace_library)) },
            text = { Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                if (runtime.library.isEmpty()) Text(stringResource(R.string.map_trace_library_empty))
                runtime.library.forEach { item ->
                    TextButton(onClick = { controller.selectDocument(item.id); dialog = null }) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(item.name ?: stringResource(R.string.map_trace_unnamed))
                            Text(stringResource(if (item.saved) R.string.map_trace_saved else R.string.map_trace_draft, item.snapshot.acceptedPointCount))
                        }
                    }
                }
            } }, confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.map_trace_close)) } })
        TraceHostDialog.SETTINGS -> TraceSettings(policy, onDismiss = { dialog = null }, onApply = { controller.configure(it); dialog = null })
        TraceHostDialog.HELP, TraceHostDialog.METHOD -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text(stringResource(R.string.map_trace_method_title)) },
            text = { Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
                if (dialog == TraceHostDialog.HELP) Text(stringResource(R.string.map_trace_help_capture))
                Text(stringResource(R.string.map_trace_method_body, snapshot?.calculationMethod ?: SphericalTraceDistance.METHOD_ID,
                    number(policy.maximumHorizontalAccuracyM), policy.maximumFixAgeNanos / 1_000_000_000,
                    number(policy.minimumPointDistanceM), policy.maximumObservationGapNanos / 1_000_000_000))
                if (runtime.selected?.sourceTraceId != null) Text(stringResource(R.string.map_trace_copy_source))
            } }, confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.map_trace_close)) } })
        TraceHostDialog.SHARE -> shareDocument?.let { document ->
            AlertDialog(onDismissRequest = { if (!shareBusy) { dialog = null; shareDocument = null } },
                title = { Text(stringResource(R.string.map_trace_share_preview)) },
                text = { Text(stringResource(R.string.map_trace_share_contents, document.snapshot.portions.size, document.snapshot.acceptedPointCount)) },
                confirmButton = { TextButton(enabled = !shareBusy, onClick = {
                    work.launch {
                        shareBusy = true
                        try {
                            val shared = shareTraceExport(
                                directory = File(appContext.cacheDir, "field_capture/map_trace_share"),
                                exportPreview = { controller.exportPreview(document.id, document.revision) },
                                openChooser = { file ->
                                    val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
                                    appContext.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/geo+json")
                                        .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
                                },
                            )
                            if (!shared) exportError = true
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { exportError = true }
                        finally {
                            shareBusy = false
                            dialog = null
                            shareDocument = null
                        }
                    }
                }) { Text(stringResource(R.string.map_trace_share_confirm)) } },
                dismissButton = { TextButton(enabled = !shareBusy, onClick = { dialog = null; shareDocument = null }) { Text(stringResource(R.string.map_trace_close)) } })
        }
        null -> Unit
    }
    if (exportError) AlertDialog(onDismissRequest = { exportError = false }, text = { Text(stringResource(R.string.map_trace_share_error)) },
        confirmButton = { TextButton(onClick = { exportError = false }) { Text(stringResource(R.string.map_trace_close)) } })
}

@Composable
private fun TraceSettings(policy: TraceCapturePolicy, onDismiss: () -> Unit, onApply: (TraceCapturePolicy) -> Unit) {
    var accuracy by remember { mutableFloatStateOf(policy.maximumHorizontalAccuracyM.toFloat()) }
    var spacing by remember { mutableFloatStateOf(policy.minimumPointDistanceM.toFloat()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.map_trace_settings_title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.map_trace_max_accuracy, number(accuracy.toDouble())))
            Slider(accuracy, onValueChange = { accuracy = it }, valueRange = 3f..50f, steps = 46)
            Text(stringResource(R.string.map_trace_min_distance, number(spacing.toDouble())))
            Slider(spacing, onValueChange = { spacing = it }, valueRange = 1f..10f, steps = 8)
            Text(stringResource(R.string.map_trace_settings_help))
        }
    }, confirmButton = { TextButton(onClick = { onApply(policy.copy(maximumHorizontalAccuracyM = accuracy.toDouble(), minimumPointDistanceM = spacing.toDouble())) }) { Text(stringResource(R.string.map_trace_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.map_trace_close)) } })
}
private enum class TraceHostDialog { OPTIONS, LIBRARY, SETTINGS, HELP, METHOD, SHARE }
private fun number(value: Double): String = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(value)
