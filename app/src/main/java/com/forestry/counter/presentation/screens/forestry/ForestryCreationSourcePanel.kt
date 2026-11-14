package com.forestry.counter.presentation.screens.forestry

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.forestry.counter.R
import com.forestry.counter.domain.location.ForestSearchResult
import com.forestry.counter.domain.location.ParcelMapSource
import com.forestry.counter.domain.location.SourceParcel
import com.quintessences.geosylva.cartography.ui.forestry.ForestrySourceDetails
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal fun forestryAreaHa(areaM2: Double): String = String.format(Locale.getDefault(), "%.2f ha", areaM2 / 10_000.0)

/** The two surface concepts remain distinct; no source value is reconstructed from the contour. */
@Composable
internal fun ForestryCreationSourcePanel(forest: ForestSearchResult? = null, parcel: SourceParcel? = null) {
    val uriHandler = LocalUriHandler.current
    var linkFailed by remember { mutableStateOf(false) }
    val onf = parcel?.source == ParcelMapSource.ONF
    val rows = buildList {
        (parcel?.geometry ?: forest?.geometry)?.let {
            add(stringResource(R.string.fc_boundary_area) to forestryAreaHa(it.areaSquareMetres))
        }
        parcel?.sourceAreaSquareMetres?.let { add(stringResource(R.string.fc_source_area) to forestryAreaHa(it)) }
        parcel?.cadastralAreaSquareMetres?.let { add(stringResource(R.string.fc_cadastral_area) to forestryAreaHa(it)) }
        (parcel?.sourceDataset ?: forest?.sourceDataset)?.let { add(stringResource(R.string.fc_dataset) to it) }
        (parcel?.id ?: forest?.id)?.let { add(stringResource(R.string.fc_identifier) to it) }
        parcel?.let {
            add(stringResource(R.string.fc_retrieved) to DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.fetchedAt)))
            it.packReference?.let { reference -> add(stringResource(R.string.fc_pack) to reference) }
        }
    }
    ForestrySourceDetails(stringResource(R.string.fc_source_details), parcel?.attribution ?: forest?.sourceLabel.orEmpty(),
        rows, stringResource(R.string.fc_area_explanation)) {
        TextButton(onClick = {
            runCatching { uriHandler.openUri(if (onf) ONF_DATASET else if (parcel != null) CADASTRE_DATASET else ONF_CATALOG) }
                .onFailure { linkFailed = true }
        }) { Text(stringResource(R.string.fc_official_source)) }
        if (!onf && (parcel != null || forest?.sourceDataset?.startsWith("onf") == true)) {
            TextButton(onClick = { runCatching { uriHandler.openUri(OPEN_LICENSE) }.onFailure { linkFailed = true } }) {
                Text(stringResource(R.string.fc_conditions))
            }
        }
    }
    if (linkFailed) Text(stringResource(R.string.fc_link_unavailable), color = MaterialTheme.colorScheme.error)
    parcel?.geometrySha256?.let { Text(stringResource(R.string.fc_geometry_digest, it), style = MaterialTheme.typography.labelSmall) }
}

private const val ONF_CATALOG = "https://geo-onf.opendata.arcgis.com/"
private const val ONF_DATASET = "https://services1.arcgis.com/Y4HgaQpzkE7kenlE/arcgis/rest/services/Parcelles_foresti%C3%A8res_publiques_de_France_m%C3%A9tropolitaine/FeatureServer/11"
private const val CADASTRE_DATASET = "https://geoservices.ign.fr/parcellaire-express-pci"
private const val OPEN_LICENSE = "https://www.data.gouv.fr/pages/legal/licences/etalab-2-0"
