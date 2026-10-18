package com.forestry.counter.presentation.navigation

import com.forestry.counter.R
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Destinations de la bottom navigation principale — spec GEOSYLVA-003 §29.3.
 *
 * 5 entrées : Accueil, Explorer, Missions, Carte, Paramètres.
 *
 * Pendant Lot 1, Missions / Carte sont des stubs "À venir". Paramètres
 * héberge l'accueil Réglages (recherche + catégories) ; Compte reste
 * atteignable via sa catégorie dédiée, à une carte de distance.
 * Accueil et Explorer sont implémentés respectivement en Sprint 3.2 et 3.3.
 */
enum class BottomNavDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    /** Indique si la destination est implémentée (false = stub "À venir"). */
    val isImplemented: Boolean = true,
) {
    ACCUEIL(
        route = "accueil",
        labelRes = R.string.journey_nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    EXPLORER(
        route = "explorer",
        labelRes = R.string.journey_nav_explorer,
        selectedIcon = Icons.Filled.Explore,
        unselectedIcon = Icons.Outlined.Explore,
    ),
    MISSIONS(
        route = "missions",
        labelRes = R.string.journey_nav_missions,
        // Partageait Icons.Filled.Map avec Carte — les deux étaient
        // strictement identiques dans la barre du bas comme dans le
        // mini-menu des sous-pages.
        selectedIcon = Icons.Filled.Checklist,
        unselectedIcon = Icons.Outlined.Checklist,
        isImplemented = false,
    ),
    CARTE(
        route = "carte",
        labelRes = R.string.journey_nav_map,
        selectedIcon = Icons.Filled.Map,
        unselectedIcon = Icons.Outlined.Map,
    ),
    PARAMETRES(
        route = "parametres",
        // « Paramètres » (10 caractères, contre 5-8 pour les autres onglets)
        // était le seul libellé à passer sur deux lignes dans la barre du
        // bas. « Réglages » tient sur une ligne, comme ses voisins.
        labelRes = R.string.journey_nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    );

    companion object {
        /** Routes de premier niveau (qui affichent la bottom bar). */
        val topLevelRoutes: Set<String> = entries.map { it.route }.toSet()

        /** Route de démarrage par défaut. */
        val startRoute: String = ACCUEIL.route

        fun fromRoute(route: String?): BottomNavDestination? =
            entries.firstOrNull { it.route == route }
    }
}
