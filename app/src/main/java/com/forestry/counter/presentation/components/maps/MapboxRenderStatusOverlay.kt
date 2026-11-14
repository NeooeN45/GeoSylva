package com.forestry.counter.presentation.components.maps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.quintessences.geosylva.cartography.mapbox.MapRenderStatus

/** Shared status for every map host. No image or previous SDK substitutes for a failed map. */
@Composable
fun MapboxRenderStatusOverlay(
    status: MapRenderStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status is MapRenderStatus.Ready) return
    Surface(modifier, shape = MaterialTheme.shapes.large, tonalElevation = 3.dp) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (status) {
                is MapRenderStatus.Loading -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.mapbox_loading))
                }
                MapRenderStatus.ConfigurationRequired -> Text(stringResource(R.string.mapbox_configuration_required))
                is MapRenderStatus.Failed -> {
                    Text(stringResource(R.string.mapbox_render_unavailable))
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                is MapRenderStatus.Ready -> Unit
            }
        }
    }
}
