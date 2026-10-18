package com.forestry.counter.presentation.screens.packs

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.forestry.counter.R
import com.forestry.counter.data.packs.*
import kotlinx.coroutines.CancellationException
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

/** Emblème dessiné depuis les contours IGN ; ne reprend pas une marque officielle. */
@Composable
fun TerritoryPresentationBadge(
    territoryId: String,
    modifier: Modifier = Modifier.size(48.dp),
    onClick: (() -> Unit)? = null
) {
    val assets by territorialAssets(territoryId)
    val outline = assets?.getOrNull()?.outline
    val description = stringResource(R.string.pack_presentation_outline_description, outline?.name ?: territoryId)
    val interactive = onClick?.let { modifier.clickable(onClick = it) } ?: modifier
    Box(interactive.semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        if (outline != null) TerritorySilhouette(outline, Modifier.fillMaxSize().padding(4.dp))
        else if (assets == null) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
    }
}

/** Fiche éditoriale locale indépendante du compte, du téléchargement et de la version d'un pack. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerritoryPresentationSheet(territoryId: String, onDismiss: () -> Unit) {
    val assets by territorialAssets(territoryId)
    val loaded = assets?.getOrNull()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column {
                TopAppBar(
                    title = { Text(loaded?.outline?.name ?: stringResource(R.string.pack_presentation_title)) },
                    navigationIcon = { IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.pack_presentation_close))
                    } }
                )
                when {
                    assets == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    loaded == null -> Text(stringResource(R.string.pack_presentation_outline_unavailable), Modifier.padding(24.dp))
                    else -> TerritoryPresentationBody(loaded)
                }
            }
        }
    }
}

@Composable
private fun territorialAssets(territoryId: String): State<Result<TerritoryPresentationAssets>?> {
    val context = LocalContext.current
    return produceState<Result<TerritoryPresentationAssets>?>(null, context, territoryId) {
        value = try { Result.success(TerritoryPresentationAssetStore.load(context, territoryId)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
    }
}

@Composable
private fun TerritoryPresentationBody(assets: TerritoryPresentationAssets) {
    val presentation = assets.presentation
    val sourcesByUrl = presentation?.sources?.associateBy { it.url }.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "heading:outline") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TerritorySilhouette(assets.outline, Modifier.size(88.dp).padding(4.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(assets.outline.name, style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(if (assets.outline.territoryId.startsWith("region:")) R.string.pack_presentation_region else R.string.pack_presentation_department), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.pack_presentation_outline_source), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item(key = "notice:separate-content") {
            Text(stringResource(R.string.pack_presentation_separate_content), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (presentation == null) {
            item(key = "notice:unavailable") {
                Text(stringResource(if (assets.presentationUnavailable) R.string.pack_presentation_unavailable else R.string.pack_presentation_not_qualified), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            item(key = "text:description") { Text(presentation.description, style = MaterialTheme.typography.bodyLarge) }
            items(presentation.images, key = { "image:${it.assetPath}" }) { image -> TerritoryPresentationPhoto(image) }
            if (presentation.highlights.isNotEmpty()) {
                item(key = "heading:highlights") { Text(stringResource(R.string.pack_presentation_highlights), style = MaterialTheme.typography.titleLarge) }
                items(presentation.highlights, key = { "highlight:${it.name}" }) { highlight ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(highlight.name, style = MaterialTheme.typography.titleMedium)
                        Text(highlight.summary, style = MaterialTheme.typography.bodyMedium)
                        highlight.sources.forEach { url ->
                            val source = sourcesByUrl.getValue(url)
                            TerritorySourceLink(source.title, source.url)
                        }
                    }
                }
            }
            item(key = "heading:sources") { Text(stringResource(R.string.pack_presentation_sources), style = MaterialTheme.typography.titleLarge) }
            items(presentation.sources, key = { "source:${it.url}" }) { source ->
                Column {
                    TerritorySourceLink(source.title, source.url)
                    Text(source.publisher, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            assets.rightsQualification?.let { rights ->
                item(key = "heading:rights") {
                    Text(stringResource(R.string.pack_presentation_media_rights), style = MaterialTheme.typography.titleLarge)
                    Text(rights.description, style = MaterialTheme.typography.bodySmall)
                }
                items(rights.sources, key = { "rights-source:${it.url}" }) { source -> TerritorySourceLink(source.title, source.url) }
            }
        }
    }
}

@Composable
private fun TerritoryPresentationPhoto(image: TerritoryImage) {
    val context = LocalContext.current
    val bitmap by produceState<Result<android.graphics.Bitmap>?>(null, context, image) {
        value = try { Result.success(TerritoryPresentationAssetStore.readPhoto(context, image)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val decoded = bitmap?.getOrNull()
            when {
                decoded != null -> Image(decoded.asImageBitmap(), contentDescription = image.title,
                    modifier = Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height), contentScale = ContentScale.Fit)
                bitmap == null -> Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> Text(stringResource(R.string.pack_presentation_photo_unavailable), Modifier.padding(16.dp))
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(image.title, style = MaterialTheme.typography.titleSmall)
                if (image.type == "aerial_view") {
                    Text(stringResource(R.string.pack_presentation_aerial_view), style = MaterialTheme.typography.labelLarge)
                    Text(image.caption, style = MaterialTheme.typography.bodySmall)
                    Text(image.captureDate?.let { stringResource(R.string.pack_presentation_capture_date, it) }
                        ?: stringResource(R.string.pack_presentation_capture_unknown), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.pack_presentation_image_author, image.author), style = MaterialTheme.typography.bodySmall)
                Text(image.attribution, style = MaterialTheme.typography.bodySmall)
                TerritorySourceLink(stringResource(R.string.pack_presentation_license, image.licenseSpdx), image.licenseUrl)
                TerritorySourceLink(stringResource(R.string.pack_presentation_image_source), image.sourceUrl)
            }
        }
    }
}

@Composable
private fun TerritorySourceLink(label: String, url: String) {
    val handler = LocalUriHandler.current
    val context = LocalContext.current
    val unavailable = stringResource(R.string.pack_presentation_link_unavailable)
    TextButton(onClick = {
        runCatching { handler.openUri(url) }.onFailure { Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show() }
    }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TerritorySilhouette(outline: TerritoryOutline, modifier: Modifier) {
    val shape = remember(outline) { silhouettePath(outline) }
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val factor = minOf(size.width / shape.width, size.height / shape.height)
        val left = (size.width - shape.width * factor) / 2
        val top = (size.height - shape.height * factor) / 2
        translate(left, top) {
            with(drawContext.canvas) {
                save()
                scale(factor, factor)
                drawPath(shape.path, androidx.compose.ui.graphics.Paint().apply { this.color = color; isAntiAlias = true })
                restore()
            }
        }
    }
}

private data class SilhouettePath(val path: Path, val width: Float, val height: Float)

/** Projection de présentation uniquement ; les coordonnées de l'asset ne sont jamais modifiées. */
private fun silhouettePath(outline: TerritoryOutline): SilhouettePath {
    val polygons = outline.polygons.map { polygon -> polygon.map { ring -> ring.map { (longitude, latitude) ->
        longitude * PI / 180 to -ln(tan(PI / 4 + latitude.coerceIn(-85.0511, 85.0511) * PI / 360))
    } } }
    val points = polygons.flatten().flatten()
    val minimumX = points.minOf { it.first }; val minimumY = points.minOf { it.second }
    val width = (points.maxOf { it.first } - minimumX).toFloat()
    val height = (points.maxOf { it.second } - minimumY).toFloat()
    require(width > 0f && height > 0f)
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
        for (polygon in polygons) for (ring in polygon) {
            ring.forEachIndexed { index, (x, y) ->
                if (index == 0) moveTo((x - minimumX).toFloat(), (y - minimumY).toFloat())
                else lineTo((x - minimumX).toFloat(), (y - minimumY).toFloat())
            }
            close()
        }
    }
    return SilhouettePath(path, width, height)
}
