package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiNature
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forestry.counter.R
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.repository.ForetRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import com.forestry.counter.domain.repository.TigeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticScopeScreen(
    foretRepository: ForetRepository,
    parcelleRepository: ParcelleRepository,
    placetteRepository: PlacetteRepository,
    tigeRepository: TigeRepository,
    readAccess: BusinessReadAccess,
    onIbp: (parcelleId: String, placetteId: String) -> Unit,
    onStation: (parcelleId: String) -> Unit,
    onRipisylve: (parcelleId: String) -> Unit,
    onBack: () -> Unit,
) {
    val factory = remember(foretRepository, parcelleRepository, placetteRepository, tigeRepository, readAccess) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DiagnosticScopeViewModel(
                foretRepository, parcelleRepository, placetteRepository, tigeRepository, readAccess,
            ) as T
        }
    }
    val model: DiagnosticScopeViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var opening by remember { mutableStateOf(false) }
    fun open(kind: ScopedDiagnosticKind) {
        if (opening) return
        val displayed = state
        opening = true
        scope.launch {
            try {
                val target = model.resolveTarget(kind, displayed)
                if (target == null) snackbar.showSnackbar(context.getString(R.string.diag_scope_changed))
                else when (kind) {
                    ScopedDiagnosticKind.IBP -> onIbp(target.parcelId, checkNotNull(target.plotId))
                    ScopedDiagnosticKind.STATION -> onStation(target.parcelId)
                    ScopedDiagnosticKind.RIPISYLVE -> onRipisylve(target.parcelId)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { snackbar.showSnackbar(context.getString(R.string.diag_scope_changed)) }
            finally { opening = false }
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.diag_scope_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
        }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.diag_scope_intro), style = MaterialTheme.typography.bodyLarge)
            if (!state.loaded) Text(stringResource(R.string.martelage_session_loading))
            else {
                ScopeDropdown(
                    label = stringResource(R.string.diag_scope_forest),
                    value = state.selectedForest?.nom ?: stringResource(R.string.diag_scope_all_forests),
                    options = listOf(null to stringResource(R.string.diag_scope_all_forests)) + state.forests.map { it.foretId to it.nom },
                    enabled = !opening, onSelect = model::chooseForest,
                )
                ScopeDropdown(
                    label = stringResource(R.string.diag_scope_parcel),
                    value = state.selectedParcel?.name.orEmpty(),
                    options = state.parcels.map { it.id to it.name },
                    enabled = !opening && state.parcels.isNotEmpty(), onSelect = { it?.let(model::chooseParcel) },
                )
                if (state.parcels.isEmpty()) Text(stringResource(R.string.diag_scope_no_parcels),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ScopeDropdown(
                    label = stringResource(R.string.diag_scope_plot),
                    value = state.selectedPlot?.name?.takeIf { it.isNotBlank() }
                        ?: if (state.selectedPlot != null) stringResource(R.string.placette_essences_title)
                        else stringResource(R.string.diag_scope_choose_plot),
                    options = listOf(null to stringResource(R.string.diag_scope_choose_plot)) + state.plots.map {
                        it.id to (it.name?.takeIf(String::isNotBlank) ?: context.getString(R.string.placette_essences_title))
                    },
                    enabled = !opening && state.selectedParcel != null && state.plots.isNotEmpty(), onSelect = model::choosePlot,
                )
                if (state.selectedParcel != null) Text(
                    stringResource(if (state.hasObservations) R.string.diag_scope_observations_present else R.string.diag_scope_observations_absent),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DiagnosticChoiceCard(
                    title = stringResource(R.string.diag_scope_ibp), description = stringResource(R.string.diag_scope_ibp_description),
                    icon = Icons.Default.EmojiNature, enabled = !opening && state.selectedParcel != null && state.selectedPlot != null,
                    onClick = { open(ScopedDiagnosticKind.IBP) },
                )
                DiagnosticChoiceCard(
                    title = stringResource(R.string.diag_scope_station), description = stringResource(R.string.diag_scope_parcel_method),
                    icon = Icons.Default.Science, enabled = !opening && state.selectedParcel != null,
                    onClick = { open(ScopedDiagnosticKind.STATION) },
                )
                DiagnosticChoiceCard(
                    title = stringResource(R.string.diag_scope_ripisylve), description = stringResource(R.string.diag_scope_parcel_method),
                    icon = Icons.Default.Water, enabled = !opening && state.selectedParcel != null,
                    onClick = { open(ScopedDiagnosticKind.RIPISYLVE) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeDropdown(label: String, value: String, options: List<Pair<String?, String>>,
    enabled: Boolean, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(value = value, onValueChange = {}, readOnly = true, enabled = enabled,
            modifier = Modifier.menuAnchor().fillMaxWidth(), singleLine = true,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && enabled) })
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelect(id) })
            }
        }
    }
}

@Composable
private fun DiagnosticChoiceCard(title: String, description: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onClick, enabled = enabled) { Text(stringResource(R.string.diag_scope_open)) }
        }
    }
}
