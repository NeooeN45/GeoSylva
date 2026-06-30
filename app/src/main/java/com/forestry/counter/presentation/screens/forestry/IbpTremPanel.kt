package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.ibp.IbpTremCalculator

/**
 * Panneau d'enrichissement TreM insérable dans IbpEvaluationScreen
 * au niveau du critère F (DMH — Dendromicrohabitats).
 *
 * Affiche :
 * - Résumé auto-calculé depuis les tiges habitat
 * - Chips de sélection des 16 types TreM observés manuellement
 * - Indicateur de score DMH résultant
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IbpTremPanel(
    tremTypesSelected: List<String>,
    nbArbresHabitat: Int,
    onTypesChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val allTypes = IbpTremCalculator.allTypes()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Dendromicrohabitats TreM",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            ScoreBadge(nbArbresHabitat = nbArbresHabitat, nbTypes = tremTypesSelected.size)
        }

        Text(
            "$nbArbresHabitat arbre(s) habitat marqué(s) dans cette placette",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(4.dp))

        Text(
            "Types TreM observés",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )

        val families = listOf(
            "TF" to "Trous & fissures",
            "TE" to "Témoins exposition",
            "SC" to "Sève & champignons",
            "EP" to "Épiphytes"
        )

        families.forEach { (prefix, familyLabel) ->
            val familyTypes = allTypes.filter { it.first.startsWith(prefix) }
            Text(
                familyLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                familyTypes.forEach { (code, label) ->
                    val isSelected = code in tremTypesSelected
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onTypesChanged(
                                if (isSelected) tremTypesSelected - code
                                else tremTypesSelected + code
                            )
                        },
                        label = { Text("$code $label", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }
}

/**
 * Badge coloré indiquant le score DMH estimé.
 */
@Composable
private fun ScoreBadge(nbArbresHabitat: Int, nbTypes: Int) {
    val score = when {
        nbArbresHabitat == 0 && nbTypes == 0        -> 0
        nbArbresHabitat >= 3 && nbTypes >= 4         -> 5
        nbArbresHabitat >= 1 || nbTypes >= 2         -> 2
        else                                         -> 0
    }
    val color = when (score) {
        5    -> MaterialTheme.colorScheme.primary
        2    -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
    ) {
        Text(
            score.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
    }
}

/**
 * Panneau de continuité forestière pour le critère CF (H).
 */
@Composable
fun IbpContinuitePanel(
    ancienneteAns: Int,
    connectivitePct: Int,
    isForetAncienne: Boolean,
    onAncienneteChanged: (Int) -> Unit,
    onConnectiviteChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Continuité forestière",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            val score = when {
                isForetAncienne || ancienneteAns >= 200 || (ancienneteAns >= 100 && connectivitePct >= 60) -> 5
                ancienneteAns >= 30 || connectivitePct >= 20 -> 2
                else -> 0
            }
            val color = when (score) {
                5    -> MaterialTheme.colorScheme.primary
                2    -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.error
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            ) {
                Text(
                    score.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = color
                )
            }
        }

        if (isForetAncienne) {
            AssistChip(
                onClick = {},
                label = { Text(stringResource(R.string.ibp_foret_ancienne_db), style = MaterialTheme.typography.labelSmall) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }

        val ancienneteOptions = listOf(0 to "Inconnue", 30 to "≥30 ans", 60 to "≥60 ans", 100 to "≥100 ans", 200 to "Forêt ancienne (≥200 ans)")
        Text(stringResource(R.string.ibp_anciennete_estimee), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ancienneteOptions.forEach { (ans, label) ->
                FilterChip(
                    selected = ancienneteAns == ans,
                    onClick = { onAncienneteChanged(ans) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        val connectiviteOptions = listOf(0 to "0%", 20 to "20%", 40 to "40%", 60 to "60%", 80 to "80%", 100 to "100%")
        Text(stringResource(R.string.ibp_connectivite_spatiale), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            connectiviteOptions.forEach { (pct, label) ->
                FilterChip(
                    selected = connectivitePct == pct,
                    onClick = { onConnectiviteChanged(pct) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}
