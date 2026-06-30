package com.forestry.counter.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.forestry.counter.ForestryCounterApplication
import com.forestry.counter.presentation.screens.forestry.DashboardScreen
import com.forestry.counter.presentation.screens.forestry.EssenceDiamScreen
import com.forestry.counter.presentation.screens.forestry.MapScreen
import com.forestry.counter.presentation.screens.forestry.MartelageScreen
import com.forestry.counter.presentation.screens.forestry.ParcellesScreen
import com.forestry.counter.presentation.screens.forestry.PlacetteDetailScreen
import com.forestry.counter.presentation.screens.forestry.PlacetteEvolutionScreen
import com.forestry.counter.presentation.screens.forestry.PlacettesScreen
import com.forestry.counter.presentation.screens.groups.GroupsScreen

/**
 * Sous-graphe flux forestier principal.
 *
 * Routes : [Screen.Forets], [Screen.Parcelles], [Screen.Placettes],
 * [Screen.PlacetteDetail], [Screen.EssenceDiam], [Screen.Dashboard],
 * [Screen.Martelage], [Screen.Map]
 */
fun NavGraphBuilder.forestryFlowNavGraph(
    navController: NavController,
    app: ForestryCounterApplication,
    transitions: NavTransitions,
) {
    composable(
        route = Screen.Forets.route,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) {
        // Forests selection reuses the Groups screen
        GroupsScreen(
            groupRepository = app.groupRepository,
            onNavigateToGroup = { groupId ->
                navController.navigate(Screen.Parcelles.createRoute(groupId))
            },
            onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
            preferencesManager = app.userPreferences,
            onNavigateToMartelage = { groupIdOrNull ->
                if (groupIdOrNull == null) {
                    navController.navigate(Screen.Martelage.forGlobal())
                } else {
                    navController.navigate(Screen.Martelage.forForest(groupIdOrNull))
                }
            },
            onNavigateToMap = { scope ->
                navController.navigate(Screen.Map.createRoute(scope))
            },
            onNavigateToIbp = { navController.navigate(Screen.IbpProjects.route) }
        )
    }

    composable(
        route = Screen.Parcelles.route,
        arguments = listOf(navArgument("forestId") { type = NavType.StringType }),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val forestArg = backStackEntry.arguments?.getString("forestId")
        val forestId = forestArg?.takeUnless { it == "none" }
        ParcellesScreen(
            forestId = forestId,
            groupRepository = app.groupRepository,
            parcelleRepository = app.parcelleRepository,
            placetteRepository = app.placetteRepository,
            tigeRepository = app.tigeRepository,
            userPreferences = app.userPreferences,
            onNavigateToPlacettes = { parcelleId ->
                navController.navigate(Screen.Placettes.createRoute(parcelleId))
            },
            onNavigateBack = { navController.popBackStack() },
            onNavigateToMartelage = { fid ->
                navController.navigate(Screen.Martelage.forForest(fid))
            },
            onNavigateToMap = {
                val scope = if (forestId != null) "forest_$forestId" else "all"
                navController.navigate(Screen.Map.createRoute(scope))
            },
            onNavigateToDiagnostic = { pid ->
                navController.navigate(Screen.DiagnosticMenu.createRoute(pid))
            },
            onNavigateToIbp = { pid ->
                navController.navigate(Screen.IbpHistory.createRoute(pid))
            }
        )
    }

    composable(
        route = Screen.Placettes.route,
        arguments = listOf(navArgument("parcelleId") { type = NavType.StringType }),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val parcelleId = backStackEntry.arguments?.getString("parcelleId") ?: return@composable
        PlacettesScreen(
            parcelleId = parcelleId,
            placetteRepository = app.placetteRepository,
            parcelleRepository = app.parcelleRepository,
            tigeRepository = app.tigeRepository,
            userPreferences = app.userPreferences,
            onNavigateToMartelage = { pid, plid ->
                navController.navigate(Screen.PlacetteDetail.createRoute(pid, plid))
            },
            onNavigateBack = { navController.popBackStack() },
            onNavigateToMartelageForParcelle = { pid ->
                navController.navigate(Screen.Martelage.forParcelle(pid))
            },
            onNavigateToMap = { pid ->
                navController.navigate(Screen.Map.createRoute(pid))
            },
            onNavigateToDashboard = { pid ->
                navController.navigate(Screen.Dashboard.createRoute(pid))
            },
            onNavigateToDiagnostic = { pid ->
                navController.navigate(Screen.DiagnosticMenu.createRoute(pid))
            }
        )
    }

    composable(
        route = Screen.PlacetteDetail.route,
        arguments = listOf(
            navArgument("parcelleId") { type = NavType.StringType },
            navArgument("placetteId") { type = NavType.StringType }
        ),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val parcelleId = backStackEntry.arguments?.getString("parcelleId") ?: return@composable
        val placetteId = backStackEntry.arguments?.getString("placetteId") ?: return@composable
        PlacetteDetailScreen(
            parcelleId = parcelleId,
            placetteId = placetteId,
            essenceRepository = app.essenceRepository,
            tigeRepository = app.tigeRepository,
            placetteRepository = app.placetteRepository,
            userPreferences = app.userPreferences,
            onNavigateToDiametres = { pid, plid, essence ->
                navController.navigate(Screen.EssenceDiam.createRoute(pid, plid, essence))
            },
            onNavigateToMartelage = { pid, plid ->
                navController.navigate(Screen.Martelage.forPlacette(pid, plid))
            },
            onNavigateToIbp = { pid, plid ->
                navController.navigate(Screen.IbpEvaluation.createRoute(pid, plid))
            },
            onNavigateToStationDiag = { pid ->
                navController.navigate(Screen.DiagnosticMenu.createRoute(pid))
            },
            onNavigateToRipisylveDiag = { pid ->
                navController.navigate(Screen.RipisylveDiagnostic.createRoute(pid))
            },
            onNavigateToEvolution = { pid, plid, year ->
                navController.navigate(Screen.PlacetteEvolution.createRoute(pid, plid, year))
            },
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.PlacetteEvolution.route,
        arguments = listOf(
            navArgument("parcelleId") { type = NavType.StringType },
            navArgument("placetteId") { type = NavType.StringType },
            navArgument("year") { type = NavType.IntType }
        ),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val parcelleId = backStackEntry.arguments?.getString("parcelleId") ?: return@composable
        val placetteId = backStackEntry.arguments?.getString("placetteId") ?: return@composable
        val year = backStackEntry.arguments?.getInt("year") ?: return@composable
        PlacetteEvolutionScreen(
            placetteId = placetteId,
            year = year,
            tigeRepository = app.tigeRepository,
            essenceRepository = app.essenceRepository,
            placetteRepository = app.placetteRepository,
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.EssenceDiam.route,
        arguments = listOf(
            navArgument("parcelleId") { type = NavType.StringType },
            navArgument("placetteId") { type = NavType.StringType },
            navArgument("essenceCode") { type = NavType.StringType }
        ),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val parcelleId = backStackEntry.arguments?.getString("parcelleId") ?: return@composable
        val placetteId = backStackEntry.arguments?.getString("placetteId") ?: return@composable
        val essenceCode = backStackEntry.arguments?.getString("essenceCode") ?: return@composable
        EssenceDiamScreen(
            parcelleId = parcelleId,
            placetteId = placetteId,
            essenceCode = essenceCode,
            tigeRepository = app.tigeRepository,
            calculator = app.forestryCalculator,
            userPreferences = app.userPreferences,
            essenceRepository = app.essenceRepository,
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.Dashboard.route,
        arguments = listOf(navArgument("parcelleId") { type = NavType.StringType }),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val parcelleId = backStackEntry.arguments?.getString("parcelleId") ?: return@composable
        DashboardScreen(
            parcelleId = parcelleId,
            tigeRepository = app.tigeRepository,
            essenceRepository = app.essenceRepository,
            parcelleRepository = app.parcelleRepository,
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.Martelage.route,
        arguments = listOf(
            navArgument("scope") { type = NavType.StringType },
            navArgument("forestId") { type = NavType.StringType },
            navArgument("parcelleId") { type = NavType.StringType },
            navArgument("placetteId") { type = NavType.StringType }
        ),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val scope = backStackEntry.arguments?.getString("scope") ?: "GLOBAL"
        val forestId = backStackEntry.arguments?.getString("forestId")?.takeUnless { it == "none" }
        val parcelleId = backStackEntry.arguments?.getString("parcelleId")?.takeUnless { it == "none" }
        val placetteId = backStackEntry.arguments?.getString("placetteId")?.takeUnless { it == "none" }
        MartelageScreen(
            scope = scope,
            forestId = forestId,
            parcelleId = parcelleId,
            placetteId = placetteId,
            essenceRepository = app.essenceRepository,
            tigeRepository = app.tigeRepository,
            parcelleRepository = app.parcelleRepository,
            forestryCalculator = app.forestryCalculator,
            userPreferences = app.userPreferences,
            onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
            onNavigateToPriceTablesEditor = { navController.navigate(Screen.PriceTablesEditor.route) },
            onNavigateToMap = { pid -> navController.navigate(Screen.Map.createRoute(pid)) },
            ibpRepository = app.ibpRepository,
            onNavigateToIbp = { pid, plid ->
                navController.navigate(Screen.IbpEvaluation.createRoute(pid, plid))
            },
            onNavigateToIbpHistory = { pid, plid ->
                navController.navigate(Screen.IbpHistory.createRoute(pid, plid))
            },
            onNavigateToSuperCorrelateur = { pid ->
                navController.navigate(Screen.SuperCorrelateur.createRoute(pid))
            },
            onNavigateToStandClassification = { pid ->
                navController.navigate(Screen.StandClassification.createRoute(pid))
            },
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.Map.route,
        arguments = listOf(
            navArgument("parcelleId") { type = NavType.StringType },
            navArgument("navLat") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("navLon") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("navEssence") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("navDiam") { type = NavType.StringType; nullable = true; defaultValue = null }
        ),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val parcelleId = backStackEntry.arguments?.getString("parcelleId") ?: return@composable
        val navLat = backStackEntry.arguments?.getString("navLat")?.toDoubleOrNull()
        val navLon = backStackEntry.arguments?.getString("navLon")?.toDoubleOrNull()
        val navEssence = backStackEntry.arguments?.getString("navEssence")
        val navDiam = backStackEntry.arguments?.getString("navDiam")?.toDoubleOrNull()
        MapScreen(
            parcelleId = parcelleId,
            tigeRepository = app.tigeRepository,
            essenceRepository = app.essenceRepository,
            parcelleRepository = app.parcelleRepository,
            preferencesManager = app.userPreferences,
            offlineTileManager = app.offlineTileManager,
            onNavigateBack = { navController.popBackStack() },
            initialNavLat = navLat,
            initialNavLon = navLon,
            initialNavEssence = navEssence,
            initialNavDiam = navDiam
        )
    }
}
