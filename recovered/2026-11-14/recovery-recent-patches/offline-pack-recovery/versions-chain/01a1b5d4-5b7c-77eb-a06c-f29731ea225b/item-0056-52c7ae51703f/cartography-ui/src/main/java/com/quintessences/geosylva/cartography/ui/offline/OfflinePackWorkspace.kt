package com.quintessences.geosylva.cartography.ui.offline

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quintessences.geosylva.cartography.ui.R

/** Map stays full size beneath a single floating header and a compact, scrollable panel. */
@Composable
fun OfflinePackWorkspace(
    title: String,
    onBack: () -> Unit,
    onSearch: (() -> Unit)?,
    onLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    map: @Composable BoxScope.(topInset: Dp, bottomInset: Dp) -> Unit,
    panel: (@Composable ColumnScope.() -> Unit)? = null,
    panelScroll: Boolean = true,
) {
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(90.dp) }
    var panelHeight by remember { mutableStateOf(0.dp) }
    LaunchedEffect(panel == null) { if (panel == null) panelHeight = 0.dp }
    BoxWithConstraints(modifier.fillMaxSize().imePadding()) {
        map(headerHeight, panelHeight)
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { headerHeight = with(density) { it.height.toDp() } }.statusBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(50), shadowElevation = 2.dp) {
                IconButton(onClick = onBack, Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.gs_offline_back)) }
            }
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(50), shadowElevation = 2.dp) {
                Text(title, Modifier.padding(horizontal = 18.dp, vertical = 14.dp), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
            onSearch?.let { search -> Surface(shape = RoundedCornerShape(50), shadowElevation = 2.dp) {
                IconButton(onClick = search, Modifier.size(48.dp)) { Icon(Icons.Default.Search, stringResource(R.string.gs_offline_search)) }
            } }
            Surface(shape = RoundedCornerShape(50), shadowElevation = 2.dp) {
                IconButton(onClick = onLibrary, Modifier.size(48.dp)) { Icon(Icons.Default.FolderOpen, stringResource(R.string.gs_offline_library)) }
            }
        }
        panel?.let {
            val panelMaxHeight = maxHeight * 0.52f
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { panelHeight = with(density) { it.height.toDp() } }.navigationBarsPadding().padding(12.dp)
                .heightIn(max = panelMaxHeight), shape = RoundedCornerShape(28.dp), shadowElevation = 4.dp) {
                val panelBody = if (panelScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier
                Column(Modifier.fillMaxWidth().then(panelBody).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp), content = it)
            }
        }
    }
}

/** The same preparation form is used for a viewport and official administrative geometries. */
@Composable
fun OfflinePackPreparation(
    name: String,
    onName: (String) -> Unit,
    minZoom: Int,
    maxZoom: Int,
    onMaxZoom: (Int) -> Unit,
    relief: Boolean,
    onRelief: ((Boolean) -> Unit)?,
    planning: Boolean,
    onEstimate: () -> Unit,
    modifier: Modifier = Modifier,
    datasets: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(name, onName, label = { Text(stringResource(R.string.gs_offline_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.gs_offline_satellite), style = MaterialTheme.typography.titleMedium)
        onRelief?.let { callback -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.gs_offline_relief), Modifier.weight(1f))
            Switch(relief, callback)
        } }
        Text(stringResource(R.string.gs_offline_zooms, minZoom, maxZoom), style = MaterialTheme.typography.bodyMedium)
        if (minZoom < 22) Slider(maxZoom.toFloat(), { onMaxZoom(it.toInt().coerceAtLeast(minZoom)) },
            valueRange = minZoom.toFloat()..22f, steps = (21 - minZoom).coerceAtLeast(0))
        datasets()
        if (planning) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onEstimate, enabled = !planning && name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.gs_offline_estimate))
        }
    }
}
