package com.forestry.counter.presentation.screens.account

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forestry.counter.R
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.forestry.counter.data.remote.identity.GoogleCredentialClient
import com.forestry.counter.presentation.theme.Motion
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Space
import com.forestry.counter.presentation.theme.Touch
import com.forestry.counter.domain.model.IdentityProvider
import com.forestry.counter.domain.model.ProviderAvailability
import com.forestry.counter.domain.model.AccountSession
import com.forestry.counter.domain.model.AccountProfile
import com.forestry.counter.domain.model.AccountConsent
import com.forestry.counter.domain.model.AccountConsentPolicy
import com.forestry.counter.domain.model.ProviderCapability
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.presentation.viewmodel.AccountUiState
import com.forestry.counter.presentation.viewmodel.AccountNotice
import com.forestry.counter.presentation.viewmodel.AccountViewModel
import com.forestry.counter.presentation.viewmodel.GeoSylvaViewModelFactory
import java.text.DateFormat
import java.util.Date

/**
 * Espace compte (exigence ID-F-013 de `IDENTITE_001`).
 *
 * Point d'entrée : la catégorie « Compte » de l'onglet Réglages. Un simple
 * push de pile, donc toujours avec flèche retour — plus d'usage en onglet
 * de premier niveau depuis que Réglages a pris cette place dans la barre du
 * bas, donc plus de lien vers Réglages ici : il ferait double emploi avec
 * le chemin qui mène déjà à cet écran.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    repository: IdentityRepository,
    onNavigateToLogin: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val factory = remember(repository) { GeoSylvaViewModelFactory { AccountViewModel(repository) } }
    val viewModel: AccountViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val googleClient = remember(context) { GoogleCredentialClient(context) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_title)) },
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
            onConsentChange = viewModel::setConsentAccepted,
            googleClientConfigured = repository.isGoogleClientConfigured,
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
    onConsentChange: (String, Boolean) -> Unit,
    googleClientConfigured: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { AccountHero(state.session) }
        state.error?.let { error -> item { IdentityErrorBanner(error) } }
        state.notice?.let { notice -> item { AccountNoticeCard(notice) } }
        if (state.session == null) {
            item { SignedOutCard(onLogin) }
        } else {
            item { SessionDetailsCard(state.session) }
            state.profile?.let { profile ->
                item { ProfileDetailsCard(profile, state.isWorking, onUpdateDisplayName) }
                if (profile.email != null && !profile.emailVerified) {
                    item {
                        EmailVerificationCard(
                            code = state.verificationCode,
                            isWorking = state.isWorking,
                            onCodeChange = onVerificationCodeChange,
                            onRequest = onRequestVerification,
                            onConfirm = onConfirmVerification,
                        )
                    }
                }
            }
            item {
                GoogleLinkCard(
                    state = state,
                    googleClientConfigured = googleClientConfigured,
                    onLinkGoogle = onLinkGoogle,
                )
            }
            item {
                AccountConsentsCard(
                    consents = state.consents,
                    loading = state.isLoadingConsents,
                    isWorking = state.isWorking,
                    onConsentChange = onConsentChange,
                )
            }
            item { AccountSecurityCard() }
            item { LogoutCard(state.isLoggingOut, onLogout) }
        }
        item { ProviderCapabilitiesCard(state.providers, state.isLoadingProviders) }
    }
}

/**
 * Rattachement de Google au compte courant (ID-F-008).
 *
 * C'est ici, et nulle part ailleurs, que se résout `ACCOUNT_LINK_REQUIRED` :
 * le serveur refuse de fusionner deux comptes sur la seule correspondance
 * d'adresse e-mail, et exige une session déjà ouverte. Sans cette carte,
 * l'utilisateur qui tentait « Continuer avec Google » sur un compte local
 * existant se retrouvait dans une impasse — le message s'affichait, aucune
 * action n'était possible.
 *
 * La carte disparaît une fois Google rattaché, remplacée par un simple
 * constat : il n'y a plus rien à faire.
 */
@Composable
private fun GoogleLinkCard(
    state: AccountUiState,
    googleClientConfigured: Boolean,
    onLinkGoogle: () -> Unit,
) {
    val alreadyLinked = state.profile
        ?.providers
        ?.contains(IdentityProvider.GOOGLE) == true

    val googleAvailable = state.providers.any {
        it.provider == IdentityProvider.GOOGLE &&
            it.availability == ProviderAvailability.AVAILABLE
    }

    // Rien à proposer si le fournisseur n'est pas exploitable : mieux vaut
    // ne rien afficher qu'un bouton mort.
    if (!alreadyLinked && !(googleAvailable && googleClientConfigured)) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.identity_google_continue),
                style = MaterialTheme.typography.titleMedium,
            )

            if (alreadyLinked) {
                StatusPill(
                    text = stringResource(R.string.account_google_linked_badge),
                    positive = true,
                )
            } else {
                Text(
                    text = stringResource(R.string.account_link_google_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onLinkGoogle,
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_google_logo),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.account_link_google))
                }
            }
        }
    }
}

@Composable
private fun AccountNoticeCard(notice: AccountNotice) {
    Card {
        Text(
            text = stringResource(
                when (notice) {
                    AccountNotice.PROFILE_UPDATED -> R.string.account_notice_profile_updated
                    AccountNotice.VERIFICATION_SENT -> R.string.account_notice_verification_sent
                    AccountNotice.EMAIL_VERIFIED -> R.string.account_notice_email_verified
                    AccountNotice.GOOGLE_LINKED -> R.string.account_notice_google_linked
                    AccountNotice.CONSENT_UPDATED -> R.string.account_notice_consent_updated
                }
            ),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(16.dp),
        )
    }
}

@Composable
private fun AccountConsentsCard(
    consents: List<AccountConsent>,
    loading: Boolean,
    isWorking: Boolean,
    onConsentChange: (String, Boolean) -> Unit,
) {
    val activeTypes = consents
        .filter { it.revokedAt == null }
        .map(AccountConsent::consentType)
        .toSet()

    IdentitySectionCard(stringResource(R.string.account_consents_title)) {
        Text(
            text = stringResource(R.string.account_consents_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        AccountConsentPolicy.required.forEach { requirement ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = consentLabel(requirement.type),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.account_consent_version,
                            requirement.documentVersion,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = requirement.type in activeTypes,
                    onCheckedChange = { accepted ->
                        onConsentChange(requirement.type, accepted)
                    },
                    enabled = !loading && !isWorking,
                )
            }
        }
    }
}

@Composable
private fun consentLabel(type: String): String = when (type) {
    AccountConsentPolicy.TERMS -> stringResource(R.string.account_consent_terms)
    AccountConsentPolicy.PRIVACY -> stringResource(R.string.account_consent_privacy)
    else -> type
}

@Composable
private fun ProfileDetailsCard(
    profile: AccountProfile,
    isWorking: Boolean,
    onUpdateDisplayName: (String?) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var displayName by rememberSaveable(profile.displayName) {
        mutableStateOf(profile.displayName.orEmpty())
    }
    IdentitySectionCard(stringResource(R.string.account_profile_title)) {
        IdentityInfoRow(
            stringResource(R.string.identity_display_name),
            profile.displayName ?: stringResource(R.string.placeholder_dash),
        )
        profile.email?.let { email ->
            IdentityInfoRow(stringResource(R.string.identity_email), email)
            IdentityInfoRow(
                stringResource(R.string.account_email_status),
                stringResource(
                    if (profile.emailVerified) R.string.account_email_verified
                    else R.string.account_email_not_verified
                ),
            )
        }
        OutlinedButton(
            onClick = { editing = true },
            enabled = !isWorking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.account_edit_profile))
        }
    }
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.account_edit_profile)) },
            text = {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(200) },
                    label = { Text(stringResource(R.string.identity_display_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateDisplayName(displayName)
                        editing = false
                    }
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { editing = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun EmailVerificationCard(
    code: String,
    isWorking: Boolean,
    onCodeChange: (String) -> Unit,
    onRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    IdentitySectionCard(stringResource(R.string.account_verify_email_title)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Email, contentDescription = null)
            Text(stringResource(R.string.account_verify_email_desc))
        }
        OutlinedButton(
            onClick = onRequest,
            enabled = !isWorking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.account_send_verification_code))
        }
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            label = { Text(stringResource(R.string.account_verification_code)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onConfirm,
            enabled = !isWorking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isWorking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.account_confirm_verification))
        }
    }
}

@Composable
private fun AccountHero(session: AccountSession?) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer,
                        )
                    )
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(48.dp))
            Text(stringResource(R.string.account_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.account_ecosystem), style = MaterialTheme.typography.bodyMedium)
            StatusPill(
                text = stringResource(
                    if (session == null) R.string.account_not_connected
                    else if (session.isExpired()) R.string.account_expired
                    else R.string.account_connected
                ),
                positive = session != null && !session.isExpired(),
            )
        }
    }
}

@Composable
private fun SignedOutCard(onLogin: () -> Unit) {
    IdentitySectionCard(stringResource(R.string.account_not_connected)) {
        Text(
            stringResource(R.string.account_not_connected_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.account_login_action))
        }
    }
}

@Composable
private fun SessionDetailsCard(session: AccountSession) {
    val expires = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(session.expiresAtEpochSeconds * 1_000L))
    IdentitySectionCard(stringResource(R.string.account_connected)) {
        IdentityInfoRow(
            stringResource(R.string.account_identifier),
            session.accountId,
        )
        IdentityInfoRow(stringResource(R.string.account_provider), providerLabel(session.provider))
        IdentityInfoRow(
            stringResource(R.string.account_roles),
            session.roles.joinToString().ifBlank { stringResource(R.string.placeholder_dash) },
        )
        IdentityInfoRow(stringResource(R.string.account_expires), expires)
    }
}

@Composable
private fun AccountSecurityCard() {
    IdentitySectionCard(stringResource(R.string.account_security_title)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Lock, contentDescription = null)
            Text(
                stringResource(R.string.account_security_desc),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun LogoutCard(isLoggingOut: Boolean, onLogout: () -> Unit) {
    IdentitySectionCard(stringResource(R.string.account_logout)) {
        Text(
            stringResource(R.string.account_logout_local_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onLogout,
            enabled = !isLoggingOut,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoggingOut) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.account_logout))
            }
        }
    }
}

@Composable
private fun ProviderCapabilitiesCard(providers: List<ProviderCapability>, loading: Boolean) {
    IdentitySectionCard(stringResource(R.string.account_providers_title)) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        providers.forEach { provider -> ProviderCapabilityRow(provider) }
    }
}

@Composable
private fun ProviderCapabilityRow(capability: ProviderCapability) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            providerLabel(capability.provider),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StatusPill(
            availabilityLabel(capability.availability),
            positive = capability.availability == com.forestry.counter.domain.model.ProviderAvailability.AVAILABLE,
        )
    }
}
