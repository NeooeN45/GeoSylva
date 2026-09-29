package com.forestry.counter.presentation.screens.account

import androidx.compose.animation.core.animateFloatAsState
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.os.LocaleListCompat
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.presentation.components.LanguageSuggestionDialog
import com.forestry.counter.presentation.components.shouldSuggestFrench
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.forestry.counter.presentation.theme.Elevation
import com.forestry.counter.presentation.theme.GreenDeepOnMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forestry.counter.R
import com.forestry.counter.data.remote.identity.GoogleCredentialClient
import com.forestry.counter.domain.model.IdentityProvider
import com.forestry.counter.domain.model.ProviderAvailability
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.presentation.components.VideoBackdrop
import com.forestry.counter.presentation.theme.GreenOnMedia
import com.forestry.counter.presentation.theme.FieldBorderOnMedia
import com.forestry.counter.presentation.theme.FieldOnMedia
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

/**
 * Écran de connexion et de création de compte — porte d'entrée de GeoSylva.
 *
 * Registre consultation, poussé à son maximum : c'est le seul écran porteur
 * d'une vidéo, et le seul moment où l'application se présente avant que le
 * travail commence.
 *
 * Parti pris de composition : **aucune carte**. Les champs sont des surfaces
 * sombres translucides posées directement sur la vidéo, alignées en bas de
 * l'écran, dans la zone du pouce. La version précédente empilait le formulaire
 * dans un panneau presque opaque qui couvrait les deux tiers de l'image.
 *
 * La création de compte utilise le même socle visuel et le même dépôt
 * d'identité, mais possède une route native dédiée. GeoSylva reste utilisable
 * hors ligne : la création est proposée comme un service GSIE optionnel.
 */
@Composable
fun LoginScreen(
    repository: IdentityRepository,
    onAuthenticated: () -> Unit,
    onContinueOffline: () -> Unit,
    onForgotPassword: () -> Unit,
    animationsEnabled: Boolean = true,
    preferencesManager: UserPreferencesManager? = null,
    initialMode: LoginMode = LoginMode.SIGN_IN,
    onCreateAccount: () -> Unit = {},
    onNavigateBackFromRegistration: (() -> Unit)? = null,
) {
    val factory = remember(repository) { GeoSylvaViewModelFactory { LoginViewModel(repository) } }
    val viewModel: LoginViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val googleClient = remember(context) { GoogleCredentialClient(context) }

    LaunchedEffect(state.completed) {
        if (state.completed) onAuthenticated()
    }

    LaunchedEffect(initialMode) { viewModel.setMode(initialMode) }

    if (preferencesManager != null) {
        LanguageSuggestionHost(preferencesManager)
    }

    VideoBackdrop(animationsEnabled = animationsEnabled) {
        Box(modifier = Modifier.fillMaxSize()) {
            // En mode inscription, le bouton "hors ligne" n'a pas de sens :
            // on remplace par une simple flèche de retour.
            if (initialMode == LoginMode.REGISTER) {
                IconButton(
                    onClick = { onNavigateBackFromRegistration?.invoke() ?: onForgotPassword() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(Space.xs),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = TextOnMedia,
                    )
                }
            } else {
                OfflineEscape(
                    onContinueOffline = onContinueOffline,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(Space.sm),
                )
            }

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
                    onCreateAccount = onCreateAccount,
                    onBackToSignIn = {
                        if (initialMode == LoginMode.REGISTER && onNavigateBackFromRegistration != null) {
                            onNavigateBackFromRegistration()
                        } else {
                            viewModel.setMode(LoginMode.SIGN_IN)
                        }
                    },
                    onDisplayNameChange = viewModel::setDisplayName,
                    onPasswordConfirmationChange = viewModel::setPasswordConfirmation,
                    onTermsAcceptedChange = viewModel::setTermsAccepted,
                    onPrivacyAcceptedChange = viewModel::setPrivacyAccepted,
                    modifier = bottomModifier,
                )
            }
        }
    }
}

/**
 * Déclenche [LanguageSuggestionDialog] une fois, au tout premier lancement,
 * si le téléphone est localisé en France sans que l'application affiche déjà
 * le français. GeoSylva force le français par défaut : ce cas ne se présente
 * donc que si l'utilisateur a explicitement choisi une autre langue.
 */
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

/**
 * Sortie hors ligne, toujours accessible.
 *
 * GeoSylva fonctionne entièrement sans compte ni réseau : cette porte ne doit
 * jamais ressembler à un abandon. Elle est traitée comme un contrôle à part
 * entière, sur une pastille de verre, pas comme un lien discret.
 */
@Composable
private fun OfflineEscape(
    onContinueOffline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(FieldOnMedia, GsShape.pill)
            .padding(horizontal = Space.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onContinueOffline) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                tint = TextSecondaryOnMedia,
                modifier = Modifier.size(Space.md),
            )
            Spacer(Modifier.size(Space.xs))
            Text(
                text = stringResource(R.string.identity_continue_offline),
                color = TextOnMedia,
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
    onBackToSignIn: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onPasswordConfirmationChange: (String) -> Unit,
    onTermsAcceptedChange: (Boolean) -> Unit,
    onPrivacyAcceptedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val registering = state.mode == LoginMode.REGISTER
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
        if (registering) {
            // En mode inscription, le titre et le sous-titre sont regroupés
            // sur un fond translucide pour rester lisibles sur la vidéo.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FieldOnMedia, GsShape.md)
                    .padding(horizontal = Space.md, vertical = Space.sm),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.identity_create_account),
                        color = TextOnMedia,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        text = stringResource(R.string.identity_create_account_subtitle),
                        color = TextSecondaryOnMedia,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(Space.md))
        } else {
            Text(
                text = stringResource(R.string.identity_welcome),
                color = TextOnMedia,
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.lg))
        }

        if (registering) {
            MediaTextField(
                value = state.displayName,
                onValueChange = onDisplayNameChange,
                placeholder = stringResource(R.string.identity_display_name),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(Space.sm))
        }

        MediaTextField(
            value = state.email,
            onValueChange = onEmailChange,
            placeholder = stringResource(R.string.identity_email),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        )

        Spacer(Modifier.height(Space.sm))

        PasswordFieldOnMedia(
            value = state.password,
            onValueChange = onPasswordChange,
        )

        if (registering) {
            RegistrationPasswordGuidance(password = state.password)
        }

        if (registering) {
            Spacer(Modifier.height(Space.sm))
            MediaTextField(
                value = state.passwordConfirmation,
                onValueChange = onPasswordConfirmationChange,
                placeholder = stringResource(R.string.identity_password_confirmation),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                visualTransformation = PasswordVisualTransformation(),
            )

            RegistrationConsentFields(
                termsAccepted = state.termsAccepted,
                privacyAccepted = state.privacyAccepted,
                onTermsAcceptedChange = onTermsAcceptedChange,
                onPrivacyAcceptedChange = onPrivacyAcceptedChange,
            )
        }

        if (!registering) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onForgotPassword) {
                    Text(
                        text = stringResource(R.string.identity_forgot_password),
                        color = TextSecondaryOnMedia,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Spacer(Modifier.height(if (registering) Space.md else Space.xs))

        PrimaryMediaButton(
            label = stringResource(
                if (registering) R.string.identity_create_account
                else R.string.identity_sign_in
            ),
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
                    .padding(top = Space.sm),
            )
        }

        state.error?.takeIf { attempted }?.let { error ->
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

        if (registering) {
            // La page native revient à la connexion sans quitter GeoSylva.
            TextButton(
                onClick = onBackToSignIn,
                modifier = Modifier
                    .background(FieldOnMedia, GsShape.md),
            ) {
                Text(
                    text = stringResource(R.string.identity_mfa_cancel),
                    color = TextSecondaryOnMedia,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            OrSeparator()

            Spacer(Modifier.height(Space.md))

            GoogleMediaButton(
                state = state,
                googleClientConfigured = googleClientConfigured,
                onGoogle = { attempted = true; onGoogle() },
            )

            Spacer(Modifier.height(Space.lg))

            CreateAccountRow(onCreateAccount = onCreateAccount)
        }
    }
}

/** Consentements obligatoires, séparés et jamais pré-cochés. */
@Composable
private fun RegistrationConsentFields(
    termsAccepted: Boolean,
    privacyAccepted: Boolean,
    onTermsAcceptedChange: (Boolean) -> Unit,
    onPrivacyAcceptedChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.sm)
            .background(FieldOnMedia, GsShape.md)
            .padding(horizontal = Space.xs, vertical = Space.xxs),
        verticalArrangement = Arrangement.spacedBy(Space.xxs),
    ) {
        Text(
            text = stringResource(R.string.identity_consent_title),
            color = TextOnMedia,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xxs),
        )
        ConsentCheckboxRow(
            checked = termsAccepted,
            onCheckedChange = onTermsAcceptedChange,
            label = stringResource(R.string.identity_consent_terms),
        )
        ConsentCheckboxRow(
            checked = privacyAccepted,
            onCheckedChange = onPrivacyAcceptedChange,
            label = stringResource(R.string.identity_consent_privacy),
        )
        Text(
            text = stringResource(R.string.identity_consent_required),
            color = TextSecondaryOnMedia,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xxs),
        )
    }
}

@Composable
private fun ConsentCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xxs),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = label,
            color = TextOnMedia,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Second facteur — étape qui suit un mot de passe accepté mais insuffisant.
 *
 * Reprend exactement le vocabulaire visuel de l'écran de connexion : mêmes
 * champs sombres translucides sur la vidéo, même bouton vert. L'utilisateur
 * doit sentir qu'il poursuit la même action, pas qu'il change d'application.
 */
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

        TextButton(onClick = { onToggleRecovery(!state.mfaUsesRecoveryCode) }) {
            Text(
                text = stringResource(
                    if (state.mfaUsesRecoveryCode) R.string.identity_mfa_use_totp
                    else R.string.identity_mfa_use_recovery
                ),
                color = GreenOnMedia,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        TextButton(onClick = onCancel) {
            Text(
                text = stringResource(R.string.identity_mfa_cancel),
                color = TextSecondaryOnMedia,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Champ de saisie posé sur le média.
 *
 * Sans étiquette flottante ni contour Material : une surface sombre arrondie,
 * l'intitulé à l'intérieur. C'est ce qui permet de garder la vidéo lisible
 * derrière tout en gardant le texte saisi parfaitement contrasté.
 */
@Composable
private fun MediaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = PlaceholderOnMedia) },
        singleLine = true,
        keyboardOptions = keyboardOptions,
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
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    MediaTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = stringResource(R.string.identity_password),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        visualTransformation = if (visible) VisualTransformation.None
                               else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
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

/**
 * Repère de robustesse local, sans remplacer le contrôle serveur Argon2id / HIBP.
 * Il donne une réponse immédiate au moment de la saisie sans exposer le secret.
 */
@Composable
private fun RegistrationPasswordGuidance(password: String) {
    val checks = listOf(
        password.length >= 12,
        password.any(Char::isLetter),
        password.any(Char::isDigit),
    )
    val score = checks.count { it }
    val label = when {
        password.isEmpty() -> stringResource(R.string.identity_password_hint)
        score == checks.size -> stringResource(R.string.identity_password_strength_ready)
        else -> stringResource(R.string.identity_password_strength_weak)
    }

    // Fond translucide identique aux champs de saisie — le guide ne doit
    // pas flotter nu sur la vidéo comme le reste du formulaire en dessous.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.xs)
            .background(FieldOnMedia, GsShape.md),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.sm, vertical = Space.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.xxs),
            ) {
                checks.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(Space.xxs)
                            .background(
                                color = if (checks[index]) GreenOnMedia else FieldBorderOnMedia,
                                shape = GsShape.pill,
                            ),
                    )
                }
            }
            Text(
                text = label,
                color = if (score == checks.size) GreenOnMedia else TextSecondaryOnMedia,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = Space.xxs),
            )
            Text(
                text = stringResource(R.string.identity_registration_security),
                color = TextSecondaryOnMedia.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Space.xxs),
            )
        }
    }
}

/**
 * Action principale.
 *
 * Le vert clair est la seule surface pleine de l'écran : tout le reste est
 * translucide. La cible est donc évidente sans flèche directrice ni consigne.
 *
 * Trois détails qui distinguent ce bouton d'un aplat de couleur :
 * un dégradé vertical très léger qui lui donne du relief sans virer au
 * clinquant, une ombre portée verte qui le décolle de la vidéo, et un chevron
 * qui avance de quelques points à l'appui — le geste est confirmé visuellement
 * avant même que le réseau réponde.
 */
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
private fun CreateAccountRow(onCreateAccount: () -> Unit) {
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
        TextButton(onClick = onCreateAccount) {
            Text(
                text = stringResource(R.string.identity_signup_web).uppercase(),
                color = GreenOnMedia,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
