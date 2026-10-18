package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.R
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.location.ParcelGeometry
import com.forestry.counter.domain.repository.ForetRepository
import java.text.NumberFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Canonical forest dossiers; historical groups stay behind their separate navigation action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForestListScreen(
    foretRepository: ForetRepository,
    businessReadAccess: BusinessReadAccess,
    onNavigateBack: () -> Unit,
    onNavigateToForest: (String) -> Unit,
    onCreateForest: () -> Unit,
    onLegacyGroups: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var retry by remember { mutableStateOf(0) }
    val states: Flow<ForestListState> = remember(foretRepository, businessReadAccess, retry) {
        val source: Flow<ForestListState> = foretRepository.getAll().map { forests ->
            ForestListState.Ready(forests.filter { it.deletedAt == null }.map { forest ->
                ForestListEntry(
                    id = forest.foretId,
                    name = forest.nom,
                    areaHa = forest.shape?.let { shape ->
                        try { ParcelGeometry.fromWkt(shape).areaSquareMetres / 10_000.0 }
                        catch (_: IllegalArgumentException) { null }
                    },
                    source = forest.sourceForestDataset?.takeIf(String::isNotBlank)
                        ?: forest.source?.takeIf(String::isNotBlank),
                )
            }.sortedBy { it.name.lowercase() })
        }.flowOn(Dispatchers.Default)
        businessReadAccess.observe(source, ForestListState.Loading) { state, access ->
            if (state is ForestListState.Ready) state.copy(
                entries = state.entries.filter { businessReadAccess.canReadForest(it.id, access) },
                access = access,
            ) else state
        }.catch { failure ->
            if (failure is CancellationException) throw failure
            emit(ForestListState.Failed)
        }
    }
    val capturedState by states.collectAsStateWithLifecycle(initialValue = ForestListState.Loading)
    val state = when (val captured = capturedState) {
        is ForestListState.Ready -> if (captured.access?.let(businessReadAccess::isCurrent) == true)
            captured else ForestListState.Loading
        else -> captured
    }
    val ready = state as? ForestListState.Ready
    val scope = rememberCoroutineScope()
    var actionPending by remember { mutableStateOf(false) }
    var actionFailed by remember { mutableStateOf(false) }

    fun withCapturedAccess(forestId: String? = null, action: () -> Unit) {
        val access = ready?.access ?: return
        if (actionPending || !businessReadAccess.isCurrent(access)) return
        actionPending = true
        actionFailed = false
        scope.launch {
            try {
                if (!businessReadAccess.revalidate(access) || !businessReadAccess.isCurrent(access) ||
                    (forestId != null && !businessReadAccess.canReadForest(forestId, access))) return@launch
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                actionFailed = true
            } finally {
                actionPending = false
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.forest_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            if (ready != null && !actionPending) {
                ExtendedFloatingActionButton(
                    onClick = { withCapturedAccess(action = onCreateForest) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.forest_list_add)) },
                    modifier = Modifier.padding(bottom = 80.dp),
                )
            }
        },
    ) { padding ->
        when (state) {
            ForestListState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.forest_list_loading))
                }
            }
            ForestListState.Failed -> Box(
                Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center,
            ) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.forest_list_load_error))
                    TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                }
            }
            is ForestListState.Ready -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 180.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    TextButton(onClick = { withCapturedAccess(action = onLegacyGroups) }, enabled = !actionPending) {
                        Text(stringResource(R.string.forest_list_legacy))
                    }
                }
                if (actionFailed) item { Text(stringResource(R.string.forest_list_action_error)) }
                if (state.entries.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.forest_list_empty), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.forest_list_empty_help))
                    }
                }
                items(state.entries, key = { it.id }) { forest ->
                    Card(
                        onClick = { withCapturedAccess(forest.id) { onNavigateToForest(forest.id) } },
                        enabled = !actionPending,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(forest.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                forest.areaHa?.let { hectares ->
                                    val number = NumberFormat.getNumberInstance().apply {
                                        minimumFractionDigits = 0
                                        maximumFractionDigits = 2
                                    }.format(hectares)
                                    stringResource(R.string.forest_list_area, number)
                                } ?: stringResource(R.string.forest_list_area_unknown),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            forest.source?.let { source ->
                                Text(stringResource(R.string.forest_list_source, source),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class ForestListEntry(val id: String, val name: String, val areaHa: Double?, val source: String?)

private sealed interface ForestListState {
    data object Loading : ForestListState
    data object Failed : ForestListState
    data class Ready(
        val entries: List<ForestListEntry>,
        val access: BusinessReadAccess.AccessSnapshot? = null,
    ) : ForestListState
}
