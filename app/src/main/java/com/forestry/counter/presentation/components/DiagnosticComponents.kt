package com.forestry.counter.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.model.station.DiagnosticPhoto

@Composable
fun DiagnosticPhotoCaptureSection(
    photos: List<DiagnosticPhoto>,
    onAddPhoto: (String, String, String) -> Unit,
    onRemovePhoto: (Int) -> Unit,
    minPhotos: Int = 0,
    photoTypeOptions: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.diag_photos_terrain, photos.size, if (minPhotos > 0) " / min $minPhotos" else ""),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(onClick = { onAddPhoto("", "", photoTypeOptions.firstOrNull() ?: "Général") }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.diag_ajouter_photo))
            }
        }
    }
}

@Composable
fun ExpertTutorialDialog(
    title: String,
    message: String,
    bulletPoints: List<String> = emptyList(),
    icon: ImageVector = Icons.Default.Info,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                bulletPoints.forEach { point ->
                    Text("• $point", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.diag_compris)) }
        }
    )
}
