package com.forestry.counter.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.domain.model.station.StationObservation
import com.forestry.counter.domain.usecase.correlateur.CorrelationEngine
import com.forestry.counter.domain.usecase.florist.GradientInferenceEngine
import com.forestry.counter.domain.usecase.station.StationDiagnosticEngine

// ─────────────────────────────────────────────────────────────────────────────
//  EditableSynthesisBlock — synthèse générée + éditable + notes perso
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EditableSynthesisBlock(
    station: StationObservation,
    diagResult: StationDiagnosticEngine.StationResult,
    floraGradients: GradientInferenceEngine.GradientResult?,
    correlReport: CorrelationEngine.CorrelationReport?,
    modifier: Modifier = Modifier
) {
    val autoText = remember(station, diagResult, floraGradients, correlReport) {
        buildAutoSynthesis(station, diagResult, floraGradients, correlReport)
    }

    var editMode   by rememberSaveable { mutableStateOf(false) }
    var userText   by rememberSaveable { mutableStateOf("") }
    var notesPerso by rememberSaveable { mutableStateOf("") }
    var isUserText by rememberSaveable { mutableStateOf(false) }
    var showNotes  by rememberSaveable { mutableStateOf(false) }

    val displayText = if (isUserText) userText else autoText

    TerrainCard(modifier = modifier, accentColor = StationDiagColors.waterBlue) {
        // ── Header ──────────────────────────────────────────────────────────
        BlockSectionTitle(
            text = "Synthèse du diagnostic",
            icon = Icons.Default.Description,
            color = StationDiagColors.waterBlue,
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isUserText) {
                        Surface(color = StationDiagColors.purpleLight, shape = StationDiagShapes.badge) {
                            Text(
                                "Modifiée",
                                style = MaterialTheme.typography.labelSmall,
                                color = StationDiagColors.userEdited,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    IconButton(
                        onClick = { editMode = !editMode; if (editMode && userText.isEmpty()) userText = autoText },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (editMode) Icons.Default.DoneAll else Icons.Default.Edit,
                            null,
                            tint = StationDiagColors.waterBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        // ── Contenu éditable ou lecture ──────────────────────────────────────
        AnimatedVisibility(visible = !editMode) {
            Column {
                // Texte affiché
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = StationDiagColors.textPrimary,
                    lineHeight = 22.sp
                )
                Spacer(Modifier.height(10.dp))
                // Bouton régénérer si modifié
                if (isUserText) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { isUserText = false; editMode = false },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Revenir à l'automatique", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = editMode, enter = expandVertically(), exit = shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Texte auto en aperçu
                if (!isUserText) {
                    Surface(
                        color = StationDiagColors.waterBlue.copy(alpha = 0.06f),
                        shape = StationDiagShapes.cardSmall
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text("Synthèse automatique", style = MaterialTheme.typography.labelSmall, color = StationDiagColors.waterBlue, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(autoText, style = MaterialTheme.typography.bodySmall, color = StationDiagColors.textSecondary, fontSize = 11.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = userText.ifEmpty { autoText },
                    onValueChange = { userText = it; isUserText = true },
                    label = { Text("Votre version de la synthèse") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    shape = StationDiagShapes.input
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { userText = autoText; isUserText = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Auto", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { editMode = false },
                        colors = ButtonDefaults.buttonColors(containerColor = StationDiagColors.waterBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sauver", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = StationDiagColors.divider)
        Spacer(Modifier.height(8.dp))

        // ── Notes personnelles ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(StationDiagShapes.chip)
                .clickable { showNotes = !showNotes }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.NoteAdd, null, tint = StationDiagColors.soilBrown, modifier = Modifier.size(18.dp))
            Text(
                "Notes personnelles",
                style = MaterialTheme.typography.labelMedium,
                color = StationDiagColors.soilBrown,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (notesPerso.isNotBlank()) {
                Surface(color = StationDiagColors.soilLight, shape = StationDiagShapes.badge) {
                    Text("Rédigées", style = MaterialTheme.typography.labelSmall, color = StationDiagColors.soilBrown, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp)
                }
            }
            Icon(if (showNotes) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = StationDiagColors.textSecondary, modifier = Modifier.size(18.dp))
        }

        AnimatedVisibility(visible = showNotes, enter = expandVertically(), exit = shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                NoteBlock("Contraintes exploitation", Icons.Default.Construction, StationDiagColors.ochrePrimary)
                NoteBlock("Note propriétaire / gestionnaire", Icons.Default.Person, StationDiagColors.waterBlue)
                OutlinedTextField(
                    value = notesPerso,
                    onValueChange = { notesPerso = it },
                    label = { Text("Notes libres") },
                    placeholder = { Text("Observations particulières, contexte historique, suivi prévu…", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = StationDiagShapes.input
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Bloc note structurée avec champ libre
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NoteBlock(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    var text by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(false) }

    Surface(
        color = color.copy(alpha = 0.06f),
        shape = StationDiagShapes.cardSmall,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(StationDiagShapes.cardSmall)
                    .clickable { show = !show },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), fontSize = 11.sp)
                Icon(if (show) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = color, modifier = Modifier.size(14.dp))
            }
            AnimatedVisibility(visible = show) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    minLines = 2,
                    shape = StationDiagShapes.input,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Générateur de texte de synthèse automatique
// ─────────────────────────────────────────────────────────────────────────────

private fun buildAutoSynthesis(
    station: StationObservation,
    result: StationDiagnosticEngine.StationResult,
    flora: GradientInferenceEngine.GradientResult?,
    correl: CorrelationEngine.CorrelationReport?
): String = buildString {
    append("Station de type « ${result.typeStation} »")
    if (station.commune.isNotBlank()) append(", secteur ${station.commune}")
    station.altitudeM?.let { append(", à environ ${it.toInt()} m d'altitude") }
    append(". ")

    // Pédologie
    station.profondeurSolCm?.let { append("Sol de profondeur ${it} cm") }
    if (station.texture.labelFr.isNotBlank() && station.texture.name != "INCONNUE") append(", texture ${station.texture.labelFr.lowercase()}")
    station.hydromorphieProfondeurCm?.let { append(", tâches d'oxydo-réduction à ${it} cm") }
    append(". ")

    // Gradients
    append("Gradient hydrique : ${result.gradientHydriqueFinal}/5 — ${gradientHLabel(result.gradientHydriqueFinal)}. ")
    append("Gradient trophique : ${result.gradientTrophiqueFinal}/5 — ${gradientTLabel(result.gradientTrophiqueFinal)}. ")

    // Flore
    if (flora != null && flora.nbTaxonsAnalysables >= 3) {
        append("L'analyse du cortège floristique (${flora.nbTaxonsAnalysables} taxons) ")
        append("indique un milieu ${flora.hydriqueLabelFr.lowercase()}, ${flora.trophiqueLabelFr.lowercase()}. ")
    }

    // Corrélations
    if (correl != null) {
        if (correl.confirmations.isNotEmpty()) append("Sources concordantes : ${correl.confirmations.size} confirmation(s). ")
        if (correl.contradictions.isNotEmpty()) append("⚠ ${correl.contradictions.size} contradiction(s) inter-sources détectée(s). ")
    }

    // Risques
    if (result.risqueEngorgement) append("Risque d'engorgement hydrique identifié. ")
    if (result.risqueDepiecement) append("Risque de dépiècement à surveiller. ")

    // Confiance
    append("Confiance du diagnostic : ${result.confidence.labelFr}.")
}

private fun gradientHLabel(v: Int) = when (v) {
    1 -> "très sec" ; 2 -> "sec" ; 3 -> "frais" ; 4 -> "humide" ; else -> "très humide"
}
private fun gradientTLabel(v: Int) = when (v) {
    1 -> "oligotrophe" ; 2 -> "pauvre" ; 3 -> "mésotrophe" ; 4 -> "riche" ; else -> "eutrophe"
}
