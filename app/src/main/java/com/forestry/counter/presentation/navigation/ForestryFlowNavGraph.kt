package com.forestry.counter.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.ForestryCounterApplication
import com.forestry.counter.presentation.screens.forestry.EssenceDiamScreen
import com.forestry.counter.presentation.screens.forestry.ForestListScreen
import com.forestry.counter.presentation.screens.forestry.MapRechercheScreen
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
 * [Screen.PlacetteDetail], [Screen.EssenceDiam],
 * [Screen.Martelage], [Screen.Map]
 */
fun NavGraphBuilder.forestryFlowNavGraph(
    navController: NavController,
    app: ForestryCounterApplication,
    transitions: NavTransitions,
) {
    composable(Screen.Worksites.route) {
        val profession = app.userPreferences.userProfession.collectAsStateWithLifecycle(initialValue = null)
        if (JourneyNavigation.access(profession.value).allows(com.forestry.counter.domain.journey.JourneyFeature.WORKSITES)) {
            com.forestry.counter.presentation.screens.field.WorksitesScreen(app.fieldRepository,
                onJournal = { navController.navigate(Screen.FieldJournal.createRoute(it)) },
                onBack = { navController.popBackStack() })
        } else com.forestry.counter.presentation.screens.home.JourneyUnavailableScreen({ navController.popBackStack() })
    }
    composable(Screen.FieldJournal.route, arguments = listOf(navArgument("kind") { type = NavType.StringType }, navArgument("targetId") { type = NavType.StringType })) { entry ->
        val profession = app.userPreferences.userProfession.collectAsStateWithLifecycle(initialValue = null)
        val target = runCatching { com.forestry.counter.domain.field.FieldTarget(
            com.forestry.counter.domain.field.FieldTargetKind.valueOf(checkNotNull(entry.arguments?.getString("kind"))),
            checkNotNull(entry.arguments?.getString("targetId"))) }.getOrNull()
        val feature = when (target?.kind) {
            com.forestry.counter.domain.field.FieldTargetKind.WORKSITE -> com.forestry.counter.domain.journey.JourneyFeature.WORKSITES
            com.forestry.counter.domain.field.FieldTargetKind.WOOD_DEPOT, com.forestry.counter.domain.field.FieldTargetKind.WOOD_OBJECT -> com.forestry.counter.domain.journey.JourneyFeature.WOOD_DEPOTS
            else -> com.forestry.counter.domain.journey.JourneyFeature.FORESTS
        }
        if (target != null && JourneyNavigation.access(profession.value).allows(feature)) {
            com.forestry.counter.presentation.screens.field.FieldJournalScreen(app.fieldRepository, target,
                onOpenWood = { depot, wood -> navController.navigate(Screen.WoodObjects.createRoute(depot,wood)) },
                onBack = { navController.popBackStack() })
        } else com.forestry.counter.presentation.screens.home.JourneyUnavailableScreen({ navController.popBackStack() })
    }
    composable(
        route = Screen.WoodDepots.route,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) {
        val profession = app.userPreferences.userProfession.collectAsStateWithLifecycle(initialValue = null)
        if (JourneyNavigation.access(profession.value).allows(com.forestry.counter.domain.journey.JourneyFeature.WOOD_DEPOTS)) {
            com.forestry.counter.presentation.screens.wood.WoodDepotsScreen(
                repository = app.woodDepotRepository,
                onOpenWood = { navController.navigate(Screen.WoodObjects.createRoute(it)) },
                professionCode = profession.value,
                onNavigateBack = { navController.popBackStack() },
            )
        } else {
            com.forestry.counter.presentation.screens.home.JourneyUnavailableScreen(
                onExplorer = { navController.navigate(BottomNavDestination.EXPLORER.route) { launchSingleTop = true } },
            )
        }
    }
    composable(
        route = Screen.WoodObjects.route,
        arguments = listOf(navArgument("depotId") { type = NavType.StringType }, navArgument("objectId") { type = NavType.StringType; nullable = true; defaultValue = null }),
        enterTransition = transitions.enter, exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter, popExitTransition = transitions.popExit,
    ) { entry ->
        val profession = app.userPreferences.userProfession.collectAsStateWithLifecycle(initialValue = null)
        val depotId = entry.arguments?.getString("depotId")?.let { raw ->
            runCatching { com.forestry.counter.domain.model.requireWoodDepotId(raw); java.util.UUID.fromString(raw) }.getOrNull()
        }
        if (depotId != null && JourneyNavigation.access(profession.value).allows(com.forestry.counter.domain.journey.JourneyFeature.WOOD_DEPOTS)) {
            com.forestry.counter.presentation.screens.wood.WoodObjectsScreen(app.woodObjectRepository, depotId,
                fieldRepository = app.fieldRepository,
                initialObjectId = entry.arguments?.getString("objectId")?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() },
                onJournal = { id -> navController.navigate(Screen.FieldJournal.createRoute(com.forestry.counter.domain.field.FieldTarget(com.forestry.counter.domain.field.FieldTargetKind.WOOD_OBJECT,id.toString()))) },
                onDepotJournal = { navController.navigate(Screen.FieldJournal.createRoute(com.forestry.counter.domain.field.FieldTarget(com.forestry.counter.domain.field.FieldTargetKind.WOOD_DEPOT,depotId.toString()))) },
                onBack = { navController.popBackStack() })
        } else {
            com.forestry.counter.presentation.screens.home.JourneyUnavailableScreen(
                onExplorer = { navController.navigate(BottomNavDestination.EXPLORER.route) { launchSingleTop = true } })
        }
    }
    composable(
        route = Screen.Forets.route,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) {
        ForestListScreen(
            foretRepository = app.foretRepository,
            businessReadAccess = app.businessReadAccess,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToForest = { id -> navController.navigate(Screen.ForestManagement.createRoute(id)) },
            onCreateForest = { navController.navigate(Screen.CreateForest.route) },
            onLegacyGroups = { navController.navigate(Screen.ForestryLegacyGroups.route) },
        )
    }

    composable(
        route = Screen.ForestryLegacyGroups.route,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) {
        // Les dossiers historiques conservent leur modèle et leurs identifiants.
        GroupsScreen(
            groupRepository = app.groupRepository,
            onNavigateToGroup = { groupId ->
                navController.navigate(Screen.Parcelles.createRoute(groupId))
            },
            onNavigateToSettings = { navController.navigate(Screen.SettingsHome.route) },
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
            onNavigateToIbp = { navController.navigate(Screen.IbpProjects.route) },
            // Poussé depuis Explorer (catégorie « Forêts ») : sans flèche
            // retour, c'était une impasse — l'écran fut conçu à l'origine
            // comme onglet de premier niveau, jamais comme sous-page.
            onNavigateBack = { navController.popBackStack() },
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
        // Historical Groups supply a groupId here. Their manual parcel creation must
        // not enter the canonical forest wizard, which requires an actual Foret ID.
        ParcellesScreen(
            forestId = forestId,
            groupRepository = app.groupRepository,
            parcelleRepository = app.parcelleRepository,
            placetteRepository = app.placetteRepository,
            tigeRepository = app.tigeRepository,
            sessionRepository = app.inventaireSessionRepository,
            onOpenSessionSynthesis = { pid, sid ->
                navController.navigate(Screen.Martelage.forParcelle(pid, sid))
            },
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
            },
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
            onOpenFieldJournal = { navController.navigate(Screen.FieldJournal.createRoute(com.forestry.counter.domain.field.FieldTarget(com.forestry.counter.domain.field.FieldTargetKind.PARCEL,parcelleId))) },
            parcelleId = parcelleId,
            placetteRepository = app.placetteRepository,
            parcelleRepository = app.parcelleRepository,
            tigeRepository = app.tigeRepository,
            sessionRepository = app.inventaireSessionRepository,
            onOpenSessionSynthesis = { pid, sid ->
                navController.navigate(Screen.Martelage.forParcelle(pid, sid))
            },
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
            onNavigateToDiagnostic = { pid ->
                navController.navigate(Screen.DiagnosticMenu.createRoute(pid))
            },
            onNavigateToCreatePlacette = { pid ->
                navController.navigate(Screen.CreatePlacette.createRoute(pid))
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
            onOpenFieldJournal = { navController.navigate(Screen.FieldJournal.createRoute(com.forestry.counter.domain.field.FieldTarget(com.forestry.counter.domain.field.FieldTargetKind.PLOT,placetteId))) },
            parcelleId = parcelleId,
            placetteId = placetteId,
            essenceRepository = app.essenceRepository,
            tigeRepository = app.tigeRepository,
            placetteRepository = app.placetteRepository,
            userPreferences = app.userPreferences,
            sessionRepository = app.inventaireSessionRepository,
            identityRepository = app.identityRepository,
            surveyContextRepository = app.surveyContextRepository,
            onOpenSessionSynthesis = { pid, plid, sid ->
                navController.navigate(Screen.Martelage.forPlacette(pid, plid, sid))
            },
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
            sessionRepository = app.inventaireSessionRepository,
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
            sessionRepository = app.inventaireSessionRepository,
            identityRepository = app.identityRepository,
            observationQualificationRepository = app.observationQualificationRepository,
            businessReadAccess = app.businessReadAccess,
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.Martelage.route,
        arguments = listOf(
            navArgument("scope") { type = NavType.StringType },
            navArgument("forestId") { type = NavType.StringType },
            navArgument("parcelleId") { type = NavType.StringType },
            navArgument("placetteId") { type = NavType.StringType },
            navArgument("sessionId") { type = NavType.StringType; nullable = true; defaultValue = null },
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
            placetteRepository = app.placetteRepository,
            sessionRepository = app.inventaireSessionRepository,
            initialSessionId = backStackEntry.arguments?.getString("sessionId"),
            businessReadAccess = app.businessReadAccess,
            onNavigateToSettings = { navController.navigate(Screen.SettingsHome.route) },
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
        ),
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) { backStackEntry ->
        val parcelleId = backStackEntry.arguments?.getString("parcelleId") ?: return@composable
        MapRechercheScreen(
            parcelleId = parcelleId,
            tigeRepository = app.tigeRepository,
            essenceRepository = app.essenceRepository,
            parcelleRepository = app.parcelleRepository,
            preferencesManager = app.userPreferences,
            offlineTileManager = app.offlineTileManager,
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
