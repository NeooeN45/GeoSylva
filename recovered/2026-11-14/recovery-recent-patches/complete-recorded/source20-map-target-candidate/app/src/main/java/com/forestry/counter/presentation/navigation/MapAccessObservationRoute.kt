package com.forestry.counter.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.R
import com.forestry.counter.domain.field.FieldTarget
import com.forestry.counter.domain.journey.JourneyFeature
import com.forestry.counter.domain.maps.MapsPlace
import com.forestry.counter.domain.repository.FieldRepository
import com.forestry.counter.presentation.screens.field.FieldMapContextConstraints
import com.forestry.counter.presentation.screens.field.MapAccessObservationScreen
import com.forestry.counter.presentation.screens.home.JourneyUnavailableScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private data class LoadedMapObservationProfession(val code: String?)

/** Route readiness is distinct from a loaded null/unknown profession. No repository is created here. */
@Composable
internal fun MapAccessObservationRoute(
    repository: FieldRepository,
    professionChanges: Flow<String?>,
    place: MapsPlace,
    onSelected: (FieldTarget) -> Unit,
    onBack: () -> Unit,
) {
    val profession = remember(professionChanges) {
        professionChanges.map { LoadedMapObservationProfession(it) }
    }.collectAsStateWithLifecycle(initialValue = null)
    val loaded = profession.value
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val selectedCallback = rememberUpdatedState(onSelected)
    if (loaded == null) {
        BackHandler(onBack = onBack)
        Column(Modifier.fillMaxSize().testTag("map_access_observation_loading"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
            CircularProgressIndicator()
            Text(stringResource(R.string.geographic_loading))
        }
    } else if (loaded.code == null) {
        BackHandler(onBack = onBack)
        JourneyUnavailableScreen(onBack)
    } else {
        val constraints = observationConstraints(loaded.code)
        MapAccessObservationScreen(repository, place, constraints.allowWorksites, constraints.allowParcels,
            onSelected = { target ->
                // Read the current collected value at callback time, including during recomposition.
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                    observationConstraints(profession.value?.code).allows(target)) {
                    selectedCallback.value(target)
                }
            }, onBack = onBack)
    }
}

private fun observationConstraints(code: String?): FieldMapContextConstraints {
    val access = JourneyNavigation.access(code)
    return FieldMapContextConstraints(access.allows(JourneyFeature.WORKSITES), access.allows(JourneyFeature.FORESTS))
}
