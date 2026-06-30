package com.forestry.counter.presentation.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.R
import com.forestry.counter.domain.usecase.florist.*

// ══════════════════════════════════════════════════════════════════════════════
//  SmartPlantInputField — Saisie intelligente d'espèces végétales
//  Tolérante aux fautes, saisie dyslexie-friendly, suggestions en temps réel.
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SmartPlantInputField(
    selectedSpeciesIds: List<String>,
    onSpeciesAdded: (String) -> Unit,
    onSpeciesRemoved: (String) -> Unit,
    contextMilieu: TypeMilieu? = null,
    contextIds: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    placeholder: String = "Saisir une espèce végétale…",
    maxSuggestions: Int = 6
) {
    var inputText by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<FloraNormalizer.FloraSuggestion>>(emptyList()) }
    var isDropdownVisible by remember { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }
    var showFamilyBrowser by remember { mutableStateOf(false) }

    // Recalcul des suggestions à chaque frappe
    LaunchedEffect(inputText, contextMilieu) {
        suggestions = if (inputText.length >= 2) {
            FloraNormalizer.searchWithCorrection(
                inputText, maxSuggestions, contextMilieu,
            )
        } else emptyList()
        isDropdownVisible = suggestions.isNotEmpty() && hasFocus
    }

    val selectedSpecies = selectedSpeciesIds.mapNotNull { FloristDatabase.findById(it) }

    Column(modifier = modifier) {
        // ── Chips des espèces déjà sélectionnées ──────────────────────────────
        if (selectedSpecies.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                selectedSpecies.forEach { sp ->
                    SpeciesChip(
                        espece = sp,
                        onRemove = { onSpeciesRemoved(sp.id) }
                    )
                }
            }
        }

        // ── Champ de saisie ───────────────────────────────────────────────────
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    hasFocus = it.isFocused
                    if (!it.isFocused) isDropdownVisible = false
                },
            placeholder = {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            },
            leadingIcon = {
                Icon(Icons.Default.Grass, null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            },
            trailingIcon = {
                if (inputText.isNotEmpty()) {
                    IconButton(onClick = { inputText = ""; isDropdownVisible = false }) {
                        Icon(Icons.Default.Clear, "Effacer", modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )

        // ── Barre d'actions : aide dyslexie + bouton parcourir ───────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 2.dp, end = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasFocus && inputText.isEmpty()) {
                Text(
                    "Saisissez les 2–3 premières lettres — fautes tolérées",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            } else Spacer(Modifier.weight(1f))

            // ─ Porte 3 : Parcourir par familles ──────────────────────
            TextButton(
                onClick = { showFamilyBrowser = true },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.List, null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.smart_parcourir), style = MaterialTheme.typography.labelSmall)
            }
        }

        // ── Browser modal ─────────────────────────────────────────────────
        if (showFamilyBrowser) {
            FloraFamilyBrowserSheet(
                selectedIds    = selectedSpeciesIds,
                contextMilieu  = contextMilieu,
                onSpeciesToggled = { id ->
                    if (id in selectedSpeciesIds) onSpeciesRemoved(id) else onSpeciesAdded(id)
                },
                onDismiss = { showFamilyBrowser = false }
            )
        }

        // ── Dropdown de suggestions ───────────────────────────────────────────
        AnimatedVisibility(
            visible = isDropdownVisible && suggestions.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    items(suggestions) { suggestion ->
                        SuggestionRow(
                            suggestion = suggestion,
                            isAlreadySelected = suggestion.espece.id in selectedSpeciesIds,
                            onClick = {
                                if (suggestion.espece.id !in selectedSpeciesIds) {
                                    onSpeciesAdded(suggestion.espece.id)
                                    inputText = ""
                                    isDropdownVisible = false
                                }
                            }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

// ─── Chip espèce sélectionnée ─────────────────────────────────────────────────

@Composable
private fun SpeciesChip(
    espece: EspeceVegetale,
    onRemove: () -> Unit
) {
    val isHygrophyte = espece.valeurIndicatrice.indicateurHydromorphie
    val isPerturbed  = espece.valeurIndicatrice.indicateurPerturbation
    val chipColor = when {
        isHygrophyte -> Color(0xFF1565C0).copy(alpha = 0.12f)
        isPerturbed  -> Color(0xFFE65100).copy(alpha = 0.12f)
        else         -> MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = when {
        isHygrophyte -> Color(0xFF1565C0)
        isPerturbed  -> Color(0xFFE65100)
        else         -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    InputChip(
        selected = false,
        onClick = onRemove,
        label = {
            Text(
                espece.taxonomie.nomFrancais,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingIcon = {
            Icon(Icons.Default.Close, "Supprimer", modifier = Modifier.size(14.dp))
        },
        colors = InputChipDefaults.inputChipColors(
            containerColor = chipColor,
            labelColor = textColor,
            trailingIconColor = textColor
        ),
        border = null,
        shape = RoundedCornerShape(20.dp)
    )
}

// ─── Ligne de suggestion ──────────────────────────────────────────────────────

@Composable
private fun SuggestionRow(
    suggestion: FloraNormalizer.FloraSuggestion,
    isAlreadySelected: Boolean,
    onClick: () -> Unit
) {
    val sp = suggestion.espece
    val confidenceColor = when (suggestion.confidence) {
        FloraNormalizer.ConfidenceLevel.HIGH      -> Color(0xFF2E7D32)
        FloraNormalizer.ConfidenceLevel.MEDIUM    -> Color(0xFFF9A825)
        FloraNormalizer.ConfidenceLevel.LOW       -> Color(0xFFE65100)
        FloraNormalizer.ConfidenceLevel.UNCERTAIN -> Color(0xFF9E9E9E)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isAlreadySelected, onClick = onClick)
            .background(if (isAlreadySelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Icône strate
        val strateIcon = when (sp.classification.strateVegetale) {
            StrateVegetale.ARBRE       -> Icons.Default.Park
            StrateVegetale.ARBUSTE     -> Icons.Default.Spa
            StrateVegetale.HERBACEE    -> Icons.Default.Grass
            StrateVegetale.SOUS_ARBUSTE -> Icons.Default.LocalFlorist
            else                       -> Icons.Default.Eco
        }
        Icon(strateIcon, null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    sp.taxonomie.nomFrancais,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isAlreadySelected) {
                    Icon(Icons.Default.CheckCircle, null,
                        tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                }
            }
            Text(
                sp.taxonomie.nomScientifique,
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (suggestion.matchType != FloraNormalizer.MatchType.EXACT_SCIENTIFIC &&
                suggestion.matchType != FloraNormalizer.MatchType.EXACT_FRENCH) {
                Text(
                    "via : ${suggestion.matchedOn} • ${suggestion.matchType.labelFr}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Dot de confiance
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(confidenceColor)
        )

        // Indicateurs écologiques clés
        Column(horizontalAlignment = Alignment.End) {
            val h = sp.valeurIndicatrice.ellenbergH.codeEllenberg
            val hColor = when {
                h <= 2 -> Color(0xFFBF360C)
                h >= 5 -> Color(0xFF1565C0)
                else   -> Color(0xFF2E7D32)
            }
            Text("H=${h}", style = MaterialTheme.typography.labelSmall,
                color = hColor, fontWeight = FontWeight.Bold)
            if (sp.valeurIndicatrice.indicateurHydromorphie)
                Text("💧", fontSize = 9.sp)
        }
    }
}
