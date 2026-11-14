package com.forestry.counter.presentation.screens.field

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.field.SavedWorksite

/** Presentation of the approved clear worksite file; the list, editor and other hosts keep their theme. */
@Composable internal fun WorksiteDossierTheme(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val inherited = MaterialTheme.colorScheme
    val colors = remember(inherited) {
        // Preserve the user's dark appearance; the approved light file has its own white foundation.
        if (inherited.surface.luminance() < .5f) inherited else inherited.copy(
            primary = Color(0xFF155B3C), onPrimary = Color.White,
            primaryContainer = Color(0xFFE7EFE5), onPrimaryContainer = Color(0xFF155B3C),
            background = Color.White, onBackground = Color(0xFF101512),
            surface = Color.White, onSurface = Color(0xFF101512),
            surfaceDim = Color.White, surfaceBright = Color.White, surfaceTint = Color.Transparent,
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF6F7F3),
            surfaceContainer = Color(0xFFF6F7F3), surfaceContainerHigh = Color.White,
            surfaceContainerHighest = Color.White,
            onSurfaceVariant = Color(0xFF5D676B),
            outline = Color(0xFFE3E8E0), outlineVariant = Color(0xFFE3E8E0),
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private val DossierCardShape = RoundedCornerShape(12.dp)

/** The file's existing actions share one real menu; opening it never edits, reloads or archives. */
@Composable internal fun WorksiteDossierOptions(siteId: String, archived: Boolean, enabled: Boolean,
    onEdit: () -> Unit, onRefresh: () -> Unit, onArchive: () -> Unit) {
    var expanded by remember(siteId, archived, enabled) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled,
            modifier = Modifier.size(48.dp).testTag("site_options")) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            if (!archived) DropdownMenuItem(text = { Text(stringResource(R.string.site_edit)) },
                onClick = { expanded = false; onEdit() }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).testTag("site_menu_edit"))
            DropdownMenuItem(text = { Text(stringResource(R.string.site_refresh)) },
                onClick = { expanded = false; onRefresh() }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).testTag("site_menu_refresh"))
            DropdownMenuItem(text = { Text(stringResource(if (archived) R.string.site_restore else R.string.site_archive)) },
                onClick = { expanded = false; onArchive() }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).testTag("site_menu_archive"))
        }
    }
}

/** Values are supplied by the existing repository-backed host; no permissions are inferred here. */
@Composable internal fun WorksiteDossierHeader(site: SavedWorksite, enabled: Boolean,
    onMap: (() -> Unit)?, onExport: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(site.form.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold)
            RetainedHelp(site.form.name, stringResource(R.string.retained_dossier_help))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(20.dp))
                    Text(stringResource(R.string.retained_local), style = MaterialTheme.typography.labelMedium)
                }
            }
            RetainedHelp(stringResource(R.string.retained_local), stringResource(R.string.retained_local_help))
        }
        if (site.archived) Text(stringResource(R.string.site_archive_badge), style = MaterialTheme.typography.labelLarge)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val vertical = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.3f
            val mapAction: @Composable (Modifier) -> Unit = { modifier ->
                onMap?.let { DossierShortcut(Icons.Outlined.Map, stringResource(R.string.retained_map), enabled,
                    modifier.testTag("site_map"), it) }
            }
            val exportAction: @Composable (Modifier) -> Unit = { modifier ->
                onExport?.let { DossierShortcut(RetainedFieldIcons.Export, stringResource(R.string.retained_export), enabled,
                    modifier.testTag("site_export"), it) }
            }
            if (vertical) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                mapAction(Modifier.fillMaxWidth()); exportAction(Modifier.fillMaxWidth())
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                mapAction(Modifier.weight(1f)); exportAction(Modifier.weight(1f))
            }
        }
        HorizontalDivider()
        DossierMetricCard(Icons.Outlined.Map, stringResource(R.string.retained_parcels),
            if (site.form.parcels.isEmpty()) stringResource(R.string.retained_no_parcels)
                else site.form.parcels.joinToString(" · ") { it.label },
            site.form.parcels.size.toString(), stringResource(R.string.retained_parcels_help),
            enabled && onMap != null, Modifier.testTag("site_linked_parcels")) { onMap?.invoke() }
        Surface(shape = DossierCardShape, color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            DossierSummaryRow(Icons.Outlined.Description, stringResource(R.string.retained_note),
                site.form.notes.ifBlank { stringResource(R.string.retained_no_note) }, null,
                stringResource(R.string.retained_note_help), false)
        }
    }
}

@Composable internal fun WorksiteDossierJournal(enabled: Boolean, onOpen: () -> Unit) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.field_title), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.retained_journal_subtitle), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onOpen, enabled = enabled, shape = DossierCardShape,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("site_open_journal")) {
            Icon(RetainedFieldIcons.Journal, null, Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.retained_open_journal), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable internal fun WorksiteDossierWood(count: Int, enabled: Boolean, onOpen: () -> Unit) {
    val title = stringResource(R.string.retained_woods)
    Surface(onClick = onOpen, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("site_view_woods"),
        shape = DossierCardShape, color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            DossierSummaryRow(RetainedFieldIcons.Wood, title, stringResource(R.string.retained_woods_preview),
                count.toString(), stringResource(R.string.retained_woods_help), false)
            HorizontalDivider(Modifier.padding(horizontal = 12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.retained_view_woods), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable internal fun WorksiteDossierReceipts(enabled: Boolean, onOpen: () -> Unit) {
    val title = stringResource(R.string.retained_wood_receipts)
    Surface(onClick = onOpen, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("site_receipts"),
        shape = DossierCardShape, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        DossierSummaryRow(RetainedFieldIcons.Receipts, title, null, null,
            stringResource(R.string.site_section_receipts_help), true)
    }
}

@Composable private fun DossierShortcut(icon: ImageVector, label: String, enabled: Boolean,
    modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick, enabled = enabled, modifier = modifier.heightIn(min = 64.dp),
        shape = DossierCardShape,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.primary)) {
        Icon(icon, null, Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable private fun DossierMetricCard(icon: ImageVector, title: String, subtitle: String,
    metric: String, help: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth(), shape = DossierCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        DossierSummaryRow(icon, title, subtitle, metric, help, true)
    }
}

/** The same summary row serves parcel, note, timber and receipt cards; large type can grow freely. */
@Composable private fun DossierSummaryRow(icon: ImageVector, title: String, subtitle: String?,
    metric: String?, help: String, chevron: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val vertical = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.3f
        Row(Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            DossierMedallion(icon, if (vertical) 44.dp else 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (vertical || metric == null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium)
                    RetainedHelp(title, help)
                } else Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (vertical && metric != null) DossierMetric(metric)
            }
            if (!vertical && metric != null) {
                DossierMetric(metric)
                RetainedHelp(title, help)
            }
            if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun DossierMetric(value: String) {
    Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary)
}

@Composable private fun DossierMedallion(icon: ImageVector, size: androidx.compose.ui.unit.Dp) {
    Surface(Modifier.size(size), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.primary) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(30.dp)) }
    }
}

/** Shared field symbols with deterministic vector paths; reuse these from other retained field screens. */
internal object RetainedFieldIcons {
    val Export = outlined("DossierExport") {
        moveTo(14f, 2.5f); lineTo(5f, 2.5f); lineTo(5f, 21.5f); lineTo(15f, 21.5f)
        moveTo(14f, 2.5f); lineTo(19f, 7.5f); lineTo(14f, 7.5f); close()
        moveTo(9f, 13f); lineTo(21f, 13f)
        moveTo(17f, 9f); lineTo(21f, 13f); lineTo(17f, 17f)
    }
    val Journal = outlined("DossierJournal") {
        moveTo(7f, 2.5f); lineTo(20f, 2.5f); lineTo(20f, 21.5f); lineTo(7f, 21.5f); close()
        moveTo(4f, 6f); lineTo(9f, 6f); moveTo(4f, 11f); lineTo(9f, 11f)
        moveTo(4f, 16f); lineTo(9f, 16f)
        moveTo(12f, 7f); lineTo(17f, 7f); moveTo(12f, 12f); lineTo(17f, 12f)
        moveTo(12f, 17f); lineTo(17f, 17f)
    }
    val Wood = outlined("DossierWood", viewportWidth = 26f) { woodStack() }
    val Receipts = ImageVector.Builder("DossierReceipts", 24.dp, 24.dp, 30f, 30f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(8f, 5.5f); lineTo(4.5f, 5.5f); lineTo(4.5f, 27.5f); lineTo(13f, 27.5f)
            moveTo(18f, 5.5f); lineTo(22f, 5.5f); lineTo(22f, 14f)
            moveTo(10f, 3f); lineTo(16f, 3f); curveTo(18f, 3f, 18f, 8f, 16f, 8f)
            lineTo(10f, 8f); curveTo(8f, 8f, 8f, 3f, 10f, 3f); close()
            moveTo(9f, 16f); lineTo(12f, 19f); lineTo(17f, 12f)
        }
        group(scaleX = .48f, scaleY = .48f, translationX = 14.5f, translationY = 15f) {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.5f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) { woodStack() }
        }
    }.build()

    private fun androidx.compose.ui.graphics.vector.PathBuilder.woodStack() {
        log(9f, 9f, 3.1f)
        log(5f, 18f, 3.3f)
        log(14f, 18f, 3.3f)
    }

    private fun androidx.compose.ui.graphics.vector.PathBuilder.log(cx: Float, cy: Float, radius: Float) {
        val n = radius * .7071f
        val bx = cx + 6f; val by = cy - 5f
        // Two tangents and the visible back cap; the complete front end remains outlined.
        moveTo(cx - n, cy - n); lineTo(bx - n, by - n)
        curveTo(bx + radius * .1f, by - radius * 1.25f,
            bx + radius * 1.25f, by - radius * .1f, bx + n, by + n)
        lineTo(cx + n, cy + n)
        val k = radius * .5522848f
        moveTo(cx + radius, cy)
        curveTo(cx + radius, cy + k, cx + k, cy + radius, cx, cy + radius)
        curveTo(cx - k, cy + radius, cx - radius, cy + k, cx - radius, cy)
        curveTo(cx - radius, cy - k, cx - k, cy - radius, cx, cy - radius)
        curveTo(cx + k, cy - radius, cx + radius, cy - k, cx + radius, cy); close()
    }

    private fun outlined(name: String, viewportWidth: Float = 24f, draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, viewportWidth, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) { draw() }
        }.build()
}
