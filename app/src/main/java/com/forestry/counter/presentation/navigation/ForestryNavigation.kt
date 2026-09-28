package com.forestry.counter.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.forestry.counter.ForestryCounterApplication
import com.forestry.counter.presentation.screens.account.LoginScreen
import com.forestry.counter.presentation.screens.explorer.ExplorerCategory
import kotlinx.coroutines.launch

// ─── Routes ──────────────────────────────────────────────────────────────────

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
    object SettingsHome : Screen("settings_home")
    object Settings : Screen("settings?section={section}") {
        fun createRoute(section: String? = null) =
            if (section != null) "settings?section=$section" else "settings"
    }
    object Account : Screen("settings/account")
    object Login : Screen("settings/account/login")
    object Register : Screen("settings/account/register")
    object PasswordRecovery : Screen("settings/account/password-recovery")
    object DeveloperOptions : Screen("settings/developer")
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
    object PlacetteEvolution : Screen("placette/{parcelleId}/{placetteId}/evolution/{year}") {
        fun createRoute(parcelleId: String, placetteId: String, year: Int) =
            "placette/$parcelleId/$placetteId/evolution/$year"
    }
    object Martelage : Screen("martelage/{scope}/{forestId}/{parcelleId}/{placetteId}") {
        fun forGlobal(): String = "martelage/GLOBAL/none/none/none"
        fun forForest(forestId: String): String = "martelage/FOREST/$forestId/none/none"
        fun forParcelle(parcelleId: String): String = "martelage/PARCELLE/none/$parcelleId/none"
        fun forPlacette(parcelleId: String, placetteId: String): String = "martelage/PLACETTE/none/$parcelleId/$placetteId"
    }
    object Map : Screen("map/{parcelleId}") {
        fun createRoute(parcelleId: String) = "map/$parcelleId"
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
    object DiagnosticMenu : Screen("diagnostic/menu/{parcelleId}") {
        fun createRoute(parcelleId: String) = "diagnostic/menu/$parcelleId"
    }
    object DiagnosticResult : Screen("diagnostic/result/{diagnosticId}") {
        fun createRoute(diagnosticId: String) = "diagnostic/result/$diagnosticId"
    }
    object Onboarding : Screen("onboarding")

    /**
     * Porte d'entrée de l'application, au tout premier lancement.
     *
     * Héberge [Screen.Login] avec une sémantique de démarrage : c'est le seul
     * écran porteur d'une vidéo, et le seul moment où GeoSylva se présente.
     * « Continuer hors ligne » y est toujours accessible — l'application doit
     * rester pleinement utilisable sans compte ni réseau.
     */
    object Welcome : Screen("welcome")
    /**
     * Sélection du métier — juste après connexion ou "Continuer hors
     * ligne", avant l'onboarding. Vue une seule fois par installation,
     * comme Welcome dont elle hérite la place dans le parcours.
     */
    object ProfessionSelection : Screen("profession_selection")
    object RipisylveDiagnostic : Screen("ripisylve/diagnostic/{parcelleId}") {
        fun createRoute(parcelleId: String) = "ripisylve/diagnostic/$parcelleId"
    }
    object RipisylveDiagnosticStandalone : Screen("ripisylve/standalone")
    object StandClassification : Screen("stand/classification/{parcelleId}") {
        fun createRoute(parcelleId: String) = "stand/classification/$parcelleId"
    }
    object TarifDocs : Screen("settings/tarif_docs")
    object PrivacyPolicy : Screen("settings/privacy_policy")
    object PackManager : Screen("packs")
    object SuperCorrelateur : Screen("super_correlateur/{parcelleId}") {
        fun createRoute(parcelleId: String) = "super_correlateur/$parcelleId"
    }
    object Projects : Screen("projects")
    object ProjectDetail : Screen("project/{projectId}") {
        fun createRoute(projectId: String) = "project/$projectId"
    }
    object ForestDetail : Screen("forest/{forestId}") {
        fun createRoute(forestId: String) = "forest/$forestId"
    }
    object CreateForest : Screen("forest/create")
    object EditForest : Screen("forest/edit/{forestId}") {
        fun createRoute(forestId: String) = "forest/edit/$forestId"
    }
    object Arbres : Screen("explorer/arbres")
    object Mesures : Screen("explorer/mesures")
    object Calculs : Screen("explorer/calculs")
    object CreateParcelle : Screen("parcelle/create/{forestId}") {
        fun createRoute(forestId: String) = "parcelle/create/$forestId"
    }
    object CreatePlacette : Screen("placette/create/{parcelleId}") {
        fun createRoute(parcelleId: String) = "placette/create/$parcelleId"
    }
}

// ─── Root navigation host ─────────────────────────────────────────────────────

@Composable
fun ForestryNavigation(app: ForestryCounterApplication) {
    val navController = rememberNavController()
    // Remonté depuis TopLevelTabContent (branche Carte) pour piloter le
    // masquage plein écran de la bottom nav sur les modes Maps/Recherche/
    // Libre — voir MainScaffold(hideBottomBar).
    var carteMode by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<com.forestry.counter.presentation.screens.forestry.CarteMode?>(null)
    }

    val animationsEnabled by app.userPreferences.animationsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val onboardingCompleted by app.userPreferences.onboardingCompleted.collectAsStateWithLifecycle(initialValue = true)

    // Courbes Material 3 Emphasized — spec 2024 (M3 expressive motion)
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    // Extra-smooth decelerate for pop-back (feels native)
    val spatialDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)

    // Durées : entrée 380ms, sortie 180ms, scale légèrement plus grand 0.96f
    val enterMs = 380
    val exitMs = 180
    val enterScaleInitial = 0.96f
    val exitScaleTarget = 0.97f   // quasi-invisible mais donne de la profondeur

    val transitions = NavTransitions(
        enter = {
            if (!animationsEnabled) EnterTransition.None
            else fadeIn(
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
        },
        exit = {
            if (!animationsEnabled) ExitTransition.None
            else fadeOut(
                animationSpec = tween(durationMillis = exitMs, easing = emphasizedAccelerate)
            ) + slideOutHorizontally(
                animationSpec = tween(durationMillis = exitMs, easing = emphasizedAccelerate),
                targetOffsetX = { -(it * 0.08f).toInt() }
            ) + scaleOut(
                animationSpec = tween(durationMillis = exitMs, easing = emphasizedAccelerate),
                targetScale = exitScaleTarget
            )
        },
        popEnter = {
            if (!animationsEnabled) EnterTransition.None
            else fadeIn(
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
        },
        popExit = {
            if (!animationsEnabled) ExitTransition.None
            else fadeOut(
                animationSpec = tween(durationMillis = exitMs, easing = emphasizedAccelerate)
            ) + slideOutHorizontally(
                animationSpec = tween(durationMillis = exitMs, easing = emphasizedAccelerate),
                targetOffsetX = { (it * 0.08f).toInt() }
            ) + scaleOut(
                animationSpec = tween(durationMillis = exitMs, easing = emphasizedAccelerate),
                targetScale = exitScaleTarget
            )
        },
    )

    // Premier lancement : connexion → onboarding → accueil.
    // Ensuite : accueil directement. La connexion n'est donc vue qu'une fois
    // par installation, ce qui justifie d'y placer la vidéo de présentation.
    val startDest = if (onboardingCompleted) BottomNavDestination.startRoute else Screen.Welcome.route

    MainScaffold(navController = navController, app = app, hideBottomBar = carteMode != null) { innerModifier ->
        NavHost(
            navController = navController,
            startDestination = startDest,
        ) {
            composable(
                route = Screen.Welcome.route,
                enterTransition = transitions.enter,
                exitTransition = transitions.exit,
                popEnterTransition = transitions.popEnter,
                popExitTransition = transitions.popExit,
            ) {
                val goToProfessionSelection = {
                    navController.navigate(Screen.ProfessionSelection.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
                LoginScreen(
                    repository = app.identityRepository,
                    onAuthenticated = goToProfessionSelection,
                    onContinueOffline = goToProfessionSelection,
                    onForgotPassword = { navController.navigate(Screen.PasswordRecovery.route) },
                    onCreateAccount = { navController.navigate(Screen.Register.route) },
                    animationsEnabled = animationsEnabled,
                    preferencesManager = app.userPreferences,
                )
            }

            composable(
                route = Screen.ProfessionSelection.route,
                enterTransition = transitions.enter,
                exitTransition = transitions.exit,
                popEnterTransition = transitions.popEnter,
                popExitTransition = transitions.popExit,
            ) {
                val scope = rememberCoroutineScope()
                com.forestry.counter.presentation.screens.onboarding.ProfessionSelectionScreen(
                    onComplete = { code, customText ->
                        scope.launch {
                            app.userPreferences.setUserProfession(code, customText)
                        }
                        navController.navigate(Screen.Onboarding.route) {
                            popUpTo(Screen.ProfessionSelection.route) { inclusive = true }
                        }
                    },
                )
            }

            // Lot 1 — 5 onglets de premier niveau (bottom nav)
            BottomNavDestination.entries.forEach { destination ->
                composable(
                    route = destination.route,
                    enterTransition = transitions.enter,
                    exitTransition = transitions.exit,
                    popEnterTransition = transitions.popEnter,
                    popExitTransition = transitions.popExit,
                ) {
                    TopLevelTabContent(
                        route = destination.route,
                        app = app,
                        carteMode = carteMode,
                        onCarteModeChange = { carteMode = it },
                        onNavigateToExplorer = {
                            navController.navigate(BottomNavDestination.EXPLORER.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToForet = { foretId ->
                            navController.navigate(Screen.ForestDetail.createRoute(foretId))
                        },
                        onNavigateToForets = {
                            navController.navigate(Screen.Forets.route)
                        },
                        onNavigateToProjects = {
                            navController.navigate(Screen.Projects.route)
                        },
                        onNavigateToLogin = {
                            navController.navigate(Screen.Login.route)
                        },
                        onNavigateToSettings = {
                            navController.navigate(Screen.SettingsHome.route)
                        },
                        onNavigateToSettingsCategory = { category ->
                            // Même logique que SettingsHomeScreen poussé en pile
                            // (SettingsNavGraph) : « compte » ouvre l'espace Compte
                            // lui-même, les autres catégories filtrent Réglages.
                            if (category == "compte") {
                                navController.navigate(Screen.Account.route)
                            } else {
                                navController.navigate(Screen.Settings.createRoute(category))
                            }
                        },
                        onCreateForest = {
                            navController.navigate(Screen.CreateForest.route)
                        },
                        onCategoryClick = { category ->
                            when (category) {
                                // Placettes et Diagnostics n'ont pas de vue globale
                                // propre : elles n'existent que rattachées à une
                                // parcelle. La liste globale des parcelles (déjà
                                // câblée vers Placettes/Diagnostic/Carte/Martelage/
                                // IBP par parcelle) est le point d'entrée commun.
                                ExplorerCategory.PARCELLES,
                                ExplorerCategory.PLACETTES,
                                ExplorerCategory.DIAGNOSTICS -> {
                                    navController.navigate(Screen.Parcelles.createRoute(null))
                                }
                                ExplorerCategory.ARBRES -> {
                                    navController.navigate(Screen.Arbres.route)
                                }
                                ExplorerCategory.MESURES -> {
                                    navController.navigate(Screen.Mesures.route)
                                }
                                ExplorerCategory.CALCULS -> {
                                    navController.navigate(Screen.Calculs.route)
                                }
                                else -> {}
                            }
                        },
                        modifier = innerModifier,
                    )
                }
            }

            onboardingNavGraph(navController, app, transitions)
            forestryFlowNavGraph(navController, app, transitions)
            ibpNavGraph(navController, app, transitions)
            diagnosticNavGraph(navController, app, transitions)
            settingsNavGraph(navController, app, transitions)
            projectsNavGraph(navController, app, transitions)
            forestDetailNavGraph(navController, app, transitions)

            // ── Explorer — catégories implémentées ───────────────────
            composable(
                route = Screen.Arbres.route,
                enterTransition = transitions.enter,
                exitTransition = transitions.exit,
                popEnterTransition = transitions.popEnter,
                popExitTransition = transitions.popExit,
            ) {
                com.forestry.counter.presentation.screens.explorer.ArbresScreen(
                    tigeRepository = app.tigeRepository,
                    essenceRepository = app.essenceRepository,
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Screen.Mesures.route,
                enterTransition = transitions.enter,
                exitTransition = transitions.exit,
                popEnterTransition = transitions.popEnter,
                popExitTransition = transitions.popExit,
            ) {
                com.forestry.counter.presentation.screens.explorer.MesuresScreen(
                    tigeRepository = app.tigeRepository,
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Screen.Calculs.route,
                enterTransition = transitions.enter,
                exitTransition = transitions.exit,
                popEnterTransition = transitions.popEnter,
                popExitTransition = transitions.popExit,
            ) {
                com.forestry.counter.presentation.screens.explorer.CalculsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMartelage = { navController.navigate(Screen.Martelage.forGlobal()) },
                    onNavigateToForets = { navController.navigate(Screen.Forets.route) },
                    onNavigateToIbp = { navController.navigate(Screen.IbpProjects.route) },
                )
            }
        }
    }
}
