package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.domain.calculation.ExpertForestryCalculator
import com.forestry.counter.domain.model.Tige
import kotlinx.coroutines.launch

/**
 * Extension experte pour l'évaluation IBP avec analyses scientifiques
 * Intègre les calculs ONF/ENGREF dans l'interface existante
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpertIbpAnalysisSection(
    tiges: List<Tige>,
    surfaceM2: Double,
    expertCalculator: ExpertForestryCalculator,
    essenceCode: String,
    ageEstime: Int
) {
    var showExpertAnalysis by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    // Calculs expert
    val expertAnalysis = remember(tiges, surfaceM2, essenceCode, ageEstime) {
        if (tiges.isNotEmpty()) {
            val diametres = tiges.map { it.diamCm }
            val diametreMoyen = diametres.average()
            val hauteurs = tiges.mapNotNull { it.hauteurM }
            val hauteurMoyenne = hauteurs.average().takeIf { hauteurs.isNotEmpty() } ?: 20.0
            
            // Calculs scientifiques
            val indiceStation = expertCalculator.calculateIndiceDeStation(
                essenceCode = essenceCode,
                age = ageEstime,
                hauteurMoyenne = hauteurMoyenne,
                diametreMoyen = diametreMoyen
            )
            
            val classeStation = expertCalculator.getClasseStation(indiceStation, essenceCode)
            val fertilité = expertCalculator.evaluateFertilityClass(indiceStation, essenceCode)
            val recommendations = expertCalculator.generateSylvicultureRecommendations(
                essenceCode = essenceCode,
                classeStation = classeStation,
                classeFertilite = fertilité,
                ageActuel = ageEstime,
                diametreMoyen = diametreMoyen
            )
            
            // Données de production
            val productionData = expertCalculator.getProductionData(essenceCode, classeStation, ageEstime)
            
            // Prédictions de croissance
            val croissanceFuture = (ageEstime + 10).let { ageFutur ->
                expertCalculator.richardsGrowthModel(essenceCode, ageFutur, classeStation)
            }
            
            ExpertAnalysisResult(
                indiceStation = indiceStation,
                classeStation = classeStation,
                fertilité = fertilité,
                productionData = productionData,
                croissanceFuture = croissanceFuture,
                recommendations = recommendations,
                diametreMoyen = diametreMoyen,
                hauteurMoyenne = hauteurMoyenne,
                surfaceTerriere = expertCalculator.computeSurfaceTerriere(diametres),
                accroissementMoyen = expertCalculator.computeMeanAnnualIncrement(diametreMoyen, ageEstime),
                accroissementActuel = expertCalculator.computeCurrentAnnualIncrement(essenceCode, ageEstime, classeStation)
            )
        } else null
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Bouton d'activation de l'analyse experte
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🎯 Analyse Expert Forestier",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(
                onClick = { showExpertAnalysis = !showExpertAnalysis }
            ) {
                Icon(
                    imageVector = if (showExpertAnalysis) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
        }
        
        if (showExpertAnalysis && expertAnalysis != null) {
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Section Station
                    ExpertStationSection(expertAnalysis)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Section Production
                    ExpertProductionSection(expertAnalysis)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Section Croissance
                    ExpertGrowthSection(expertAnalysis, essenceCode)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Section Recommandations
                    ExpertRecommendationsSection(expertAnalysis)
                }
            }
        }
    }
}

@Composable
private fun ExpertStationSection(analysis: ExpertAnalysisResult) {
    Column {
        Text(
            text = "📍 Analyse Stationnelle",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Indice de station
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Indice de station (IA):")
            Text(
                text = "${analysis.indiceStation.toInt()}/30",
                fontWeight = FontWeight.Bold,
                color = when {
                    analysis.indiceStation < 10 -> Color.Red
                    analysis.indiceStation < 18 -> Color(0xFFFF9800)
                    else -> Color.Green
                }
            )
        }
        
        // Classe de station
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Classe de station:")
            Text(
                text = "Station ${analysis.classeStation}",
                fontWeight = FontWeight.Bold
            )
        }
        
        // Fertilité
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Classe de fertilité:")
            Text(
                text = "F${analysis.fertilité.classe}",
                fontWeight = FontWeight.Bold,
                color = when (analysis.fertilité.classe) {
                    1, 2 -> Color(0xFFFF9800)
                    3 -> Color.Blue
                    4, 5 -> Color.Green
                    else -> Color.Gray
                }
            )
        }
        
        // Surface terrière
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Surface terrière:")
            Text(
                text = "${"%.1f".format(analysis.surfaceTerriere)} m²/ha",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ExpertProductionSection(analysis: ExpertAnalysisResult) {
    Column {
        Text(
            text = "📊 Données de Production ONF",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        analysis.productionData?.let { production ->
            // Hauteur moyenne théorique
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Hauteur moyenne ONF:")
                Text(
                    text = "${"%.1f".format(production.hauteurMoyenne)} m",
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Diamètre moyen théorique
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Diamètre moyen ONF:")
                Text(
                    text = "${"%.1f".format(production.diametreMoyen)} cm",
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Volume total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Volume total ONF:")
                Text(
                    text = "${"%.0f".format(production.volumeTotal)} m³/ha",
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Accroissement annuel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Accroissement annuel:")
                Text(
                    text = "${"%.1f".format(production.accroissementAnnuel)} m³/ha/an",
                    fontWeight = FontWeight.Bold
                )
            }
        } ?: run {
            Text(
                text = "Données de production non disponibles pour cette essence",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ExpertGrowthSection(analysis: ExpertAnalysisResult, essenceCode: String) {
    Column {
        Text(
            text = "🌱 Analyse de Croissance",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Diamètre moyen actuel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Diamètre moyen actuel:")
            Text(
                text = "${"%.1f".format(analysis.diametreMoyen)} cm",
                fontWeight = FontWeight.Bold
            )
        }
        
        // Hauteur moyenne actuelle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Hauteur moyenne actuelle:")
            Text(
                text = "${"%.1f".format(analysis.hauteurMoyenne)} m",
                fontWeight = FontWeight.Bold
            )
        }
        
        // Accroissement moyen
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Accroissement moyen (AMA):")
            Text(
                text = "${"%.2f".format(analysis.accroissementMoyen)} cm/an",
                fontWeight = FontWeight.Bold
            )
        }
        
        // Accroissement actuel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Accroissement actuel (ACA):")
            Text(
                text = "${"%.2f".format(analysis.accroissementActuel)} cm/an",
                fontWeight = FontWeight.Bold,
                color = if (analysis.accroissementActuel > analysis.accroissementMoyen) 
                    Color.Green else Color(0xFFFF9800)
            )
        }
        
        // Prédiction 10 ans
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Prédiction 10 ans:")
            Text(
                text = "${"%.1f".format(analysis.croissanceFuture)} cm",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        // Diamètre d'exploitation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Ø exploitation optimal:")
            Text(
                text = "${analysis.fertilité.diametreExploitation.toInt()} cm",
                fontWeight = FontWeight.Bold
            )
        }
        
        // Âge d'exploitation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Âge exploitation optimal:")
            Text(
                text = "${analysis.fertilité.ageExploitation} ans",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ExpertRecommendationsSection(analysis: ExpertAnalysisResult) {
    Column {
        Text(
            text = "💡 Recommandations Sylvicoles",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Essences optimales
        Text(
            text = "Essences optimales:",
            fontWeight = FontWeight.Medium
        )
        Text(
            text = analysis.recommendations.essencesOptimales.joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Densité de plantation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Densité plantation:")
            Text(
                text = "${analysis.recommendations.densitePlantation} tiges/ha",
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Âge première éclaircie
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Première éclaircie:")
            Text(
                text = "${analysis.recommendations.agePremiereEclaircie} ans",
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Recommandations détaillées
        Text(
            text = "Recommandations:",
            fontWeight = FontWeight.Medium
        )
        
        analysis.recommendations.recommandations.forEach { recommendation ->
            Text(
                text = "• $recommendation",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )
        }
    }
}

// Classe de données pour les résultats d'analyse experte
data class ExpertAnalysisResult(
    val indiceStation: Double,
    val classeStation: Int,
    val fertilité: com.forestry.counter.domain.calculation.FertilityClass,
    val productionData: com.forestry.counter.domain.calculation.ProductionData?,
    val croissanceFuture: Double,
    val recommendations: com.forestry.counter.domain.calculation.SylvicultureRecommendations,
    val diametreMoyen: Double,
    val hauteurMoyenne: Double,
    val surfaceTerriere: Double,
    val accroissementMoyen: Double,
    val accroissementActuel: Double
)
