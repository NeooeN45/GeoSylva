package com.forestry.counter.presentation.screens.account

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forestry.counter.R
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.data.remote.identity.GoogleCredentialClient
import com.forestry.counter.domain.model.IdentityProvider
import com.forestry.counter.domain.model.ProviderAvailability
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.presentation.components.LanguageSuggestionDialog
import com.forestry.counter.presentation.components.VideoBackdrop
import com.forestry.counter.presentation.components.shouldSuggestFrench
import com.forestry.counter.presentation.screens.settings.PrivacyPolicyScreen
import com.forestry.counter.presentation.theme.Elevation
import com.forestry.counter.presentation.theme.FieldBorderOnMedia
import com.forestry.counter.presentation.theme.FieldOnMedia
import com.forestry.counter.presentation.theme.GreenDeepOnMedia
import com.forestry.counter.presentation.theme.GreenOnMedia
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Motion
import com.forestry.counter.presentation.theme.OnGreenOnMedia
import com.forestry.counter.presentation.theme.PlaceholderOnMedia
import com.forestry.counter.presentation.theme.Radius
import com.forestry.counter.presentation.theme.Space
import com.forestry.counter.presentation.theme.TextOnMedia
import com.forestry.counter.presentation.theme.TextSecondaryOnMedia
import com.forestry.counter.presentation.theme.Touch
import com.forestry.counter.presentation.viewmodel.GeoSylvaViewModelFactory
import com.forestry.counter.presentation.viewmodel.LoginMode
import com.forestry.counter.presentation.viewmodel.LoginUiState
import com.forestry.counter.presentation.viewmodel.LoginViewModel
import com.forestry.counter.presentation.viewmodel.RegistrationStep
import kotlinx.coroutines.launch

/** Connexion vidéo conservée ; inscription native sur un fond de verre distinct. */
@Composable
fun LoginScreen(
    repository: IdentityRepository,
    onAuthenticated: () -> Unit,
    onContinueOffline: () -> Unit,
    onForgotPassword: () -> Unit,
    animationsEnabled: Boolean = true,
    preferencesManager: UserPreferencesManager? = null,
) {
    val factory = remember(repository) { GeoSylvaViewModelFactory { LoginViewModel(repository) } }
    val viewModel: LoginViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val googleClient = remember(context) { GoogleCredentialClient(context) }
    var showPrivacyPolicy by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.completed) {
        if (state.completed) onAuthenticated()
    }

    BackHandler(enabled = showPrivacyPolicy || state.mode == LoginMode.REGISTER || state.isSubmitting) {
        if (!state.isSubmitting) {
            when {
                showPrivacyPolicy -> showPrivacyPolicy = false
                state.registrationStep == RegistrationStep.EMAIL -> viewModel.setMode(LoginMode.SIGN_IN)
                else -> viewModel.previousRegistrationStep()
            }
        }
    }

    if (showPrivacyPolicy) {
        MaterialTheme(colorScheme = identityLightColorScheme()) {
            PrivacyPolicyScreen(onNavigateBack = { showPrivacyPolicy = false })
        }
        return
    }

    if (preferencesManager != null) {
        LanguageSuggestionHost(preferencesManager)
    }

    if (state.mode == LoginMode.REGISTER && !state.isAwaitingMfa) {
        MaterialTheme(colorScheme = registrationGlassColorScheme()) {
            // La vidéo locale préfloutée assure le rendu dès Android API 24.
            // Ce voile de verre appartient uniquement au parcours d'inscription.
            VideoBackdrop(
                videoRes = R.raw.registration_nature,
                posterRes = R.drawable.registration_nature_poster,
                animationsEnabled = animationsEnabled,
                scrim = registrationGlassWash(),
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .consumeWindowInsets(WindowInsets.safeDrawing)
                            .imePadding(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Space.screenH, vertical = Space.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.registration_app_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            OfflineEscape(
                                onContinueOffline = onContinueOffline,
                                enabled = !state.isSubmitting,
                                onMedia = false,
                            )
                        }
                        RegistrationForm(
                            state = state,
                            onEmailChange = viewModel::setEmail,
                            onPasswordChange = viewModel::setPassword,
                            onPasswordConfirmationChange = viewModel::setPasswordConfirmation,
                            onDisplayNameChange = viewModel::setDisplayName,
                            onNext = viewModel::continueRegistration,
                            onPrevious = viewModel::previousRegistrationStep,
                            onBackToSignIn = { viewModel.setMode(LoginMode.SIGN_IN) },
                            onSubmit = viewModel::submit,
                            onPrivacyPolicy = { showPrivacyPolicy = true },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }
        return
    }

    VideoBackdrop(animationsEnabled = animationsEnabled) {
        Box(modifier = Modifier.fillMaxSize()) {
            OfflineEscape(
                onContinueOffline = onContinueOffline,
                enabled = !state.isSubmitting,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(Space.sm),
            )

            val bottomModifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .consumeWindowInsets(WindowInsets.safeDrawing)
                .imePadding()

            // Le second facteur remplace le formulaire au lieu de s'y ajouter :
            // à cette étape, l'utilisateur n'a qu'une seule chose à faire.
            if (state.isAwaitingMfa) {
                MfaForm(
                    state = state,
                    onCodeChange = viewModel::setMfaCode,
                    onToggleRecovery = viewModel::setMfaUsesRecoveryCode,
                    onSubmit = viewModel::submitMfa,
                    onCancel = viewModel::cancelMfa,
                    modifier = bottomModifier,
                )
            } else {
                LoginForm(
                    state = state,
                    googleClientConfigured = repository.isGoogleClientConfigured,
                    onEmailChange = viewModel::setEmail,
                    onPasswordChange = viewModel::setPassword,
                    onSubmit = viewModel::submit,
                    onGoogle = { viewModel.signInWithGoogle(googleClient) },
                    onForgotPassword = onForgotPassword,
                    onCreateAccount = { viewModel.setMode(LoginMode.REGISTER) },
                    modifier = bottomModifier,
                )
            }
        }
    }
}

/** Le verre garde les textes foncés lisibles quelle que soit la scène. */
@Composable
private fun registrationGlassColorScheme() = identityLightColorScheme().copy(
    primary = com.forestry.counter.presentation.theme.OnPrimaryContainer,
    onSurfaceVariant = com.forestry.counter.presentation.theme.OnSurface,
    error = com.forestry.counter.presentation.theme.OnErrorDark,
)

/** Une surface de verre transparente et continue sur toute la page. */
private fun registrationGlassWash(): Brush = Brush.verticalGradient(
    0f to com.forestry.counter.presentation.theme.SurfaceBright.copy(alpha = 0.80f),
    0.30f to com.forestry.counter.presentation.theme.SurfaceBright.copy(alpha = 0.70f),
    0.68f to com.forestry.counter.presentation.theme.SurfaceBright.copy(alpha = 0.74f),
    1f to com.forestry.counter.presentation.theme.SurfaceBright.copy(alpha = 0.82f),
)

@Composable
private fun identityLightColorScheme() = MaterialTheme.colorScheme.copy(
    primary = com.forestry.counter.presentation.theme.Primary,
    onPrimary = com.forestry.counter.presentation.theme.OnPrimary,
    surface = com.forestry.counter.presentation.theme.SurfaceBright,
    onSurface = com.forestry.counter.presentation.theme.OnSurface,
    onSurfaceVariant = com.forestry.counter.presentation.theme.OnSurfaceVariant,
    surfaceVariant = com.forestry.counter.presentation.theme.SurfaceVariant,
    outline = com.forestry.counter.presentation.theme.Outline,
    outlineVariant = com.forestry.counter.presentation.theme.OutlineVariant,
    error = com.forestry.counter.presentation.theme.Error,
)

@Composable
private fun LanguageSuggestionHost(preferencesManager: UserPreferencesManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appLanguage by preferencesManager.appLanguage.collectAsStateWithLifecycle(initialValue = "fr")
    val answered by preferencesManager.languageSuggestionAnswered.collectAsStateWithLifecycle(initialValue = true)
    val shouldShow = !answered && shouldSuggestFrench(context, appLanguage)
    if (shouldShow) {
        LanguageSuggestionDialog(
            onAccept = {
                scope.launch {
                    preferencesManager.setAppLanguage("fr")
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fr"))
                    preferencesManager.setLanguageSuggestionAnswered(true)
                }
            },
            onDecline = {
                scope.launch { preferencesManager.setLanguageSuggestionAnswered(true) }
            },
        )
    }
}

@Composable
private fun OfflineEscape(
    onContinueOffline: () -> Unit,
    enabled: Boolean,
    onMedia: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = if (onMedia) modifier
            .background(FieldOnMedia, GsShape.pill)
            .padding(horizontal = Space.xxs) else modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onContinueOffline, enabled = enabled) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                tint = if (onMedia) TextSecondaryOnMedia else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Space.md),
            )
            Spacer(Modifier.size(Space.xs))
            Text(
                text = stringResource(R.string.identity_continue_offline),
                color = if (onMedia) TextOnMedia else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun LoginForm(
    state: LoginUiState,
    googleClientConfigured: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoogle: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    // Une erreur ne s'affiche qu'après une action de l'utilisateur. Sans ce
    // garde-fou, l'échec du chargement des fournisseurs d'identité — normal
    // hors réseau, ce qui est le cas nominal en forêt — accueillait
    // l'utilisateur par un message rouge dès l'ouverture.
    var attempted by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg)
            .padding(bottom = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.identity_welcome),
            color = TextOnMedia,
            fontSize = 40.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Space.lg))

        MediaTextField(
            value = state.email,
            onValueChange = onEmailChange,
            placeholder = stringResource(R.string.identity_email),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                autoCorrect = false,
                imeAction = ImeAction.Next,
            ),
            enabled = !state.isSubmitting,
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
        )

        Spacer(Modifier.height(Space.sm))

        PasswordFieldOnMedia(
            value = state.password,
            onValueChange = onPasswordChange,
            enabled = !state.isSubmitting,
            onDone = { attempted = true; onSubmit() },
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onForgotPassword, enabled = !state.isSubmitting) {
                Text(
                    text = stringResource(R.string.identity_forgot_password),
                    color = TextSecondaryOnMedia,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(Space.xs))

        PrimaryMediaButton(
            label = stringResource(R.string.identity_sign_in),
            loading = state.isSubmitting,
            onClick = { attempted = true; onSubmit() },
        )

        // Seules les erreurs réellement provoquées par l'utilisateur sont
        // affichées. L'état du serveur GSIE n'apparaît plus au premier
        // lancement : ce n'est pas au premier écran d'exposer la configuration
        // du système.
        // Compte administrateur sans second facteur : l'application ne porte
        // pas ce parcours, mais elle doit dire pourquoi la connexion s'arrête.
        if (state.mfaSetupRequired) {
            Text(
                text = stringResource(R.string.identity_mfa_setup_required),
                color = TextOnMedia,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .padding(top = Space.sm),
            )
        }

        state.error?.takeIf { attempted }?.let { error ->
            IdentityErrorBanner(
                error = error,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .padding(top = Space.sm),
            )
        }

        Spacer(Modifier.height(Space.md))

        OrSeparator()

        Spacer(Modifier.height(Space.md))

        GoogleMediaButton(
            state = state,
            googleClientConfigured = googleClientConfigured,
            onGoogle = { attempted = true; onGoogle() },
        )

        Spacer(Modifier.height(Space.lg))

        CreateAccountRow(onCreateAccount = onCreateAccount, enabled = !state.isSubmitting)
    }
}

@Composable
private fun MfaForm(
    state: LoginUiState,
    onCodeChange: (String) -> Unit,
    onToggleRecovery: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg)
            .padding(bottom = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.identity_mfa_title),
            color = TextOnMedia,
            fontSize = 30.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Space.sm))

        Text(
            text = stringResource(
                if (state.mfaUsesRecoveryCode) R.string.identity_mfa_hint_recovery
                else R.string.identity_mfa_hint
            ),
            color = TextSecondaryOnMedia,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Space.lg))

        MediaTextField(
            value = state.mfaCode,
            onValueChange = onCodeChange,
            placeholder = stringResource(
                if (state.mfaUsesRecoveryCode) R.string.identity_mfa_recovery_code
                else R.string.identity_mfa_code
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (state.mfaUsesRecoveryCode) KeyboardType.Text
                               else KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            enabled = !state.isSubmitting,
            keyboardActions = KeyboardActions(onDone = { if (!state.isSubmitting) onSubmit() }),
        )

        state.error?.let { error ->
            Text(
                text = stringResource(identityErrorMessage(error)),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.sm),
            )
        }

        Spacer(Modifier.height(Space.md))

        PrimaryMediaButton(
            label = stringResource(R.string.identity_mfa_verify),
            loading = state.isSubmitting,
            onClick = onSubmit,
        )

        Spacer(Modifier.height(Space.xs))

        TextButton(onClick = { onToggleRecovery(!state.mfaUsesRecoveryCode) }, enabled = !state.isSubmitting) {
            Text(
                text = stringResource(
                    if (state.mfaUsesRecoveryCode) R.string.identity_mfa_use_totp
                    else R.string.identity_mfa_use_recovery
                ),
                color = GreenOnMedia,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        TextButton(onClick = onCancel, enabled = !state.isSubmitting) {
            Text(
                text = stringResource(R.string.identity_mfa_cancel),
                color = TextSecondaryOnMedia,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MediaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = PlaceholderOnMedia) },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        enabled = enabled,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(Radius.lg),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = FieldOnMedia,
            unfocusedContainerColor = FieldOnMedia,
            disabledContainerColor = FieldOnMedia,
            focusedTextColor = TextOnMedia,
            unfocusedTextColor = TextOnMedia,
            cursorColor = GreenOnMedia,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedTrailingIconColor = TextSecondaryOnMedia,
            unfocusedTrailingIconColor = TextSecondaryOnMedia,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(Touch.field),
    )
}

@Composable
private fun PasswordFieldOnMedia(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onDone: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    MediaTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        placeholder = stringResource(R.string.identity_password),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrect = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { if (enabled) onDone() }),
        visualTransformation = if (visible) VisualTransformation.None
                               else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }, enabled = enabled) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff
                                  else Icons.Default.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.identity_hide_password
                        else R.string.identity_show_password
                    ),
                )
            }
        },
    )
}

@Composable
private fun PrimaryMediaButton(
    label: String,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) Motion.PRESS_SCALE else 1f,
        animationSpec = Motion.springSnappy(),
        label = "appui-connexion",
    )
    val arrowShift by animateDpAsState(
        targetValue = if (pressed) Space.xxs else 0.dp,
        animationSpec = Motion.springBouncy(),
        label = "chevron-connexion",
    )

    Button(
        onClick = onClick,
        enabled = !loading,
        interactionSource = interaction,
        shape = GsShape.pill,
        contentPadding = PaddingValues(horizontal = Space.lg),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = OnGreenOnMedia,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = OnGreenOnMedia.copy(alpha = 0.6f),
        ),
        elevation = null,
        modifier = Modifier
            .fillMaxWidth()
            .height(Touch.fieldPrimary)
            .scale(scale)
            .shadow(
                elevation = if (pressed) Elevation.card else Elevation.overlay,
                shape = GsShape.pill,
                ambientColor = GreenOnMedia,
                spotColor = GreenOnMedia,
            )
            .background(
                brush = Brush.verticalGradient(
                    listOf(GreenOnMedia.copy(alpha = 0.98f), GreenDeepOnMedia),
                ),
                shape = GsShape.pill,
            ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(Space.lg),
                strokeWidth = Space.xxs / 2,
                color = OnGreenOnMedia,
            )
        } else {
            // Le libellé reste optiquement centré : le chevron est posé à
            // droite dans un espace de largeur égale au décalage de gauche.
            Spacer(Modifier.size(Space.lg))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier
                    .size(Space.lg)
                    .offset(x = arrowShift),
            )
        }
    }
}

@Composable
private fun OrSeparator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = FieldBorderOnMedia)
        Text(
            text = stringResource(R.string.identity_or),
            color = TextSecondaryOnMedia,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Space.sm),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = FieldBorderOnMedia)
    }
}

@Composable
private fun GoogleMediaButton(
    state: LoginUiState,
    googleClientConfigured: Boolean,
    onGoogle: () -> Unit,
) {
    val capability = state.providers.firstOrNull { it.provider == IdentityProvider.GOOGLE }
    val available = capability?.availability == ProviderAvailability.AVAILABLE &&
        googleClientConfigured

    OutlinedButton(
        onClick = onGoogle,
        enabled = available && !state.isSubmitting,
        shape = GsShape.pill,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = FieldOnMedia,
            contentColor = TextOnMedia,
            disabledContainerColor = FieldOnMedia.copy(alpha = 0.5f),
            disabledContentColor = TextSecondaryOnMedia.copy(alpha = 0.5f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(Touch.field),
    ) {
        // Le logo est affiché sans teinte : les couleurs de la marque Google
        // sont imposées et ne se dérivent pas de la palette GeoSylva.
        Image(
            painter = painterResource(R.drawable.ic_google_logo),
            contentDescription = null,
            modifier = Modifier.size(Space.lg),
        )
        Spacer(Modifier.size(Space.sm))
        Text(
            text = stringResource(R.string.identity_google_continue),
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun CreateAccountRow(onCreateAccount: () -> Unit, enabled: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.identity_no_account),
            color = TextSecondaryOnMedia,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onCreateAccount, enabled = enabled) {
            Text(
                text = stringResource(R.string.identity_signup_web).uppercase(),
                color = GreenOnMedia,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
