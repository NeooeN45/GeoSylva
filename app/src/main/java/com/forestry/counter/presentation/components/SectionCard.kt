package com.forestry.counter.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Carte de section réutilisable avec titre + icône + contenu.
 * Remplace les SectionCard privées dupliquées dans PlacetteEvolutionScreen,
 * StationDiagnosticScreen, RipisylveDiagnosticScreen, etc.
 */
@Composable
fun SectionCard(
    title: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 18,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * Carte de métrique (KPI) réutilisable.
 * Affiche un label + une valeur + une unité optionnelle.
 * Remplace les IndicatorTile, PremiumKpiCard, MetricCard dupliqués.
 */
@Composable
fun MetricTile(
    label: String,
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (unit != null) {
                    Spacer(Modifier.width(2.dp))
                    Text(
                        unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}
