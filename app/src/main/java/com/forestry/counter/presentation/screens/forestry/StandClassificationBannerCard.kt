package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.calculation.MartelageStats
import com.forestry.counter.domain.classification.stand.StandTypologyDatabase

@Composable
fun StandClassificationBannerCard(
    stats: MartelageStats,
    onNavigateToClassification: () -> Unit,
    modifier: Modifier = Modifier
) {
    val classification = StandTypologyDatabase.classifyFromStats(stats)
    Card(
        onClick = onNavigateToClassification,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Forest, contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.standclass_typologique_cnpf), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                Text(classification.label, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(classification.advice, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f))
            }
        }
    }
}
