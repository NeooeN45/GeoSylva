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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forestry.counter.R

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
                Text(stringResource(R.string.tigesyl_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.tigesyl_essence_diam_format, essenceCode, diamCm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                SectionLabel(stringResource(R.string.tigesyl_kraft_class))
                KraftSelector(
                    selected = selectedKraft,
                    onSelect = { selectedKraft = it }
                )

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // État sanitaire
                SectionLabel(stringResource(R.string.tigesyl_health_state))
                RadioGroup(
                    options = listOf("SAIN" to stringResource(R.string.tigesyl_health_sain), "MOYEN" to stringResource(R.string.tigesyl_health_moyen), "MAUVAIS" to stringResource(R.string.tigesyl_health_mauvais), "MORT" to stringResource(R.string.tigesyl_health_mort)),
                    selected = selectedSanitaire,
                    onSelect = { selectedSanitaire = it }
                )

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Vigueur
                SectionLabel(stringResource(R.string.tigesyl_vigor))
                RadioGroup(
                    options = listOf("FORTE" to stringResource(R.string.tigesyl_vigor_forte), "MOYENNE" to stringResource(R.string.tigesyl_vigor_moyenne), "FAIBLE" to stringResource(R.string.tigesyl_vigor_faible)),
                    selected = selectedVigueur,
                    onSelect = { selectedVigueur = it }
                )

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Origine
                SectionLabel(stringResource(R.string.tigesyl_origin))
                RadioGroup(
                    options = listOf(
                        "FRANC_PIED" to stringResource(R.string.tigesyl_origin_franc_pied),
                        "TAILLIS" to stringResource(R.string.tigesyl_origin_taillis),
                        "PLANTATION" to stringResource(R.string.tigesyl_origin_plantation),
                        "NATUREL" to stringResource(R.string.tigesyl_origin_natural)
                    ),
                    selected = selectedOrigine,
                    onSelect = { selectedOrigine = it }
                )

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Arbre habitat
                SectionLabel(stringResource(R.string.tigesyl_habitat_tree))
                RadioGroup(
                    options = listOf("OUI" to stringResource(R.string.tigesyl_habitat_yes), "NON" to stringResource(R.string.tigesyl_habitat_no)),
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
            }) { Text(stringResource(R.string.tigesyl_validate)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.tigesyl_cancel)) }
        }
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Sélecteur classes de Kraft 1–5 avec labels CNPF
// ──────────────────────────────────────────────────────────────────────────────
@Composable
private fun KraftSelector(selected: Int?, onSelect: (Int) -> Unit) {
    val kraftLabels = listOf(
        1 to stringResource(R.string.tigesyl_kraft_1),
        2 to stringResource(R.string.tigesyl_kraft_2),
        3 to stringResource(R.string.tigesyl_kraft_3),
        4 to stringResource(R.string.tigesyl_kraft_4),
        5 to stringResource(R.string.tigesyl_kraft_5)
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
