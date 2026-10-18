package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.R
import com.forestry.counter.domain.model.InventaireSession
import com.forestry.counter.domain.model.MartelageClockReading
import com.forestry.counter.domain.model.MartelageStates
import com.forestry.counter.domain.model.SurveyContextDraft
import com.forestry.counter.domain.model.SurveyContextRecord
import com.forestry.counter.domain.model.SurveyPurpose
import com.forestry.counter.domain.model.durationAt
import com.forestry.counter.domain.model.needsClockRecovery
import com.forestry.counter.domain.repository.InventaireSessionRepository
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.domain.repository.SurveyContextRepository
import com.forestry.counter.presentation.components.SurveyContextDialog
import com.forestry.counter.presentation.components.SurveyStartDialog
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Plot-owned field controls. Finish pauses the exact version before its synthesis is reviewed. */
@Composable
fun MartelageSessionControls(
    parcelleId: String,
    placetteId: String,
    sessionRepository: InventaireSessionRepository,
    onOpenSessionSynthesis: ((String, String, String) -> Unit)?,
    modifier: Modifier = Modifier,
    onFeedback: () -> Unit = {},
    identityRepository: IdentityRepository? = null,
    surveyContextRepository: SurveyContextRepository? = null,
) {
    val accountId = identityRepository?.session?.collectAsStateWithLifecycle()?.value?.accountId
    val generation = identityRepository?.sessionGeneration?.collectAsStateWithLifecycle()?.value
    val identityGuard = remember(identityRepository, accountId, generation) {
        accountId?.let { identityRepository?.captureSessionGuard(it)?.getOrNull() }
    }
    val requestGuard = remember(identityRepository, accountId, generation, identityGuard) {
        {
            identityRepository == null ||
                (identityRepository.session.value?.accountId == accountId &&
                    identityRepository.sessionGeneration.value == generation &&
                    (accountId == null || identityGuard?.let(identityRepository::isSessionGuardCurrent) == true))
        }
    }
    // Account changes, including logout/login of the same account, dispose drafts and pending jobs.
    // This UI guard only restricts a request; the repository still enforces canonical rights.
    key(parcelleId, placetteId, accountId, generation) {
        ScopedMartelageSessionControls(
            parcelleId, placetteId, sessionRepository, onOpenSessionSynthesis, modifier,
            onFeedback, surveyContextRepository, requestGuard,
        )
    }
}

private data class SurveyContextEdit(
    val sessionId: String,
    val record: SurveyContextRecord?,
    val reload: Int = 0,
)

@Composable
private fun ScopedMartelageSessionControls(
    parcelleId: String,
    placetteId: String,
    sessionRepository: InventaireSessionRepository,
    onOpenSessionSynthesis: ((String, String, String) -> Unit)?,
    modifier: Modifier,
    onFeedback: () -> Unit,
    surveyContextRepository: SurveyContextRepository?,
    requestGuard: () -> Boolean,
) {
    var failed by remember(parcelleId, placetteId) { mutableStateOf(false) }
    var readRevision by remember(parcelleId, placetteId, sessionRepository) { mutableStateOf(0) }
    var readPending by remember(parcelleId, placetteId, sessionRepository) { mutableStateOf(true) }
    val sessionsFlow = remember(parcelleId, placetteId, sessionRepository, readRevision) {
        val expectedReadRevision = readRevision
        sessionRepository.getByPlacette(placetteId).map<List<InventaireSession>, List<InventaireSession>?> {
            check(requestGuard()) { "SURVEY_UI_SCOPE_CHANGED" }
            it
        }
            .onEach {
                if (expectedReadRevision == readRevision) {
                    failed = false
                    readPending = false
                }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (expectedReadRevision == readRevision) {
                    failed = true
                    readPending = false
                }
                emit(null)
            }
    }
    val storedSessions by sessionsFlow.collectAsStateWithLifecycle(initialValue = null)
    val selection = remember(storedSessions, parcelleId, placetteId) {
        selectEssenceSession(storedSessions.orEmpty(), parcelleId, placetteId)
    }
    val displayed = selection.session
    val active = displayed?.takeIf { it.martelageState in MartelageStates.ACTIVE }
    val latestClosed = displayed?.takeIf { it.martelageState == MartelageStates.CLOSED }
    val scope = rememberCoroutineScope()
    var busy by remember(parcelleId, placetteId) { mutableStateOf(false) }
    var showStartDialog by rememberSaveable(parcelleId, placetteId) { mutableStateOf(false) }
    var introHandled by rememberSaveable(parcelleId, placetteId) { mutableStateOf(false) }
    var finishSessionId by rememberSaveable(parcelleId, placetteId) { mutableStateOf<String?>(null) }
    var checkedFinish by rememberSaveable(parcelleId, placetteId) { mutableStateOf(false) }
    var correctionSessionId by rememberSaveable(parcelleId, placetteId) { mutableStateOf<String?>(null) }
    var reading by remember(parcelleId, placetteId) { mutableStateOf<MartelageClockReading?>(null) }
    var contextRecord by remember { mutableStateOf<SurveyContextRecord?>(null) }
    var contextReadPending by remember { mutableStateOf(false) }
    var contextReadFailed by remember { mutableStateOf(false) }
    var contextReadRevision by remember { mutableStateOf(0) }
    var contextEdit by remember { mutableStateOf<SurveyContextEdit?>(null) }
    var contextError by remember { mutableStateOf<SurveyContextUiError?>(null) }

    suspend fun refreshClock() {
        check(requestGuard()) { "SURVEY_UI_SCOPE_CHANGED" }
        reading = sessionRepository.currentClockReading()
        check(requestGuard()) { "SURVEY_UI_SCOPE_CHANGED" }
        sessionRepository.reconcileForPlacette(parcelleId, placetteId)
    }

    fun perform(action: suspend () -> Unit) {
        if (busy || failed || readPending || selection.inconsistent || !requestGuard()) return
        busy = true
        onFeedback()
        scope.launch {
            try {
                check(requestGuard()) { "SURVEY_UI_SCOPE_CHANGED" }
                action()
                check(requestGuard()) { "SURVEY_UI_SCOPE_CHANGED" }
                reading = sessionRepository.currentClockReading()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            } finally { busy = false }
        }
    }

    fun retryStatus() {
        if (busy || readPending) return
        busy = true
        readPending = true
        onFeedback()
        scope.launch {
            try {
                refreshClock()
                // A caught Flow failure completes that subscription. Recreate it;
                // only its next successful read may clear the failed state.
                readPending = true
                readRevision++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
                readPending = false
            } finally { busy = false }
        }
    }

    LaunchedEffect(parcelleId, placetteId, sessionRepository) {
        try { refreshClock() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    LaunchedEffect(storedSessions, readPending, introHandled) {
        if (storedSessions != null && !readPending && !introHandled) {
            introHandled = true
            showStartDialog = displayed == null && !selection.inconsistent && !failed
        }
    }
    LaunchedEffect(active?.sessionId, active?.martelageState, readRevision) {
        val current = active ?: return@LaunchedEffect
        while (true) {
            try {
                check(requestGuard()) { "SURVEY_UI_SCOPE_CHANGED" }
                val value = sessionRepository.currentClockReading()
                reading = value
                if (current.needsClockRecovery(value)) {
                    sessionRepository.reconcileForPlacette(parcelleId, placetteId)
                    break
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true; break }
            if (current.martelageState != MartelageStates.RUNNING) break
            delay(1_000L)
        }
    }

    LaunchedEffect(displayed?.sessionId, contextReadRevision, surveyContextRepository) {
        contextRecord = null
        contextReadFailed = false
        val id = displayed?.sessionId ?: return@LaunchedEffect
        val repository = surveyContextRepository ?: return@LaunchedEffect
        contextReadPending = true
        try {
            check(requestGuard()) { "SURVEY_UI_SCOPE_CHANGED" }
            val value = repository.getContext(id)
            check(requestGuard()) { "SURVEY_UI_SCOPE_CHANGED" }
            contextRecord = value
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            contextReadFailed = true
        } finally { contextReadPending = false }
    }

    val duration = displayed?.let { session -> reading?.let { session.durationAt(it) } ?: session.activeDurationMs } ?: 0L
    val stateLabel = when (displayed?.martelageState) {
        MartelageStates.RUNNING -> stringResource(R.string.survey_session_running)
        MartelageStates.PAUSED -> stringResource(R.string.survey_session_paused)
        MartelageStates.CLOSED -> stringResource(R.string.survey_session_closed)
        else -> stringResource(R.string.survey_session_ready)
    }
    val actionsEnabled = !busy && !failed && !readPending && !selection.inconsistent && requestGuard()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(when (displayed?.martelageState) {
                        MartelageStates.RUNNING -> Icons.Default.PlayArrow
                        MartelageStates.PAUSED -> Icons.Default.Pause
                        MartelageStates.CLOSED -> Icons.Default.CheckCircle
                        else -> Icons.Default.PlayArrow
                    }, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Column {
                        Text(stateLabel, style = MaterialTheme.typography.titleSmall)
                        if (!contextReadPending && !contextReadFailed) {
                            contextRecord?.draft?.purpose?.let { purpose ->
                                Text(stringResource(when (purpose) {
                                    SurveyPurpose.INVENTORY -> R.string.survey_preparation_inventory
                                    SurveyPurpose.MARKING -> R.string.survey_preparation_marking
                                    SurveyPurpose.COMBINED -> R.string.survey_preparation_combined
                                }), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        displayed?.let {
                            Text(stringResource(R.string.martelage_session_revision, it.revisionNumber),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (displayed != null) {
                    Text(formatMartelageDuration(duration), style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace)
                }
            }
            if (active?.recoveryRequired == true) {
                Text(stringResource(R.string.martelage_session_clock_recovery), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (failed || selection.inconsistent) {
                Text(stringResource(R.string.martelage_session_action_failed), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                TextButton(onClick = ::retryStatus, enabled = !busy && !readPending) {
                    Text(stringResource(R.string.martelage_session_retry))
                }
            }
            when {
                readPending || storedSessions == null -> Text(stringResource(R.string.martelage_session_loading), style = MaterialTheme.typography.bodySmall)
                selection.inconsistent -> Unit
                active != null -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f), enabled = actionsEnabled,
                            onClick = { perform {
                                if (active.martelageState == MartelageStates.RUNNING) sessionRepository.pauseMartelage(active.sessionId)
                                else sessionRepository.resumeMartelage(active.sessionId)
                            } },
                        ) {
                            Text(stringResource(if (active.martelageState == MartelageStates.RUNNING)
                                R.string.martelage_session_pause else R.string.martelage_session_resume))
                        }
                        Button(modifier = Modifier.weight(1f), enabled = actionsEnabled && onOpenSessionSynthesis != null,
                            onClick = { onFeedback(); checkedFinish = false; finishSessionId = active.sessionId }) {
                            Text(stringResource(R.string.martelage_session_finish))
                        }
                    }
                    if (active.martelageState == MartelageStates.PAUSED && onOpenSessionSynthesis != null) {
                        TextButton(onClick = { onFeedback(); onOpenSessionSynthesis(parcelleId, placetteId, active.sessionId) }, enabled = actionsEnabled) {
                            Text(stringResource(R.string.martelage_session_open_synthesis))
                        }
                    }
                }
                latestClosed != null -> {
                    Text(remember(latestClosed.dateDebut) {
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(latestClosed.dateDebut))
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onOpenSessionSynthesis != null) {
                            Button(modifier = Modifier.weight(1f), enabled = actionsEnabled,
                                onClick = { onFeedback(); onOpenSessionSynthesis(parcelleId, placetteId, latestClosed.sessionId) }) {
                                Text(stringResource(R.string.martelage_synthesis_title))
                            }
                        }
                        OutlinedButton(modifier = Modifier.weight(1f), enabled = actionsEnabled,
                            onClick = { onFeedback(); correctionSessionId = latestClosed.sessionId }) {
                            Text(stringResource(R.string.martelage_session_correction))
                        }
                    }
                    TextButton(onClick = { onFeedback(); showStartDialog = true }, enabled = actionsEnabled, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.martelage_session_new_campaign))
                    }
                }
                else -> Button(onClick = { onFeedback(); showStartDialog = true }, enabled = actionsEnabled,
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.survey_session_start)) }
            }
            if (displayed != null && surveyContextRepository != null) {
                when {
                    contextReadPending -> Text(stringResource(R.string.survey_context_loading), style = MaterialTheme.typography.bodySmall)
                    contextReadFailed -> {
                        Text(stringResource(R.string.survey_context_read_failed), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { contextReadRevision++ }, enabled = actionsEnabled) {
                            Text(stringResource(R.string.survey_context_retry))
                        }
                    }
                    else -> TextButton(onClick = {
                        if (requestGuard()) {
                            contextError = null
                            contextEdit = SurveyContextEdit(displayed.sessionId, contextRecord)
                        }
                    }, enabled = actionsEnabled, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.survey_context_open))
                    }
                }
            }
        }
    }

    if (showStartDialog && active == null) {
        SurveyStartDialog(
            busy = busy || readPending,
            failed = failed || selection.inconsistent,
            onDismiss = { if (!busy && !readPending) showStartDialog = false },
            onStart = { draft -> perform {
                sessionRepository.startForPlacetteWithContext(parcelleId, placetteId, draft, requestGuard)
                showStartDialog = false
            } },
        )
    }
    val edit = contextEdit
    if (edit != null && displayed?.sessionId == edit.sessionId && surveyContextRepository != null) {
        val readOnly = active?.sessionId != edit.sessionId || edit.record?.isSealed == true
        SurveyContextDialog(
            stateKey = "${edit.sessionId}:${edit.record?.revision}:${edit.reload}",
            initialDraft = edit.record?.draft ?: SurveyContextDraft(),
            readOnly = readOnly,
            busy = busy,
            error = contextError,
            onDismiss = { if (!busy) contextEdit = null },
            onSave = { draft ->
                if (!busy && !readOnly && requestGuard()) {
                    busy = true
                    scope.launch {
                        try {
                            val saved = surveyContextRepository.saveContextForScope(
                                edit.sessionId, draft, edit.record?.revision, requestGuard,
                            )
                            check(requestGuard()) { "SURVEY_UI_SCOPE_CHANGED" }
                            contextRecord = saved
                            contextEdit = null
                            contextError = null
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) {
                            contextError = when {
                                error.message == "SURVEY_CONTEXT_REVISION_CONFLICT" -> SurveyContextUiError.CONFLICT
                                !requestGuard() || error.message == "SURVEY_CONTEXT_SESSION_NOT_EDITABLE" -> SurveyContextUiError.UNAVAILABLE
                                else -> SurveyContextUiError.SAVE_FAILED
                            }
                        } finally { busy = false }
                    }
                }
            },
            onReload = {
                if (!busy && requestGuard()) {
                    busy = true
                    scope.launch {
                        try {
                            val refreshed = surveyContextRepository.getContext(edit.sessionId)
                            check(requestGuard()) { "SURVEY_UI_SCOPE_CHANGED" }
                            contextRecord = refreshed
                            contextEdit = edit.copy(record = refreshed, reload = edit.reload + 1)
                            contextError = null
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { contextError = SurveyContextUiError.UNAVAILABLE }
                        finally { busy = false }
                    }
                }
            },
        )
    }
    val finishId = finishSessionId
    if (finishId != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) finishSessionId = null },
            title = { Text(stringResource(R.string.martelage_session_finish_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.martelage_session_finish_description))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checkedFinish, onCheckedChange = { checkedFinish = it }, enabled = !busy)
                        Text(stringResource(R.string.martelage_session_finish_check), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = checkedFinish && actionsEnabled && onOpenSessionSynthesis != null && active?.sessionId == finishId,
                    onClick = { perform {
                        sessionRepository.pauseMartelage(finishId)
                        finishSessionId = null
                        checkedFinish = false
                        onOpenSessionSynthesis?.invoke(parcelleId, placetteId, finishId)
                    } }) { Text(stringResource(R.string.martelage_session_finish_review)) }
            },
            dismissButton = { TextButton(onClick = { finishSessionId = null }, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
        )
    }
    val correctionId = correctionSessionId
    if (correctionId != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) correctionSessionId = null },
            title = { Text(stringResource(R.string.martelage_session_correction_title)) },
            text = { Text(stringResource(R.string.martelage_session_correction_description)) },
            confirmButton = { TextButton(enabled = actionsEnabled && latestClosed?.sessionId == correctionId, onClick = { perform {
                sessionRepository.createCorrection(correctionId)
                correctionSessionId = null
            } }) { Text(stringResource(R.string.martelage_session_correction_create)) } },
            dismissButton = { TextButton(onClick = { correctionSessionId = null }, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

fun formatMartelageDuration(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0L) / 1_000L
    return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3_600L, (seconds / 60L) % 60L, seconds % 60L)
}
