package com.forestry.counter.presentation.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.forestry.counter.ForestryCounterApplication
import com.forestry.counter.presentation.coachmark.CoachMarkOverlay
import com.forestry.counter.presentation.coachmark.coachMarkAnchor
import com.forestry.counter.presentation.coachmark.rememberCoachMarkAnchorRegistry
import com.forestry.counter.presentation.coachmark.rememberCoachMarkController
import com.forestry.counter.presentation.coachmark.shouldStartCoachMarkTour
import com.forestry.counter.presentation.screens.settings.SettingsHomeScreen
import com.forestry.counter.presentation.screens.common.ComingSoonScreen
import com.forestry.counter.presentation.screens.explorer.ExplorerCategory
import com.forestry.counter.presentation.screens.explorer.ExplorerScreen
import com.forestry.counter.presentation.screens.home.HomeScreen
import com.forestry.counter.presentation.screens.home.HomeViewModel
import com.forestry.counter.presentation.theme.Motion
import com.forestry.counter.presentation.theme.Space
import kotlinx.coroutines.launch

/**
 * Scaffold principal avec bottom navigation 5 entrées — spec GEOSYLVA-003 §29.3.
 *
 * Sur les 5 destinations de premier niveau, la barre complète reste
 * affichée en permanence. Sur une sous-page (Forêts, Projets, fiche
 * forêt…), elle disparaît entièrement — plus de FAB masqué, plus de menu
 * qu'on ne peut pas refermer — remplacée par [CollapsedMiniNav], une
 * pastille compacte en bas à gauche, à l'écart de tout FAB, qui se déplie
 * sur place et se referme d'un tap.
 *
 * Un essai a rendu la barre repliable même sur les onglets de premier
 * niveau ; revenu en arrière sur demande — la barre y reste permanente.
 */
@Composable
fun MainScaffold(
    navController: NavHostController,
    app: ForestryCounterApplication,
    hideBottomBar: Boolean = false,
    content: @Composable (Modifier) -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentDestination = BottomNavDestination.fromRoute(currentRoute)
    val isTopLevel = currentDestination != null
    // Écrans d'avant-entrée dans l'app (connexion, sélection du métier,
    // onboarding) : ni barre complète, ni mini-menu. Le mini-menu
    // s'affichait par-dessus le bouton "Suivant" de l'onboarding — un tap
    // dessus faisait sauter directement dans l'app, sautant le reste du
    // parcours de bienvenue.
    val isPreEntry = currentRoute in setOf(
        Screen.Welcome.route,
        Screen.ProfessionSelection.route,
        Screen.Onboarding.route,
        Screen.PackWizard.route,
        Screen.Login.route,
        Screen.PasswordRecovery.route,
    )
    val isSettingsRoute = currentRoute != null &&
        (currentRoute.startsWith("settings") || currentRoute == Screen.PackManager.route)

    // Visite guidée (coachmarks) des 5 onglets — se déclenche une seule
    // fois, dès qu'on atteint un écran de premier niveau après que
    // `coachMarkTourPending` a été armé (juste après les autorisations
    // GPS/caméra/galerie qui suivent l'onboarding, voir OnboardingNavGraph).
    val coachMarkController = rememberCoachMarkController()
    val coachMarkAnchors = rememberCoachMarkAnchorRegistry()
    val coachMarkPending by app.userPreferences.coachMarkTourPending.collectAsStateWithLifecycle(initialValue = false)
    val coachMarkCompleted by app.userPreferences.coachMarkTourCompleted.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()

    LaunchedEffect(coachMarkPending, coachMarkCompleted) {
        if (coachMarkCompleted && coachMarkPending) {
            app.userPreferences.setCoachMarkTourPending(false)
        }
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            // `saveState`/`restoreState` mémorisaient l'état de toute la
            // portion de pile entre le départ et l'onglet quitté — y compris
            // les sous-pages poussées par-dessus (Forêts, Projets...).
            // Revenir sur Explorer rouvrait alors la dernière sous-page
            // visitée au lieu de la grille. Toujours repartir propre.
            popUpTo(navController.graph.startDestinationId) {
                inclusive = false
            }
            launchSingleTop = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                AnimatedVisibility(
                    visible = isTopLevel && !hideBottomBar,
                    enter = slideInVertically(animationSpec = tween(220)) { it } + fadeIn(tween(220)),
                    exit = slideOutVertically(animationSpec = tween(180)) { it } + fadeOut(tween(180)),
                ) {
                    NavigationBar {
                        BottomNavDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = { navigateToTab(destination.route) },
                                modifier = Modifier.coachMarkAnchor(
                                    registry = coachMarkAnchors,
                                    key = "nav_${destination.route}",
                                ),
                                icon = {
                                    Icon(
                                        imageVector = if (currentRoute == destination.route)
                                            destination.selectedIcon else destination.unselectedIcon,
                                        contentDescription = destination.label,
                                    )
                                },
                                label = {
                                    Text(
                                        destination.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            content(Modifier.padding(innerPadding))
        }

        if (!isTopLevel && !isPreEntry && !isSettingsRoute) {
            CollapsedMiniNav(
                currentRoute = currentRoute,
                onNavigateToTab = ::navigateToTab,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        CoachMarkOverlay(
            anchors = coachMarkAnchors,
            step = coachMarkController.currentStep,
            stepIndex = coachMarkController.stepIndex ?: 0,
            totalSteps = com.forestry.counter.presentation.coachmark.COACH_MARK_STEPS.size,
            onNext = {
                coachMarkController.next()
                if (!coachMarkController.isActive) {
                    scope.launch { app.userPreferences.setCoachMarkTourCompleted(true) }
                }
            },
            onSkip = {
                coachMarkController.stop()
                scope.launch { app.userPreferences.setCoachMarkTourCompleted(true) }
            },
        )
    }
}

/**
 * Mini-navigation repliée — remplace l'ancienne barre complète "révélée"
 * qui, une fois ouverte, ne pouvait plus se refermer et masquait le FAB de
 * l'écran (ex. "Créer un groupe"). Centrée, légèrement surélevée par
 * rapport au bord ; repliée par défaut sur une simple icône. Le bouton
 * bascule reste ancré au centre — l'ouverture est horizontale et
 * symétrique de part et d'autre : la pastille entière est centrée
 * (`Alignment.BottomCenter`), donc en grandissant vers la largeur des 5
 * destinations, elle s'étend d'autant à gauche qu'à droite plutôt que de
 * déraper d'un côté.
 */
@Composable
private fun CollapsedMiniNav(
    currentRoute: String?,
    onNavigateToTab: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(currentRoute) { expanded = false }

    Surface(
        modifier = modifier
            .padding(bottom = 36.dp)
            .animateContentSize(animationSpec = Motion.springSnappy()),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.height(56.dp).padding(horizontal = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(180)) + expandHorizontally(tween(180)),
                exit = fadeOut(tween(140)) + shrinkHorizontally(tween(140)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BottomNavDestination.entries.forEach { destination ->
                        IconButton(onClick = { onNavigateToTab(destination.route) }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = if (currentRoute == destination.route)
                                    destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.label,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
            }
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Apps,
                    contentDescription = if (expanded) "Fermer la navigation" else "Ouvrir la navigation",
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

/**
 * Contenu des 5 onglets de premier niveau.
 *
 * Accueil affiche [HomeScreen] (tableau de bord).
 * Explorer liste les catégories de données.
 * Carte affiche la carte globale (scope "all", toutes forêts confondues).
 * Missions affiche encore [ComingSoonScreen] (aucune donnée métier dédiée).
 */
@Composable
fun TopLevelTabContent(
    route: String,
    app: ForestryCounterApplication,
    carteMode: com.forestry.counter.presentation.screens.forestry.CarteMode?,
    onCarteModeChange: (com.forestry.counter.presentation.screens.forestry.CarteMode?) -> Unit,
    onNavigateToExplorer: () -> Unit,
    onNavigateToForet: (String) -> Unit,
    onNavigateToForets: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSettingsCategory: (String) -> Unit,
    onCreateForest: () -> Unit,
    onCategoryClick: (ExplorerCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (route) {
        BottomNavDestination.ACCUEIL.route -> {
            val viewModel = HomeViewModel(app.foretRepository, app.parcelleRepository)
            HomeScreen(
                viewModel = viewModel,
                onNavigateToExplorer = onNavigateToExplorer,
                onNavigateToForet = onNavigateToForet,
                onCreateForest = onCreateForest,
                modifier = modifier,
            )
        }
        BottomNavDestination.EXPLORER.route -> {
            ExplorerScreen(
                onNavigateToForets = onNavigateToForets,
                onNavigateToProjects = onNavigateToProjects,
                onCategoryClick = onCategoryClick,
                modifier = modifier,
            )
        }
        BottomNavDestination.MISSIONS.route -> {
            ComingSoonScreen("Missions", modifier)
        }
        BottomNavDestination.CARTE.route -> {
            // Taper l'onglet Carte n'affiche jamais directement une carte :
            // on choisit d'abord un mode (Maps/Recherche/Libre), chacun une
            // page distincte. `navigateToTab` ne restaure pas l'état d'un
            // onglet quitté (pas de saveState/restoreState) : ce sélecteur
            // réapparaît donc à chaque fois qu'on revient sur l'onglet,
            // comme voulu — pas besoin de le forcer explicitement ici.
            // `carteMode` est remonté jusqu'à `ForestryNavigation` (pour
            // piloter le masquage plein écran de la bottom nav) ; on le
            // réinitialise ici au démontage de la branche, quand on quitte
            // l'onglet Carte, pour conserver ce même comportement.
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose { onCarteModeChange(null) }
            }
            when (carteMode) {
                null -> com.forestry.counter.presentation.screens.forestry.CarteModeSelectorScreen(
                    onSelectMode = onCarteModeChange,
                    modifier = modifier,
                )
                com.forestry.counter.presentation.screens.forestry.CarteMode.RECHERCHE -> {
                    // Base "mode Recherche" (esprit QField) : moteur MapLibre
                    // avec interface repensée (barre d'outils unique). Depuis
                    // la suppression de l'ancien MapScreen, c'est aussi le
                    // moteur unique utilisé pour Forêts→Parcelle→carte (voir
                    // ForestryFlowNavGraph). Un choix de zone
                    // (forêt/parcelle/placette) précède l'ouverture de la
                    // carte elle-même — état imbriqué, même pattern que
                    // `carteMode`, réinitialisé à la sortie de la branche.
                    var rechercheScope by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf<String?>(null)
                    }
                    when (rechercheScope) {
                        null -> com.forestry.counter.presentation.screens.forestry.RechercheScopeSelectorScreen(
                            groupRepository = app.groupRepository,
                            parcelleRepository = app.parcelleRepository,
                            placetteRepository = app.placetteRepository,
                            onSelectScope = { rechercheScope = it },
                            onBack = { onCarteModeChange(null) },
                            modifier = modifier,
                        )
                        else -> com.forestry.counter.presentation.screens.forestry.MapRechercheScreen(
                            parcelleId = rechercheScope!!,
                            tigeRepository = app.tigeRepository,
                            essenceRepository = app.essenceRepository,
                            parcelleRepository = app.parcelleRepository,
                            preferencesManager = app.userPreferences,
                            offlineTileManager = app.offlineTileManager,
                            onNavigateBack = { rechercheScope = null },
                            modifier = modifier,
                        )
                    }
                }
                com.forestry.counter.presentation.screens.forestry.CarteMode.MAPS -> {
                    com.forestry.counter.presentation.screens.forestry.CarteModeStubScreen(
                        title = stringResource(com.forestry.counter.R.string.carte_mode_maps_title),
                        description = stringResource(com.forestry.counter.R.string.carte_mode_maps_stub_desc),
                        icon = Icons.Default.Map,
                        onBack = { onCarteModeChange(null) },
                        modifier = modifier,
                    )
                }
                com.forestry.counter.presentation.screens.forestry.CarteMode.LIBRE -> {
                    // Même base que le mode Recherche (moteur MapLibre, calques,
                    // mesure, zoom), mais sans le sélecteur de zone préalable et
                    // sans tiges : parcelleId="none" fait déjà tourner
                    // tigesFlow à emptyList() dans MapRechercheScreen — aucune
                    // logique de filtrage ou de rendu de tiges à dupliquer ici.
                    com.forestry.counter.presentation.screens.forestry.MapRechercheScreen(
                        parcelleId = "none",
                        tigeRepository = app.tigeRepository,
                        preferencesManager = app.userPreferences,
                        offlineTileManager = app.offlineTileManager,
                        onNavigateBack = { onCarteModeChange(null) },
                        modifier = modifier,
                    )
                }
            }
        }
        BottomNavDestination.PARAMETRES.route -> {
            // L'onglet portait Compte jusqu'ici — un simple bouton engrenage
            // minuscule dans sa barre du haut était le seul accès aux
            // réglages. L'onglet héberge maintenant directement l'accueil
            // Réglages (recherche + catégories) ; Compte reste à une carte
            // de distance via la catégorie « Compte ».
            SettingsHomeScreen(
                onNavigateToSection = onNavigateToSettingsCategory,
                modifier = modifier,
            )
        }
    }
}
