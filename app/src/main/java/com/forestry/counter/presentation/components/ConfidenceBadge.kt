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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ══════════════════════════════════════════════════════════════════════════════
//  ConfidenceBadge — Badge visuel de niveau de confiance / provenance
//  Usage : diagnostic stationnel, gradients floristiques, corrélations
// ══════════════════════════════════════════════════════════════════════════════

enum class BadgeType {
    AUTO_DEDUCED,      // Déduit automatiquement
    TERRAIN_OBS,       // Observé sur le terrain
    TO_VERIFY,         // À vérifier
    HIGH_CONFIDENCE,   // Forte confiance
    CONFLICT,          // Conflit détecté
    INSUFFICIENT,      // Données insuffisantes
    INFERRED           // Inféré / probable
}

@Composable
fun ConfidenceBadge(
    type: BadgeType,
    label: String? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val (bg, fg, icon, defaultLabel) = badgeStyle(type)
    val displayLabel = label ?: defaultLabel

    Row(
        modifier = modifier
            .background(bg, RoundedCornerShape(20.dp))
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 2.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(if (compact) 10.dp else 12.dp)
        )
        Text(
            text = displayLabel,
            color = fg,
            fontSize = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

private data class BadgeStyle(
    val bg: Color,
    val fg: Color,
    val icon: ImageVector,
    val label: String
)

private fun badgeStyle(type: BadgeType): BadgeStyle = when (type) {
    BadgeType.AUTO_DEDUCED    -> BadgeStyle(Color(0xFFE3F2FD), Color(0xFF1565C0), Icons.Default.AutoMode,      "Auto-déduit")
    BadgeType.TERRAIN_OBS     -> BadgeStyle(Color(0xFFE8F5E9), Color(0xFF2E7D32), Icons.Default.Terrain,       "Observé terrain")
    BadgeType.TO_VERIFY       -> BadgeStyle(Color(0xFFFFF3E0), Color(0xFFE65100), Icons.Default.PriorityHigh,  "À vérifier")
    BadgeType.HIGH_CONFIDENCE -> BadgeStyle(Color(0xFFE8F5E9), Color(0xFF2E7D32), Icons.Default.CheckCircle,   "Forte confiance")
    BadgeType.CONFLICT        -> BadgeStyle(Color(0xFFFFEBEE), Color(0xFFC62828), Icons.Default.Warning,       "Conflit détecté")
    BadgeType.INSUFFICIENT    -> BadgeStyle(Color(0xFFF3E5F5), Color(0xFF6A1B9A), Icons.Default.Info,          "Données insuffisantes")
    BadgeType.INFERRED        -> BadgeStyle(Color(0xFFF1F8E9), Color(0xFF558B2F), Icons.Default.Psychology,    "Probable / inféré")
}

// ─── Badge de source de donnée ────────────────────────────────────────────────

@Composable
fun SourceBadge(
    source: String,
    icon: ImageVector = Icons.Default.DataObject,
    color: Color = Color(0xFF546E7A),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(10.dp))
        Text(source, color = color, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
}

// ─── Jauge de confiance (barre) ───────────────────────────────────────────────

@Composable
fun ConfidenceBar(
    fraction: Float,          // 0.0–1.0
    label: String,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val fillColor = when {
        fraction >= 0.75f -> Color(0xFF2E7D32)
        fraction >= 0.50f -> Color(0xFFF9A825)
        fraction >= 0.25f -> Color(0xFFE65100)
        else              -> Color(0xFFC62828)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = fillColor)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = fillColor,
            trackColor = trackColor
        )
    }
}
