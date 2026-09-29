package com.forestry.counter.presentation.screens.account

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.model.IdentityError
import com.forestry.counter.domain.model.IdentityProvider
import com.forestry.counter.domain.model.ProviderAvailability

@Composable
internal fun IdentityErrorBanner(error: IdentityError, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null)
            Text(
                text = stringResource(identityErrorMessage(error)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun IdentityInfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.55f),
        )
    }
}

@Composable
internal fun IdentitySectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
internal fun StatusPill(text: String, positive: Boolean, modifier: Modifier = Modifier) {
    val colors = if (positive) {
        Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    } else {
        Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(modifier = modifier, color = colors.first, shape = MaterialTheme.shapes.extraLarge) {
        Text(
            text = text,
            color = colors.second,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
internal fun providerLabel(provider: IdentityProvider): String = stringResource(
    when (provider) {
        IdentityProvider.LOCAL -> R.string.account_provider_local
        IdentityProvider.GOOGLE -> R.string.account_provider_google
        IdentityProvider.ENTERPRISE -> R.string.account_provider_enterprise
        IdentityProvider.UNKNOWN -> R.string.account_provider_unknown
    }
)

@Composable
internal fun availabilityLabel(availability: ProviderAvailability): String = stringResource(
    when (availability) {
        ProviderAvailability.AVAILABLE -> R.string.account_provider_available
        ProviderAvailability.NOT_CONFIGURED -> R.string.account_provider_not_configured
        ProviderAvailability.DEVELOPMENT -> R.string.identity_in_development
    }
)

@StringRes
// `internal` et non `private` : l'écran de connexion affiche l'erreur sans
// bandeau, directement sur le média, et a donc besoin du libellé seul.
internal fun identityErrorMessage(error: IdentityError): Int = when (error) {
    IdentityError.API_NOT_CONFIGURED -> R.string.identity_server_not_configured
    IdentityError.GOOGLE_NOT_CONFIGURED -> R.string.identity_error_google_config
    IdentityError.SECURE_STORAGE_UNAVAILABLE -> R.string.identity_error_secure_storage
    IdentityError.INVALID_INPUT -> R.string.identity_error_invalid_input
    IdentityError.REGISTRATION_CONSENT_REQUIRED -> R.string.identity_error_consent_required
    IdentityError.INVALID_CREDENTIALS -> R.string.identity_error_invalid_credentials
    IdentityError.ACCOUNT_ALREADY_EXISTS -> R.string.identity_error_account_exists
    IdentityError.ACCOUNT_LINK_REQUIRED -> R.string.identity_error_link_required
    IdentityError.GOOGLE_ALREADY_LINKED -> R.string.identity_error_google_already_linked
    IdentityError.ACTION_CODE_INVALID -> R.string.identity_error_action_code
    IdentityError.EMAIL_DELIVERY_UNAVAILABLE -> R.string.identity_error_email_delivery
    IdentityError.NETWORK_UNAVAILABLE -> R.string.identity_error_network
    IdentityError.SERVER_UNAVAILABLE -> R.string.identity_server_unavailable
    IdentityError.INVALID_SERVER_RESPONSE,
    IdentityError.CANCELLED,
    IdentityError.UNKNOWN,
    -> R.string.identity_error_generic
}
