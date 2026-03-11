package com.forestry.counter.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.forestry.counter.ForestryCounterApplication
import com.forestry.counter.presentation.screens.group.GroupScreen
import com.forestry.counter.presentation.screens.groups.GroupsScreen
import com.forestry.counter.presentation.screens.settings.SettingsScreen
import com.forestry.counter.presentation.screens.formulas.FormulasScreen
import com.forestry.counter.presentation.screens.calculator.CalculatorScreen
import com.forestry.counter.presentation.screens.forestry.ParcellesScreen
import com.forestry.counter.presentation.screens.forestry.PlacettesScreen
import com.forestry.counter.presentation.screens.forestry.MartelageScreen
import com.forestry.counter.presentation.screens.forestry.MapScreen
import com.forestry.counter.presentation.screens.forestry.PlacetteDetailScreen
import com.forestry.counter.presentation.screens.forestry.EssenceDiamScreen
import com.forestry.counter.presentation.screens.forestry.DashboardScreen
import com.forestry.counter.presentation.screens.forestry.IbpEvaluationScreen
import com.forestry.counter.presentation.screens.forestry.IbpProjectsScreen
import com.forestry.counter.presentation.screens.forestry.IbpHistoryScreen
import com.forestry.counter.presentation.screens.forestry.IbpReferenceScreen
import com.forestry.counter.presentation.screens.forestry.IbpDiagnosticScreen
import com.forestry.counter.presentation.screens.forestry.IbpCompareScreen
import com.forestry.counter.presentation.screens.forestry.RipisylveDiagnosticScreen
import com.forestry.counter.presentation.screens.forestry.StationDiagnosticScreen
import com.forestry.counter.presentation.screens.settings.PriceTablesEditorScreen
import com.forestry.counter.presentation.screens.onboarding.OnboardingScreen
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Forets : Screen("forets")
    object Groups : Screen("groups")
    object GroupDetail : Screen("group/{groupId}") {
        fun createRoute(groupId: String) = "group/$groupId"
    }
    object Formulas : Screen("group/{groupId}/formulas") {
        fun createRoute(groupId: String) = "group/$groupId/formulas"
    }
    object Calculator : Screen("group/{groupId}/calculator") {
        fun createRoute(groupId: String) = "group/$groupId/calculator"
    }
    object Settings : Screen("settings")
    object PriceTablesEditor : Screen("settings/price_tables")
    object Parcelles : Screen("parcelles/{forestId}") {
        fun createRoute(forestId: String?) = "parcelles/${forestId ?: "none"}"
    }
    object Placettes : Screen("placettes/{parcelleId}") {
        fun createRoute(parcelleId: String) = "placettes/$parcelleId"
    }
    object PlacetteDetail : Screen("placette/{parcelleId}/{placetteId}") {
        fun createRoute(parcelleId: String, placetteId: String) = "placette/$parcelleId/$placetteId"
    }
    object Martelage : Screen("martelage/{scope}/{forestId}/{parcelleId}/{placetteId}") {
        fun forGlobal(): String = "martelage/GLOBAL/none/none/none"
        fun forForest(forestId: String): String = "martelage/FOREST/$forestId/none/none"
        fun forParcelle(parcelleId: String): String = "martelage/PARCELLE/none/$parcelleId/none"
        fun forPlacette(parcelleId: String, placetteId: String): String = "martelage/PLACETTE/none/$parcelleId/$placetteId"
    }
    object Map : Screen("map/{parcelleId}?navLat={navLat}&navLon={navLon}&navEssence={navEssence}&navDiam={navDiam}") {
        fun createRoute(parcelleId: String) = "map/$parcelleId"
        fun createRouteWithNav(
            parcelleId: String,
            lat: Double,
            lon: Double,
            essenceName: String,
            diamCm: Double
        ) = "map/$parcelleId?navLat=$lat&navLon=$lon&navEssence=$essenceName&navDiam=$diamCm"
    }
    object EssenceDiam : Screen("placette/{parcelleId}/{placetteId}/essence/{essenceCode}") {
        fun createRoute(parcelleId: String, placetteId: String, essenceCode: String) = "placette/$parcelleId/$placetteId/essence/$essenceCode"
    }
    object Dashboard : Screen("dashboard/{parcelleId}") {
        fun createRoute(parcelleId: String) = "dashboard/$parcelleId"
    }
    object IbpEvaluation : Screen("ibp/{parcelleId}/{placetteId}?evalId={evalId}") {
        fun createRoute(parcelleId: String, placetteId: String, evalId: String? = null): String =
            if (evalId != null) "ibp/$parcelleId/$placetteId?evalId=$evalId"
            else "ibp/$parcelleId/$placetteId"
    }
    object IbpProjects : Screen("ibp/projects")
    object IbpStandalone : Screen("ibp/standalone")
    object IbpHistory : Screen("ibp/history/{parcelleId}?placetteId={placetteId}") {
        fun createRoute(parcelleId: String, placetteId: String? = null): String =
            if (placetteId != null) "ibp/history/$parcelleId?placetteId=$placetteId"
            else "ibp/history/$parcelleId"
    }
    object IbpReference : Screen("ibp/reference")
    object IbpDiagnostic : Screen("ibp/diagnostic/{parcelleId}") {
        fun createRoute(parcelleId: String) = "ibp/diagnostic/$parcelleId"
    }
    object IbpCompare : Screen("ibp/compare/{parcelleId}") {
        fun createRoute(parcelleId: String) = "ibp/compare/$parcelleId"
    }
    object RipisylveDiagnostic : Screen("ripisylve/diagnostic/{parcelleId}") {
        fun createRoute(parcelleId: String) = "ripisylve/diagnostic/$parcelleId"
    }
    object StationDiagnostic : Screen("station/diagnostic/{parcelleId}") {
        fun createRoute(parcelleId: String) = "station/diagnostic/$parcelleId"
    }
    object Onboarding : Screen("onboarding")
}

@Composable
fun ForestryNavigation(app: ForestryCounterApplication) {
    val navController = rememberNavController()

    val animationsEnabled by app.userPreferences.animationsEnabled.collectAsState(initial = true)
    val onboardingCompleted by app.userPreferences.onboardingCompleted.collectAsState(initial = true)
    val coroutineScope = rememberCoroutineScope()

    // Courbes Material 3 Emphasized — spec 2024 (M3 expressive motion)
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    // Extra-smooth decelerate for pop-back (feels native)
    val spatialDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)

    // Durées : entrée 380ms, sortie 180ms, scale légèrement plus grand 0.96f
    val enterMs  = 380
    val exitMs   = 180
    val enterScaleInitial = 0.96f
    val exitScaleTarget   = 0.97f   // quasi-invisible mais donne de la profondeur

    val navEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (!animationsEnabled) {
            EnterTransition.None
        } else {
            fadeIn(
                animationSpec = tween(durationMillis = enterMs, delayMillis = 40, easing = emphasizedDecelerate)
            ) + slideInHorizontally(
                animationSpec = tween(durationMillis = enterMs, easing = emphasizedDecelerate),
                initialOffsetX = { (it * 0.18f).toInt() }
            ) + scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialScale = enterScaleInitial
            )
        }
    }
    val navExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (!animationsEnabled) {
            ExitTransition.None
        } else {
            fadeOut(
                animationSpec = tween(durationMillis = exitMs, easing = emphasizedAccelerate)
            ) + slideOutHorizontally(
                animationSpec = tween(durationMillis = exitMs, easing = emphasizedAccelerate),
                targetOffsetX = { -(it * 0.08f).toInt() }
            ) + scaleOut(
                animationSpec = tween(durationMillis = exitMs, easing = emphasizedAccelerate),
                targetScale = exitScaleTarget
            )
        }
    }
    val navPopEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (!animationsEnabled) {
            EnterTransition.None
        } else {
            fadeIn(
                animationSpec = tween(durationMillis = enterMs, delayMillis = 40, easing = spatialDecelerate)
            ) + slideInHorizontally(
                animationSpec = tween(durationMillis = enterMs, easing = spatialDecelerate),
                initialOffsetX = { -(it * 0.18f).toInt() }
            ) + scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialScale = enterScaleInitial
            )
        }
    }
    val navPopExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (!animationsEnabled) {
            ExitTransition.None
        } else {
            fadeOut(
                animationSpec = tween(durationMillis = exitMs, easing = emphasizedAccelerate)
            ) + slideOutHorizontally(
                animationSpec = tween(durationMillis = exitMs, easing = emphasizedAccelerate),
                targetOffsetX = { (it * 0.08f).toInt() }
            ) + scaleOut(
                animationSpec = tween(durationMillis = exitMs, easing = emphasizedAccelerate),
                targetScale = exitScaleTarget
            )
        }
    }

    val startDest = if (onboardingCompleted) Screen.Forets.route else Screen.Onboarding.route

    NavHost(
        navController = navController,
        startDestination = startDest
    ) {
        composable(
            route = Screen.Onboarding.route,
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
        ) {
            val permissionsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { _ ->
                navController.navigate(Screen.Forets.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            }
            OnboardingScreen(
                onComplete = {
                    coroutineScope.launch {
                        app.userPreferences.setOnboardingCompleted(true)
                    }
                    val permissions = buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.CAMERA)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.READ_MEDIA_IMAGES)
                        } else {
                            add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }
                    permissionsLauncher.launch(permissions.toTypedArray())
                }
            )
        }

        // Forests selection using existing Groups screen
        composable(
            route = Screen.Forets.route,
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
        ) {
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
            route = Screen.IbpProjects.route,
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
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
            route = Screen.EssenceDiam.route,
            arguments = listOf(
                navArgument("parcelleId") { type = NavType.StringType },
                navArgument("placetteId") { type = NavType.StringType },
                navArgument("essenceCode") { type = NavType.StringType }
            ),
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
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
        // Forestry flow
        composable(
            route = Screen.Parcelles.route,
            arguments = listOf(navArgument("forestId") { type = NavType.StringType }),
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
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
                }
            )
        }

        composable(
            route = Screen.Placettes.route,
            arguments = listOf(navArgument("parcelleId") { type = NavType.StringType }),
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
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
                onNavigateToRipisylve = { pid ->
                    navController.navigate(Screen.RipisylveDiagnostic.createRoute(pid))
                },
                onNavigateToStation = { pid ->
                    navController.navigate(Screen.StationDiagnostic.createRoute(pid))
                }
            )
        }

        composable(
            route = Screen.PlacetteDetail.route,
            arguments = listOf(
                navArgument("parcelleId") { type = NavType.StringType },
                navArgument("placetteId") { type = NavType.StringType }
            ),
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
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
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
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
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
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
                ibpRepository = app.ibpRepository,
                ripisylveRepository = app.ripisylveRepository,
                stationRepository = app.stationRepository,
                preferencesManager = app.userPreferences,
                offlineTileManager = app.offlineTileManager,
                onNavigateBack = { navController.popBackStack() },
                initialNavLat = navLat,
                initialNavLon = navLon,
                initialNavEssence = navEssence,
                initialNavDiam = navDiam,
                onNavigateToIbpDiagnostic = { pid ->
                    navController.navigate(Screen.IbpDiagnostic.createRoute(pid))
                },
                onNavigateToRipisylveDiagnostic = { pid ->
                    navController.navigate(Screen.RipisylveDiagnostic.createRoute(pid))
                },
                onNavigateToStationDiagnostic = { pid ->
                    navController.navigate(Screen.StationDiagnostic.createRoute(pid))
                }
            )
        }

        composable(
            route = Screen.Dashboard.route,
            arguments = listOf(navArgument("parcelleId") { type = NavType.StringType }),
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
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
            route = Screen.IbpStandalone.route,
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
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
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
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
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
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
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
        ) {
            IbpReferenceScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.IbpDiagnostic.route,
            arguments = listOf(navArgument("parcelleId") { type = NavType.StringType }),
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
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
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
        ) { backStackEntry ->
            val parcelleId = backStackEntry.arguments?.getString("parcelleId") ?: return@composable
            IbpCompareScreen(
                parcelleId = parcelleId,
                ibpRepository = app.ibpRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Groups.route,
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
        ) {
            GroupsScreen(
                groupRepository = app.groupRepository,
                onNavigateToGroup = { groupId ->
                    navController.navigate(Screen.GroupDetail.createRoute(groupId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                preferencesManager = app.userPreferences,
                onNavigateToIbp = { navController.navigate(Screen.IbpProjects.route) }
            )
        }

        composable(
            route = Screen.GroupDetail.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            GroupScreen(
                groupId = groupId,
                groupRepository = app.groupRepository,
                counterRepository = app.counterRepository,
                formulaRepository = app.formulaRepository,
                exportDataUseCase = app.exportDataUseCase,
                importDataUseCase = app.importDataUseCase,
                preferencesManager = app.userPreferences,
                onNavigateToFormulas = { navController.navigate(Screen.Formulas.createRoute(groupId)) },
                onNavigateToCalculator = { navController.navigate(Screen.Calculator.createRoute(groupId)) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Formulas.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            FormulasScreen(
                groupId = groupId,
                formulaRepository = app.formulaRepository,
                preferencesManager = app.userPreferences,
                onNavigateToCalculator = { navController.navigate(Screen.Calculator.createRoute(groupId)) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Settings.route,
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
        ) {
            SettingsScreen(
                preferencesManager = app.userPreferences,
                exportDataUseCase = app.exportDataUseCase,
                parameterRepository = app.parameterRepository,
                tigeRepository = app.tigeRepository,
                essenceRepository = app.essenceRepository,
                forestryCalculator = app.forestryCalculator,
                parcelleRepository = app.parcelleRepository,
                placetteRepository = app.placetteRepository,
                offlineTileManager = app.offlineTileManager,
                onNavigateToPriceTablesEditor = { navController.navigate(Screen.PriceTablesEditor.route) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.PriceTablesEditor.route,
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
        ) {
            PriceTablesEditorScreen(
                parameterRepository = app.parameterRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Calculator.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            CalculatorScreen(
                groupId = groupId,
                counterRepository = app.counterRepository,
                formulaRepository = app.formulaRepository,
                formulaParser = app.formulaParser,
                preferencesManager = app.userPreferences,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.RipisylveDiagnostic.route,
            arguments = listOf(navArgument("parcelleId") { type = NavType.StringType }),
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
        ) { backStackEntry ->
            val parcelleId = backStackEntry.arguments?.getString("parcelleId") ?: return@composable
            RipisylveDiagnosticScreen(
                parcelleId = parcelleId,
                ripisylveRepository = app.ripisylveRepository,
                tigeRepository = app.tigeRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.StationDiagnostic.route,
            arguments = listOf(navArgument("parcelleId") { type = NavType.StringType }),
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition
        ) { backStackEntry ->
            val parcelleId = backStackEntry.arguments?.getString("parcelleId") ?: return@composable
            StationDiagnosticScreen(
                parcelleId = parcelleId,
                tigeRepository = app.tigeRepository,
                parcelleRepository = app.parcelleRepository,
                stationRepository = app.stationRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
