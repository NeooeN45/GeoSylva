package com.forestry.counter.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.R
import com.forestry.counter.domain.model.station.StationObservation
import com.forestry.counter.domain.usecase.florist.GradientInferenceEngine
import com.forestry.counter.domain.usecase.station.StationDiagnosticEngine

// ─────────────────────────────────────────────────────────────────────────────
//  SmartSummaryCard — résumé intelligent de la station
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SmartSummaryCard(
    station: StationObservation,
    diagResult: StationDiagnosticEngine.StationResult,
    floraGradients: GradientInferenceEngine.GradientResult?,
    modifier: Modifier = Modifier
) {
    val confColor = when (diagResult.confidence) {
        StationDiagnosticEngine.DiagConfidenceStation.FORTE   -> StationDiagColors.forestGreen
        StationDiagnosticEngine.DiagConfidenceStation.MOYENNE -> StationDiagColors.ochrePrimary
        else                                                  -> StationDiagColors.conflictRed
    }

    val animH by animateFloatAsState(diagResult.gradientHydriqueFinal / 5f, tween(800), label = "h")
    val animT by animateFloatAsState(diagResult.gradientTrophiqueFinal / 5f, tween(800, 100), label = "t")

    TerrainCard(modifier = modifier, accentColor = confColor) {
        // ── En-tête type station ─────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(confColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Forest, null, tint = confColor, modifier = Modifier.size(26.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "DIAGNOSTIC STATIONNEL",
                    style = MaterialTheme.typography.labelSmall,
                    color = confColor.copy(alpha = 0.7f),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp
                )
                Text(
                    diagResult.typeStation,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = confColor
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = StationDiagColors.divider)
        Spacer(Modifier.height(12.dp))

        // ── Facteur limitant principal ───────────────────────────────────────
        val facteurLimitant = buildFacteurLimitant(diagResult, station)
        if (facteurLimitant != null) {
            Surface(
                color = StationDiagColors.ochreLight,
                shape = StationDiagShapes.chip
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, null, tint = StationDiagColors.ochrePrimary, modifier = Modifier.size(16.dp))
                    Column {
                        Text(stringResource(R.string.smart_facteur_limitant), style = MaterialTheme.typography.labelSmall, color = StationDiagColors.ochrePrimary, fontWeight = FontWeight.Bold)
                        Text(facteurLimitant, style = MaterialTheme.typography.bodySmall, color = StationDiagColors.textPrimary)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // ── Gradients animés ─────────────────────────────────────────────────
        Text(
            "Profil écologique",
            style = MaterialTheme.typography.labelSmall,
            color = StationDiagColors.textSecondary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))

        GradientBarRow(
            label = stringResource(R.string.smart_hydrique),
            terrainFrac = animH,
            floreFrac = floraGradients?.hydrique?.let { (it / 7f).toFloat() },
            terrainColor = StationDiagColors.waterBlue,
            floraColor = StationDiagColors.forestGreen,
            minLabel = "Très sec",
            maxLabel = "Très humide"
        )
        Spacer(Modifier.height(8.dp))
        GradientBarRow(
            label = stringResource(R.string.smart_trophique),
            terrainFrac = animT,
            floreFrac = floraGradients?.trophique?.let { (it / 6f).toFloat() },
            terrainColor = StationDiagColors.soilBrown,
            floraColor = StationDiagColors.forestGreen,
            minLabel = "Oligotrophe",
            maxLabel = "Eutrophe"
        )

        // ── Légende flore vs station ─────────────────────────────────────────
        if (floraGradients != null) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(StationDiagColors.soilBrown, stringResource(R.string.smart_station_terrain))
                LegendDot(StationDiagColors.forestGreen, stringResource(R.string.smart_flore_ellenberg))
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = StationDiagColors.divider)
        Spacer(Modifier.height(10.dp))

        // ── Risques + atouts rapides ─────────────────────────────────────────
        val risques = buildList {
            if (diagResult.risqueEngorgement) add(Pair("⚠ Engorgement", StationDiagColors.conflictRed))
            if (diagResult.risqueDepiecement) add(Pair("⚠ Dépiècement", StationDiagColors.conflictRed))
            if (diagResult.contrainteHydrique == StationDiagnosticEngine.Contrainte.FORTE ||
                diagResult.contrainteHydrique == StationDiagnosticEngine.Contrainte.TRES_FORTE)
                add(Pair("Contrainte hydrique forte", StationDiagColors.ochrePrimary))
            if (diagResult.contrainteProfondeur == StationDiagnosticEngine.Contrainte.FORTE ||
                diagResult.contrainteProfondeur == StationDiagnosticEngine.Contrainte.TRES_FORTE)
                add(Pair("Sol peu profond", StationDiagColors.ochrePrimary))
        }

        if (risques.isNotEmpty() || diagResult.atouts.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                diagResult.atouts.take(2).forEach { atout ->
                    QuickTag(text = atout, color = StationDiagColors.forestGreen, modifier = Modifier.weight(1f, fill = false))
                }
                risques.take(2).forEach { (label, color) ->
                    QuickTag(text = label, color = color, modifier = Modifier.weight(1f, fill = false))
                }
            }
        }

        // ── Hypothèses secondaires ───────────────────────────────────────────
        if (diagResult.alertes.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Points d'attention",
                style = MaterialTheme.typography.labelSmall,
                color = StationDiagColors.textSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            diagResult.alertes.take(3).forEach { alerte ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("•", color = StationDiagColors.conflictRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(alerte, style = MaterialTheme.typography.bodySmall, color = StationDiagColors.textPrimary, fontSize = 12.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  GradientBarRow — barre double (terrain + flore)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GradientBarRow(
    label: String,
    terrainFrac: Float,
    floreFrac: Float?,
    terrainColor: Color,
    floraColor: Color,
    minLabel: String,
    maxLabel: String
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = StationDiagColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text("${(terrainFrac * 5).toInt()}/5", style = MaterialTheme.typography.labelSmall, color = terrainColor, fontWeight = FontWeight.Bold)
        }
        // Barre terrain
        Box(
            modifier = Modifier.fillMaxWidth().height(10.dp)
                .background(terrainColor.copy(alpha = 0.10f), RoundedCornerShape(5.dp))
        ) {
            Box(Modifier.fillMaxWidth(terrainFrac.coerceIn(0f, 1f)).fillMaxHeight().background(terrainColor, RoundedCornerShape(5.dp)))
        }
        // Barre flore (si disponible)
        if (floreFrac != null) {
            Box(
                modifier = Modifier.fillMaxWidth().height(6.dp)
                    .background(floraColor.copy(alpha = 0.08f), RoundedCornerShape(3.dp))
            ) {
                Box(Modifier.fillMaxWidth(floreFrac.coerceIn(0f, 1f)).fillMaxHeight().background(floraColor.copy(alpha = 0.7f), RoundedCornerShape(3.dp)))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(minLabel, style = MaterialTheme.typography.labelSmall, color = StationDiagColors.textSecondary, fontSize = 9.sp)
            Text(maxLabel, style = MaterialTheme.typography.labelSmall, color = StationDiagColors.textSecondary, fontSize = 9.sp)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall, color = StationDiagColors.textSecondary, fontSize = 9.sp)
    }
}

@Composable
private fun QuickTag(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = color.copy(alpha = 0.12f), shape = StationDiagShapes.badge, modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

private fun buildFacteurLimitant(
    result: StationDiagnosticEngine.StationResult,
    station: StationObservation
): String? {
    return when {
        result.risqueEngorgement -> "Engorgement hydrique — risque d'asphyxie racinaire"
        result.risqueDepiecement -> "Dépiècement potentiel — profondeur sol insuffisante"
        result.contrainteHydrique == StationDiagnosticEngine.Contrainte.TRES_FORTE ->
            "Contrainte hydrique très forte"
        result.contrainteTrophique == StationDiagnosticEngine.Contrainte.TRES_FORTE ->
            "Très faible richesse trophique"
        result.contrainteProfondeur == StationDiagnosticEngine.Contrainte.TRES_FORTE ->
            "Sol très peu profond (< 20 cm)"
        result.alertes.isNotEmpty() -> result.alertes.first()
        else -> null
    }
}
