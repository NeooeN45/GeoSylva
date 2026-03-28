package com.forestry.counter.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.domain.model.ripisylve.ConsigneGestion
import com.forestry.counter.domain.model.ripisylve.RipisylveFonctionnalite
import com.forestry.counter.domain.model.ripisylve.RipisylveObservation
import com.forestry.counter.domain.model.ripisylve.RipisylveScore
import com.forestry.counter.domain.usecase.ripisylve.RipisylveScorer

// ══════════════════════════════════════════════════════════════════════════════
//  RipisylveFunctionalDiagBlock
//
//  Bloc diagnostic fonctionnel de la ripisylve :
//    - Score total animé + jauge colorée
//    - Fonctionnalité (TRES_MAUVAISE … TRES_BONNE)
//    - Détail des critères positifs et pénalités
//    - Consigne de gestion prioritaire
//    - Synthèse textuelle auto
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun RipisylveFunctionalDiagBlock(
    observation: RipisylveObservation,
    modifier: Modifier = Modifier
) {
    val score = remember(observation) { RipisylveScorer.score(observation) }

    CollapsibleBlock(
        title      = "Diagnostic Fonctionnel Ripisylve",
        icon       = Icons.Default.Water,
        accentColor = Color(score.fonctionnalite.colorHex),
        initiallyExpanded = true,
        saveKey    = "rip_functional_diag",
        badge      = {
            ConfidenceBadge(
                type    = when (score.fonctionnalite) {
                    RipisylveFonctionnalite.TRES_BONNE,
                    RipisylveFonctionnalite.BONNE      -> BadgeType.HIGH_CONFIDENCE
                    RipisylveFonctionnalite.MOYENNE,
                    RipisylveFonctionnalite.MEDIOCRE   -> BadgeType.TO_VERIFY
                    RipisylveFonctionnalite.MAUVAISE,
                    RipisylveFonctionnalite.TRES_MAUVAISE -> BadgeType.CONFLICT
                },
                label   = score.fonctionnalite.labelFr,
                compact = true
            )
        },
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // ── Score global animé ──────────────────────────────────────
            ScoreMeter(score = score)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // ── Détail critères positifs ────────────────────────────────
            Text(
                "Critères de fonctionnalité",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CritereRow("Continuité linéaire", score.scoreContinuite, 30, Color(0xFF1565C0))
                CritereRow("Largeur de la bande", score.scoreLargeur, 20, Color(0xFF1976D2))
                CritereRow("Nombre de strates", score.scoreStrates, 20, Color(0xFF2E7D32))
                CritereRow("Diversité spécifique", score.scoreDiversite, 10, Color(0xFF388E3C))
                CritereRow("Classes de diamètre", score.scoreDiametres, 10, Color(0xFF558B2F))
                CritereRow("Microhabitats", score.scoreMicrohabitats, 10, Color(0xFF689F38))
            }

            // ── Pénalités ───────────────────────────────────────────────
            if (score.scorePenalite < 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Text(
                    "Pénalités",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC62828)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (score.scoreSanitaire < 0)
                        PenaliteRow("État sanitaire dégradé", score.scoreSanitaire)
                    if (score.scoreInvasives < 0)
                        PenaliteRow("Espèces invasives", score.scoreInvasives)
                    if (score.scoreInadaptees < 0)
                        PenaliteRow("Essences inadaptées", score.scoreInadaptees)
                    if (score.scoreStabilite < 0)
                        PenaliteRow("Instabilité berges/arbres", score.scoreStabilite)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // ── Consigne de gestion ─────────────────────────────────────
            ConsigneGestionCard(score.consigneGestion)

            // ── Synthèse textuelle ──────────────────────────────────────
            Surface(
                color  = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape  = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    score.generateSummary(),
                    style    = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp),
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Info microhabitats + strates ────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniStatCard(
                    label    = "${score.nbStrates}",
                    sublabel = "strate(s)",
                    color    = Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )
                MiniStatCard(
                    label    = "${score.nbMicrohabitats}",
                    sublabel = "microhabitat(s)",
                    color    = Color(0xFF6A1B9A),
                    modifier = Modifier.weight(1f)
                )
                MiniStatCard(
                    label    = "${score.nbClassesDiam}",
                    sublabel = "classe(s) Ø",
                    color    = Color(0xFF795548),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Score global animé
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScoreMeter(score: RipisylveScore) {
    val fonctColor = Color(score.fonctionnalite.colorHex)
    val progress by animateFloatAsState(
        targetValue  = ((score.scoreTotal + 20f) / 120f).coerceIn(0f, 1f),
        animationSpec = tween(700),
        label        = "score_anim"
    )

    Row(
        modifier             = Modifier.fillMaxWidth(),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cercle score
        Box(
            modifier         = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(fonctColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${score.scoreTotal}",
                    style      = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color      = fonctColor
                )
                Text(
                    "/ 100",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    score.fonctionnalite.labelFr,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = fonctColor
                )
                Text(
                    "+${score.scorePositif} / -${-score.scorePenalite}",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress   = { progress },
                modifier   = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color      = fonctColor,
                trackColor = fonctColor.copy(alpha = 0.15f)
            )
            // Labels min/max
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("-20", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                Text("100", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Ligne critère positif
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CritereRow(label: String, value: Int, max: Int, color: Color) {
    Row(
        modifier             = Modifier.fillMaxWidth(),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            style    = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            color    = MaterialTheme.colorScheme.onSurface
        )
        LinearProgressIndicator(
            progress   = { value.toFloat() / max.toFloat() },
            modifier   = Modifier.width(80.dp).height(5.dp).clip(RoundedCornerShape(3.dp)),
            color      = color,
            trackColor = color.copy(alpha = 0.15f)
        )
        Text(
            "$value / $max",
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color      = color,
            modifier   = Modifier.width(40.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Ligne pénalité
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PenaliteRow(label: String, points: Int) {
    Row(
        modifier             = Modifier.fillMaxWidth(),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.RemoveCircle,
            null,
            tint     = Color(0xFFC62828),
            modifier = Modifier.size(14.dp)
        )
        Text(
            label,
            style    = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            color    = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "$points pts",
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color      = Color(0xFFC62828)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Consigne de gestion
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConsigneGestionCard(consigne: ConsigneGestion) {
    val (icon, color, bg) = when (consigne) {
        ConsigneGestion.RESTAURATION -> Triple(Icons.Default.Build,       Color(0xFFC62828), Color(0xFFFFEBEE))
        ConsigneGestion.ENTRETIEN    -> Triple(Icons.Default.Engineering,  Color(0xFFE65100), Color(0xFFFFF3E0))
        ConsigneGestion.MAINTIEN     -> Triple(Icons.Default.CheckCircle,  Color(0xFF2E7D32), Color(0xFFE8F5E9))
    }
    Surface(
        color    = bg,
        shape    = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier             = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    "Consigne de gestion",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = color.copy(alpha = 0.8f)
                )
                Text(
                    consigne.labelFr,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = color
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Mini carte statistique
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MiniStatCard(label: String, sublabel: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(10.dp),
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.09f))
    ) {
        Column(
            modifier            = Modifier.padding(8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold, color = color)
            Text(sublabel, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
    }
}
