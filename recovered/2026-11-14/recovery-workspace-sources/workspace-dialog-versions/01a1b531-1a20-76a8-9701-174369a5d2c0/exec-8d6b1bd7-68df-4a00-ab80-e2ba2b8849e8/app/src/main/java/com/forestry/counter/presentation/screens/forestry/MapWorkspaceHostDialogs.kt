package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forestry.counter.R
import com.forestry.counter.data.maps.PublicMapsRepository
import com.forestry.counter.domain.maps.MapsFailure
import com.forestry.counter.domain.maps.MapsPlace
import com.forestry.counter.domain.maps.MapsRepository
import com.forestry.counter.domain.maps.MapsServiceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Public address search, started only by the user's search action. No field data is sent. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapWorkspacePlaceSearch(
    onDismiss: () -> Unit,
    onSelect: (MapsPlace) -> Unit,
    repository: MapsRepository? = null,
) {
    val source = remember(repository) { repository ?: PublicMapsRepository() }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var query by rememberSaveable { mutableStateOf("") }
    var requestVersion by remember { mutableIntStateOf(0) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<MapsPlace>>(emptyList()) }
    var failure by remember { mutableStateOf<MapsFailure?>(null) }

    fun cancelSearch() {
        requestVersion++
        searchJob?.cancel()
        searchJob = null
        searching = false
    }

    fun search() {
        val submittedQuery = query.trim()
        if (submittedQuery.length < 3 || searching) return
        cancelSearch()
        val version = requestVersion
        searching = true
        searched = false
        results = emptyList()
        failure = null
        keyboard?.hide()
        searchJob = scope.launch {
            try {
                val found = source.search(submittedQuery)
                if (version == requestVersion) {
                    results = found
                    searched = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (version == requestVersion) {
                    failure = (error as? MapsServiceException)?.failure ?: MapsFailure.NETWORK
                }
            } finally {
                if (version == requestVersion) searching = false
            }
        }
    }

    // A request never survives leaving this sheet or the foreground.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) cancelSearch()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            cancelSearch()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { cancelSearch(); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WorkspaceSheetHeading(stringResource(R.string.gs_workspace_search_title)) {
                cancelSearch()
                onDismiss()
            }
            OutlinedTextField(
                value = query,
                onValueChange = {
                    cancelSearch()
                    query = it.take(160)
                    results = emptyList()
                    searched = false
                    failure = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.gs_workspace_search_label)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
            )
            Button(
                onClick = { search() },
                enabled = query.trim().length >= 3 && !searching,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                if (searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Search, contentDescription = null)
                Text(
                    stringResource(if (searching) R.string.gs_workspace_search_loading else R.string.gs_workspace_search_action),
                    Modifier.padding(start = 10.dp),
                )
            }
            failure?.let { error ->
                Text(
                    stringResource(when (error) {
                        MapsFailure.RATE_LIMIT -> R.string.maps_rate_limit
                        MapsFailure.INVALID_RESPONSE -> R.string.gs_workspace_search_invalid
                        else -> R.string.maps_network_error
                    }),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (searched && results.isEmpty()) {
                Text(stringResource(R.string.gs_workspace_search_empty), style = MaterialTheme.typography.bodyMedium)
            }
            results.forEach { place ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            cancelSearch()
                            keyboard?.hide()
                            onSelect(place)
                        }.heightIn(min = 56.dp).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(place.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Text(
                stringResource(R.string.gs_workspace_search_source),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Entry points into the existing tools; callbacks do not duplicate their state or storage. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapWorkspaceTools(
    onDismiss: () -> Unit,
    onGpsPrecision: () -> Unit,
    onOffline: () -> Unit,
    onEssences: () -> Unit,
    onSavedMeasures: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    canZoomIn: Boolean = true,
    canZoomOut: Boolean = true,
    showEssences: Boolean = true,
    onMeasure: (() -> Unit)? = null,
    onImportLayer: (() -> Unit)? = null,
    onTrace: (() -> Unit)? = null,
) {
    fun open(action: () -> Unit) { onDismiss(); action() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkspaceSheetHeading(stringResource(R.string.gs_workspace_tools_title), onDismiss)
            onImportLayer?.let { action -> WorkspaceTool(Icons.Default.Add, stringResource(R.string.root_workspace_import_layer)) { open(action) } }
            onTrace?.let { action -> WorkspaceTool(Icons.Default.GpsFixed, stringResource(R.string.root_workspace_trace)) { open(action) } }
            WorkspaceTool(Icons.Default.GpsFixed, stringResource(R.string.gs_workspace_tools_gps)) { open(onGpsPrecision) }
            WorkspaceTool(Icons.Default.CloudDownload, stringResource(R.string.gs_workspace_tools_offline)) { open(onOffline) }
            if (showEssences) {
                WorkspaceTool(Icons.Default.Forest, stringResource(R.string.gs_workspace_tools_essences)) { open(onEssences) }
            }
            onMeasure?.let { measure ->
                WorkspaceTool(Icons.Default.Straighten, stringResource(R.string.measure_tool_title)) { open(measure) }
            }
            WorkspaceTool(Icons.Default.Straighten, stringResource(R.string.gs_workspace_tools_saved)) { open(onSavedMeasures) }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { open(onZoomOut) }, enabled = canZoomOut,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Default.Remove, contentDescription = null)
                    Text(stringResource(R.string.map_zoom_out), Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = { open(onZoomIn) }, enabled = canZoomIn,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.map_zoom_in), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun WorkspaceSheetHeading(title: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Close, stringResource(R.string.gs_workspace_close))
        }
    }
}

@Composable
private fun WorkspaceTool(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
