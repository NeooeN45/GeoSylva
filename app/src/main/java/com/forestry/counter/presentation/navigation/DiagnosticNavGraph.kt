package com.forestry.counter.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.forestry.counter.ForestryCounterApplication
import com.forestry.counter.presentation.screens.forestry.DiagnosticMenuScreen
import com.forestry.counter.presentation.screens.forestry.DiagnosticScreen
import com.forestry.counter.presentation.screens.forestry.RipisylveDiagnosticScreen
import com.forestry.counter.presentation.screens.forestry.StandClassificationScreen
import com.forestry.counter.presentation.screens.forestry.SuperCorrelateurScreen

/**
 * Sous-graphe Diagnostic sylvicole, ripisylve et corrélateur.
 *
 * Routes : [Screen.DiagnosticMenu], [Screen.DiagnosticResult],
 * [Screen.RipisylveDiagnostic], [Screen.RipisylveDiagnosticStandalone],
 * [Screen.StandClassification], [Screen.SuperCorrelateur]
 */
fun NavGraphBuilder.diagnosticNavGraph(
    navController: NavController,
    app: ForestryCounterApplication,
    transitions: NavTransitions,
) {
    composable(
        route = Screen.DiagnosticMenu.route,
        arguments = listOf(navArgument("parcelleId") { type = NavType.StringType }),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val parcelleId = backStackEntry.arguments?.getString("parcelleId") ?: return@composable
        DiagnosticMenuScreen(
            parcelleId           = parcelleId,
            parcelleRepository   = app.parcelleRepository,
            tigeRepository       = app.tigeRepository,
            stationRepository    = app.stationEnvironnementaleRepository,
            diagnosticRepository = app.diagnosticSylvicoleRepository,
            onNavigateBack       = { navController.popBackStack() },
            onNavigateToDiagnosticResult = { diagId ->
                navController.navigate(Screen.DiagnosticResult.createRoute(diagId))
            }
        )
    }

    composable(
        route = Screen.DiagnosticResult.route,
        arguments = listOf(navArgument("diagnosticId") { type = NavType.StringType }),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val diagnosticId = backStackEntry.arguments?.getString("diagnosticId") ?: return@composable
        DiagnosticScreen(
            diagnosticId         = diagnosticId,
            diagnosticRepository = app.diagnosticSylvicoleRepository,
            onNavigateBack       = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.RipisylveDiagnostic.route,
        arguments = listOf(navArgument("parcelleId") { type = NavType.StringType }),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val parcelleId = backStackEntry.arguments?.getString("parcelleId") ?: return@composable
        RipisylveDiagnosticScreen(
            parcelleId = parcelleId,
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.RipisylveDiagnosticStandalone.route,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) {
        RipisylveDiagnosticScreen(
            parcelleId = "",
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.StandClassification.route,
        arguments = listOf(navArgument("parcelleId") { type = NavType.StringType }),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val parcelleId = backStackEntry.arguments?.getString("parcelleId") ?: return@composable
        StandClassificationScreen(
            parcelleId = parcelleId,
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.SuperCorrelateur.route,
        arguments = listOf(navArgument("parcelleId") { type = NavType.StringType }),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val parcelleId = backStackEntry.arguments?.getString("parcelleId") ?: return@composable
        SuperCorrelateurScreen(
            parcelleId = parcelleId,
            tigeRepository = app.tigeRepository,
            stationRepository = app.stationRepository,
            ripisylveRepository = app.ripisylveRepository,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToStation = {
                navController.navigate(Screen.DiagnosticMenu.createRoute(parcelleId))
            },
            onNavigateToRipisylve = {
                navController.navigate(Screen.RipisylveDiagnostic.createRoute(parcelleId))
            }
        )
    }
}
