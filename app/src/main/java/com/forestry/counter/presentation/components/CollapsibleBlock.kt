package com.forestry.counter.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ══════════════════════════════════════════════════════════════════════════════
//  CollapsibleBlock — Bloc repliable premium pour tableaux de bord terrain
//  Design : bande accent gauche, header animé, shadow dynamique, spring anim.
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

    val rotation   by animateFloatAsState(if (expanded) 180f else 0f, tween(250), label = "chevron")
    val elevation  by animateDpAsState(if (expanded) 4.dp else 1.dp,  tween(200), label = "elev")
    val headerBg   by animateColorAsState(
        if (expanded) accentColor.copy(alpha = 0.055f) else Color.Transparent,
        tween(260), label = "hbg"
    )

    Surface(
        modifier      = modifier.fillMaxWidth(),
        shape         = RoundedCornerShape(16.dp),
        shadowElevation = elevation,
        tonalElevation = 0.dp,
        color         = MaterialTheme.colorScheme.surface
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {

            // ── Bande accent gauche ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            listOf(accentColor, accentColor.copy(alpha = 0.35f))
                        )
                    )
            )

            Column(modifier = Modifier.weight(1f)) {

                // ── Header cliquable ───────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBg)
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Icon container with radial gradient
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        accentColor.copy(alpha = 0.20f),
                                        accentColor.copy(alpha = 0.07f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }

                    Text(
                        title,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.weight(1f)
                    )
                    badge?.invoke()
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Replier" else "Déplier",
                        modifier = Modifier.size(20.dp).rotate(rotation),
                        tint     = accentColor.copy(alpha = 0.75f)
                    )
                }

                // ── Séparateur dégradé ─────────────────────────────────────
                if (expanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(horizontal = 4.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        accentColor.copy(alpha = 0.5f),
                                        accentColor.copy(alpha = 0.05f)
                                    )
                                )
                            )
                    )
                }

                // ── Contenu ────────────────────────────────────────────────
                AnimatedVisibility(
                    visible = expanded,
                    enter   = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(180)),
                    exit    = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(120))
                ) {
                    Column(
                        modifier              = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement   = Arrangement.spacedBy(10.dp),
                        content               = content
                    )
                }
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
