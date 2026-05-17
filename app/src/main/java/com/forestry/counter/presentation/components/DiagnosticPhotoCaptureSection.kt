package com.forestry.counter.presentation.components

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.forestry.counter.domain.model.station.DiagnosticPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Composant réutilisable de capture photo pour les diagnostics (Ripisylve, Station).
 * Gère : prise de vue caméra, import galerie, choix du type, légende, affichage vignette.
 */
@Composable
fun DiagnosticPhotoCaptureSection(
    photos: List<DiagnosticPhoto>,
    onAddPhoto: (uri: String, legend: String, type: String) -> Unit,
    onRemovePhoto: (index: Int) -> Unit,
    minPhotos: Int = 2,
    photoTypeOptions: List<String> = listOf("Général", "Paysage", "Sol", "Végétation")
) {
    val context = LocalContext.current
    var showSourceDialog by remember { mutableStateOf(false) }
    var showTypeDialog by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf(photoTypeOptions.firstOrNull() ?: "Général") }
    var legendText by remember { mutableStateOf("") }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraUri?.toString()?.let {
                pendingUri = it
                selectedType = photoTypeOptions.firstOrNull() ?: "Général"
                legendText = ""
                showTypeDialog = true
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}
            pendingUri = it.toString()
            selectedType = photoTypeOptions.firstOrNull() ?: "Général"
            legendText = ""
            showTypeDialog = true
        }
    }

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("Ajouter une photo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            showSourceDialog = false
                            val dir = File(context.getExternalFilesDir(null), "photos")
                            dir.mkdirs()
                            val file = File(dir, "diag_${System.currentTimeMillis()}.jpg")
                            val uri = FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", file
                            )
                            cameraUri = uri
                            cameraLauncher.launch(uri)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Prendre une photo")
                    }
                    OutlinedButton(
                        onClick = {
                            showSourceDialog = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Choisir depuis la galerie")
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showTypeDialog && pendingUri != null) {
        AlertDialog(
            onDismissRequest = { showTypeDialog = false; pendingUri = null },
            title = { Text("Classer la photo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Type :", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    photoTypeOptions.forEach { type ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { selectedType = type }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedType == type, onClick = { selectedType = type })
                            Spacer(Modifier.width(8.dp))
                            Text(type, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = legendText,
                        onValueChange = { legendText = it },
                        label = { Text("Légende (optionnel)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onAddPhoto(pendingUri!!, legendText, selectedType)
                    pendingUri = null
                    legendText = ""
                    showTypeDialog = false
                }) { Text("Valider") }
            },
            dismissButton = {
                TextButton(onClick = { showTypeDialog = false; pendingUri = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    Button(
        onClick = { showSourceDialog = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.AddAPhoto, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Ajouter une photo")
    }

    if (photos.isEmpty()) {
        Box(
            Modifier.fillMaxWidth().height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.PhotoLibrary, contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Text(
                    "Aucune photo — $minPhotos requises minimum",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        photos.forEachIndexed { idx, photo ->
            PhotoThumbnailCard(photo = photo, onRemove = { onRemovePhoto(idx) })
        }
    }
}

@Composable
private fun PhotoThumbnailCard(photo: DiagnosticPhoto, onRemove: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(photo.uri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photo.uri) {
        withContext(Dispatchers.IO) {
            val loaded = try {
                val uri = Uri.parse(photo.uri)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
                }
            } catch (_: Exception) { null }
            withContext(Dispatchers.Main.immediate) { bitmap = loaded }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = photo.legend,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Image, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        photo.type,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    photo.legend.ifBlank { "Sans légende" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
