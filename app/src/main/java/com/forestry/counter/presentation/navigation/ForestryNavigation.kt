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
import androidx.compose.runtime.key
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
import kotlinx.coroutines.CancellationException

// ─── Routes ──────────────────────────────────────────────────────────────────

sealed class Screen(val route: String) {
    object Forets : Screen("forets")
    object ForestryLegacyGroups : Screen("forets/legacy-groups")
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
    object ParcelSyncSelection : Screen("settings/account/parcel-sync-selection")
    object Login : Screen("settings/account/login")
    object PasswordRecovery : Screen("settings/account/password-recovery")
    object DeveloperOptions : Screen("settings/developer")
    object PriceTablesEditor : Screen("settings/price_tables")
    object Parcelles : Screen("parcelles/{forestId}") {
        fun createRoute(forestId: String?) = "parcelles/${forestId ?: "none"}"
    }
    object Placettes : Screen("placettes/{parcelleId}") {
        fun createRoute(parcelleId: String) = "placettes/$parcelleId"
    }
    object Worksites : Screen("worksites")
    object FieldJournal : Screen("field/{kind}/{targetId}") {
        fun createRoute(target: com.forestry.counter.domain.field.FieldTarget) = "field/${target.kind.name}/${android.net.Uri.encode(target.id)}"
    }
    object WoodDepots : Screen("wood/depots")
    object WoodObjects : Screen("wood/depots/{depotId}/objects?objectId={objectId}") {
        fun createRoute(depotId: String, objectId: String? = null): String {
            com.forestry.counter.domain.model.requireWoodDepotId(depotId)
            if (objectId != null) require(java.util.UUID.fromString(objectId).toString() == objectId)
            return "wood/depots/$depotId/objects" + (objectId?.let { "?objectId=$it" } ?: "")
        }
    }
    object PlacetteDetail : Screen("placette/{parcelleId}/{placetteId}") {
        fun createRoute(parcelleId: String, placetteId: String) = "placette/$parcelleId/$placetteId"
    }
    object PlacetteEvolution : Screen("placette/{parcelleId}/{placetteId}/evolution/{year}") {
        fun createRoute(parcelleId: String, placetteId: String, year: Int) =
            "placette/$parcelleId/$placetteId/evolution/$year"
    }
    object Martelage : Screen("martelage/{scope}/{forestId}/{parcelleId}/{placetteId}?sessionId={sessionId}") {
        fun forGlobal(): String = "martelage/GLOBAL/none/none/none"
        fun forForest(forestId: String): String = "martelage/FOREST/$forestId/none/none"
        fun forParcelle(parcelleId: String, sessionId: String? = null): String =
            "martelage/PARCELLE/none/$parcelleId/none" +
                (sessionId?.let { "?sessionId=${android.net.Uri.encode(it)}" } ?: "")
        fun forPlacette(parcelleId: String, placetteId: String, sessionId: String? = null): String =
            "martelage/PLACETTE/none/$parcelleId/$placetteId" +
                (sessionId?.let { "?sessionId=${android.net.Uri.encode(it)}" } ?: "")
    }
    object Map : Screen("map/{parcelleId}") {
        fun createRoute(parcelleId: String) = "map/$parcelleId"
    }
    object EssenceDiam : Screen("placette/{parcelleId}/{placetteId}/essence/{essenceCode}") {
        fun createRoute(parcelleId: String, placetteId: String, essenceCode: String) = "placette/$parcelleId/$placetteId/essence/$essenceCode"
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
    object DiagnosticScopes : Screen("diagnostics/scopes")
    object StationDiagnostic : Screen("diagnostics/station/{parcelleId}") {
        fun createRoute(parcelleId: String) = "diagnostics/station/$parcelleId"
    }
    object DiagnosticResult : Screen("diagnostic/result/{diagnosticId}") {
        fun createRoute(diagnosticId: String) = "diagnostic/result/$diagnosticId"
    }
    /** Final first-entry permissions bridge; the six legacy presentation pages were removed. */
    object Onboarding : Screen("onboarding")
    object PackOnboarding : Screen("onboarding/packs")

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
     * Premier choix du parcours initial, avant Welcome/connexion.
     * Le métier est enregistré localement avant toute navigation ; cette
     * déclaration ne crée ni affiliation entreprise ni consentement.
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
    object ForestSyncSelection : Screen("settings/forest_sync")
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
    object ForestManagement : Screen("forest/{forestId}/management") {
        fun createRoute(forestId: String) = "forest/$forestId/management"
    }
    object CreateForest : Screen("forest/create")
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
    val session by app.identityRepository.session.collectAsStateWithLifecycle()
    // A new account receives a fresh controller and screen state; the previous
    // account's saved business destinations cannot be restored by the tab bar.
    key(session?.accountId) {
        ForestryIdentityNavigation(app)
    }
}

@Composable
private fun ForestryIdentityNavigation(app: ForestryCounterApplication) {
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

    // Premier lancement : métier → connexion → packs facultatifs → permissions → accueil.
    // Ensuite : accueil directement. La connexion n'est donc vue qu'une fois
    // par installation, ce qui justifie d'y placer la vidéo de présentation.
    val startDest = when {
        onboardingCompleted -> BottomNavDestination.startRoute
        app.identityRepository.session.value != null -> Screen.PackOnboarding.route
        else -> Screen.ProfessionSelection.route
    }

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
                LoginScreen(
                    repository = app.identityRepository,
                    onAuthenticated = {
                        navController.navigate(if (onboardingCompleted) BottomNavDestination.startRoute else Screen.PackOnboarding.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    },
                    onContinueOffline = {
                        navController.navigate(if (onboardingCompleted) BottomNavDestination.startRoute else Screen.Onboarding.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    },
                    onForgotPassword = { navController.navigate(Screen.PasswordRecovery.route) },
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
                val currentProfession by app.userPreferences.userProfession.collectAsStateWithLifecycle(initialValue = null)
                val currentProfessionText by app.userPreferences.userProfessionCustomText.collectAsStateWithLifecycle(initialValue = null)
                var savingProfession by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                var professionSaveFailed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                com.forestry.counter.presentation.screens.onboarding.ProfessionSelectionScreen(
                    isSaving = savingProfession,
                    saveFailed = professionSaveFailed,
                    initialCode = currentProfession,
                    initialCustomText = currentProfessionText,
                    onComplete = { code, customText ->
                        if (!savingProfession) {
                            savingProfession = true
                            professionSaveFailed = false
                            scope.launch {
                                try {
                                    val saved = try {
                                        app.userPreferences.setUserProfession(code, customText)
                                        true
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        false
                                    }
                                    if (saved) {
                                        if (onboardingCompleted) {
                                            navController.popBackStack()
                                        } else {
                                            navController.navigate(Screen.Welcome.route) {
                                                popUpTo(Screen.ProfessionSelection.route) { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        }
                                    } else professionSaveFailed = true
                                } finally {
                                    savingProfession = false
                                }
                            }
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
                        onNavigateToMap = { navController.navigate(BottomNavDestination.CARTE.route) { launchSingleTop = true } },
                        onNavigateToForet = { foretId ->
                            navController.navigate(Screen.ForestManagement.createRoute(foretId))
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
                            // lui-même et « packs » le gestionnaire hors ligne.
                            when (category) {
                                "metier" -> navController.navigate(Screen.ProfessionSelection.route)
                                "compte" -> navController.navigate(Screen.Account.route)
                                "packs" -> navController.navigate(Screen.PackManager.route)
                                else -> navController.navigate(Screen.Settings.createRoute(category))
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
                                    navController.navigate(Screen.DiagnosticScopes.route)
                                }
                                // Le reste n'a encore ni écran ni entrée dédiée
                                // (Lot 1) : rien à faire.
                                else -> {}
                            }
                        },
                        onNavigateToWoodDepots = { navController.navigate(Screen.WoodDepots.route) },
                        onNavigateToWorksites = { navController.navigate(Screen.Worksites.route) },
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
        }
    }
}
