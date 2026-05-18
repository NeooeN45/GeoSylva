package com.forestry.counter.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.forestry.counter.ForestryCounterApplication
import com.forestry.counter.presentation.screens.forestry.IbpCompareScreen
import com.forestry.counter.presentation.screens.forestry.IbpDiagnosticScreen
import com.forestry.counter.presentation.screens.forestry.IbpEvaluationScreen
import com.forestry.counter.presentation.screens.forestry.IbpHistoryScreen
import com.forestry.counter.presentation.screens.forestry.IbpProjectsScreen
import com.forestry.counter.presentation.screens.forestry.IbpReferenceScreen

/**
 * Sous-graphe IBP (Indicateurs de Biodiversité Potentielle).
 *
 * Routes : [Screen.IbpProjects], [Screen.IbpStandalone], [Screen.IbpHistory],
 * [Screen.IbpEvaluation], [Screen.IbpReference], [Screen.IbpDiagnostic],
 * [Screen.IbpCompare]
 */
fun NavGraphBuilder.ibpNavGraph(
    navController: NavController,
    app: ForestryCounterApplication,
    transitions: NavTransitions,
) {
    composable(
        route = Screen.IbpProjects.route,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) {
        IbpProjectsScreen(
            ibpRepository = app.ibpRepository,
            parcelleRepository = app.parcelleRepository,
            placetteRepository = app.placetteRepository,
            onNavigateBack = { navController.popBackStack() },
            onOpenEvaluation = { pid, plid, evalId ->
                navController.navigate(Screen.IbpEvaluation.createRoute(pid, plid, evalId))
            },
            onNavigateToDiagnostic = { pid ->
                navController.navigate(Screen.IbpDiagnostic.createRoute(pid))
            },
            onNavigateToCompare = { pid ->
                navController.navigate(Screen.IbpCompare.createRoute(pid))
            }
        )
    }

    composable(
        route = Screen.IbpStandalone.route,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) {
        IbpEvaluationScreen(
            parcelleId = "GLOBAL",
            placetteId = "GLOBAL",
            ibpRepository = app.ibpRepository,
            evaluationId = null,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToReference = { navController.navigate(Screen.IbpReference.route) }
        )
    }

    composable(
        route = Screen.IbpHistory.route,
        arguments = listOf(
            navArgument("parcelleId") { type = NavType.StringType },
            navArgument("placetteId") { type = NavType.StringType; nullable = true; defaultValue = null }
        ),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val parcelleId = backStackEntry.arguments?.getString("parcelleId") ?: return@composable
        val placetteId = backStackEntry.arguments?.getString("placetteId")
        IbpHistoryScreen(
            parcelleId = parcelleId,
            placetteId = placetteId,
            ibpRepository = app.ibpRepository,
            placetteRepository = app.placetteRepository,
            onNavigateBack = { navController.popBackStack() },
            onOpenEvaluation = { pid: String, plid: String, evalId: String? ->
                navController.navigate(Screen.IbpEvaluation.createRoute(pid, plid, evalId))
            }
        )
    }

    composable(
        route = Screen.IbpEvaluation.route,
        arguments = listOf(
            navArgument("parcelleId") { type = NavType.StringType },
            navArgument("placetteId") { type = NavType.StringType },
            navArgument("evalId") { type = NavType.StringType; nullable = true; defaultValue = null }
        ),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val parcelleId = backStackEntry.arguments?.getString("parcelleId") ?: return@composable
        val placetteId = backStackEntry.arguments?.getString("placetteId") ?: return@composable
        val evalId = backStackEntry.arguments?.getString("evalId")
        IbpEvaluationScreen(
            parcelleId = parcelleId,
            placetteId = placetteId,
            ibpRepository = app.ibpRepository,
            placetteRepository = app.placetteRepository,
            userPreferences = app.userPreferences,
            evaluationId = evalId,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToReference = { navController.navigate(Screen.IbpReference.route) }
        )
    }

    composable(
        route = Screen.IbpReference.route,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) {
        IbpReferenceScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.IbpDiagnostic.route,
        arguments = listOf(navArgument("parcelleId") { type = NavType.StringType }),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val parcelleId = backStackEntry.arguments?.getString("parcelleId") ?: return@composable
        IbpDiagnosticScreen(
            parcelleId = parcelleId,
            ibpRepository = app.ibpRepository,
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.IbpCompare.route,
        arguments = listOf(navArgument("parcelleId") { type = NavType.StringType }),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val parcelleId = backStackEntry.arguments?.getString("parcelleId") ?: return@composable
        IbpCompareScreen(
            parcelleId = parcelleId,
            ibpRepository = app.ibpRepository,
            onNavigateBack = { navController.popBackStack() }
        )
    }
}
