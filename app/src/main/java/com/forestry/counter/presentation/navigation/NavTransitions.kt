package com.forestry.counter.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavBackStackEntry

/**
 * Regroupe les 4 lambdas de transition Material 3 Emphasized utilisées par
 * tous les sous-graphes de navigation.
 *
 * Créé à l'intérieur de [ForestryNavigation] via [NavTransitions.build] afin
 * de capturer l'état `animationsEnabled` issu du ViewModel/preferences.
 */
data class NavTransitions(
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
)
