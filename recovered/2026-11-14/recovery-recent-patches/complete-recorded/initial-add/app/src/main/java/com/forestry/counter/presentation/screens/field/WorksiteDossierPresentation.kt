package com.forestry.counter.presentation.screens.field

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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

/** Values are supplied by the existing repository-backed host; no permissions are inferred here. */
@Composable internal fun WorksiteDossierHeader(site: SavedWorksite, enabled: Boolean,
    onMap: (() -> Unit)?, onExport: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(site.form.name, Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge,
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            onMap?.let { action ->
                DossierShortcut(Icons.Outlined.Map, stringResource(R.string.retained_map), enabled,
                    Modifier.weight(1f).testTag("site_map"), action)
            }
            onExport?.let { action ->
                DossierShortcut(DossierIcons.Export, stringResource(R.string.retained_export), enabled,
                    Modifier.weight(1f).testTag("site_export"), action)
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
            Row(Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 60.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                DossierMedallion(Icons.Outlined.Description)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.retained_note), Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleMedium)
                        RetainedHelp(stringResource(R.string.retained_note), stringResource(R.string.retained_note_help))
                    }
                    Text(site.form.notes.ifBlank { stringResource(R.string.retained_no_note) },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
            Icon(DossierIcons.Journal, null, Modifier.size(28.dp))
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
            Row(Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 60.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                DossierMedallion(DossierIcons.Wood)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.retained_woods_preview), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(count.toString(), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                RetainedHelp(title, stringResource(R.string.retained_woods_help))
            }
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
        Row(Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            DossierMedallion(Icons.Outlined.AssignmentTurnedIn)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            RetainedHelp(title, stringResource(R.string.site_section_receipts_help))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
        Row(Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            DossierMedallion(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(metric, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            RetainedHelp(title, help)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun DossierMedallion(icon: ImageVector) {
    Surface(Modifier.size(52.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.primary) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(30.dp)) }
    }
}

/** Small outlined symbols with deterministic vector paths, shared by all worksite-file instances. */
private object DossierIcons {
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
    val Wood = outlined("DossierWood") {
        moveTo(5f, 12f); lineTo(11f, 6f)
        moveTo(11f, 12f); lineTo(17f, 6f)
        moveTo(11f, 6f); curveTo(11f, 2f, 17f, 2f, 17f, 6f)
        moveTo(2f, 18f); curveTo(2f, 13.3f, 9f, 13.3f, 9f, 18f)
        curveTo(9f, 22.7f, 2f, 22.7f, 2f, 18f); close()
        moveTo(5.5f, 14.5f); lineTo(11.5f, 8.5f)
        moveTo(12f, 18f); curveTo(12f, 13.3f, 19f, 13.3f, 19f, 18f)
        curveTo(19f, 22.7f, 12f, 22.7f, 12f, 18f); close()
        moveTo(15.5f, 14.5f); lineTo(21.5f, 8.5f)
        moveTo(19f, 18f); lineTo(23f, 14f)
        moveTo(21.5f, 8.5f); curveTo(25f, 9f, 25f, 12f, 23f, 14f)
    }

    private fun outlined(name: String, draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 26f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
        }.build()
}
