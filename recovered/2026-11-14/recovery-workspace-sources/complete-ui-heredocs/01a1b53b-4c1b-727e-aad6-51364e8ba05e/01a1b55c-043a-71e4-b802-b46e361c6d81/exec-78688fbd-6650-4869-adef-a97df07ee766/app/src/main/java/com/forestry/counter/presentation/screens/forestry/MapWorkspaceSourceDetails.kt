package com.forestry.counter.presentation.screens.forestry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.forestry.counter.R
import com.forestry.counter.domain.location.MapSourcePolicy
import com.forestry.counter.presentation.components.maps.MapCatalogThumbnail
import com.quintessences.geosylva.cartography.ui.MapHelp
import com.quintessences.geosylva.cartography.ui.MapPreviewKey
import com.quintessences.geosylva.cartography.ui.source.MapLayerSourcePanel
import com.quintessences.geosylva.cartography.ui.source.MapSourceLayerUi
import com.quintessences.geosylva.cartography.ui.source.MapSourcePanelState
import java.net.URI

/**
 * Shared non-modal layer/source presentation. The host supplies confirmed source and
 * live authorized layer controls; missing edition, origin and licence remain unknown.
 */
@Composable
internal fun MapWorkspaceSourceDetails(
    help: MapHelp,
    basemap: MapLayerDef?,
    onDismiss: () -> Unit,
    onPanelHeightChanged: (Dp) -> Unit = {},
    layer: MapSourceLayerUi? = null,
    onLayerVisibilityChanged: ((Boolean) -> Unit)? = null,
    onLayerOpacityChanged: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    var showSource by remember(help, basemap?.key, layer?.id) { mutableStateOf(layer == null) }
    var linkUnavailable by remember(help, basemap?.key, layer?.id) { mutableStateOf(false) }
    val source = if (layer != null) MapSourcePanelState(
        title = layer.title, previewKey = layer.previewKey, layer = layer, showSource = showSource,
        origin = layer.origin, distribution = layer.distribution, edition = layer.edition,
        licenceLabel = layer.licenceLabel, licenceUrl = layer.licenceUrl, officialUrl = layer.officialUrl,
        notes = listOfNotNull(layer.note), previewDetails = stringResource(R.string.gs_source_preview_details),
    ) else sourceForHelp(help, basemap)
    MapLayerSourcePanel(
        state = source.copy(linkUnavailable = linkUnavailable),
        onBack = { if (layer != null && showSource) showSource = false else onDismiss() },
        onClose = onDismiss,
        onSource = { showSource = true },
        onOpenLink = { value ->
            linkUnavailable = false
            val uri = runCatching { URI(value) }.getOrNull()
            if (uri?.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) linkUnavailable = true
            else try { uriHandler.openUri(value) } catch (_: Exception) { linkUnavailable = true }
        },
        modifier = modifier,
        onPanelHeightChanged = onPanelHeightChanged,
        onVisibilityChanged = onLayerVisibilityChanged,
        onOpacityChanged = onLayerOpacityChanged,
        thumbnail = { key, imageModifier -> MapCatalogThumbnail(key, imageModifier) },
    )
}

@Composable
private fun sourceForHelp(help: MapHelp, basemap: MapLayerDef?): MapSourcePanelState = when (help) {
    MapHelp.BASEMAP_SOURCE -> when {
        basemap?.key == "ORTHO_IGN" -> MapSourcePanelState(
            title = stringResource(basemap.labelResId), subtitle = "BD ORTHO®", previewKey = MapPreviewKey(basemap.key),
            origin = "IGN", distribution = "IGN",
            licenceLabel = stringResource(R.string.gs_source_open_license), licenceUrl = SOURCE_OPEN_LICENSE_URL,
            officialUrl = SOURCE_IGN_ORTHO_URL,
            notes = listOf(stringResource(R.string.gs_source_ortho_dates)),
            previewDetails = stringResource(R.string.gs_source_preview_credit) + "\n" + stringResource(R.string.gs_source_preview_details),
        )
        basemap?.key == MapSourcePolicy.LOCAL_LAYER_KEY -> MapSourcePanelState(
            title = stringResource(basemap.labelResId), subtitle = stringResource(R.string.gs_source_on_device),
            facts = listOf(stringResource(R.string.gs_source_location) to stringResource(R.string.gs_source_on_device)),
            notes = listOf(stringResource(R.string.gs_source_local_details)),
        )
        basemap != null && basemap.key in MAPBOX_SOURCE_KEYS -> MapSourcePanelState(
            title = stringResource(basemap.labelResId),
            subtitle = MapSourcePolicy.sources.firstOrNull { it.key == basemap.key }?.product,
            previewKey = MapPreviewKey(basemap.key), distribution = "Mapbox",
            licenceLabel = stringResource(R.string.gs_source_service_terms), licenceUrl = SOURCE_MAPBOX_TERMS_URL,
            officialUrl = if (basemap.key == "MAPBOX_SATELLITE") SOURCE_MAPBOX_SATELLITE_URL else SOURCE_MAPBOX_STYLES_URL,
            notes = listOf(stringResource(R.string.gs_source_mapbox_attribution)),
            previewDetails = stringResource(R.string.map_catalog_mapbox_credit) + "\n" + stringResource(R.string.map_catalog_mapbox_details),
        )
        else -> MapSourcePanelState(title = stringResource(R.string.gs_source_unavailable_title),
            notes = listOf(stringResource(R.string.gs_source_unavailable)))
    }
    MapHelp.RELIEF_SOURCE -> {
        val confirmed = basemap != null && (basemap.key in MAPBOX_SOURCE_KEYS || basemap.key == "ORTHO_IGN")
        MapSourcePanelState(title = stringResource(R.string.gs_source_relief_title),
            subtitle = if (confirmed) "Mapbox Terrain-DEM v1" else null,
            distribution = if (confirmed) "Mapbox" else null,
            licenceLabel = if (confirmed) stringResource(R.string.gs_source_service_terms) else null,
            licenceUrl = SOURCE_MAPBOX_TERMS_URL.takeIf { confirmed }, officialUrl = SOURCE_MAPBOX_DEM_URL.takeIf { confirmed },
            notes = if (confirmed) listOf(stringResource(R.string.gs_source_dem_details), stringResource(R.string.gs_source_dem_accuracy))
                else listOf(stringResource(R.string.gs_source_dem_unavailable)))
    }
    MapHelp.VERTICAL_EXAGGERATION -> MapSourcePanelState(
        title = stringResource(R.string.gs_source_exaggeration_title), showProvenance = false,
        facts = listOf(stringResource(R.string.gs_source_scale_label) to stringResource(R.string.gs_source_scale_value),
            stringResource(R.string.gs_source_camera_label) to stringResource(R.string.gs_source_camera_value)),
        notes = listOf(stringResource(R.string.gs_source_scale_details), stringResource(R.string.gs_source_camera_details)),
        officialUrl = SOURCE_MAPBOX_DEM_URL,
    )
}

private val MAPBOX_SOURCE_KEYS = setOf("MAPBOX_SATELLITE", "MAPBOX_PLAN", "MAPBOX_TOPO", "MAPBOX_SATELLITE_LABELS")
private const val SOURCE_MAPBOX_SATELLITE_URL = "https://docs.mapbox.com/data/tilesets/reference/mapbox-satellite/"
private const val SOURCE_MAPBOX_STYLES_URL = "https://docs.mapbox.com/map-styles/reference/"
private const val SOURCE_MAPBOX_DEM_URL = "https://docs.mapbox.com/data/tilesets/reference/mapbox-terrain-dem-v1/"
private const val SOURCE_MAPBOX_TERMS_URL = "https://www.mapbox.com/legal/tos/"
private const val SOURCE_IGN_ORTHO_URL = "https://geoservices.ign.fr/bdortho"
private const val SOURCE_OPEN_LICENSE_URL = "https://www.data.gouv.fr/pages/legal/licences/etalab-2-0"
