package com.quintessences.geosylva.cartography.ui.forestry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Shared, non-modal creation workspace. The host owns all data and mutations. */
@Composable
fun ForestryMapLayout(
    title: String,
    subtitle: String?,
    backLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    helpLabel: String? = null,
    onHelp: (() -> Unit)? = null,
    expandedPanel: Boolean = false,
    headerControls: @Composable ColumnScope.() -> Unit = {},
    map: @Composable (Modifier) -> Unit,
    panel: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val panelLimit = maxHeight * if (expandedPanel) 0.58f else 0.46f
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                map(Modifier.fillMaxSize())
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(28.dp), shadowElevation = 3.dp,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)) {
                        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack, enabled = enabled, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, backLabel)
                            }
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis)
                                subtitle?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (onHelp != null) IconButton(onClick = onHelp, enabled = enabled,
                                modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Info, helpLabel) }
                        }
                    }
                    headerControls()
                }
            }
            Surface(shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), tonalElevation = 3.dp,
                shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().heightIn(max = panelLimit).navigationBarsPadding().imePadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp),
                        shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.outlineVariant) {}
                    panel()
                }
            }
        }
    }
}

@Composable
fun ForestryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** Provenance values are supplied as recorded; this component never fills missing metadata. */
@Composable
fun ForestrySourceDetails(title: String, attribution: String, rows: List<Pair<String, String>>,
    caution: String, links: @Composable RowScope.() -> Unit = {}) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    rows.forEach { (label, value) -> ForestryMetric(label, value) }
    Text(caution, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(attribution, style = MaterialTheme.typography.labelSmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = links)
}
