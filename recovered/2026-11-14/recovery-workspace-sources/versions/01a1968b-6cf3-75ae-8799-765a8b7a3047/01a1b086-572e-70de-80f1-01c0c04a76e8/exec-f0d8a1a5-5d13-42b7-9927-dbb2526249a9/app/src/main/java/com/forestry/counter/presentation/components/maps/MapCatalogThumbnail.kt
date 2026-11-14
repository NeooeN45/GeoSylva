package com.forestry.counter.presentation.components.maps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.forestry.counter.R
import com.quintessences.geosylva.cartography.ui.MapPreviewKey

/**
 * Offline catalog illustrations, never the user's data or evidence of availability.
 * Layer/IGN previews use a licensed public IGN orthophoto with fictitious vectors.
 * Mapbox style choices retain schematic illustrations, not redistributed SDK tiles.
 * Provenance and license are available in Sources and details.
 */
@Composable
internal fun MapCatalogThumbnail(key: MapPreviewKey, modifier: Modifier = Modifier) {
    if (key.value in PHOTO_PREVIEW_KEYS) PhotographicLayerPreview(key, modifier)
    else SchematicCatalogThumbnail(key, modifier)
}

private val PHOTO_PREVIEW_KEYS = setOf(
    "ORTHO_IGN", "LOCAL_TREES", "LOCAL_MEASUREMENTS", "ILLUSTRATION_PARCELS",
    "ILLUSTRATION_CADASTRE", "ILLUSTRATION_PUBLIC_FORESTS", "ILLUSTRATION_SLOPES",
)

@Composable
private fun PhotographicLayerPreview(key: MapPreviewKey, modifier: Modifier) {
    val description = stringResource(R.string.map_catalog_photo_illustration)
    Box(modifier.semantics { contentDescription = description }) {
        Image(painterResource(R.drawable.gs_preview_ortho_vercors), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High)
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun outline(vararg vertices: Pair<Float, Float>) = Path().apply {
                moveTo(vertices[0].first * w, vertices[0].second * h)
                vertices.drop(1).forEach { lineTo(it.first * w, it.second * h) }
                close()
            }
            val boundary = outline(.12f to .26f, .39f to .13f, .83f to .29f,
                .89f to .7f, .57f to .88f, .19f to .72f)
            val green = Color(0xFF9BE4B4)
            when (key.value) {
                "LOCAL_TREES" -> {
                    drawPath(boundary, green.copy(alpha = .12f))
                    drawPath(boundary, green.copy(alpha = .8f), style = Stroke(.8f * density))
                    listOf(.32f to .61f, .48f to .35f, .7f to .57f).forEach { (x, y) ->
                        val point = Offset(w * x, h * y)
                        drawCircle(Color(0xFF102C27).copy(alpha = .4f), 4.4f * density, point)
                        drawCircle(Color.White, 3.6f * density, point)
                        drawCircle(Color(0xFF1675CC), 2.3f * density, point)
                    }
                }
                "LOCAL_MEASUREMENTS" -> {
                    val a = Offset(w * .17f, h * .77f)
                    val b = Offset(w * .48f, h * .48f)
                    val c = Offset(w * .84f, h * .22f)
                    listOf(a to b, b to c).forEach { (from, to) ->
                        drawLine(Color.White, from, to, 3.2f * density, StrokeCap.Round)
                        drawLine(Color(0xFF2878C7), from, to, 1.8f * density, StrokeCap.Round)
                    }
                    listOf(a, b, c).forEach {
                        drawCircle(Color.White, 3.2f * density, it)
                        drawCircle(Color(0xFF246BB0), 1.8f * density, it)
                    }
                }
                "ILLUSTRATION_CADASTRE" -> {
                    drawPath(boundary, Color(0xFFF5E6BD), style = Stroke(1.1f * density))
                    drawLine(Color(0xFFF5E6BD), Offset(w * .4f, h * .15f), Offset(w * .55f, h * .86f), density)
                    drawLine(Color(0xFFF5E6BD), Offset(w * .14f, h * .5f), Offset(w * .88f, h * .5f), density)
                }
                "ILLUSTRATION_SLOPES" -> {
                    repeat(5) { index ->
                        val contour = Path().apply {
                            moveTo(-w * .05f, h * (.15f + index * .17f))
                            cubicTo(w * .3f, h * (index * .17f - .08f), w * .65f,
                                h * (index * .17f + .5f), w * 1.05f, h * (index * .17f + .12f))
                        }
                        drawPath(contour, Color(0xFFEAE2CC).copy(alpha = .9f), style = Stroke(.8f * density))
                    }
                }
                "ILLUSTRATION_PARCELS", "ILLUSTRATION_PUBLIC_FORESTS" -> {
                    drawPath(boundary, green.copy(alpha = .2f))
                    drawPath(boundary, Color(0xFF153F31).copy(alpha = .6f), style = Stroke(2.5f * density))
                    drawPath(boundary, green, style = Stroke(1.3f * density))
                }
            }
        }
    }
}

@Composable
private fun SchematicCatalogThumbnail(key: MapPreviewKey, modifier: Modifier) {
    val description = stringResource(R.string.map_catalog_illustration)
    Canvas(modifier.semantics { contentDescription = description }) {
        val w = size.width
        val h = size.height
        val satellite = key.value in setOf("MAPBOX_SATELLITE", "MAPBOX_SATELLITE_LABELS", "ORTHO_IGN")
        val topographic = key.value == "MAPBOX_TOPO"
        val trees = key.value == "LOCAL_TREES"
        val measurement = key.value == "LOCAL_MEASUREMENTS"
        val local = key.value == "OFFLINE_LOCAL"
        val forest = satellite || trees
        val ground = when {
            forest -> Color(0xFF49694B)
            topographic -> Color(0xFFE2E7CD)
            local -> Color(0xFFE7EBE7)
            else -> Color(0xFFF1EEE4)
        }
        drawRect(ground)
        fun shape(vararg points: Pair<Float, Float>): Path = Path().apply {
            moveTo(points[0].first * w, points[0].second * h)
            points.drop(1).forEach { lineTo(it.first * w, it.second * h) }
            close()
        }
        val forestAreas = listOf(
            shape(0f to 0f, .62f to 0f, .47f to .23f, .57f to .44f, .2f to .56f, 0f to .35f),
            shape(.71f to .28f, 1f to .2f, 1f to 1f, .46f to 1f, .56f to .72f, .48f to .53f),
        )
        forestAreas.forEachIndexed { index, path ->
            drawPath(path, if (forest) listOf(Color(0xFF284D3B), Color(0xFF66835C))[index]
                else listOf(Color(0xFFC6D7B5), Color(0xFFD3DEC0))[index])
        }
        if (satellite) {
            // Deterministic texture to distinguish orthophoto-style imagery from
            // a road plan; no invented location or geographic labels are shown.
            repeat(48) { i ->
                val x = ((i * 37 + 13) % 101) / 100f * w
                val y = ((i * 23 + 7) % 97) / 96f * h
                drawCircle(Color(0xFF99AC76).copy(alpha = .24f), w * (.012f + i % 3 * .006f), Offset(x, y))
            }
        }
        if (topographic) repeat(7) { i ->
            val line = Path().apply {
                moveTo(-w * .1f, h * (i * .13f))
                cubicTo(w * .28f, h * (i * .13f - .3f), w * .43f, h * (i * .13f + .46f), w * 1.1f, h * (i * .13f + .06f))
            }
            drawPath(line, Color(0xFF8E9D72), style = Stroke(width = 1.1f * density))
        }
        val road = Path().apply {
            moveTo(-w * .08f, h * .85f)
            cubicTo(w * .23f, h * .69f, w * .29f, h * .42f, w * .53f, h * .4f)
            cubicTo(w * .72f, h * .37f, w * .65f, h * .1f, w * .94f, -h * .1f)
        }
        drawPath(road, if (forest) Color(0xFFBBC7A9) else Color(0xFFD4CEBB), style = Stroke(4f * density))
        drawPath(road, if (forest) Color(0xFFEDF1D8) else Color.White, style = Stroke(2f * density))
        if (!forest && !topographic) {
            drawLine(Color(0xFF9EC6D0), Offset(0f, h * .19f), Offset(w, h * .85f), 2f * density)
            repeat(5) { i ->
                drawRect(Color(0xFFC5BFB0), Offset(w * (.1f + i * .14f), h * (.35f + (i % 2) * .18f)), Size(w * .075f, h * .12f))
            }
        }
        if (trees || measurement) {
            val a = Offset(w * .22f, h * .72f)
            val b = Offset(w * .48f, h * .42f)
            val c = Offset(w * .79f, h * .26f)
            if (measurement) {
                listOf(a to b, b to c).forEach { (from, to) ->
                    drawLine(Color.White, from, to, 4f * density)
                    drawLine(Color(0xFF226DAD), from, to, 2f * density)
                }
            }
            listOf(a, b, c).forEach { point ->
                drawCircle(Color.White, 4.5f * density, point)
                drawCircle(if (trees) Color(0xFF075B3D) else Color(0xFF226DAD), 2.8f * density, point)
            }
        }
        if (local) drawRect(Color(0xFF61726B), style = Stroke(width = density))
    }
}
