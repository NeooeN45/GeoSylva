package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.R
import com.forestry.counter.data.sync.hierarchy.BusinessMutationGuard
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.model.InventaireSession
import com.forestry.counter.domain.model.MartelageStates
import com.forestry.counter.domain.model.ObservationQualificationDraft
import com.forestry.counter.domain.model.ObservationQualificationStatus
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.domain.repository.InventaireSessionRepository
import com.forestry.counter.domain.repository.ObservationQualificationRepository
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.presentation.components.ObservationQualificationDialog
import com.forestry.counter.presentation.components.ObservationQualificationDialogError
import java.text.NumberFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Les observations d'une seule campagne sont précisées, jamais toutes les tiges d'une classe. */
@Composable
fun ObservationQualificationEntry(
    parcelleId: String,
    placetteId: String,
    essenceCode: String,
    essenceName: String,
    session: InventaireSession?,
    observations: List<Tige>,
    available: Boolean,
    identityRepository: IdentityRepository,
    businessReadAccess: BusinessReadAccess,
    tigeRepository: TigeRepository,
    sessionRepository: InventaireSessionRepository,
    qualificationRepository: ObservationQualificationRepository,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accountId = identityRepository.session.collectAsStateWithLifecycle().value?.accountId
    val generation = identityRepository.sessionGeneration.collectAsStateWithLifecycle().value
    // Logout/reconnexion, y compris du même compte, disposent les brouillons et leurs coroutines.
    key(parcelleId, placetteId, essenceCode, session?.sessionId, accountId, generation) {
        ScopedObservationQualificationEntry(
            parcelleId, placetteId, essenceCode, essenceName, session, observations, available,
            identityRepository, businessReadAccess, tigeRepository, sessionRepository,
            qualificationRepository, onSaved, modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopedObservationQualificationEntry(
    parcelleId: String,
    placetteId: String,
    essenceCode: String,
    essenceName: String,
    session: InventaireSession?,
    observations: List<Tige>,
    available: Boolean,
    identityRepository: IdentityRepository,
    businessReadAccess: BusinessReadAccess,
    tigeRepository: TigeRepository,
    sessionRepository: InventaireSessionRepository,
    qualificationRepository: ObservationQualificationRepository,
    onSaved: () -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var showList by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }
    var openFailed by remember { mutableStateOf(false) }
    var controller by remember { mutableStateOf<ObservationQualificationEditorController?>(null) }
    var editorGuard by remember { mutableStateOf<(() -> Boolean)?>(null) }
    DisposableEffect(controller) {
        val current = controller
        onDispose { current?.invalidate() }
    }
    val editorState = controller?.state?.collectAsStateWithLifecycle()?.value
    val selectedSessionId = session?.sessionId
    val scopedObservations = observations.filter {
        selectedSessionId != null && it.deletedAt == null && it.parcelleId == parcelleId &&
            it.placetteId == placetteId && it.sessionId == selectedSessionId &&
            it.essenceCode.equals(essenceCode, ignoreCase = true)
    }.sortedWith(compareBy<Tige> { it.diamCm }.thenBy { it.timestamp }.thenBy { it.id })
    val basis = editorState?.basis
    val editorStillAccessible = basis == null || (editorGuard?.invoke() == true &&
        scopedObservations.any { it.id == basis.tigeId })
    LaunchedEffect(editorStillAccessible) {
        if (!editorStillAccessible) {
            controller?.invalidate()
            controller = null
            editorGuard = null
            showList = false
        }
    }

    fun openObservation(tree: Tige) {
        val sessionId = selectedSessionId ?: return
        if (opening || !available) return
        // Capturer la demande avant le premier await, plutôt qu'adopter le compte au moment du save.
        val identityGuard = BusinessMutationGuard(identityRepository)
            .capture(identityRepository.session.value?.accountId)
        opening = true
        openFailed = false
        scope.launch {
            try {
                check(identityGuard()) { "OBSERVATION_QUALIFICATION_UI_SCOPE_CHANGED" }
                val access = businessReadAccess.capture()
                val requestGuard = {
                    identityGuard() && businessReadAccess.isCurrent(access) &&
                        businessReadAccess.canReadParcelle(parcelleId, access)
                }
                check(requestGuard()) { "OBSERVATION_QUALIFICATION_UI_SCOPE_CHANGED" }
                val next = ObservationQualificationEditorController(
                    tigeRepository, qualificationRepository, sessionRepository,
                    parcelleId, placetteId, sessionId, tree.essenceCode, requestGuard,
                )
                if (next.open(tree) && requestGuard()) {
                    editorGuard = requestGuard
                    controller = next
                    showList = false
                } else {
                    next.invalidate()
                    openFailed = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                openFailed = true
            } finally {
                opening = false
            }
        }
    }

    OutlinedButton(
        onClick = { openFailed = false; showList = true },
        enabled = available && scopedObservations.isNotEmpty(),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("observation_qualification_open"),
    ) {
        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.observation_qualification_open_action), Modifier.padding(start = 8.dp))
    }

    if (showList) {
        ModalBottomSheet(
            onDismissRequest = { if (!opening) showList = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.observation_qualification_list_title, essenceName),
                    style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.observation_qualification_list_description),
                    style = MaterialTheme.typography.bodyMedium)
                if (session?.martelageState == MartelageStates.CLOSED) {
                    Text(stringResource(R.string.observation_qualification_list_closed),
                        color = MaterialTheme.colorScheme.primary)
                }
                if (opening) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Text(stringResource(R.string.observation_qualification_loading))
                    }
                }
                if (openFailed) {
                    Text(stringResource(R.string.observation_qualification_load_failed),
                        color = MaterialTheme.colorScheme.error)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                        .testTag("observation_qualification_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (scopedObservations.isEmpty()) item {
                        Text(stringResource(R.string.observation_qualification_list_empty))
                    }
                    items(scopedObservations, key = { it.id }) { tree ->
                        Card(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .testTag("observation_qualification_item_${tree.id}")
                                .clickable(enabled = available && !opening, role = Role.Button) { openObservation(tree) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    if (tree.numero != null) stringResource(R.string.observation_qualification_list_number, tree.numero)
                                    else stringResource(R.string.observation_qualification_list_identity, tree.id),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                                val format = NumberFormat.getNumberInstance()
                                Text(stringResource(R.string.observation_qualification_list_diameter, format.format(tree.diamCm)))
                                Text(tree.hauteurM?.let {
                                    stringResource(R.string.observation_qualification_list_height, format.format(it))
                                } ?: stringResource(R.string.observation_qualification_list_height_unknown))
                                Text(stringResource(R.string.observation_qualification_list_version, tree.version),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                TextButton(
                    onClick = { showList = false }, enabled = !opening,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.observation_qualification_list_close)) }
            }
        }
    }

    val activeController = controller
    if (activeController != null && basis != null && editorState != null && editorStillAccessible) {
        ObservationQualificationDialog(
            stateKey = listOf(
                identityRepository.session.value?.accountId?.let { "account:$it" } ?: "anonymous",
                identityRepository.sessionGeneration.value.toString(),
                parcelleId, placetteId, essenceCode, basis.sessionId.orEmpty(),
                basis.tigeId, basis.fingerprint(), editorState.reloadCount.toString(),
                editorState.read?.record?.revision?.let { "revision:$it" } ?: "no-declaration",
            ).joinToString(separator = "") { "${it.length}:$it" },
            basis = basis,
            initialDraft = editorState.read?.record?.payload?.draft ?: ObservationQualificationDraft(),
            stale = editorState.read?.status == ObservationQualificationStatus.STALE,
            readOnly = editorState.readOnly || session?.martelageState == MartelageStates.CLOSED,
            busy = editorState.isLoading || editorState.isSaving,
            error = when (editorState.error) {
                ObservationQualificationEditorError.CONFLICT -> ObservationQualificationDialogError.CONFLICT
                ObservationQualificationEditorError.UNAVAILABLE -> ObservationQualificationDialogError.UNAVAILABLE
                ObservationQualificationEditorError.SAVE_FAILED -> ObservationQualificationDialogError.SAVE_FAILED
                null -> null
            },
            onDismiss = {
                activeController.invalidate()
                controller = null
                editorGuard = null
                showList = true
            },
            onSave = { draft ->
                scope.launch {
                    if (activeController.save(draft) && editorGuard?.invoke() == true && controller === activeController) {
                        controller = null
                        editorGuard = null
                        showList = true
                        onSaved()
                    }
                }
            },
            onReload = { scope.launch { activeController.reload() } },
        )
    }
}
