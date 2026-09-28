package com.forestry.counter.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.forestry.counter.ForestryCounterApplication
import com.forestry.counter.presentation.screens.forestry.CreateForestViewModel
import com.forestry.counter.presentation.screens.forestry.CreateForestWizard
import com.forestry.counter.presentation.screens.forestry.CreateParcelleViewModel
import com.forestry.counter.presentation.screens.forestry.CreateParcelleWizard
import com.forestry.counter.presentation.screens.forestry.CreatePlacetteViewModel
import com.forestry.counter.presentation.screens.forestry.CreatePlacetteWizard
import com.forestry.counter.presentation.screens.forestry.EditForestViewModel
import com.forestry.counter.presentation.screens.forestry.ForestDetailScreen
import com.forestry.counter.presentation.screens.forestry.ForestDetailViewModel

/**
 * Sous-graphe ForestDetail + Wizards — spec GEOSYLVA-003 §29.5-29.8.
 */
fun NavGraphBuilder.forestDetailNavGraph(
    navController: NavController,
    app: ForestryCounterApplication,
    transitions: NavTransitions,
) {
    composable(
        route = Screen.ForestDetail.route,
        arguments = listOf(navArgument("forestId") { type = NavType.StringType }),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val forestId = backStackEntry.arguments?.getString("forestId") ?: return@composable
        val viewModel = ForestDetailViewModel(
            foretRepository = app.foretRepository,
            parcelleRepository = app.parcelleRepository,
            foretId = forestId,
            groupRepository = app.groupRepository,
        )
        ForestDetailScreen(
            viewModel = viewModel,
            app = app,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToParcelles = { parcelleId ->
                navController.navigate(Screen.Placettes.createRoute(parcelleId))
            },
            onEditForet = { foretIdToEdit ->
                navController.navigate(Screen.EditForest.createRoute(foretIdToEdit))
            },
        )
    }

    composable(
        route = Screen.CreateForest.route,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) {
        val viewModel = CreateForestViewModel(app.foretRepository, app.groupRepository)
        CreateForestWizard(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onCreated = {
                navController.popBackStack()
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
        val viewModel = CreateParcelleViewModel(app.parcelleRepository, forestId)
        CreateParcelleWizard(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onCreated = { navController.popBackStack() },
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
        val viewModel = CreatePlacetteViewModel(app.placetteRepository, parcelleId)
        CreatePlacetteWizard(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onCreated = { navController.popBackStack() },
        )
    }

    composable(
        route = Screen.EditForest.route,
        arguments = listOf(navArgument("forestId") { type = NavType.StringType }),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val forestId = backStackEntry.arguments?.getString("forestId") ?: return@composable
        val viewModel = EditForestViewModel(
            foretRepository = app.foretRepository,
            groupRepository = app.groupRepository,
            foretId = forestId,
        )
        CreateForestWizard(
            viewModel = viewModel,
            isEditing = true,
            onNavigateBack = { navController.popBackStack() },
            onCreated = { navController.popBackStack() },
        )
    }
}
