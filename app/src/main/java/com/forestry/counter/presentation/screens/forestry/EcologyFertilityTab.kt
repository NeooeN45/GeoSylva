package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forestry.counter.domain.model.Essence
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.R
import com.forestry.counter.domain.repository.EssenceRepository
import kotlinx.coroutines.flow.firstOrNull
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EcologyFertilityTab(
    tigesInScope: List<Tige>,
    essenceRepository: EssenceRepository,
    onNavigateToRipisylve: () -> Unit,
    onNavigateToStation: () -> Unit
) {
    var essences by remember { mutableStateOf<Map<String, Essence>>(emptyMap()) }
    
    LaunchedEffect(Unit) {
        val allEssences = essenceRepository.getAllEssences().firstOrNull() ?: emptyList()
        essences = allEssences.associateBy { it.code.uppercase(Locale.getDefault()) }
    }

    val tigesByEssence = tigesInScope.groupBy { it.essenceCode.uppercase(Locale.getDefault()) }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.ecology_tab_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.ecology_tab_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onNavigateToStation,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF795548))
                        ) {
                            Icon(Icons.Default.Landscape, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.diag_station_btn))
                        }
                        Button(
                            onClick = onNavigateToRipisylve,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) {
                            Icon(Icons.Default.WaterDrop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.diag_ripisylve_btn))
                        }
                    }
                }
            }
        }

        item {
            Text(
                stringResource(R.string.ecology_fertility_classes_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }

        if (tigesByEssence.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.ecology_no_stems),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        items(tigesByEssence.entries.toList().sortedByDescending { it.value.size }) { (code, tiges) ->
            val essence = essences[code]
            val nom = essence?.name ?: code
            
            // Calcul basique Ho (hauteur dominante) pour l'exemple
            // En réalité, devrait utiliser la fonction getDominantHeight de ForestryCalculator
            val tigesHauteur = tiges.filter { it.hauteurM != null }.sortedByDescending { it.diamCm }
            val tigesDominantes = tigesHauteur.take((tigesHauteur.size * 0.2).toInt().coerceAtLeast(1))
            val hoEstimee = if (tigesDominantes.isNotEmpty()) tigesDominantes.mapNotNull { it.hauteurM }.average() else null
            
            // Estimation très basique de la classe de fertilité (à remplacer par de vrais modèles sylvicoles)
            val fertilityClass = hoEstimee?.let {
                when {
                    it > 30 -> "I (Excellente)"
                    it > 25 -> "II (Très bonne)"
                    it > 20 -> "III (Bonne)"
                    it > 15 -> "IV (Moyenne)"
                    else -> "V (Faible)"
                }
            } ?: "Non évaluable (Manque hauteurs)"

            val fertilityColor = hoEstimee?.let {
                when {
                    it > 30 -> Color(0xFF2E7D32) // Vert foncé
                    it > 25 -> Color(0xFF4CAF50) // Vert
                    it > 20 -> Color(0xFF8BC34A) // Vert clair
                    it > 15 -> Color(0xFFFFC107) // Jaune
                    else -> Color(0xFFFF9800)    // Orange
                }
            } ?: MaterialTheme.colorScheme.surfaceVariant

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(fertilityColor, CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = nom,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${tiges.size} tiges",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Classe de Fertilité",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                fertilityClass,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "H. Dominante (Ho)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                hoEstimee?.let { "${String.format(Locale.getDefault(), "%.1f", it)} m" } ?: "-",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    if (hoEstimee != null) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { (hoEstimee / 40.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = fertilityColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}
