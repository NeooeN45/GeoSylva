package com.forestry.counter.presentation.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.forestry.counter.ForestryCounterApplication
import com.forestry.counter.R
import com.forestry.counter.presentation.screens.packs.PackOnboardingScreen
import kotlinx.coroutines.CancellationException

/**
 * Sous-graphe Onboarding.
 *
 * Routes : [Screen.PackOnboarding] (two optional pack pages),
 * [Screen.Onboarding] (permissions bridge, no presentation or consent page).
 */
fun NavGraphBuilder.onboardingNavGraph(
    navController: NavController,
    app: ForestryCounterApplication,
    transitions: NavTransitions,
) {
    composable(
        route = Screen.PackOnboarding.route,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) {
        PackOnboardingScreen(onContinue = {
            navController.navigate(Screen.Onboarding.route) {
                popUpTo(Screen.PackOnboarding.route) { inclusive = true }
            }
        })
    }
    composable(
        route = Screen.Onboarding.route,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) {
        val context = LocalContext.current
        var permissionsRequested by rememberSaveable { mutableStateOf(false) }
        var permissionsAnswered by rememberSaveable { mutableStateOf(false) }
        var completionAttempt by rememberSaveable { mutableStateOf(0) }
        var completionFailed by rememberSaveable { mutableStateOf(false) }
        val permissionsLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ -> permissionsAnswered = true }
        LaunchedEffect(permissionsAnswered, completionAttempt) {
            if (permissionsAnswered) {
                completionFailed = false
                // This marks the app setup and guided tour only. It neither
                // records privacy consent nor treats a denied permission as granted.
                // The answer survives recreation so an interrupted write is retried.
                try {
                    app.userPreferences.completeOnboardingAndScheduleCoachMarkTour()
                    navController.navigate(BottomNavDestination.startRoute) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                        launchSingleTop = true
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    completionFailed = true
                }
            } else {
                val permissions = buildList {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        // Android 12+ requires coarse and precise location together.
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        add(Manifest.permission.CAMERA)
                    }
                }
                if (permissions.isEmpty()) permissionsAnswered = true
                else if (!permissionsRequested) {
                    permissionsRequested = true
                    permissionsLauncher.launch(permissions.toTypedArray())
                }
            }
        }
        // No legacy presentation remains between the useful Android prompts
        // and Home. Imports use the system document/image picker rather than
        // requesting global gallery access at startup.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (completionFailed) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.first_entry_finish_error))
                    Button(onClick = { completionAttempt++ }) { Text(stringResource(R.string.retry)) }
                }
            } else CircularProgressIndicator()
        }
    }
}
