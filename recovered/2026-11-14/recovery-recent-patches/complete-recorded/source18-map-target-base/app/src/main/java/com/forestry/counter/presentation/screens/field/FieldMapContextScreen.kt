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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal data class FieldMapContextState(
    val site: SavedWorksite? = null, val choices: List<FieldTargetChoice> = emptyList(),
    val unavailableReferences: Int = 0, val busy: Boolean = true,
    val error: FieldError? = null, val accessLost: Boolean = false,
)

/** Coordinates existing authorized reads; selecting a context never writes an entry. */
internal class FieldMapContextViewModel(
    private val repository: FieldRepository, private val siteId: String?,
    private val allowWorksites: Boolean = true, private val allowParcels: Boolean = true,
) : ViewModel() {
    private val mutable = MutableStateFlow(FieldMapContextState())
    val state = mutable.asStateFlow()
    private var access: FieldAccess? = null
    init {
        reload()
        viewModelScope.launch { repository.accessChanges().collect {
            access?.let { if (!repository.isCurrent(it)) revoke() }
        } }
    }
    private fun revoke() { mutable.value = FieldMapContextState(busy = false, accessLost = true, error = FieldError.ACCESS_CHANGED) }
    private suspend fun load() {
        val permit = access ?: repository.captureAccess().also { access = it }
        val sites = repository.sites(permit)
        val parcels = repository.parcelChoices(permit)
        val site = siteId?.let { id -> sites.firstOrNull { it.id == id } ?: throw FieldException(FieldError.UNAVAILABLE) }
        val choices = if (site != null) {
            val readable = parcels.mapNotNull { parcel -> parcel.localParcelId?.let { it to parcel } }.toMap()
            site.form.parcels.mapNotNull { linked ->
                linked.localParcelId?.let { id -> readable[id]?.let { FieldTargetChoice(FieldTarget(FieldTargetKind.PARCEL, id), it.label) } }
            }.distinctBy { it.target }
        } else {
            val result = mutableListOf<FieldTargetChoice>()
            if (allowWorksites) for (candidate in sites) {
                val target = FieldTarget(FieldTargetKind.WORKSITE, candidate.id)
                if (!candidate.archived && repository.writable(target, permit)) result += FieldTargetChoice(target, candidate.form.name)
            }
            if (allowParcels) for (parcel in parcels) {
                val id = parcel.localParcelId ?: continue
                val target = FieldTarget(FieldTargetKind.PARCEL, id)
                if (repository.writable(target, permit)) result += FieldTargetChoice(target, parcel.label)
            }
            result.distinctBy { it.target }
        }
        if (!repository.isCurrent(permit)) { revoke(); return }
        mutable.value = FieldMapContextState(site = site, choices = choices,
            unavailableReferences = site?.form?.parcels?.count { linked -> choices.none { it.target.id == linked.localParcelId } } ?: 0)
    }
    fun reload() {
        if (mutable.value.accessLost) return
        action { load() }
    }
    fun select(target: FieldTarget, onSelected: (FieldTarget) -> Unit) {
        if (mutable.value.busy || mutable.value.accessLost) return
        action {
            load() // Recheck current links and access immediately before navigation.
            if (mutable.value.accessLost) return@action
            if (mutable.value.choices.none { it.target == target }) throw FieldException(FieldError.UNAVAILABLE)
            onSelected(target)
        }
    }
    private fun action(block: suspend () -> Unit) {
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (access?.let { !repository.isCurrent(it) } == true) revoke()
                else mutable.value = FieldMapContextState(busy = false, error = (e as? FieldException)?.code ?: FieldError.IO)
            } finally { if (!mutable.value.accessLost) mutable.value = mutable.value.copy(busy = false) }
        }
    }
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
    val state by vm.state.collectAsStateWithLifecycle()
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
                        vm.select(choice.target, onSelected)
                    }
                }
                if (state.error != null) item { TextButton(vm::reload, enabled = !state.busy) { Text(stringResource(R.string.field_retry)) } }
            }
        }
    }
}
