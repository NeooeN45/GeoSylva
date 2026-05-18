package com.forestry.counter.presentation.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.forestry.counter.domain.model.station.DiagnosticPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
//  Types photo
// ─────────────────────────────────────────────────────────────────────────────

enum class PhotoCategory(val label: String, val emoji: String, val color: Color) {
    SOL("Sol",            "🪨", Color(0xFF7D5A3C)),
    FLORE("Flore",        "🌿", Color(0xFF2D6A4F)),
    PROFIL("Profil",      "📐", Color(0xFF5D4037)),
    HYDROMORPH("Hydro.",  "💧", Color(0xFF1B4F72)),
    PEUPLEMENT("Peupl.",  "🌲", Color(0xFF1B5E20)),
    PAYSAGE("Paysage",    "🏔",  Color(0xFF546E7A)),
    RIPISYLVE("Ripis.",   "🏞", Color(0xFF00695C)),
    AUTRE("Autre",        "📷", Color(0xFF757575))
}

// ─────────────────────────────────────────────────────────────────────────────
//  StationPhotoGalleryBlock
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StationPhotoGalleryBlock(
    photos: List<DiagnosticPhoto>,
    onAddPhoto: (uri: String, legend: String, type: String) -> Unit,
    onUpdatePhoto: (index: Int, legend: String, type: String) -> Unit,
    onRemovePhoto: (index: Int) -> Unit,
    onSetPrimary: (index: Int) -> Unit,
    minPhotos: Int = 2,
    primaryIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSourceDialog   by remember { mutableStateOf(false) }
    var showEditDialog     by remember { mutableStateOf<Int?>(null) }
    var showFullscreenIdx  by remember { mutableStateOf<Int?>(null) }
    var pendingUri         by remember { mutableStateOf<String?>(null) }
    var cameraUri          by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.toString()?.let { pendingUri = it; showEditDialog = -1 }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.toString()?.let { pendingUri = it; showEditDialog = -1 }
    }

    TerrainCard(modifier = modifier, accentColor = StationDiagColors.purpleAlt) {
        // ── Header ──────────────────────────────────────────────────────────
        BlockSectionTitle(
            text = "Photos & Documents",
            icon = Icons.Default.PhotoLibrary,
            color = StationDiagColors.purpleAlt,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val enough = photos.size >= minPhotos
                    Surface(
                        color = if (enough) StationDiagColors.forestLight else StationDiagColors.ochreLight,
                        shape = StationDiagShapes.badge
                    ) {
                        Text(
                            "${photos.size}/$minPhotos",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (enough) StationDiagColors.forestGreen else StationDiagColors.ochrePrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    IconButton(
                        onClick = { showSourceDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.AddAPhoto, "Ajouter", tint = StationDiagColors.purpleAlt, modifier = Modifier.size(20.dp))
                    }
                }
            }
        )

        Spacer(Modifier.height(4.dp))
        if (photos.size < minPhotos) {
            Text(
                "Minimum $minPhotos photos requises pour finaliser le diagnostic.",
                style = MaterialTheme.typography.bodySmall,
                color = StationDiagColors.ochrePrimary,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(12.dp))

        // ── Grille de photos ─────────────────────────────────────────────────
        if (photos.isEmpty()) {
            EmptyPhotoPlaceholder { showSourceDialog = true }
        } else {
            // Hauteur fixe calculée selon nb de lignes (2 colonnes)
            val rows = (photos.size + 1) / 2
            val gridHeight = (rows * 120 + (rows - 1) * 8).dp
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(gridHeight),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                userScrollEnabled = false
            ) {
                itemsIndexed(photos) { idx, photo ->
                    PhotoThumbnailCard(
                        photo = photo,
                        isPrimary = idx == primaryIndex,
                        onEdit = { showEditDialog = idx },
                        onDelete = { onRemovePhoto(idx) },
                        onSetPrimary = { onSetPrimary(idx) },
                        onFullscreen = { showFullscreenIdx = idx }
                    )
                }
                item {
                    AddPhotoTile { showSourceDialog = true }
                }
            }
        }

        // ── Catégories présentes ─────────────────────────────────────────────
        if (photos.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            val cats = photos.mapNotNull { p ->
                PhotoCategory.entries.firstOrNull { it.label == p.type || it.name == p.type }
            }.distinct()
            if (cats.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    cats.forEach { cat ->
                        Surface(color = cat.color.copy(alpha = 0.12f), shape = StationDiagShapes.badge) {
                            Text(
                                "${cat.emoji} ${cat.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = cat.color,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────
    if (showSourceDialog) {
        PhotoSourceDialog(
            onCamera = {
                showSourceDialog = false
                val file = File(context.getExternalFilesDir(null), "diag_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                cameraUri = uri
                cameraLauncher.launch(uri)
            },
            onGallery = { showSourceDialog = false; galleryLauncher.launch("image/*") },
            onDismiss = { showSourceDialog = false }
        )
    }

    val editIdx = showEditDialog
    if (editIdx != null) {
        val existing = if (editIdx >= 0 && editIdx < photos.size) photos[editIdx] else null
        PhotoEditDialog(
            initial = existing,
            onConfirm = { legend, type ->
                val uri = pendingUri
                if (existing == null && uri != null) {
                    onAddPhoto(uri, legend, type)
                    pendingUri = null
                } else if (existing != null) {
                    onUpdatePhoto(editIdx, legend, type)
                }
                showEditDialog = null
            },
            onDismiss = { showEditDialog = null; pendingUri = null }
        )
    }

    val fsIdx = showFullscreenIdx
    if (fsIdx != null && fsIdx < photos.size) {
        PhotoFullscreenDialog(photo = photos[fsIdx], onDismiss = { showFullscreenIdx = null })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Miniature photo
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PhotoThumbnailCard(
    photo: DiagnosticPhoto,
    isPrimary: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetPrimary: () -> Unit,
    onFullscreen: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val category = PhotoCategory.entries.firstOrNull { it.label == photo.type || it.name == photo.type }

    LaunchedEffect(photo.uri) {
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(android.net.Uri.parse(photo.uri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()?.let { bitmap = it }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(StationDiagShapes.cardSmall)
            .background(StationDiagColors.surfaceSubtle)
            .border(
                width = if (isPrimary) 2.dp else 0.dp,
                color = if (isPrimary) StationDiagColors.forestGreen else Color.Transparent,
                shape = StationDiagShapes.cardSmall
            )
            .clickable(onClick = onFullscreen)
    ) {
        if (bitmap != null) {
            val bmp = bitmap ?: return@Box
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = photo.legend,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize().background(StationDiagColors.surfaceSubtle), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Photo, null, tint = StationDiagColors.textSecondary, modifier = Modifier.size(28.dp))
            }
        }

        // Overlay bas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 5.dp, vertical = 3.dp)
        ) {
            Text(
                if (photo.legend.isNotBlank()) photo.legend else category?.label ?: photo.type,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontSize = 9.sp,
                maxLines = 1
            )
        }

        // Boutons actions
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SmallPhotoAction(Icons.Default.Edit, StationDiagColors.purpleAlt, onEdit)
            SmallPhotoAction(Icons.Default.Delete, StationDiagColors.conflictRed, onDelete)
        }

        if (isPrimary) {
            Surface(
                color = StationDiagColors.forestGreen,
                shape = RoundedCornerShape(topStart = 0.dp, bottomEnd = 6.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text("⭐", modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun SmallPhotoAction(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun AddPhotoTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(StationDiagShapes.cardSmall)
            .background(StationDiagColors.purpleAlt.copy(alpha = 0.07f))
            .border(1.dp, StationDiagColors.purpleAlt.copy(alpha = 0.3f), StationDiagShapes.cardSmall)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.AddAPhoto, null, tint = StationDiagColors.purpleAlt, modifier = Modifier.size(26.dp))
            Text("Ajouter", style = MaterialTheme.typography.labelSmall, color = StationDiagColors.purpleAlt, fontSize = 10.sp)
        }
    }
}

@Composable
private fun EmptyPhotoPlaceholder(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = StationDiagColors.purpleAlt.copy(alpha = 0.06f),
        shape = StationDiagShapes.cardSmall,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.AddAPhoto, null, tint = StationDiagColors.purpleAlt, modifier = Modifier.size(36.dp))
            Text("Aucune photo", style = MaterialTheme.typography.bodyMedium, color = StationDiagColors.textSecondary, fontWeight = FontWeight.SemiBold)
            Text("Appuyez pour photographier ou importer une image du terrain", style = MaterialTheme.typography.bodySmall, color = StationDiagColors.textSecondary, fontSize = 11.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PhotoSourceDialog(onCamera: () -> Unit, onGallery: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter une photo", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ListItem(
                    headlineContent = { Text("Prendre une photo") },
                    leadingContent = { Icon(Icons.Default.PhotoCamera, null, tint = StationDiagColors.forestGreen) },
                    modifier = Modifier.clip(StationDiagShapes.cardSmall).clickable(onClick = onCamera)
                )
                ListItem(
                    headlineContent = { Text("Importer depuis la galerie") },
                    leadingContent = { Icon(Icons.Default.PhotoLibrary, null, tint = StationDiagColors.waterBlue) },
                    modifier = Modifier.clip(StationDiagShapes.cardSmall).clickable(onClick = onGallery)
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoEditDialog(
    initial: DiagnosticPhoto?,
    onConfirm: (legend: String, type: String) -> Unit,
    onDismiss: () -> Unit
) {
    var legend by remember { mutableStateOf(initial?.legend ?: "") }
    var selectedCategory by remember {
        mutableStateOf(
            PhotoCategory.entries.firstOrNull { it.label == initial?.type || it.name == initial?.type }
                ?: PhotoCategory.SOL
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nouvelle photo" else "Modifier la photo", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = legend,
                    onValueChange = { legend = it },
                    label = { Text("Légende (optionnel)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = StationDiagShapes.input
                )
                Text("Type de photo", style = MaterialTheme.typography.labelMedium, color = StationDiagColors.textSecondary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PhotoCategory.entries.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { cat ->
                                val sel = selectedCategory == cat
                                Surface(
                                    onClick = { selectedCategory = cat },
                                    color = if (sel) cat.color.copy(alpha = 0.2f) else StationDiagColors.surfaceSubtle,
                                    shape = StationDiagShapes.badge,
                                    border = if (sel) BorderStroke(1.dp, cat.color) else null
                                ) {
                                    Text(
                                        "${cat.emoji} ${cat.label}",
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (sel) cat.color else StationDiagColors.textSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(legend, selectedCategory.label) },
                colors = ButtonDefaults.buttonColors(containerColor = StationDiagColors.forestGreen)
            ) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun PhotoFullscreenDialog(photo: DiagnosticPhoto, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(photo.uri) {
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(android.net.Uri.parse(photo.uri))?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.let { bitmap = it }
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = StationDiagShapes.card) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(photo.legend.ifBlank { photo.type }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Fermer") }
                }
                if (bitmap != null) {
                    val bmp = bitmap ?: return@Column
                    Image(bmp.asImageBitmap(), null, modifier = Modifier.fillMaxWidth().aspectRatio(4f/3f), contentScale = ContentScale.Fit)
                }
                if (photo.legend.isNotBlank()) {
                    Text(photo.legend, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp), color = StationDiagColors.textSecondary)
                }
            }
        }
    }
}
