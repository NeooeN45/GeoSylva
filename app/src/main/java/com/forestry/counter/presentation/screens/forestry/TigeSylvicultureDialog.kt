package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class TigeSylvicultureData(
    val classeKraft: Int?,
    val etatSanitaire: String?,
    val vigueur: String?,
    val origine: String?,
    val isTigeHabitat: Boolean
)

/**
 * Dialogue de saisie des données sylvicoles avancées d'une tige :
 * - Classe de Kraft (1–5)
 * - État sanitaire (SAIN, MOYEN, MAUVAIS, MORT)
 * - Vigueur (FORTE, MOYENNE, FAIBLE)
 * - Origine (FRANC_PIED, TAILLIS, PLANTATION, NATUREL)
 * - Arbre habitat (oui/non)
 */
@Composable
fun TigeSylvicultureDialog(
    tigeId: String,
    diamCm: Double,
    essenceCode: String,
    initial: TigeSylvicultureData,
    onDismiss: () -> Unit,
    onConfirm: (tigeId: String, data: TigeSylvicultureData) -> Unit
) {
    var selectedKraft by remember { mutableStateOf(initial.classeKraft) }
    var selectedSanitaire by remember { mutableStateOf(initial.etatSanitaire) }
    var selectedVigueur by remember { mutableStateOf(initial.vigueur) }
    var selectedOrigine by remember { mutableStateOf(initial.origine) }
    var isHabitat by remember { mutableStateOf(initial.isTigeHabitat) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Sylviculture avancée", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${essenceCode} — ⌀ ${diamCm} cm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Classe de Kraft
                SectionLabel("Classe de Kraft (dominance)")
                KraftSelector(
                    selected = selectedKraft,
                    onSelect = { selectedKraft = it }
                )

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // État sanitaire
                SectionLabel("État sanitaire")
                RadioGroup(
                    options = listOf("SAIN" to "Sain", "MOYEN" to "Moyen", "MAUVAIS" to "Mauvais", "MORT" to "Mort"),
                    selected = selectedSanitaire,
                    onSelect = { selectedSanitaire = it }
                )

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Vigueur
                SectionLabel("Vigueur")
                RadioGroup(
                    options = listOf("FORTE" to "Forte", "MOYENNE" to "Moyenne", "FAIBLE" to "Faible"),
                    selected = selectedVigueur,
                    onSelect = { selectedVigueur = it }
                )

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Origine
                SectionLabel("Origine")
                RadioGroup(
                    options = listOf(
                        "FRANC_PIED" to "Franc pied",
                        "TAILLIS" to "Taillis",
                        "PLANTATION" to "Plantation",
                        "NATUREL" to "Régénération naturelle"
                    ),
                    selected = selectedOrigine,
                    onSelect = { selectedOrigine = it }
                )

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Arbre habitat
                SectionLabel("Arbre habitat (TreM)")
                RadioGroup(
                    options = listOf("OUI" to "Oui — potentiel TreM", "NON" to "Non"),
                    selected = if (isHabitat) "OUI" else "NON",
                    onSelect = { isHabitat = it == "OUI" }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(tigeId, TigeSylvicultureData(
                    classeKraft = selectedKraft,
                    etatSanitaire = selectedSanitaire,
                    vigueur = selectedVigueur,
                    origine = selectedOrigine,
                    isTigeHabitat = isHabitat
                ))
            }) { Text("Valider") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Sélecteur classes de Kraft 1–5 avec labels CNPF
// ──────────────────────────────────────────────────────────────────────────────
@Composable
private fun KraftSelector(selected: Int?, onSelect: (Int) -> Unit) {
    val kraftLabels = listOf(
        1 to "1 — Élite / Co-dominant dominant",
        2 to "2 — Co-dominant",
        3 to "3 — Dominé",
        4 to "4 — Supprimé",
        5 to "5 — Dépérissant/mort"
    )
    Column(Modifier.selectableGroup()) {
        kraftLabels.forEach { (k, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == k,
                        onClick = { onSelect(k) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 2.dp)
            ) {
                RadioButton(selected = selected == k, onClick = null)
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun RadioGroup(
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    Column(Modifier.selectableGroup()) {
        options.forEach { (value, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == value,
                        onClick = { onSelect(value) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 2.dp)
            ) {
                RadioButton(selected = selected == value, onClick = null)
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
}
