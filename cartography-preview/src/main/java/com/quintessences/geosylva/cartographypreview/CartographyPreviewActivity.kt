package com.quintessences.geosylva.cartographypreview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quintessences.geosylva.cartography.mapbox.*
import com.quintessences.geosylva.cartography.ui.*
import java.util.UUID
import kotlin.math.abs

/** Hôte interne : vrai moteur partagé, sans accès aux données ni aux outils métier de Géosylva. */
class CartographyPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { PreviewWorkspace(onClose = ::finish) } }
    }
}

@Composable
private fun PreviewWorkspace(onClose: () -> Unit) {
    val token = BuildConfig.MAPBOX_PUBLIC_ACCESS_TOKEN
    val configured = token.startsWith("pk.")
    var styleId by rememberSaveable { mutableStateOf(MapboxStyles.SatelliteLabels.id) }
    var panel by rememberSaveable { mutableStateOf<MapPanel?>(null) }
    var camera by remember { mutableStateOf(MapCameraPosition(5.47, 45.05, 12.5, pitch = 45.0)) }
    var cameraCommand by remember { mutableStateOf<MapCameraCommand?>(null) }
    var requestedRelief by remember { mutableStateOf(MapRelief(enabled = true)) }
    var appliedRelief by remember { mutableStateOf<MapRelief?>(null) }
    var status by remember { mutableStateOf<MapRenderStatus>(MapRenderStatus.ConfigurationRequired) }
    var message by remember { mutableStateOf<Int?>(null) }
    var retry by remember { mutableStateOf(0) }
    var compassTop by remember { mutableStateOf(156.dp) }
    val styles = listOf(MapboxStyles.SatelliteLabels, MapboxStyles.Satellite, MapboxStyles.Plan, MapboxStyles.Topographic)
    val style = styles.first { it.id == styleId }
    val acknowledged = appliedRelief?.let {
        it.enabled == requestedRelief.enabled && abs(it.exaggeration - requestedRelief.exaggeration) < 0.0001
    } == true
    val ready = (status as? MapRenderStatus.Ready)?.styleId == style.id && acknowledged
    val choices = listOf(
        MapboxStyles.SatelliteLabels to R.string.preview_basemap_satellite_labels,
        MapboxStyles.Satellite to R.string.preview_basemap_satellite,
        MapboxStyles.Plan to R.string.preview_basemap_plan,
        MapboxStyles.Topographic to R.string.preview_basemap_topographic,
    ).map { (item, title) -> MapBasemapChoice(item.id, stringResource(title), "Mapbox",
        availability = if (configured) MapChoiceAvailability.AVAILABLE else MapChoiceAvailability.CONFIGURATION_REQUIRED) }
    val state = MapWorkspaceState(
        contextTitle = stringResource(R.string.preview_vercors),
        contextSubtitle = stringResource(R.string.preview_context_subtitle),
        basemaps = choices, selectedBasemapId = style.id, panel = panel,
        pitchDegrees = camera.pitch, verticalExaggeration = appliedRelief?.exaggeration ?: 1.0,
        controlsEnabled = ready, controlsUnavailableReason = stringResource(R.string.preview_camera_requires_map),
        taskBarVisible = false, locationEnabled = false,
    )
    val actions = object : MapWorkspaceActions {
        override fun onBack() = onClose()
        override fun onChooseContext() { message = R.string.preview_no_data }
        override fun onPanelChanged(value: MapPanel?) { panel = value }
        override fun onLocate() { message = R.string.preview_no_position }
        override fun onTask(task: MapTask) { message = R.string.preview_unavailable_body }
        override fun onSelectBasemap(id: String) {
            if (!configured) { message = R.string.preview_configuration_body; return }
            if (styles.none { it.id == id } || id == styleId) return
            styleId = id
            appliedRelief = null
            status = MapRenderStatus.Loading(id)
        }
        override fun onToggleLayer(id: String, visible: Boolean) { message = R.string.preview_no_data }
        override fun onAddLayer(id: String) { message = R.string.preview_no_data }
        override fun onPitchChange(degrees: Double) {
            if (ready) cameraCommand = MapCameraCommand(UUID.randomUUID().toString(), camera.copy(pitch = degrees.coerceIn(0.0, 60.0)))
        }
        override fun onExaggerationChange(exaggeration: Double) {
            if (ready) requestedRelief = requestedRelief.copy(exaggeration = exaggeration.coerceIn(1.0, 3.0))
        }
        override fun onFlatView(flat: Boolean) {
            if (ready) cameraCommand = MapCameraCommand(UUID.randomUUID().toString(), camera.copy(pitch = if (flat) 0.0 else 45.0))
        }
        override fun onExplain(help: MapHelp) { message = R.string.preview_sources_body }
    }
    MapWorkspace(state, actions, Modifier.fillMaxSize(), onCompassTopChanged = { compassTop = it }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val bottom = (if (panel != null) mapWorkspacePanelHeight(maxHeight, LocalDensity.current.fontScale) else 76.dp) +
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
            key(retry) {
                SharedMapboxCanvas(
                    accessToken = token, style = style, initialCamera = camera,
                    modifier = Modifier.fillMaxSize(), cameraCommand = cameraCommand, relief = requestedRelief,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = compassTop, bottom = bottom),
                    onCameraChanged = { camera = it },
                    onCommandConsumed = { id -> if (cameraCommand?.id == id) cameraCommand = null },
                    onReliefChanged = { appliedRelief = it }, onStatusChanged = { status = it },
                    scaleBarVisible = panel == null,
                )
            }
            when (status) {
                MapRenderStatus.ConfigurationRequired -> Surface(Modifier.align(Alignment.Center).padding(28.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.preview_configuration_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.preview_configuration_body))
                    }
                }
                is MapRenderStatus.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is MapRenderStatus.Failed -> Surface(Modifier.align(Alignment.Center).padding(28.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.preview_failure))
                        Button(onClick = { appliedRelief = null; status = MapRenderStatus.Loading(style.id); retry++ }) {
                            Text(stringResource(R.string.preview_retry))
                        }
                    }
                }
                is MapRenderStatus.Ready -> Unit
            }
        }
    }
    message?.let { resource -> AlertDialog(
        onDismissRequest = { message = null },
        title = { Text(stringResource(R.string.preview_information)) },
        text = { Text(stringResource(resource)) },
        confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.preview_close)) } },
    ) }
}
