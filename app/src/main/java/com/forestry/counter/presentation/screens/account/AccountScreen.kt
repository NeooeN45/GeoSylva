package com.forestry.counter.presentation.screens.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forestry.counter.R
import com.forestry.counter.data.remote.identity.GoogleCredentialClient
import com.forestry.counter.domain.model.AccountProfile
import com.forestry.counter.domain.model.AccountSession
import com.forestry.counter.domain.model.IdentityProvider
import com.forestry.counter.domain.model.ProviderAvailability
import com.forestry.counter.domain.model.ProviderCapability
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.presentation.account.AccountInputValidator
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Space
import com.forestry.counter.presentation.theme.Touch
import com.forestry.counter.presentation.viewmodel.AccountNotice
import com.forestry.counter.presentation.viewmodel.AccountUiState
import com.forestry.counter.presentation.viewmodel.AccountViewModel
import com.forestry.counter.presentation.viewmodel.GeoSylvaViewModelFactory
import java.text.DateFormat
import java.util.Date

/** Gestion du compte GSIE depuis Réglages, sans déclencher de synchronisation de relevés. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    repository: IdentityRepository,
    onNavigateToLogin: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onNavigateToParcelSync: (() -> Unit)? = null,
    onNavigateToForestSync: (() -> Unit)? = null,
) {
    val factory = remember(repository) { GeoSylvaViewModelFactory { AccountViewModel(repository) } }
    val viewModel: AccountViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val googleClient = remember(context) { GoogleCredentialClient(context) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_management_title)) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(R.string.cd_back),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.refreshAccount()
                        },
                        enabled = !state.isWorking && !state.isLoggingOut &&
                            !state.isLoadingProviders && !state.isRefreshingProfile &&
                            state.mfaChallengeToken == null && !state.mfaSetupRequired,
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.account_management_refresh),
                        )
                    }
                },
            )
        },
    ) { padding ->
        AccountContent(
            state = state,
            onLogin = onNavigateToLogin,
            onLogout = viewModel::logout,
            onUpdateDisplayName = viewModel::updateDisplayName,
            onVerificationCodeChange = viewModel::setVerificationCode,
            onRequestVerification = viewModel::requestEmailVerification,
            onConfirmVerification = viewModel::confirmEmailVerification,
            onLinkGoogle = { viewModel.linkGoogle(googleClient) },
            onRefresh = viewModel::refreshAccount,
            onMfaCodeChange = viewModel::setMfaCode,
            onMfaUsesRecoveryCodeChange = viewModel::setMfaUsesRecoveryCode,
            onSubmitMfa = viewModel::submitMfa,
            onCancelMfa = viewModel::cancelMfa,
            googleClientConfigured = repository.isGoogleClientConfigured,
            onNavigateToParcelSync = onNavigateToParcelSync,
            onNavigateToForestSync = onNavigateToForestSync,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun AccountContent(
    state: AccountUiState,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onUpdateDisplayName: (String?) -> Unit,
    onVerificationCodeChange: (String) -> Unit,
    onRequestVerification: () -> Unit,
    onConfirmVerification: () -> Unit,
    onLinkGoogle: () -> Unit,
    onRefresh: () -> Unit,
    onMfaCodeChange: (String) -> Unit,
    onMfaUsesRecoveryCodeChange: (Boolean) -> Unit,
    onSubmitMfa: () -> Unit,
    onCancelMfa: () -> Unit,
    googleClientConfigured: Boolean,
    onNavigateToParcelSync: (() -> Unit)?,
    onNavigateToForestSync: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val session = state.session
    // A profile from the previous account must never appear during a session change.
    val profile = state.profile?.takeIf { it.accountId == session?.accountId }
    val busy = state.isWorking || state.isLoggingOut || state.isRefreshingProfile
    val awaitingMfa = state.mfaChallengeToken != null || state.mfaSetupRequired
    val canManageAccount = session != null && !session.isExpired() && !busy && !awaitingMfa

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.screenH, vertical = Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item { AccountHero(session, profile) }
        state.error?.let { error -> item { IdentityErrorBanner(error) } }
        state.notice?.let { notice -> item { AccountNoticeCard(notice) } }
        if (state.isWorking || state.isLoggingOut || state.isRefreshingProfile) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Space.xs),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(Space.lg), strokeWidth = Space.xxs)
                    Text(
                        stringResource(
                            if (state.isLoggingOut) R.string.account_management_logging_out
                            else if (state.isRefreshingProfile) R.string.account_management_refreshing
                            else R.string.account_management_working
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (state.mfaChallengeToken != null) {
            item {
                AccountMfaCard(
                    code = state.mfaCode,
                    usesRecoveryCode = state.mfaUsesRecoveryCode,
                    enabled = !busy,
                    onCodeChange = onMfaCodeChange,
                    onUsesRecoveryCodeChange = onMfaUsesRecoveryCodeChange,
                    onSubmit = onSubmitMfa,
                    onCancel = onCancelMfa,
                )
            }
        } else if (state.mfaSetupRequired) {
            item {
                AccountMfaSetupCard(enabled = !busy, onCancel = onCancelMfa)
            }
        }
        if (session == null) {
            item { SignedOutCard(onLogin, enabled = !busy && !awaitingMfa) }
        } else {
            if (session.isExpired()) {
                item { ExpiredSessionCard(onLogin, busy || awaitingMfa) }
            }
            if (profile != null) {
                item { ProfileDetailsCard(profile, canManageAccount, onUpdateDisplayName) }
                if (profile.email != null && !profile.emailVerified) {
                    item {
                        EmailVerificationCard(
                            email = profile.email,
                            code = state.verificationCode,
                            enabled = canManageAccount,
                            codeSent = state.notice == AccountNotice.VERIFICATION_SENT,
                            onCodeChange = onVerificationCodeChange,
                            onRequest = onRequestVerification,
                            onConfirm = onConfirmVerification,
                        )
                    }
                }
            } else if (!state.isRefreshingProfile) {
                item {
                    AccountMessageCard(
                        stringResource(R.string.account_management_profile_unavailable),
                        onRetry = onRefresh,
                        enabled = !busy && !state.isLoadingProviders && !awaitingMfa,
                    )
                }
            }
        }
        if (session != null && onNavigateToForestSync != null) {
            item {
                AccountSectionCard(stringResource(R.string.forest_sync_title), Icons.Default.Refresh) {
                    Text(stringResource(R.string.forest_sync_account_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onNavigateToForestSync, enabled = canManageAccount,
                        shape = GsShape.md, modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min)) {
                        Text(stringResource(R.string.forest_sync_open))
                    }
                }
            }
        }
        if (session != null && onNavigateToParcelSync != null) {
            item {
                AccountSectionCard(stringResource(R.string.parcel_selection_title), Icons.Default.Refresh) {
                    Text(
                        stringResource(R.string.parcel_selection_account_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onNavigateToParcelSync,
                        enabled = canManageAccount,
                        shape = GsShape.md,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min),
                    ) {
                        Text(stringResource(R.string.parcel_selection_open))
                    }
                }
            }
        }
        item {
            SignInMethodsCard(
                state = state,
                profile = profile,
                googleClientConfigured = googleClientConfigured,
                enabled = canManageAccount,
                onLinkGoogle = onLinkGoogle,
                onRefresh = onRefresh,
            )
        }
        if (session != null) {
            item { SessionDetailsCard(session) }
            item { AccountSecurityCard() }
            item { LogoutCard(session.accountId, state.isLoggingOut, onLogout) }
        }
    }
}

@Composable
private fun AccountMfaCard(
    code: String,
    usesRecoveryCode: Boolean,
    enabled: Boolean,
    onCodeChange: (String) -> Unit,
    onUsesRecoveryCodeChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    val validCode = if (usesRecoveryCode) {
        code.trim().length in 6..20
    } else {
        code.length == 6 && code.all { it in '0'..'9' }
    }
    AccountSectionCard(stringResource(R.string.identity_mfa_title), Icons.Default.Lock) {
        Text(
            stringResource(R.string.account_management_mfa_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            enabled = enabled,
            label = {
                Text(
                    stringResource(
                        if (usesRecoveryCode) R.string.identity_mfa_recovery_code
                        else R.string.identity_mfa_code
                    )
                )
            },
            supportingText = {
                Text(
                    stringResource(
                        if (usesRecoveryCode) R.string.identity_mfa_hint_recovery
                        else R.string.identity_mfa_hint
                    )
                )
            },
            singleLine = true,
            shape = GsShape.md,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (usesRecoveryCode) KeyboardType.Ascii else KeyboardType.NumberPassword,
                autoCorrect = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (enabled && validCode) onSubmit()
            }),
        )
        Button(
            onClick = onSubmit,
            enabled = enabled && validCode,
            shape = GsShape.md,
            modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min),
        ) {
            Text(stringResource(R.string.account_management_mfa_confirm))
        }
        TextButton(
            onClick = { onUsesRecoveryCodeChange(!usesRecoveryCode) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min),
        ) {
            Text(
                stringResource(
                    if (usesRecoveryCode) R.string.identity_mfa_use_totp
                    else R.string.identity_mfa_use_recovery
                )
            )
        }
        OutlinedButton(
            onClick = onCancel,
            enabled = enabled,
            shape = GsShape.md,
            modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min),
        ) {
            Text(stringResource(R.string.account_management_mfa_cancel))
        }
    }
}

@Composable
private fun AccountMfaSetupCard(enabled: Boolean, onCancel: () -> Unit) {
    AccountSectionCard(stringResource(R.string.account_management_mfa_setup_title), Icons.Default.Lock) {
        Text(
            stringResource(R.string.account_management_mfa_setup_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onCancel,
            enabled = enabled,
            shape = GsShape.md,
            modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min),
        ) {
            Text(stringResource(R.string.account_management_mfa_cancel))
        }
    }
}

@Composable
private fun AccountHero(session: AccountSession?, profile: AccountProfile?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = GsShape.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                ) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.padding(Space.sm).size(Touch.min),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Space.xxs),
                ) {
                    Text(
                        stringResource(R.string.account_management_identity_label),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        profile?.displayName?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.account_management_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            StatusPill(
                text = stringResource(
                    if (session == null) R.string.account_not_connected
                    else if (session.isExpired()) R.string.account_expired
                    else R.string.account_connected
                ),
                positive = session != null && !session.isExpired(),
            )
            Text(
                stringResource(R.string.account_management_intro),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ProfileDetailsCard(
    profile: AccountProfile,
    enabled: Boolean,
    onUpdateDisplayName: (String?) -> Unit,
) {
    var editing by rememberSaveable(profile.accountId) { mutableStateOf(false) }
    var displayName by rememberSaveable(profile.accountId, profile.displayName) {
        mutableStateOf(profile.displayName.orEmpty())
    }
    AccountSectionCard(stringResource(R.string.account_profile_title), Icons.Default.AccountCircle) {
        AccountDetail(
            stringResource(R.string.identity_display_name),
            profile.displayName?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.account_management_name_missing),
        )
        profile.email?.let { email ->
            AccountDetail(stringResource(R.string.identity_email), email)
            StatusPill(
                stringResource(
                    if (profile.emailVerified) R.string.account_management_email_verified
                    else R.string.account_management_email_unverified
                ),
                positive = profile.emailVerified,
            )
        }
        OutlinedButton(
            onClick = {
                displayName = profile.displayName.orEmpty()
                editing = true
            },
            enabled = enabled,
            shape = GsShape.md,
            modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min),
        ) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(Space.lg))
            Spacer(Modifier.size(Space.xs))
            Text(stringResource(R.string.account_edit_profile))
        }
    }
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.account_edit_profile)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(
                        stringResource(R.string.account_management_name_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it.take(200) },
                        label = { Text(stringResource(R.string.identity_display_name)) },
                        singleLine = true,
                        shape = GsShape.md,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateDisplayName(displayName)
                        editing = false
                    },
                    enabled = enabled && AccountInputValidator.isValidDisplayName(displayName) &&
                        displayName.trim() != profile.displayName.orEmpty().trim(),
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun EmailVerificationCard(
    email: String,
    code: String,
    enabled: Boolean,
    codeSent: Boolean,
    onCodeChange: (String) -> Unit,
    onRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val validCode = AccountInputValidator.isValidActionCode(code)
    AccountSectionCard(stringResource(R.string.account_verify_email_title), Icons.Default.Email) {
        Text(
            stringResource(R.string.account_management_verification_hint, email),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onRequest,
            enabled = enabled,
            shape = GsShape.md,
            modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min),
        ) {
            Text(
                stringResource(
                    if (codeSent) R.string.account_management_resend_code
                    else R.string.account_send_verification_code
                )
            )
        }
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            enabled = enabled,
            label = { Text(stringResource(R.string.account_verification_code)) },
            supportingText = {
                Text(stringResource(R.string.account_management_verification_code_hint))
            },
            singleLine = true,
            shape = GsShape.md,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                autoCorrect = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (enabled && validCode) onConfirm()
            }),
        )
        Button(
            onClick = onConfirm,
            enabled = enabled && validCode,
            shape = GsShape.md,
            modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min),
        ) {
            Text(stringResource(R.string.account_confirm_verification))
        }
    }
}

/** Google is linked only when the current GSIE profile explicitly reports that identity. */
@Composable
private fun SignInMethodsCard(
    state: AccountUiState,
    profile: AccountProfile?,
    googleClientConfigured: Boolean,
    enabled: Boolean,
    onLinkGoogle: () -> Unit,
    onRefresh: () -> Unit,
) {
    AccountSectionCard(stringResource(R.string.account_management_sign_in_methods), Icons.Default.Lock) {
        val localCapability = state.providers.firstOrNull { it.provider == IdentityProvider.LOCAL }
        SignInMethodStatus(
            name = stringResource(R.string.account_provider_local),
            status = when {
                profile?.providers?.contains(IdentityProvider.LOCAL) == true ->
                    stringResource(R.string.account_management_method_linked)
                else -> capabilityStatus(localCapability, state.isLoadingProviders)
            },
            positive = profile?.providers?.contains(IdentityProvider.LOCAL) == true ||
                (!state.isLoadingProviders && localCapability?.availability == ProviderAvailability.AVAILABLE),
        )
        GoogleLinkContent(state, profile, googleClientConfigured, enabled, onLinkGoogle)
        state.providers.filter { it.provider == IdentityProvider.ENTERPRISE }.forEach { capability ->
            SignInMethodStatus(
                name = providerLabel(capability.provider),
                status = capabilityStatus(capability, state.isLoadingProviders),
                positive = !state.isLoadingProviders && capability.availability == ProviderAvailability.AVAILABLE,
            )
        }
        if (state.providers.isEmpty() && !state.isLoadingProviders) {
            TextButton(
                onClick = onRefresh,
                enabled = !state.isWorking && !state.isLoggingOut && !state.isRefreshingProfile &&
                    state.mfaChallengeToken == null && !state.mfaSetupRequired,
                modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min),
            ) { Text(stringResource(R.string.account_management_refresh)) }
        }
    }
}

@Composable
private fun GoogleLinkContent(
    state: AccountUiState,
    profile: AccountProfile?,
    googleClientConfigured: Boolean,
    enabled: Boolean,
    onLinkGoogle: () -> Unit,
) {
    val alreadyLinked = profile?.providers?.contains(IdentityProvider.GOOGLE) == true
    val capability = state.providers.firstOrNull { it.provider == IdentityProvider.GOOGLE }
    val googleAvailable = !state.isLoadingProviders &&
        capability?.availability == ProviderAvailability.AVAILABLE && googleClientConfigured
    val canLink = googleAvailable && profile != null && enabled && !alreadyLinked
    val status = when {
        alreadyLinked -> stringResource(R.string.account_google_linked_badge)
        !googleClientConfigured -> stringResource(R.string.account_management_unavailable)
        else -> capabilityStatus(capability, state.isLoadingProviders)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), GsShape.md)
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_google_logo),
                contentDescription = null,
                modifier = Modifier.size(Space.lg),
            )
            Text(
                stringResource(R.string.account_provider_google),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
        }
        StatusPill(status, positive = alreadyLinked || googleAvailable)
        Text(
            stringResource(
                when {
                    alreadyLinked && !googleClientConfigured -> R.string.account_management_google_linked_unavailable
                    alreadyLinked && (state.isLoadingProviders || capability == null) -> R.string.account_management_google_linked_unknown
                    alreadyLinked && !googleAvailable -> R.string.account_management_google_linked_unavailable
                    alreadyLinked -> R.string.account_management_google_linked_hint
                    !googleClientConfigured -> R.string.account_management_google_unavailable_hint
                    state.isLoadingProviders -> R.string.account_management_methods_loading
                    capability == null -> R.string.account_management_methods_unknown
                    capability.availability != ProviderAvailability.AVAILABLE -> R.string.account_management_google_unavailable_hint
                    state.session == null -> R.string.account_management_google_sign_in_hint
                    profile == null && state.isRefreshingProfile -> R.string.account_management_profile_loading
                    profile == null -> R.string.account_management_google_profile_unknown
                    else -> R.string.account_management_google_link_hint
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.mfaChallengeToken == null && !state.mfaSetupRequired &&
            (canLink || (googleAvailable && profile != null && !alreadyLinked && state.isWorking))
        ) {
            Button(
                onClick = onLinkGoogle,
                enabled = canLink,
                shape = GsShape.md,
                modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min),
            ) {
                Text(stringResource(R.string.account_link_google))
            }
        }
    }
}

@Composable
private fun SignInMethodStatus(name: String, status: String, positive: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(name, style = MaterialTheme.typography.titleSmall)
        StatusPill(status, positive)
    }
}

@Composable
private fun capabilityStatus(capability: ProviderCapability?, loading: Boolean): String =
    stringResource(
        when {
            loading -> R.string.account_management_checking
            capability == null -> R.string.account_management_status_unknown
            capability.availability == ProviderAvailability.AVAILABLE -> R.string.account_provider_available
            capability.availability == ProviderAvailability.DEVELOPMENT -> R.string.account_management_in_preparation
            else -> R.string.account_management_unavailable
        }
    )

@Composable
private fun SignedOutCard(onLogin: () -> Unit, enabled: Boolean) {
    AccountSectionCard(stringResource(R.string.account_management_access_title), Icons.Default.AccountCircle) {
        Text(
            stringResource(R.string.account_management_signed_out_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onLogin,
            enabled = enabled,
            shape = GsShape.md,
            modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min),
        ) {
            Text(stringResource(R.string.account_login_action))
        }
    }
}

@Composable
private fun ExpiredSessionCard(onLogin: () -> Unit, busy: Boolean) {
    AccountSectionCard(stringResource(R.string.account_expired), Icons.Default.Lock) {
        Text(
            stringResource(R.string.account_management_expired_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onLogin,
            enabled = !busy,
            shape = GsShape.md,
            modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min),
        ) {
            Text(stringResource(R.string.account_management_reconnect))
        }
    }
}

@Composable
private fun SessionDetailsCard(session: AccountSession) {
    var expanded by rememberSaveable(session.accountId) { mutableStateOf(false) }
    AccountSectionCard(stringResource(R.string.account_management_session_title), Icons.Default.Info) {
        AccountDetail(stringResource(R.string.account_provider), providerLabel(session.provider))
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min),
        ) {
            Text(
                stringResource(
                    if (expanded) R.string.account_management_hide_details
                    else R.string.account_management_show_details
                )
            )
            Spacer(Modifier.size(Space.xs))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                AccountDetail(stringResource(R.string.account_identifier), session.accountId)
                AccountDetail(
                    stringResource(R.string.account_roles),
                    session.roles.joinToString().ifBlank { stringResource(R.string.placeholder_dash) },
                )
                val expires = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(session.expiresAtEpochSeconds * 1_000L))
                AccountDetail(stringResource(R.string.account_expires), expires)
            }
        }
    }
}

@Composable
private fun AccountSecurityCard() {
    AccountSectionCard(stringResource(R.string.account_security_title), Icons.Default.Lock) {
        Text(
            stringResource(R.string.account_management_security_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogoutCard(
    accountId: String,
    isLoggingOut: Boolean,
    onLogout: () -> Unit,
) {
    var confirming by rememberSaveable(accountId) { mutableStateOf(false) }
    AccountSectionCard(stringResource(R.string.account_management_device_title), Icons.AutoMirrored.Filled.Logout) {
        Text(
            stringResource(R.string.account_management_logout_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { confirming = true },
            enabled = !isLoggingOut,
            shape = GsShape.md,
            modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min),
        ) {
            if (isLoggingOut) {
                CircularProgressIndicator(Modifier.size(Space.md), strokeWidth = Space.xxs)
                Spacer(Modifier.size(Space.xs))
            } else {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.size(Space.xs))
            }
            Text(stringResource(R.string.account_logout))
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.account_management_logout_confirm_title)) },
            text = { Text(stringResource(R.string.account_management_logout_confirm_hint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onLogout()
                    },
                    enabled = !isLoggingOut,
                ) { Text(stringResource(R.string.account_logout)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun AccountNoticeCard(notice: AccountNotice) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GsShape.md,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = stringResource(
                when (notice) {
                    AccountNotice.PROFILE_UPDATED -> R.string.account_notice_profile_updated
                    AccountNotice.VERIFICATION_SENT -> R.string.account_notice_verification_sent
                    AccountNotice.EMAIL_VERIFIED -> R.string.account_notice_email_verified
                    AccountNotice.GOOGLE_LINKED -> R.string.account_notice_google_linked
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(Space.md),
        )
    }
}

@Composable
private fun AccountMessageCard(
    message: String,
    onRetry: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GsShape.md,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            if (onRetry != null) {
                TextButton(
                    onClick = onRetry,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min),
                ) { Text(stringResource(R.string.account_management_refresh)) }
            }
        }
    }
}

@Composable
private fun AccountDetail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AccountSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = GsShape.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(Space.screenH),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Space.lg),
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            content()
        }
    }
}
