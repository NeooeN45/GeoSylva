package com.forestry.counter.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ══════════════════════════════════════════════════════════════════════════════
//  CollapsibleBlock — Bloc repliable standard pour les tableaux de bord terrain
//  Lisible au soleil, utilisable à une main, rapide.
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun CollapsibleBlock(
    title: String,
    icon: ImageVector,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    badge: (@Composable () -> Unit)? = null,
    initiallyExpanded: Boolean = true,
    saveKey: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by if (saveKey != null) rememberSaveable(saveKey) { mutableStateOf(initiallyExpanded) }
    else remember { mutableStateOf(initiallyExpanded) }

    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 1.dp,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // ── Header cliquable ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                badge?.invoke()
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Replier" else "Déplier",
                    modifier = Modifier.size(20.dp).rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Séparateur
            if (expanded) {
                HorizontalDivider(
                    color = accentColor.copy(alpha = 0.15f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ── Contenu ────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

// ─── Version avec sous-titre ──────────────────────────────────────────────────

@Composable
fun CollapsibleBlockWithSubtitle(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    initiallyExpanded: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    CollapsibleBlock(
        title = title,
        icon = icon,
        accentColor = accentColor,
        initiallyExpanded = initiallyExpanded,
        modifier = modifier,
        badge = {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1
            )
        },
        content = content
    )
}

// ─── Ligne info simple dans un bloc ──────────────────────────────────────────

@Composable
fun BlockInfoRow(
    label: String,
    value: String,
    icon: ImageVector? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    badge: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Icon(icon, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = valueColor)
            badge?.invoke()
        }
    }
}

// ─── Alert banner inline ─────────────────────────────────────────────────────

@Composable
fun InlineAlert(
    message: String,
    type: AlertType = AlertType.WARNING,
    modifier: Modifier = Modifier
) {
    val (bg, fg, icon) = when (type) {
        AlertType.ERROR   -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), Icons.Default.Error)
        AlertType.WARNING -> Triple(Color(0xFFFFF8E1), Color(0xFFE65100), Icons.Default.Warning)
        AlertType.INFO    -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), Icons.Default.Info)
        AlertType.SUCCESS -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Icons.Default.CheckCircle)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(10.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = fg)
    }
}

enum class AlertType { ERROR, WARNING, INFO, SUCCESS }
