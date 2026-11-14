package com.forestry.counter.presentation.screens.forestry.layers

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.domain.repository.*
import com.quintessences.geosylva.cartography.ui.R as CartographyR
import com.quintessences.geosylva.cartography.ui.layers.LayerWorkspace
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Native chooser reads only the selected document, with a captured scope ticket and a hard byte bound. */
@Composable
fun MapLayerHost(viewModel: MapLayerViewModel, modifier: Modifier = Modifier, onPanelHeightChanged: (Dp) -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectionTicket by remember { mutableStateOf<MapLayerViewModel.FileSelectionTicket?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val ticket = selectionTicket
        selectionTicket = null
        if (uri != null && ticket != null) coroutineScope.launch {
            try {
                val document = withContext(Dispatchers.IO) { readLayerDocument(context, uri) }
                viewModel.importFile(document.bytes, document.name, document.format, ticket)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: LayerDocumentFailure) { viewModel.reportFileError(failure.reason, ticket) }
            catch (_: Exception) { viewModel.reportFileError(LayerStoreError.IO, ticket) }
        }
    }
    DisposableEffect(viewModel, launcher) {
        viewModel.setFileChooser {
            if (selectionTicket == null) {
                selectionTicket = viewModel.captureFileSelectionTicket()
                if (selectionTicket != null) launcher.launch(arrayOf("*/*"))
            }
        }
        onDispose { viewModel.setFileChooser(null) }
    }
    LaunchedEffect(state.visible) { if (!state.visible) onPanelHeightChanged(0.dp) }
    if (state.visible) LayerWorkspace(state.ui.copy(error = state.error?.let { stringResource(layerErrorResource(it)) }), viewModel, modifier,
        onPanelHeightChanged = onPanelHeightChanged)
}
private data class LayerDocument(val name: String, val bytes: ByteArray, val format: LayerImportFormat)
private class LayerDocumentFailure(val reason: LayerStoreError) : RuntimeException()
private fun readLayerDocument(context: Context, uri: Uri): LayerDocument {
    var filename: String? = null
    var declaredSize: Long? = null
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && !cursor.isNull(nameIndex)) filename = cursor.getString(nameIndex)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) declaredSize = cursor.getLong(sizeIndex)
        }
    }
    val name = filename ?: uri.lastPathSegment?.substringAfterLast('/') ?: throw LayerDocumentFailure(LayerStoreError.INVALID_INPUT)
    val format = when (name.substringAfterLast('.', "").lowercase()) {
        "geojson", "json" -> LayerImportFormat.GEOJSON
        "kml" -> LayerImportFormat.KML
        "kmz" -> LayerImportFormat.KMZ
        "gpx" -> LayerImportFormat.GPX
        "csv" -> LayerImportFormat.CSV
        "gpkg" -> LayerImportFormat.GEOPACKAGE
        else -> throw LayerDocumentFailure(LayerStoreError.UNSUPPORTED_FORMAT)
    }
    if (declaredSize?.let { it > MAX_DOCUMENT_BYTES } == true) throw LayerDocumentFailure(LayerStoreError.LIMIT_EXCEEDED)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    context.contentResolver.openInputStream(uri)?.use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size().toLong() + read > MAX_DOCUMENT_BYTES) throw LayerDocumentFailure(LayerStoreError.LIMIT_EXCEEDED)
            output.write(buffer, 0, read)
        }
    } ?: throw LayerDocumentFailure(LayerStoreError.IO)
    return LayerDocument(name, output.toByteArray(), format)
}
private const val MAX_DOCUMENT_BYTES = 8 * 1024 * 1024
private fun layerErrorResource(error: LayerStoreError): Int = when (error) {
    LayerStoreError.ACCESS_CHANGED -> CartographyR.string.layer_error_access
    LayerStoreError.UNAVAILABLE -> CartographyR.string.layer_error_unavailable
    LayerStoreError.INVALID_INPUT -> CartographyR.string.layer_error_input
    LayerStoreError.UNSUPPORTED_FORMAT -> CartographyR.string.layer_error_format
    LayerStoreError.UNSUPPORTED_CRS -> CartographyR.string.layer_error_crs
    LayerStoreError.INVALID_GEOMETRY -> CartographyR.string.layer_error_geometry
    LayerStoreError.LIMIT_EXCEEDED -> CartographyR.string.layer_error_limit
    LayerStoreError.UNSAFE_ARCHIVE -> CartographyR.string.layer_error_archive
    LayerStoreError.EMPTY -> CartographyR.string.layer_error_empty
    LayerStoreError.CONFLICT -> CartographyR.string.layer_error_conflict
    LayerStoreError.CORRUPT -> CartographyR.string.layer_error_corrupt
    LayerStoreError.IO -> CartographyR.string.layer_error_io
    LayerStoreError.UNKNOWN_RESULT -> CartographyR.string.layer_error_unknown
}
