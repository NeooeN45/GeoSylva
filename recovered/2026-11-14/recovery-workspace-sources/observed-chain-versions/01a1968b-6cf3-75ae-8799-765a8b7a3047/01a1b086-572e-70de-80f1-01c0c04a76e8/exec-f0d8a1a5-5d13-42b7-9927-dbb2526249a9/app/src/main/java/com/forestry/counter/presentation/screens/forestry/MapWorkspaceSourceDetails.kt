package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.location.MapSourcePolicy
import com.quintessences.geosylva.cartography.ui.MapHelp

/** Provenance supplied by the qualified source catalogue, not by sample map data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapWorkspaceSourceDetails(
    help: MapHelp,
    basemap: MapLayerDef?,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var linkUnavailable by remember(help, basemap?.key) { mutableStateOf(false) }
    fun open(uri: String) {
        linkUnavailable = false
        try { uriHandler.openUri(uri) } catch (_: Exception) { linkUnavailable = true }
    }
    val title = when (help) {
        MapHelp.BASEMAP_SOURCE -> basemap?.let { stringResource(it.labelResId) }
            ?: stringResource(R.string.gs_source_unavailable_title)
        MapHelp.RELIEF_SOURCE -> stringResource(R.string.gs_source_relief_title)
        MapHelp.VERTICAL_EXAGGERATION -> stringResource(R.string.gs_source_exaggeration_title)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.gs_source_details),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.gs_workspace_close))
                }
            }
            HorizontalDivider()
            when (help) {
                MapHelp.BASEMAP_SOURCE -> when {
                    basemap?.key == "ORTHO_IGN" -> {
                        SourceFact(stringResource(R.string.gs_source_provider), "IGN · BD ORTHO®")
                        Text(MapSourcePolicy.ORTHOPHOTO_ATTRIBUTION, style = MaterialTheme.typography.bodyMedium)
                        SourceNote(stringResource(R.string.gs_source_ortho_dates))
                        SourceLink(stringResource(R.string.gs_source_open_license)) { open(SOURCE_OPEN_LICENSE_URL) }
                        SourceLink(stringResource(R.string.gs_source_official)) { open(SOURCE_IGN_ORTHO_URL) }
                    }
                    basemap?.key == MapSourcePolicy.LOCAL_LAYER_KEY -> {
                        SourceFact(stringResource(R.string.gs_source_location), stringResource(R.string.gs_source_on_device))
                        SourceNote(stringResource(R.string.gs_source_local_details))
                    }
                    basemap != null && basemap.key in MAPBOX_SOURCE_KEYS -> {
                        SourceFact(stringResource(R.string.gs_source_provider), "Mapbox")
                        MapSourcePolicy.sources.firstOrNull { it.key == basemap.key }?.let { source ->
                            SourceFact(stringResource(R.string.gs_source_product), source.product)
                        }
                        SourceNote(stringResource(R.string.gs_source_mapbox_attribution))
                        SourceNote(stringResource(R.string.gs_source_mapbox_dates))
                        SourceLink(stringResource(R.string.gs_source_service_terms)) { open(SOURCE_MAPBOX_TERMS_URL) }
                        SourceLink(stringResource(R.string.gs_source_official)) {
                            open(if (basemap.key == "MAPBOX_SATELLITE") SOURCE_MAPBOX_SATELLITE_URL else SOURCE_MAPBOX_STYLES_URL)
                        }
                    }
                    else -> SourceNote(stringResource(R.string.gs_source_unavailable))
                }
                MapHelp.RELIEF_SOURCE -> {
                    if (basemap != null && (basemap.key in MAPBOX_SOURCE_KEYS || basemap.key == "ORTHO_IGN")) {
                        SourceFact(stringResource(R.string.gs_source_provider), "Mapbox")
                        SourceFact(stringResource(R.string.gs_source_product), "Mapbox Terrain-DEM v1")
                        SourceNote(stringResource(R.string.gs_source_dem_details))
                        SourceNote(stringResource(R.string.gs_source_dem_accuracy))
                    } else {
                        SourceNote(stringResource(R.string.gs_source_dem_unavailable))
                    }
                    SourceLink(stringResource(R.string.gs_source_official)) { open(SOURCE_MAPBOX_DEM_URL) }
                }
                MapHelp.VERTICAL_EXAGGERATION -> {
                    SourceFact(stringResource(R.string.gs_source_scale_label), stringResource(R.string.gs_source_scale_value))
                    SourceNote(stringResource(R.string.gs_source_scale_details))
                    SourceFact(stringResource(R.string.gs_source_camera_label), stringResource(R.string.gs_source_camera_value))
                    SourceNote(stringResource(R.string.gs_source_camera_details))
                    SourceLink(stringResource(R.string.gs_source_relief_documentation)) { open(SOURCE_MAPBOX_DEM_URL) }
                }
            }
            if (help == MapHelp.BASEMAP_SOURCE) {
                HorizontalDivider()
                SourceFact(stringResource(R.string.gs_source_preview_title),
                    stringResource(R.string.gs_source_preview_credit))
                SourceNote(stringResource(R.string.gs_source_preview_details))
                SourceLink(stringResource(R.string.gs_source_open_license)) { open(SOURCE_OPEN_LICENSE_URL) }
                SourceLink(stringResource(R.string.gs_source_official)) { open(SOURCE_IGN_ORTHO_URL) }
            }
            if (linkUnavailable) {
                Text(
                    stringResource(R.string.gs_source_link_unavailable),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SourceFact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SourceNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SourceLink(label: String, onClick: () -> Unit) {
    HorizontalDivider()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

private val MAPBOX_SOURCE_KEYS = setOf("MAPBOX_SATELLITE", "MAPBOX_PLAN", "MAPBOX_TOPO", "MAPBOX_SATELLITE_LABELS")
private const val SOURCE_MAPBOX_SATELLITE_URL = "https://docs.mapbox.com/data/tilesets/reference/mapbox-satellite/"
private const val SOURCE_MAPBOX_STYLES_URL = "https://docs.mapbox.com/map-styles/reference/"
private const val SOURCE_MAPBOX_DEM_URL = "https://docs.mapbox.com/data/tilesets/reference/mapbox-terrain-dem-v1/"
private const val SOURCE_MAPBOX_TERMS_URL = "https://www.mapbox.com/legal/tos/"
private const val SOURCE_IGN_ORTHO_URL = "https://geoservices.ign.fr/bdortho"
private const val SOURCE_OPEN_LICENSE_URL = "https://www.data.gouv.fr/pages/legal/licences/etalab-2-0"
