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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.R
import com.forestry.counter.domain.usecase.florist.GradientInferenceEngine

// ══════════════════════════════════════════════════════════════════════════════
//  CortegeQualityBlock — Bloc "Cortège crédible / peu crédible"
//
//  Affiche un diagnostic de la qualité du cortège floristique :
//  - SUFFISANT     : cortège riche, gradients fiables
//  - PARTIEL       : quelques espèces, gradient probable mais incertain
//  - CONTRADICTOIRE: conflits internes, gradient peu fiable
//  - TROP_PAUVRE   : moins de 3 taxons, aucune conclusion possible
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun CortegeQualityBlock(
    gradientResult: GradientInferenceEngine.GradientResult,
    modifier: Modifier = Modifier
) {
    val quality = rememberCortegeQuality(gradientResult)
    val animProgress by animateFloatAsState(
        targetValue = quality.credibilityScore,
        animationSpec = tween(800),
        label = "cortege_score"
    )

    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = quality.backgroundColor),
        border    = androidx.compose.foundation.BorderStroke(1.dp, quality.accentColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── En-tête verdict ───────────────────────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(quality.accentColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(quality.icon, null, tint = quality.accentColor, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        quality.title,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color      = quality.accentColor
                    )
                    Text(
                        stringResource(R.string.cortege_taxons_analyzed_format, gradientResult.nbTaxonsAnalysables, gradientResult.nbTaxonsTotaux),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Score circulaire
                CredibilityCircle(score = animProgress, color = quality.accentColor)
            }

            // ── Barre de crédibilité ──────────────────────────────────────
            Column {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.cortege_credibility_gradients),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${(quality.credibilityScore * 100).toInt()} %",
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color      = quality.accentColor)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress        = { animProgress },
                    modifier        = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color           = quality.accentColor,
                    trackColor      = quality.accentColor.copy(alpha = 0.15f)
                )
            }

            // ── Description ───────────────────────────────────────────────
            Text(
                quality.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            // ── Conflits (si présents) ────────────────────────────────────
            if (gradientResult.conflits.isNotEmpty()) {
                HorizontalDivider(color = quality.accentColor.copy(alpha = 0.15f))
                Text(
                    stringResource(R.string.cortege_conflicts_detected),
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color      = quality.accentColor
                )
                gradientResult.conflits.take(3).forEach { conflit ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.Top,
                        modifier              = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CompareArrows, null,
                            tint     = quality.accentColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(13.dp).padding(top = 1.dp))
                        Text(
                            stringResource(R.string.cortege_conflict_format, conflit.gradient, conflit.espece1, conflit.espece2),
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── Espèces tirantes ──────────────────────────────────────────
            if (gradientResult.especesTirantVersSec.isNotEmpty() || gradientResult.especesTirantVersFrais.isNotEmpty()) {
                HorizontalDivider(color = quality.accentColor.copy(alpha = 0.15f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (gradientResult.especesTirantVersSec.isNotEmpty()) {
                        DirectionChip(
                            label   = stringResource(R.string.cortege_direction_dry),
                            species = gradientResult.especesTirantVersSec,
                            color   = Color(0xFFF57F17)
                        )
                    }
                    if (gradientResult.especesTirantVersFrais.isNotEmpty()) {
                        DirectionChip(
                            label   = stringResource(R.string.cortege_direction_fresh),
                            species = gradientResult.especesTirantVersFrais,
                            color   = Color(0xFF1565C0)
                        )
                    }
                }
            }

            // ── Conseil terrain ───────────────────────────────────────────
            if (quality.advice.isNotEmpty()) {
                Surface(
                    color  = quality.accentColor.copy(alpha = 0.07f),
                    shape  = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier              = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.Top
                    ) {
                        Icon(Icons.Default.Lightbulb, null,
                            tint     = quality.accentColor,
                            modifier = Modifier.size(14.dp).padding(top = 1.dp))
                        Text(
                            quality.advice,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Indicateur circulaire de crédibilité
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CredibilityCircle(score: Float, color: Color) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
        CircularProgressIndicator(
            progress    = { score },
            modifier    = Modifier.fillMaxSize(),
            strokeWidth = 4.dp,
            color       = color,
            trackColor  = color.copy(alpha = 0.15f)
        )
        Text(
            "${(score * 100).toInt()}",
            fontSize   = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color      = color
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Chip "espèces tirantes"
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DirectionChip(label: String, species: List<String>, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
        species.take(3).forEach { name ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(5.dp).background(color.copy(alpha = 0.5f), CircleShape))
                Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Logique de calcul qualité cortège
// ─────────────────────────────────────────────────────────────────────────────

private data class CortegeQuality(
    val title: String,
    val description: String,
    val advice: String,
    val icon: ImageVector,
    val accentColor: Color,
    val backgroundColor: Color,
    val credibilityScore: Float
)

private enum class CortegeVerdict { SUFFISANT, PARTIEL, CONTRADICTOIRE, TROP_PAUVRE }

@Composable
private fun rememberCortegeQuality(
    result: GradientInferenceEngine.GradientResult
): CortegeQuality {
    val titleSuffisant = stringResource(R.string.cortege_suffisant)
    val titlePartiel = stringResource(R.string.cortege_partiel)
    val titleContradictoire = stringResource(R.string.cortege_contradictoire)
    val titleTropPauvre = stringResource(R.string.cortege_trop_pauvre)

    return remember(result) {
    val verdict = when {
        result.nbTaxonsAnalysables < 3          -> CortegeVerdict.TROP_PAUVRE
        result.conflits.size >= 3               -> CortegeVerdict.CONTRADICTOIRE
        result.conflits.any { it.severite == GradientInferenceEngine.Severite.FORTE } -> CortegeVerdict.CONTRADICTOIRE
        result.nbTaxonsAnalysables >= 5 &&
            result.conflits.isEmpty()           -> CortegeVerdict.SUFFISANT
        else                                    -> CortegeVerdict.PARTIEL
    }

    when (verdict) {
        CortegeVerdict.SUFFISANT -> CortegeQuality(
            title            = titleSuffisant,
            description      = "Le cortège floristique est riche et cohérent. Les gradients calculés sont fiables pour orienter le diagnostic stationnel.",
            advice           = "Vous pouvez vous appuyer sur ces gradients pour le diagnostic.",
            icon             = Icons.Default.CheckCircle,
            accentColor      = Color(0xFF2E7D32),
            backgroundColor  = Color(0xFFE8F5E9),
            credibilityScore = minOf(0.95f, 0.60f + result.nbTaxonsAnalysables * 0.04f)
        )
        CortegeVerdict.PARTIEL -> CortegeQuality(
            title            = titlePartiel,
            description      = "Quelques espèces indicatrices sont présentes mais le cortège reste incomplet. Les gradients sont des tendances à confirmer sur le terrain.",
            advice           = "Enrichir le cortège : chercher des herbacées du sous-bois, mousses, fougères.",
            icon             = Icons.Default.HourglassBottom,
            accentColor      = Color(0xFFE65100),
            backgroundColor  = Color(0xFFFFF3E0),
            credibilityScore = 0.35f + result.nbTaxonsAnalysables * 0.06f
        )
        CortegeVerdict.CONTRADICTOIRE -> CortegeQuality(
            title            = titleContradictoire,
            description      = "Des espèces aux exigences opposées cohabitent. Le gradient calculé est peu fiable — il peut masquer une hétérogénéité spatiale.",
            advice           = "Vérifier la micro-topographie : les espèces contradictoires viennent peut-être de micro-unités différentes.",
            icon             = Icons.Default.SyncProblem,
            accentColor      = Color(0xFFC62828),
            backgroundColor  = Color(0xFFFFEBEE),
            credibilityScore = 0.15f + (result.nbTaxonsAnalysables - result.conflits.size).coerceAtLeast(0) * 0.04f
        )
        CortegeVerdict.TROP_PAUVRE -> CortegeQuality(
            title            = titleTropPauvre,
            description      = "Moins de 3 taxons analysables — aucune conclusion écologique fiable possible. Les gradients affichés sont purement indicatifs.",
            advice           = "Observer davantage d'espèces avant de conclure. Au moins 5 taxons du sous-bois sont recommandés.",
            icon             = Icons.Default.WarningAmber,
            accentColor      = Color(0xFF6A1B9A),
            backgroundColor  = Color(0xFFF3E5F5),
            credibilityScore = 0.05f + result.nbTaxonsAnalysables * 0.05f
        )
    }
    }
}
