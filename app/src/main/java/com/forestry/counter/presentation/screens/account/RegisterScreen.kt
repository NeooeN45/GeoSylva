package com.forestry.counter.presentation.screens.account

import androidx.compose.runtime.Composable
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.presentation.viewmodel.LoginMode

/**
 * Page native dédiée à la création du compte Quintessences.
 *
 * Le formulaire et sa validation restent mutualisés avec la connexion via
 * [LoginScreen] et [LoginMode.REGISTER] ; cette route ne duplique donc ni le
 * dépôt d'identité, ni les règles de validation, ni le coffre de session.
 */
@Composable
fun RegisterScreen(
    repository: IdentityRepository,
    onAuthenticated: () -> Unit,
    onContinueOffline: () -> Unit,
    onNavigateBack: () -> Unit,
    animationsEnabled: Boolean = true,
    preferencesManager: UserPreferencesManager? = null,
) {
    LoginScreen(
        repository = repository,
        onAuthenticated = onAuthenticated,
        onContinueOffline = onContinueOffline,
        onForgotPassword = onNavigateBack,
        animationsEnabled = animationsEnabled,
        preferencesManager = preferencesManager,
        initialMode = LoginMode.REGISTER,
        onCreateAccount = {},
        onNavigateBackFromRegistration = onNavigateBack,
    )
}
