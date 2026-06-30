package com.forestry.counter.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.R
import com.forestry.counter.domain.usecase.confidence.ConfidenceEngine

// ══════════════════════════════════════════════════════════════════════════════
//  ConfidenceCard — Bloc de confiance diagnostique
//
//  Affiche le ConfidenceReport (ConfidenceEngine) de façon lisible et
//  actionnable : score global, points forts, points faibles, données
//  manquantes, contradictions et conseil d'amélioration.
//
//  Utilisé dans :
//   - StationDiagnosticScreen (bloc "Fiabilité du diagnostic")
//   - RipisylveDiagnosticScreen (bloc "Fiabilité ripisylve")
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun ConfidenceCard(
    report: ConfidenceEngine.ConfidenceReport,
    title: String = "Fiabilité du diagnostic",
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val levelColor = Color(report.level.colorHex)
    val bgColor = levelColor.copy(alpha = 0.08f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        tonalElevation = 0.dp
    ) {
        Column {
            // ── Header ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = confidenceLevelIcon(report.level),
                    contentDescription = null,
                    tint = levelColor,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = report.level.labelFr,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = levelColor
                    )
                }
                // Score pill
                Box(
                    modifier = Modifier
                        .background(levelColor, RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${report.score}%",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            // ── Barre de score ───────────────────────────────────────────────
            LinearProgressIndicator(
                progress = { (report.score / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(4.dp),
                color = levelColor,
                trackColor = levelColor.copy(alpha = 0.2f)
            )

            // ── Détail repliable ─────────────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Points forts
                    if (report.pointsForts.isNotEmpty()) {
                        ConfidenceSection(
                            title = stringResource(R.string.conf_points_forts, report.pointsForts.size),
                            icon = Icons.Default.CheckCircle,
                            iconColor = Color(0xFF2E7D32),
                            items = report.pointsForts
                        )
                    }

                    // Points faibles
                    if (report.pointsFaibles.isNotEmpty()) {
                        ConfidenceSection(
                            title = stringResource(R.string.conf_limites, report.pointsFaibles.size),
                            icon = Icons.Default.Warning,
                            iconColor = Color(0xFFF9A825),
                            items = report.pointsFaibles
                        )
                    }

                    // Contradictions
                    if (report.contradictions.isNotEmpty()) {
                        ConfidenceSection(
                            title = stringResource(R.string.conf_contradictions, report.contradictions.size),
                            icon = Icons.Default.SyncProblem,
                            iconColor = Color(0xFFC62828),
                            items = report.contradictions,
                            itemColor = Color(0xFFC62828)
                        )
                    }

                    // Données manquantes
                    if (report.donnéesManquantes.isNotEmpty()) {
                        ConfidenceSection(
                            title = "À compléter (${report.donnéesManquantes.size})",
                            icon = Icons.Default.EditNote,
                            iconColor = Color(0xFF1565C0),
                            items = report.donnéesManquantes
                        )
                    }

                    // Conseil d'amélioration
                    if (report.conseilAmelioration.isNotBlank()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = levelColor.copy(alpha = 0.2f)
                        )
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = Color(0xFF1565C0),
                                modifier = Modifier.size(15.dp).padding(top = 1.dp)
                            )
                            Text(
                                text = report.conseilAmelioration,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF1565C0),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

// ─── Section interne ─────────────────────────────────────────────────────────

@Composable
private fun ConfidenceSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    items: List<String>,
    itemColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(13.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = iconColor
            )
        }
        items.forEach { item ->
            Row(
                modifier = Modifier.padding(start = 18.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("·", color = itemColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodySmall,
                    color = itemColor,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ─── Icône selon niveau de confiance ─────────────────────────────────────────

private fun confidenceLevelIcon(level: ConfidenceEngine.ConfidenceLevel) = when (level) {
    ConfidenceEngine.ConfidenceLevel.FORTE       -> Icons.Default.VerifiedUser
    ConfidenceEngine.ConfidenceLevel.BONNE       -> Icons.Default.CheckCircle
    ConfidenceEngine.ConfidenceLevel.MOYENNE     -> Icons.Default.RemoveCircle
    ConfidenceEngine.ConfidenceLevel.FAIBLE      -> Icons.Default.Warning
    ConfidenceEngine.ConfidenceLevel.INSUFFISANTE -> Icons.Default.ErrorOutline
}

// ─── Variante compacte (inline dans un header) ───────────────────────────────

@Composable
fun ConfidencePill(
    report: ConfidenceEngine.ConfidenceReport,
    modifier: Modifier = Modifier
) {
    val levelColor = Color(report.level.colorHex)
    Row(
        modifier = modifier
            .background(levelColor.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = confidenceLevelIcon(report.level),
            contentDescription = null,
            tint = levelColor,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = "${report.level.shortLabel} · ${report.score}%",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = levelColor
        )
    }
}

// ─── Bloc corrélation (CorrelationReport summary) ────────────────────────────

@Composable
fun CorrelationSummaryCard(
    report: com.forestry.counter.domain.usecase.correlateur.CorrelationEngine.CorrelationReport,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val coherenceColor = Color(android.graphics.Color.parseColor(report.coherenceGlobale.color))

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = coherenceColor.copy(alpha = 0.08f),
        tonalElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = null,
                    tint = coherenceColor,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Corrélations multi-sources",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        report.coherenceGlobale.labelFr.substringBefore(" —"),
                        fontSize = 12.sp,
                        color = coherenceColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Badges confirmations / contradictions
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (report.confirmations.isNotEmpty())
                        MiniCountBadge(report.confirmations.size, Color(0xFF2E7D32), Icons.Default.Check)
                    if (report.contradictions.isNotEmpty())
                        MiniCountBadge(report.contradictions.size, Color(0xFFC62828), Icons.Default.Warning)
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val conclusion = report.conclusionPrincipale

                    if (conclusion.pointsCertains.isNotEmpty())
                        ConfidenceSection("Confirmé", Icons.Default.CheckCircle, Color(0xFF2E7D32), conclusion.pointsCertains)

                    if (conclusion.pointsAmbigus.isNotEmpty())
                        ConfidenceSection("À investiguer", Icons.Default.Help, Color(0xFFF9A825), conclusion.pointsAmbigus)

                    if (conclusion.pointsManquants.isNotEmpty())
                        ConfidenceSection("Données manquantes", Icons.Default.EditNote, Color(0xFF1565C0), conclusion.pointsManquants)

                    if (conclusion.recommandationTerrain.isNotBlank()) {
                        HorizontalDivider(color = coherenceColor.copy(alpha = 0.2f))
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Lightbulb, null, tint = coherenceColor,
                                modifier = Modifier.size(15.dp).padding(top = 1.dp))
                            Text(
                                conclusion.recommandationTerrain,
                                style = MaterialTheme.typography.bodySmall,
                                color = coherenceColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MiniCountBadge(count: Int, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(10.dp))
        Text("$count", fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
    }
}
