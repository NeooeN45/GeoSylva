package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.R
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.repository.GroupRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.presentation.components.LoadingState
import kotlinx.coroutines.launch

/** Working entry for a selected canonical forest; the descriptive dossier stays behind Details. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForestManagementScreen(
    viewModel: ForestDetailViewModel,
    businessReadAccess: BusinessReadAccess,
    groupRepository: GroupRepository,
    parcelleRepository: ParcelleRepository,
    placetteRepository: PlacetteRepository,
    tigeRepository: TigeRepository,
    userPreferences: UserPreferencesManager,
    onNavigateBack: () -> Unit,
    onNavigateToPlacettes: (String) -> Unit,
    onNavigateToDetails: () -> Unit,
    onCreateParcelle: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToSynthesis: () -> Unit,
    onOpenFieldJournal: (() -> Unit)? = null,
) {
    val capturedState by viewModel.uiState.collectAsStateWithLifecycle()
    val state = if (capturedState is ForestDetailUiState.Success && !viewModel.isCurrentState(capturedState))
        ForestDetailUiState.Loading else capturedState
    val scope = rememberCoroutineScope()

    when (val ready = state) {
        is ForestDetailUiState.Success -> {
            suspend fun validateCapturedAccess(): Boolean {
                val access = ready.accessSnapshot ?: return false
                return viewModel.isCurrentState(ready) &&
                    businessReadAccess.canReadForest(ready.foret.foretId, access) &&
                    businessReadAccess.revalidate(access) && viewModel.isCurrentState(ready)
            }

            fun navigate(parcelId: String? = null, action: () -> Unit) {
                scope.launch {
                    if (!validateCapturedAccess()) return@launch
                    if (parcelId != null && (ready.parcelles.none { it.id == parcelId } ||
                        !businessReadAccess.canReadParcelle(parcelId, checkNotNull(ready.accessSnapshot)))) return@launch
                    action()
                }
            }

            ParcellesScreen(
                onOpenFieldJournal = onOpenFieldJournal?.let { action -> { navigate(action = action) } },
                forestId = ready.foret.foretId,
                groupRepository = groupRepository,
                parcelleRepository = parcelleRepository,
                placetteRepository = placetteRepository,
                tigeRepository = tigeRepository,
                userPreferences = userPreferences,
                onNavigateToPlacettes = { id -> navigate(id) { onNavigateToPlacettes(id) } },
                onNavigateBack = onNavigateBack,
                onNavigateToMartelage = { navigate(action = onNavigateToSynthesis) },
                onNavigateToMap = { navigate(action = onNavigateToMap) },
                onNavigateToCreateParcelle = { navigate(action = onCreateParcelle) },
                managedForest = ready.foret,
                managedParcelles = ready.parcelles,
                onNavigateToForestDetails = { navigate(action = onNavigateToDetails) },
                canUseManagedState = { viewModel.isCurrentState(ready) },
                validateManagedState = { validateCapturedAccess() },
            )
        }
        else -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.forest_management_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    },
                )
            },
        ) { padding ->
            if (ready == ForestDetailUiState.Loading) {
                LoadingState(stringResource(R.string.forest_management_loading), Modifier.padding(padding))
            } else Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.forest_management_unavailable))
            }
        }
    }
}
