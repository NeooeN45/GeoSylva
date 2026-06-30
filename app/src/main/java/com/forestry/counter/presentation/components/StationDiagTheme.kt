package com.forestry.counter.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.R

// ─────────────────────────────────────────────────────────────────────────────
//  Palette & tokens
// ─────────────────────────────────────────────────────────────────────────────

object StationDiagColors {
    val background     = Color(0xFFF5F2ED)
    val surface        = Color(0xFFFFFFFF)
    val surfaceSubtle  = Color(0xFFF0EDE7)

    val forestGreen    = Color(0xFF2D6A4F)
    val forestLight    = Color(0xFFD8F3DC)
    val waterBlue      = Color(0xFF1B4F72)
    val waterLight     = Color(0xFFD6EAF8)
    val soilBrown      = Color(0xFF7D5A3C)
    val soilLight      = Color(0xFFF5E6D3)
    val ochrePrimary   = Color(0xFFD4832A)
    val ochreLight     = Color(0xFFFFF3E0)
    val conflictRed    = Color(0xFFB03A2E)
    val conflictLight  = Color(0xFFFDEBEA)
    val purpleAlt      = Color(0xFF6A1B9A)
    val purpleLight    = Color(0xFFF3E5F5)

    val textPrimary    = Color(0xFF1C1C1E)
    val textSecondary  = Color(0xFF6B6B70)
    val divider        = Color(0xFFE8E4DE)

    val autoDeduit     = Color(0xFF1B4F72)
    val terrainObs     = Color(0xFF2D6A4F)
    val toVerify       = Color(0xFFD4832A)
    val conflict       = Color(0xFFB03A2E)
    val userEdited     = Color(0xFF6A1B9A)
    val strongConf     = Color(0xFF2D6A4F)
    val mediumConf     = Color(0xFFD4832A)
    val weakConf       = Color(0xFFB03A2E)
}

object StationDiagShapes {
    val card       = RoundedCornerShape(16.dp)
    val cardSmall  = RoundedCornerShape(12.dp)
    val chip       = RoundedCornerShape(8.dp)
    val badge      = RoundedCornerShape(6.dp)
    val input      = RoundedCornerShape(10.dp)
}

// ─────────────────────────────────────────────────────────────────────────────
//  Badge système — source / statut d'une information
// ─────────────────────────────────────────────────────────────────────────────

enum class InfoStatus(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val bg: Color
) {
    AUTO_DEDUIT("Auto-déduit",     Icons.Default.AutoAwesome,   StationDiagColors.autoDeduit,  StationDiagColors.waterLight),
    TERRAIN_OBS("Terrain",         Icons.Default.EditNote,       StationDiagColors.terrainObs,  StationDiagColors.forestLight),
    A_VERIFIER("À vérifier",       Icons.Default.HelpOutline,    StationDiagColors.toVerify,    StationDiagColors.ochreLight),
    CONFLIT("Conflit",             Icons.Default.ErrorOutline,   StationDiagColors.conflict,    StationDiagColors.conflictLight),
    MODIFIE("Modifié",             Icons.Default.Edit,           StationDiagColors.userEdited,  StationDiagColors.purpleLight),
    CONF_FORTE("Conf. forte",      Icons.Default.Verified,       StationDiagColors.strongConf,  StationDiagColors.forestLight),
    CONF_MOYENNE("Conf. moyenne",  Icons.Default.RadioButtonUnchecked, StationDiagColors.mediumConf, StationDiagColors.ochreLight),
    CONF_FAIBLE("Conf. faible",    Icons.Default.Warning,        StationDiagColors.weakConf,    StationDiagColors.conflictLight)
}

@Composable
fun InfoStatusBadge(
    status: InfoStatus,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    Surface(
        color = status.bg,
        shape = StationDiagShapes.badge,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (showLabel) 8.dp else 5.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(status.icon, null, tint = status.color, modifier = Modifier.size(12.dp))
            if (showLabel) {
                Text(status.label, style = MaterialTheme.typography.labelSmall, color = status.color, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Section header de bloc
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BlockSectionTitle(
    text: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(color.copy(alpha = 0.12f), StationDiagShapes.cardSmall),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = StationDiagColors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Ligne de données (label + valeur + badge optionnel + bouton corriger)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DataRow(
    label: String,
    value: String,
    status: InfoStatus? = null,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = StationDiagColors.textSecondary, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = StationDiagColors.textPrimary, modifier = Modifier.weight(1f))
        if (status != null) InfoStatusBadge(status, showLabel = false)
        if (onEdit != null) {
            Spacer(Modifier.width(6.dp))
            TextButton(
                onClick = onEdit,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(2.dp))
                Text(stringResource(R.string.station_theme_corriger), fontSize = 10.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Carte terrain standard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TerrainCard(
    modifier: Modifier = Modifier,
    accentColor: Color = StationDiagColors.forestGreen,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = StationDiagShapes.card,
        colors = CardDefaults.cardColors(containerColor = StationDiagColors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(accentColor)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            content = content
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Gauge linéaire animée
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GradientGauge(
    label: String,
    value: Float,
    maxValue: Float = 5f,
    color: Color,
    labelMin: String = "1",
    labelMax: String = maxValue.toInt().toString(),
    modifier: Modifier = Modifier
) {
    val fraction = (value / maxValue).coerceIn(0f, 1f)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = StationDiagColors.textSecondary, fontSize = 11.sp)
            Text("${value.toInt()}/${ maxValue.toInt()}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(10.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(5.dp))
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(labelMin, style = MaterialTheme.typography.labelSmall, color = StationDiagColors.textSecondary, fontSize = 9.sp)
            Text(labelMax, style = MaterialTheme.typography.labelSmall, color = StationDiagColors.textSecondary, fontSize = 9.sp)
        }
    }
}
