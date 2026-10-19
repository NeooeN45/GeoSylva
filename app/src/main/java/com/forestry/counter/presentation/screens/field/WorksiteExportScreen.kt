package com.forestry.counter.presentation.screens.field

import android.provider.DocumentsContract
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forestry.counter.R
import com.forestry.counter.domain.repository.FieldRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorksiteExportScreen(repository: FieldRepository, siteId: String, onBack: () -> Unit) {
    val factory = remember(repository, siteId) { object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = WorksiteExportViewModel(repository, siteId) as T
    } }
    val vm: WorksiteExportViewModel = viewModel(key = "site-export-$siteId", factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.save(writer = { bytes, current ->
            check(current())
            checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { output ->
                var offset = 0
                while (offset < bytes.size) {
                    check(current())
                    val size = minOf(8192, bytes.size - offset)
                    output.write(bytes, offset, size); offset += size
                }
                output.flush(); check(current())
            }
        }, cleanup = {
            // A provider can refuse deletion. Try removing the content, then report any residual risk.
            val deleted = runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }.getOrDefault(false)
            deleted || runCatching {
                checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.flush() }
                true
            }.getOrDefault(false)
        })
    }
    BackHandler(enabled = state.busy) { /* Finish or cancel the SAF write before leaving this screen. */ }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.field_export_title)) }, navigationIcon = { IconButton(onBack, enabled = !state.busy) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.field_leave))
    } }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.field_export_heading), style = MaterialTheme.typography.headlineMedium)
                if (state.title.isNotBlank()) Text(state.title, style = MaterialTheme.typography.titleLarge)
                if (state.cleanupFailed) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Text(stringResource(R.string.field_export_cleanup_failed), Modifier.padding(16.dp))
                }
                if (state.accessLost) FieldErrorCard(com.forestry.counter.domain.repository.FieldError.ACCESS_CHANGED)
                else {
                    Text(stringResource(R.string.field_export_help))
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
                        Text(stringResource(R.string.field_export_photos_help), Modifier.padding(20.dp))
                    }
                    if (state.error) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Text(stringResource(R.string.field_export_error), Modifier.padding(16.dp))
                    }
                    OutlinedButton(vm::prepare, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("field_export_prepare")) { Text(stringResource(R.string.field_export_prepare)) }
                    if (state.prepared) {
                        Text(stringResource(R.string.field_export_prepared, state.objects, state.receipts, state.entries), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.field_export_size, Formatter.formatShortFileSize(context, state.byteCount.toLong())))
                        Text(stringResource(R.string.field_export_frozen), style = MaterialTheme.typography.bodySmall)
                        Button({ if (vm.canSave()) launcher.launch(state.fileName) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("field_export_save")) {
                            Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.field_export_save))
                        }
                    }
                    if (state.saved) Text(stringResource(R.string.field_export_saved), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
