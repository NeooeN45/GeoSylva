package com.forestry.counter.presentation.navigation

import com.forestry.counter.domain.journey.JourneyFeature
import com.forestry.counter.domain.journey.NavigationAccessDecision
import com.forestry.counter.domain.journey.NavigationFeaturePolicy
import com.forestry.counter.presentation.screens.explorer.ExplorerCategory

/** UI composition only. Repositories and the server remain responsible for data access. */
object JourneyNavigation {
    // Only connected modules. Enterprise Missions still require a trusted server adapter.
    private val implemented = setOf(
        JourneyFeature.HOME, JourneyFeature.EXPLORER, JourneyFeature.MAP, JourneyFeature.SETTINGS,
        JourneyFeature.FORESTS, JourneyFeature.PARCELS, JourneyFeature.PLOTS,
        JourneyFeature.FOREST_DIAGNOSTICS, JourneyFeature.FOREST_SUMMARIES,
        JourneyFeature.WOOD_DEPOTS, JourneyFeature.WORKSITES,
    )

    fun access(professionCode: String?): NavigationAccessDecision = NavigationFeaturePolicy.evaluate(
        professionCode = professionCode,
        implementedFeatures = implemented,
        // No trusted enterprise evidence adapter exists. Never infer it from a selected profession.
        serverAccess = null,
        nowEpochSeconds = 0,
    )

    fun tabs(access: NavigationAccessDecision): List<BottomNavDestination> =
        BottomNavDestination.entries.filter { allowsTab(access, it) }

    fun allowsTab(access: NavigationAccessDecision, tab: BottomNavDestination): Boolean = access.allows(
        when (tab) {
            BottomNavDestination.ACCUEIL -> JourneyFeature.HOME
            BottomNavDestination.EXPLORER -> JourneyFeature.EXPLORER
            BottomNavDestination.CARTE -> JourneyFeature.MAP
            BottomNavDestination.PARAMETRES -> JourneyFeature.SETTINGS
            BottomNavDestination.MISSIONS -> JourneyFeature.MISSIONS
        },
    )

    /** Every listed category has a real navigation callback; no decorative dead destinations. */
    fun categories(access: NavigationAccessDecision): List<ExplorerCategory> = buildList {
        if (access.allows(JourneyFeature.FORESTS)) add(ExplorerCategory.FORETS)
        if (access.allows(JourneyFeature.PARCELS)) add(ExplorerCategory.PARCELLES)
        if (access.allows(JourneyFeature.PLOTS)) add(ExplorerCategory.PLACETTES)
        if (access.allows(JourneyFeature.FOREST_DIAGNOSTICS)) add(ExplorerCategory.DIAGNOSTICS)
    }
}
