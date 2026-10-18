package com.forestry.counter.presentation.screens.account

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forestry.counter.BuildConfig
import com.forestry.counter.R
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.domain.model.ApiConnectionState
import com.forestry.counter.domain.model.ApiDiagnostic
import com.forestry.counter.domain.model.ProviderCapability
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.domain.repository.ParcelSyncRepository
import com.forestry.counter.presentation.viewmodel.DeveloperOptionsUiState
import com.forestry.counter.presentation.viewmodel.DeveloperOptionsViewModel
import com.forestry.counter.presentation.viewmodel.GeoSylvaViewModelFactory
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperOptionsScreen(
    repository: IdentityRepository,
    parcelSyncRepository: ParcelSyncRepository,
    preferences: UserPreferencesManager,
    onNavigateBack: () -> Unit,
    onNavigateToParcelSync: () -> Unit,
) {
    val factory = remember(repository, parcelSyncRepository, preferences) {
        GeoSylvaViewModelFactory {
            DeveloperOptionsViewModel(repository, preferences, parcelSyncRepository)
        }
    }
    val viewModel: DeveloperOptionsViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.developerModeEnabled) {
        if (state.developerModeEnabled == false) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.developer_options_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        DeveloperContent(
            state = state,
            apiBaseUrl = repository.apiBaseUrl,
            onRefresh = viewModel::refresh,
            onSynchronizeParcels = onNavigateToParcelSync,
            onPullParcels = viewModel::pullParcels,
            onDisable = viewModel::disableDeveloperMode,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun DeveloperContent(
    state: DeveloperOptionsUiState,
    apiBaseUrl: String,
    onRefresh: () -> Unit,
    onSynchronizeParcels: () -> Unit,
    onPullParcels: () -> Unit,
    onDisable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { DeveloperSafetyNotice() }
        item { RefreshButton(state.isRefreshing, onRefresh) }
        item { ApiDiagnosticCard(apiBaseUrl, state.diagnostic) }
        item { IdentityDiagnosticCard(state) }
        item { ParcelSyncDiagnosticCard(state, onSynchronizeParcels, onPullParcels) }
        item { BuildDiagnosticCard() }
        item { DeviceDiagnosticCard() }
        item { DisableDeveloperCard(onDisable) }
    }
}

@Composable
private fun ParcelSyncDiagnosticCard(
    state: DeveloperOptionsUiState,
    onSynchronizeParcels: () -> Unit,
    onPullParcels: () -> Unit,
) {
    IdentitySectionCard(stringResource(R.string.developer_section_parcel_sync)) {
        IdentityInfoRow(
            stringResource(R.string.developer_sync_pending),
            state.parcelSync.pending.toString(),
        )
        IdentityInfoRow(
            stringResource(R.string.developer_sync_in_progress),
            state.parcelSync.syncing.toString(),
        )
        IdentityInfoRow(
            stringResource(R.string.developer_sync_synced),
            state.parcelSync.synced.toString(),
        )
        IdentityInfoRow(
            stringResource(R.string.developer_sync_conflicts),
            state.parcelSync.conflicts.toString(),
        )
        IdentityInfoRow(
            stringResource(R.string.developer_sync_errors),
            state.parcelSync.errors.toString(),
        )
        state.parcelSync.lastSuccessAt?.let { timestamp ->
            IdentityInfoRow(
                stringResource(R.string.developer_sync_last_success),
                DateFormat.getDateTimeInstance().format(Date(timestamp)),
            )
        }
        Button(
            onClick = onSynchronizeParcels,
            enabled = state.session != null && state.session?.isExpired() == false,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Text(
                stringResource(R.string.parcel_selection_open),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        state.pullResult?.let { result ->
            Text(
                stringResource(
                    R.string.developer_sync_pull_result,
                    result.inserted,
                    result.updated,
                    result.deleted,
                    result.skippedLocalDirty,
                ),
            )
            if (result.skippedOtherAccount > 0) {
                Text(
                    stringResource(R.string.parcel_selection_pull_other_account, result.skippedOtherAccount),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.pullError) {
            Text(
                stringResource(R.string.developer_sync_pull_error),
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedButton(
            onClick = onPullParcels,
            enabled = !state.isPullingParcels && state.session != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isPullingParcels) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null)
            }
            Text(
                stringResource(R.string.developer_sync_pull_parcels),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun DeveloperSafetyNotice() {
    IdentitySectionCard(stringResource(R.string.developer_options_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.WarningAmber, contentDescription = null)
            Text(stringResource(R.string.developer_read_only_notice))
        }
    }
}

@Composable
private fun RefreshButton(refreshing: Boolean, onRefresh: () -> Unit) {
    Button(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.fillMaxWidth()) {
        if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Icon(Icons.Default.Refresh, contentDescription = null)
        Text(
            stringResource(R.string.developer_refresh),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun ApiDiagnosticCard(apiBaseUrl: String, diagnostic: ApiDiagnostic?) {
    IdentitySectionCard(stringResource(R.string.developer_section_api)) {
        IdentityInfoRow(
            stringResource(R.string.developer_api_url),
            apiBaseUrl.ifBlank { stringResource(R.string.developer_not_configured) },
        )
        if (diagnostic == null) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            ApiDiagnosticRows(diagnostic)
        }
    }
}

@Composable
private fun ApiDiagnosticRows(diagnostic: ApiDiagnostic) {
    IdentityInfoRow(
        stringResource(R.string.developer_api_status),
        connectionStateLabel(diagnostic.state),
    )
    diagnostic.latencyMillis?.let {
        IdentityInfoRow(
            stringResource(R.string.developer_latency),
            stringResource(R.string.developer_latency_value, it),
        )
    }
    diagnostic.httpCode?.let {
        IdentityInfoRow(stringResource(R.string.developer_http_code), it.toString())
    }
    diagnostic.version?.let {
        IdentityInfoRow(stringResource(R.string.developer_server_version), it)
    }
    diagnostic.environment?.let {
        IdentityInfoRow(stringResource(R.string.developer_environment), it)
    }
    DependencyRows(diagnostic.dependencies)
}

@Composable
private fun DependencyRows(dependencies: Map<String, String>) {
    Text(
        stringResource(R.string.developer_dependencies),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
    if (dependencies.isEmpty()) {
        Text(
            stringResource(R.string.developer_no_dependencies),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    dependencies.toSortedMap().forEach { (name, status) -> IdentityInfoRow(name, status) }
}

@Composable
private fun IdentityDiagnosticCard(state: DeveloperOptionsUiState) {
    IdentitySectionCard(stringResource(R.string.developer_section_identity)) {
        IdentityInfoRow(
            stringResource(R.string.account_connected),
            stringResource(
                if (state.session == null) R.string.developer_session_inactive
                else R.string.developer_session_active
            ),
        )
        state.session?.let { session ->
            IdentityInfoRow(stringResource(R.string.account_provider), providerLabel(session.provider))
            IdentityInfoRow(
                stringResource(R.string.account_roles),
                session.roles.joinToString().ifBlank { stringResource(R.string.placeholder_dash) },
            )
        }
        ProviderDiagnosticRows(state.providers)
    }
}

@Composable
private fun ProviderDiagnosticRows(providers: List<ProviderCapability>) {
    Text(
        stringResource(R.string.developer_providers),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
    providers.forEach { provider ->
        IdentityInfoRow(
            providerLabel(provider.provider),
            availabilityLabel(provider.availability),
        )
    }
}

@Composable
private fun BuildDiagnosticCard() {
    val timestamp = if (BuildConfig.BUILD_TIMESTAMP > 0L) {
        DateFormat.getDateTimeInstance().format(Date(BuildConfig.BUILD_TIMESTAMP))
    } else {
        stringResource(R.string.developer_not_timestamped)
    }
    IdentitySectionCard(stringResource(R.string.developer_section_build)) {
        IdentityInfoRow(stringResource(R.string.developer_app_version), BuildConfig.VERSION_NAME)
        IdentityInfoRow(stringResource(R.string.developer_build_id), BuildConfig.BUILD_ID)
        IdentityInfoRow(stringResource(R.string.developer_build_timestamp), timestamp)
    }
}

@Composable
private fun DeviceDiagnosticCard() {
    val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull()
        ?: stringResource(R.string.placeholder_dash)
    IdentitySectionCard(stringResource(R.string.developer_section_device)) {
        IdentityInfoRow(
            stringResource(R.string.developer_device_model),
            "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        )
        IdentityInfoRow(stringResource(R.string.developer_android_version), Build.VERSION.RELEASE)
        IdentityInfoRow(stringResource(R.string.developer_sdk_version), Build.VERSION.SDK_INT.toString())
        IdentityInfoRow(stringResource(R.string.developer_primary_abi), primaryAbi)
    }
}

@Composable
private fun DisableDeveloperCard(onDisable: () -> Unit) {
    IdentitySectionCard(stringResource(R.string.developer_disable)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.BugReport, contentDescription = null)
            Text(stringResource(R.string.developer_disable_desc), modifier = Modifier.weight(1f))
        }
        OutlinedButton(onClick = onDisable, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.developer_disable))
        }
    }
}

@Composable
private fun connectionStateLabel(state: ApiConnectionState): String = stringResource(
    when (state) {
        ApiConnectionState.CONNECTED -> R.string.developer_status_connected
        ApiConnectionState.DEGRADED -> R.string.developer_status_degraded
        ApiConnectionState.UNREACHABLE -> R.string.developer_status_unreachable
        ApiConnectionState.NOT_CONFIGURED -> R.string.developer_status_not_configured
    }
)
