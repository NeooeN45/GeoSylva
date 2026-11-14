package com.forestry.counter.presentation.screens.field

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forestry.counter.R
import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.maps.MapsPlace
import com.forestry.counter.domain.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal data class FieldMapContextConstraints(val allowWorksites: Boolean, val allowParcels: Boolean) {
    fun allows(target: FieldTarget): Boolean = when (target.kind) {
        FieldTargetKind.WORKSITE -> allowWorksites
        FieldTargetKind.PARCEL -> allowParcels
        else -> false
    }
}

internal data class FieldMapContextState(
    val site: SavedWorksite? = null, val choices: List<FieldTargetChoice> = emptyList(),
    val unavailableReferences: Int = 0, val busy: Boolean = true,
    val error: FieldError? = null, val accessLost: Boolean = false,
    val constraints: FieldMapContextConstraints = FieldMapContextConstraints(true, true),
)

/** Coordinates existing authorized reads; selecting a context never writes an entry. */
internal class FieldMapContextViewModel(
    private val repository: FieldRepository, private val siteId: String?,
    allowWorksites: Boolean = true, allowParcels: Boolean = true,
) : ViewModel() {
    private var constraints = FieldMapContextConstraints(allowWorksites, allowParcels)
    private val mutable = MutableStateFlow(FieldMapContextState(constraints = constraints))
    val state = mutable.asStateFlow()
    private var access: FieldAccess? = null
    private var requestToken = Any()
    private var requestJob: Job? = null
    private var needsReload = false
    init {
        reload()
        viewModelScope.launch { repository.accessChanges().collect {
            access?.let { if (!repository.isCurrent(it)) revoke() }
        } }
    }
    private fun invalidateRequest() {
        requestToken = Any()
        requestJob?.cancel()
        requestJob = null
    }
    private fun current(token: Any) = requestToken === token && !mutable.value.accessLost
    private fun revoke() {
        invalidateRequest()
        mutable.value = FieldMapContextState(busy = false, accessLost = true,
            error = FieldError.ACCESS_CHANGED, constraints = constraints)
    }
    fun updateConstraints(updated: FieldMapContextConstraints) {
        if (updated == constraints && !needsReload) return
        constraints = updated
        needsReload = false
        if (mutable.value.accessLost) {
            mutable.value = mutable.value.copy(constraints = updated)
            return
        }
        request(clearChoices = true)
    }
    private suspend fun load(token: Any, requested: FieldMapContextConstraints): FieldMapContextState? {
        val permit = access ?: repository.captureAccess().also { if (current(token)) access = it }
        if (!current(token)) return null
        val sites = repository.sites(permit)
        if (!current(token)) return null
        val parcels = repository.parcelChoices(permit)
        if (!current(token)) return null
        val site = siteId?.let { id -> sites.firstOrNull { it.id == id } ?: throw FieldException(FieldError.UNAVAILABLE) }
        val choices = if (site != null) {
            val readable = parcels.mapNotNull { parcel -> parcel.localParcelId?.let { it to parcel } }.toMap()
            site.form.parcels.mapNotNull { linked ->
                linked.localParcelId?.let { id -> readable[id]?.let { FieldTargetChoice(FieldTarget(FieldTargetKind.PARCEL, id), it.label) } }
            }.distinctBy { it.target }
        } else {
            val result = mutableListOf<FieldTargetChoice>()
            if (requested.allowWorksites) for (candidate in sites) {
                if (!current(token)) return null
                val target = FieldTarget(FieldTargetKind.WORKSITE, candidate.id)
                if (!candidate.archived && repository.writable(target, permit)) result += FieldTargetChoice(target, candidate.form.name)
            }
            if (requested.allowParcels) for (parcel in parcels) {
                if (!current(token)) return null
                val id = parcel.localParcelId ?: continue
                val target = FieldTarget(FieldTargetKind.PARCEL, id)
                if (repository.writable(target, permit)) result += FieldTargetChoice(target, parcel.label)
            }
            result.distinctBy { it.target }
        }
        if (!current(token)) return null
        if (!repository.isCurrent(permit)) { revoke(); return null }
        return FieldMapContextState(site = site, choices = choices, constraints = requested,
            unavailableReferences = site?.form?.parcels?.count { linked -> choices.none { it.target.id == linked.localParcelId } } ?: 0)
    }
    fun reload() {
        if (!mutable.value.accessLost) { needsReload = false; request() }
    }
    fun deactivate() {
        invalidateRequest()
        needsReload = true
        if (!mutable.value.accessLost) mutable.value = FieldMapContextState(constraints = constraints)
    }
    fun select(target: FieldTarget, expected: FieldMapContextConstraints = constraints, onSelected: (FieldTarget) -> Unit) {
        if (mutable.value.busy || mutable.value.accessLost || expected != constraints || !constraints.allows(target)) return
        request(target, onSelected)
    }
    private fun request(target: FieldTarget? = null, onSelected: ((FieldTarget) -> Unit)? = null, clearChoices: Boolean = false) {
        invalidateRequest()
        val token = requestToken
        val requested = constraints
        mutable.value = if (clearChoices) FieldMapContextState(constraints = requested)
            else mutable.value.copy(busy = true, error = null)
        requestJob = viewModelScope.launch {
            try {
                val loaded = load(token, requested) ?: return@launch
                currentCoroutineContext().ensureActive()
                if (!current(token)) return@launch
                mutable.value = loaded
                if (target != null) {
                    if (!requested.allows(target) || loaded.choices.none { it.target == target }) throw FieldException(FieldError.UNAVAILABLE)
                    onSelected?.invoke(target)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (!current(token)) return@launch
                if (access?.let { !repository.isCurrent(it) } == true) revoke()
                else mutable.value = FieldMapContextState(busy = false, constraints = requested,
                    error = (e as? FieldException)?.code ?: FieldError.IO)
            } finally {
                if (current(token)) mutable.value = mutable.value.copy(busy = false)
            }
        }
    }
    override fun onCleared() { invalidateRequest(); super.onCleared() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun WorksiteMapScreen(repository: FieldRepository, siteId: String, onOpenParcel: (String) -> Unit, onBack: () -> Unit) {
    FieldMapContextScreen(repository, siteId, null, true, true, onSelected = { onOpenParcel(it.id) }, onBack = onBack)
}

@Composable fun MapAccessObservationScreen(repository: FieldRepository, place: MapsPlace,
    allowWorksites: Boolean, allowParcels: Boolean, onSelected: (FieldTarget) -> Unit, onBack: () -> Unit,
) {
    FieldMapContextScreen(repository, null, place, allowWorksites, allowParcels, onSelected, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun FieldMapContextScreen(repository: FieldRepository, siteId: String?, place: MapsPlace?,
    allowWorksites: Boolean, allowParcels: Boolean, onSelected: (FieldTarget) -> Unit, onBack: () -> Unit,
) {
    val factory = remember(repository, siteId, place, allowWorksites, allowParcels) { object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FieldMapContextViewModel(repository, siteId, allowWorksites, allowParcels) as T
    } }
    val vm: FieldMapContextViewModel = viewModel(key = "map-field-context-${siteId ?: place.toString()}", factory = factory)
    val requested = FieldMapContextConstraints(allowWorksites, allowParcels)
    val observed by vm.state.collectAsStateWithLifecycle()
    // Hide old choices in this composition, before SideEffect updates/cancels the shared VM.
    val state = if (observed.constraints == requested) observed else FieldMapContextState(constraints = requested)
    val selectedCallback = rememberUpdatedState(onSelected)
    SideEffect { vm.updateConstraints(requested) }
    DisposableEffect(vm) { onDispose { vm.deactivate() } }
    BackHandler(onBack = onBack)
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(if (siteId != null) R.string.retained_worksite_map else R.string.retained_access_observation)) },
        navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.field_leave)) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("field_map_context"), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { error -> item { FieldErrorCard(error) } }
            if (!state.accessLost) {
                state.site?.let { site -> item { Text(site.form.name, style = MaterialTheme.typography.headlineSmall) } }
                if (place != null) item {
                    OutlinedCard { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(place.label, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.retained_maps_coordinates, place.point.longitude.toString(), place.point.latitude.toString()))
                        Text(stringResource(R.string.retained_access_context_help))
                    } }
                }
                item { Text(stringResource(if (siteId != null) R.string.retained_choose_parcel_map else R.string.retained_choose_access_target)) }
                if (!state.busy && state.error == null && state.choices.isEmpty()) item {
                    Text(stringResource(if (siteId != null) R.string.retained_no_local_parcel else R.string.retained_no_access_target))
                }
                if (state.unavailableReferences > 0) item { Text(stringResource(R.string.retained_unmapped_references, state.unavailableReferences)) }
                items(state.choices, key = { "${it.target.kind}-${it.target.id}" }) { choice ->
                    RetainedActionRow(Icons.Default.Map, choice.label,
                        if (siteId == null) stringResource(if (choice.target.kind == FieldTargetKind.WORKSITE) R.string.retained_dossier else R.string.retained_parcel_context) else null,
                        enabled = !state.busy, modifier = Modifier.testTag("field_map_target_${choice.target.id}")) {
                        vm.select(choice.target, requested) { selected -> selectedCallback.value(selected) }
                    }
                }
                if (state.error != null) item { TextButton(vm::reload, enabled = !state.busy) { Text(stringResource(R.string.field_retry)) } }
            }
        }
    }
}
