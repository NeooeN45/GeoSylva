package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.forestry.counter.BuildConfig
import com.forestry.counter.R
import com.forestry.counter.domain.location.OfflineTileManager
import com.forestry.counter.network.SecureHttpClient
import com.forestry.counter.presentation.theme.Elevation
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Radius
import com.forestry.counter.presentation.theme.Space
import com.forestry.counter.presentation.theme.Touch

/**
 * Tiroir plein-largeur de sélection de fond de carte : aperçu réel de
 * chaque couche (tuile centrée France), nom adapté, recherche.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapLayerPicker(
    visible: Boolean,
    loadState: MapLayerLoadState,
    hasOfflineTilesState: Boolean,
    offlineTileManager: OfflineTileManager,
    onLayerSelected: (Int) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    is3DActive: Boolean = false,
    onToggle3D: (() -> Unit)? = null,
) {
    if (!visible) return
    var query by remember { mutableStateOf("") }
    val (offlineTileCount, _) = remember(hasOfflineTilesState) { offlineTileManager.cacheStats() }
    // Résolu ici (contexte composable) car stringResource ne peut pas être
    // appelé depuis un lambda de filtrage ordinaire plus bas.
    val layerLabels = MAP_LAYERS.associateWith { stringResource(it.previewLabelResId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md)) {
            Text(
                stringResource(R.string.map_layer_picker_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            if (loadState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = Space.xs))
                Text(
                    stringResource(R.string.map_layer_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.xs),
                )
            } else if (loadState.failedIndex != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = GsShape.sm,
                    modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.map_layer_load_error),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }

            // ── Barre de recherche (même look que Réglages) ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.sm)
                    .height(48.dp),
                shape = GsShape.pill,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(Space.xs))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.map_layer_search_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.cd_clear), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            val visibleLayers = MAP_LAYERS.filter { layer ->
                if (layer.key == "OFFLINE_LOCAL" && !hasOfflineTilesState) return@filter false
                query.isBlank() || (layerLabels[layer]?.contains(query, ignoreCase = true) == true)
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
                contentPadding = PaddingValues(bottom = Space.md),
                modifier = Modifier.heightIn(max = 480.dp),
            ) {
                items(visibleLayers, key = { it.key }) { layer ->
                    val index = MAP_LAYERS.indexOf(layer)
                    LayerTile(
                        layer = layer,
                        isSelected = index == loadState.displayedIndex,
                        isLoading = index == loadState.loadingRequest?.layerIndex,
                        badgeCount = if (layer.key == "OFFLINE_LOCAL") offlineTileCount else 0,
                        onClick = { onLayerSelected(index) },
                    )
                }
            }

            if (onToggle3D != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Touch.field)
                        .clip(GsShape.sm)
                        .selectable(selected = is3DActive, onClick = onToggle3D)
                        .padding(horizontal = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    Icon(
                        Icons.Default.Landscape,
                        contentDescription = null,
                        modifier = Modifier.size(Space.md),
                        tint = if (is3DActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.map_toggle_3d),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (is3DActive) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = is3DActive, onCheckedChange = { onToggle3D() })
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Space.md))
            }
        }
    }
}

/**
 * Coordonnée fixe (Web Mercator, zoom 6) centrée sur la France pour l'aperçu.
 * Uniquement pour les sources raster (PNG/JPEG) : les tuiles vectorielles
 * MapTiler (.pbf) ne sont pas des images décodables par Coil et n'ont donc
 * jamais d'aperçu réel — repli direct sur le dégradé + emoji pour elles.
 */
private fun previewTileUrl(layer: MapLayerDef): String? {
    val template = layer.tileUrls.firstOrNull() ?: return null
    if (!com.forestry.counter.domain.location.MapSourcePolicy.isOnlineTemplateQualified(template)) return null
    if (template.contains(".pbf")) return null
    val url = template.replace("{z}", "6").replace("{x}", "32").replace("{y}", "22")
    return if (SecureHttpClient.isSecureDomain(url)) url else null
}

/** Même en-tête que le client HTTP MapLibre — certains serveurs de tuiles
 * (IGN notamment) rejettent les requêtes sans User-Agent identifiable. */
private val PREVIEW_USER_AGENT =
    "GeoSylva/${BuildConfig.VERSION_NAME} (+https://geosylva.fr; contact: contact@geosylva.fr)"

/**
 * Grande tuile illustrée d'un fond de carte : dégradé + emoji toujours en
 * fond (lisible immédiatement, jamais "cassé"), aperçu réel (Coil) superposé
 * par-dessus quand disponible — s'il échoue, le dégradé reste visible en
 * dessous au lieu d'un rendu vide.
 */
@Composable
private fun LayerTile(
    layer: MapLayerDef,
    isSelected: Boolean,
    isLoading: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
) {
    val previewUrl = remember(layer.key) { previewTileUrl(layer) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .clip(GsShape.md)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = GsShape.md,
            )
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(layer.emoji, style = MaterialTheme.typography.displaySmall)
        }
        if (previewUrl != null) {
            val request = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(previewUrl)
                .setHeader("User-Agent", PREVIEW_USER_AGENT)
                .crossfade(true)
                .build()
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        // Scrim bas pour la lisibilité du libellé.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                        startY = 0.4f,
                    )
                ),
        )

        Text(
            stringResource(layer.previewLabelResId),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Start,
            modifier = Modifier.align(Alignment.BottomStart).padding(Space.sm),
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.TopEnd).padding(Space.xs).size(Space.lg),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
            )
        } else if (isSelected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(Space.xs)
                    .background(Color.White, androidx.compose.foundation.shape.CircleShape),
            )
        }

        if (badgeCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = GsShape.field,
                modifier = Modifier.align(Alignment.TopStart).padding(Space.xs),
            ) {
                Text(
                    "$badgeCount",
                    modifier = Modifier.padding(horizontal = Space.xs, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
