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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.forestry.counter.ForestryCounterApplication

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
    object DiagnosticMenu : Screen("diagnostic/menu/{parcelleId}") {
        fun createRoute(parcelleId: String) = "diagnostic/menu/$parcelleId"
    }
    object DiagnosticResult : Screen("diagnostic/result/{diagnosticId}") {
        fun createRoute(diagnosticId: String) = "diagnostic/result/$diagnosticId"
    }
    object Onboarding : Screen("onboarding")
    object RipisylveDiagnostic : Screen("ripisylve/diagnostic/{parcelleId}") {
        fun createRoute(parcelleId: String) = "ripisylve/diagnostic/$parcelleId"
    }
    object RipisylveDiagnosticStandalone : Screen("ripisylve/standalone")
    object StandClassification : Screen("stand/classification/{parcelleId}") {
        fun createRoute(parcelleId: String) = "stand/classification/$parcelleId"
    }
    object TarifDocs : Screen("settings/tarif_docs")
    object PackManager : Screen("packs")
    object SuperCorrelateur : Screen("super_correlateur/{parcelleId}") {
        fun createRoute(parcelleId: String) = "super_correlateur/$parcelleId"
    }
}

// ─── Root navigation host ─────────────────────────────────────────────────────

@Composable
fun ForestryNavigation(app: ForestryCounterApplication) {
    val navController = rememberNavController()

    val animationsEnabled by app.userPreferences.animationsEnabled.collectAsState(initial = true)
    val onboardingCompleted by app.userPreferences.onboardingCompleted.collectAsState(initial = true)

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

    val startDest = if (onboardingCompleted) Screen.Forets.route else Screen.Onboarding.route

    NavHost(
        navController = navController,
        startDestination = startDest,
    ) {
        onboardingNavGraph(navController, app, transitions)
        forestryFlowNavGraph(navController, app, transitions)
        ibpNavGraph(navController, app, transitions)
        diagnosticNavGraph(navController, app, transitions)
        settingsNavGraph(navController, app, transitions)
    }
}
