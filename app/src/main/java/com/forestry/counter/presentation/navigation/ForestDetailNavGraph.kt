package com.forestry.counter.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.forestry.counter.ForestryCounterApplication
import com.forestry.counter.presentation.screens.forestry.CreateForestViewModel
import com.forestry.counter.presentation.screens.forestry.CreateForestWizard
import com.forestry.counter.presentation.screens.forestry.ForestMapCreationScreen
import com.forestry.counter.presentation.screens.forestry.ParcelMapCreationScreen
import com.forestry.counter.presentation.screens.forestry.ParcelMapCreationViewModel
import com.forestry.counter.data.repository.ParcelMapRepositoryImpl
import com.forestry.counter.data.packs.OfflineForestryLayerStore
import com.forestry.counter.network.SecureHttpClient
import java.util.concurrent.TimeUnit
import com.forestry.counter.presentation.screens.forestry.CreatePlacetteViewModel
import com.forestry.counter.presentation.screens.forestry.CreatePlacetteWizard
import com.forestry.counter.presentation.screens.forestry.ForestDetailScreen
import com.forestry.counter.presentation.screens.forestry.ForestDetailViewModel
import com.forestry.counter.presentation.screens.forestry.ForestManagementScreen

/**
 * Sous-graphe ForestDetail + Wizards — spec GEOSYLVA-003 §29.5-29.8.
 */
fun NavGraphBuilder.forestDetailNavGraph(
    navController: NavController,
    app: ForestryCounterApplication,
    transitions: NavTransitions,
) {
    composable(
        route = Screen.ForestManagement.route,
        arguments = listOf(navArgument("forestId") { type = NavType.StringType }),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val forestId = backStackEntry.arguments?.getString("forestId") ?: return@composable
        val viewModel = viewModel<ForestDetailViewModel>(backStackEntry, factory = viewModelFactory {
            initializer { ForestDetailViewModel(app.foretRepository, app.parcelleRepository, forestId, app.businessReadAccess) }
        })
        ForestManagementScreen(
            onOpenFieldJournal = { navController.navigate(Screen.FieldJournal.createRoute(com.forestry.counter.domain.field.FieldTarget(com.forestry.counter.domain.field.FieldTargetKind.FOREST,forestId))) },
            viewModel = viewModel,
            businessReadAccess = app.businessReadAccess,
            groupRepository = app.groupRepository,
            parcelleRepository = app.parcelleRepository,
            placetteRepository = app.placetteRepository,
            tigeRepository = app.tigeRepository,
            userPreferences = app.userPreferences,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToPlacettes = { id -> navController.navigate(Screen.Placettes.createRoute(id)) },
            onNavigateToDetails = { navController.navigate(Screen.ForestDetail.createRoute(forestId)) },
            onCreateParcelle = { navController.navigate(Screen.CreateParcelle.createRoute(forestId)) },
            onNavigateToMap = { navController.navigate(Screen.Map.createRoute("forest_$forestId")) },
            onNavigateToSynthesis = { navController.navigate(Screen.Martelage.forForest(forestId)) },
        )
    }

    composable(
        route = Screen.ForestDetail.route,
        arguments = listOf(navArgument("forestId") { type = NavType.StringType }),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val forestId = backStackEntry.arguments?.getString("forestId") ?: return@composable
        val viewModel = viewModel<ForestDetailViewModel>(backStackEntry, factory = viewModelFactory {
            initializer { ForestDetailViewModel(app.foretRepository, app.parcelleRepository, forestId, app.businessReadAccess) }
        })
        ForestDetailScreen(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToParcelles = { parcelleId ->
                navController.navigate(Screen.Placettes.createRoute(parcelleId))
            },
            onCreateParcelle = { foretId ->
                navController.navigate(Screen.CreateParcelle.createRoute(foretId))
            },
            onEditForet = { /* TODO Lot 2 : edit wizard */ },
        )
    }

    composable(
        route = Screen.CreateForest.route,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val maps = rememberParcelMaps(app)
        val viewModel = viewModel<CreateForestViewModel>(backStackEntry, factory = viewModelFactory {
            initializer { CreateForestViewModel(app.foretRepository, createSavedStateHandle(), maps::searchForests) }
        })
        ForestMapCreationScreen(
            viewModel = viewModel,
            maps = maps,
            onNavigateBack = { navController.popBackStack() },
            onCreated = {
                navController.popBackStack()
                navController.navigate(Screen.ForestManagement.createRoute(viewModel.createdForestId))
                navController.navigate(Screen.CreateParcelle.createRoute(viewModel.createdForestId))
            },
        )
    }

    composable(
        route = Screen.CreateParcelle.route,
        arguments = listOf(navArgument("forestId") { type = NavType.StringType }),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val forestId = backStackEntry.arguments?.getString("forestId") ?: return@composable
        val maps = rememberParcelMaps(app)
        val viewModel = viewModel<ParcelMapCreationViewModel>(backStackEntry, factory = viewModelFactory {
            initializer { ParcelMapCreationViewModel(app.parcelleRepository, app.foretRepository, maps, forestId, app.userPreferences.userProfession) }
        })
        ParcelMapCreationScreen(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onCreated = {
                navController.popBackStack()
                navController.navigate(Screen.Placettes.createRoute(viewModel.createdParcelId))
            },
        )
    }

    composable(
        route = Screen.CreatePlacette.route,
        arguments = listOf(navArgument("parcelleId") { type = NavType.StringType }),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val parcelleId = backStackEntry.arguments?.getString("parcelleId") ?: return@composable
        val viewModel = viewModel<CreatePlacetteViewModel>(backStackEntry, factory = viewModelFactory {
            initializer { CreatePlacetteViewModel(app.placetteRepository, parcelleId, app.parcelleRepository) }
        })
        CreatePlacetteWizard(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onCreated = { navController.popBackStack() },
        )
    }
}

@Composable
private fun rememberParcelMaps(app: ForestryCounterApplication): ParcelMapRepositoryImpl = remember(app) {
    ParcelMapRepositoryImpl(SecureHttpClient.createSecureClient(app).newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS).build(), OfflineForestryLayerStore(app))
}
