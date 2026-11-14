package com.forestry.counter.presentation.screens.field

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.forestry.counter.R

/** Shared presentation only: values and permissions are supplied by the existing hosts. */
@Composable internal fun RetainedHelp(title: String, body: String, modifier: Modifier = Modifier) {
    var open by remember(title, body) { mutableStateOf(false) }
    IconButton({ open = true }, modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
        Icon(Icons.Outlined.Info, stringResource(R.string.retained_explain, title), Modifier.size(18.dp))
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) },
        text = { Text(body) }, confirmButton = { TextButton({ open = false }) { Text(stringResource(R.string.retained_close)) } })
}

@Composable internal fun RetainedHeading(title: String, help: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        help?.let { RetainedHelp(title, it) }
    }
}

@Composable internal fun RetainedActionRow(
    icon: ImageVector, title: String, subtitle: String? = null,
    enabled: Boolean = true, modifier: Modifier = Modifier, help: String? = null, compact: Boolean = false, action: () -> Unit,
) {
    Surface(onClick = action, enabled = enabled, modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = if (compact) 4.dp else 12.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            help?.let { RetainedHelp(title, it) }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp))
        }
    }
}
