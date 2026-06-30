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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.R
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
            // Hdom (hauteur dominante) : moyenne des 100 plus gros arbres/ha — proxy de l'IS ONF.
            val surfaceHa = surfaceM2 / 10_000.0
            val hdom = expertCalculator.computeHdom(tiges, surfaceHa) ?: hauteurMoyenne
            
            // Calculs scientifiques
            val indiceStation = expertCalculator.calculateIndiceDeStation(
                essenceCode = essenceCode,
                age = ageEstime,
                hdom = hdom,
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
                text = stringResource(R.string.expertibp_title),
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
            text = stringResource(R.string.expertibp_station_title),
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
            Text(stringResource(R.string.expertibp_station_index))
            Text(
                text = stringResource(R.string.expertibp_station_index_value, analysis.indiceStation.toInt()),
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
            Text(stringResource(R.string.expertibp_station_class))
            Text(
                text = stringResource(R.string.expertibp_station_class_value, analysis.classeStation),
                fontWeight = FontWeight.Bold
            )
        }
        
        // Fertilité
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.expertibp_fertility_class))
            Text(
                text = stringResource(R.string.expertibp_fertility_class_value, analysis.fertilité.classe),
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
            Text(stringResource(R.string.expertibp_basal_area))
            Text(
                text = stringResource(R.string.expertibp_basal_area_value, analysis.surfaceTerriere),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ExpertProductionSection(analysis: ExpertAnalysisResult) {
    Column {
        Text(
            text = stringResource(R.string.expertibp_production_title),
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
                Text(stringResource(R.string.expertibp_mean_height))
                Text(
                    text = stringResource(R.string.expertibp_mean_height_value, production.hauteurMoyenne),
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Diamètre moyen théorique
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.expertibp_mean_diam))
                Text(
                    text = stringResource(R.string.expertibp_mean_diam_value, production.diametreMoyen),
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Volume total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.expertibp_total_volume))
                Text(
                    text = stringResource(R.string.expertibp_total_volume_value, production.volumeTotal),
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Accroissement annuel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.expertibp_annual_increment))
                Text(
                    text = stringResource(R.string.expertibp_annual_increment_value, production.accroissementAnnuel),
                    fontWeight = FontWeight.Bold
                )
            }
        } ?: run {
            Text(
                text = stringResource(R.string.expertibp_production_unavailable),
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
            text = stringResource(R.string.expertibp_growth_title),
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
            Text(stringResource(R.string.expertibp_current_mean_diam))
            Text(
                text = stringResource(R.string.expertibp_current_mean_diam_value, analysis.diametreMoyen),
                fontWeight = FontWeight.Bold
            )
        }
        
        // Hauteur moyenne actuelle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.expertibp_current_mean_height))
            Text(
                text = stringResource(R.string.expertibp_current_mean_height_value, analysis.hauteurMoyenne),
                fontWeight = FontWeight.Bold
            )
        }
        
        // Accroissement moyen
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.expertibp_mean_increment))
            Text(
                text = stringResource(R.string.expertibp_mean_increment_value, analysis.accroissementMoyen),
                fontWeight = FontWeight.Bold
            )
        }
        
        // Accroissement actuel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.expertibp_current_increment))
            Text(
                text = stringResource(R.string.expertibp_current_increment_value, analysis.accroissementActuel),
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
            Text(stringResource(R.string.expertibp_prediction_10y))
            Text(
                text = stringResource(R.string.expertibp_prediction_10y_value, analysis.croissanceFuture),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        // Diamètre d'exploitation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.expertibp_optimal_harvest_diam))
            Text(
                text = stringResource(R.string.expertibp_optimal_harvest_diam_value, analysis.fertilité.diametreExploitation.toInt()),
                fontWeight = FontWeight.Bold
            )
        }
        
        // Âge d'exploitation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.expertibp_optimal_harvest_age))
            Text(
                text = stringResource(R.string.expertibp_optimal_harvest_age_value, analysis.fertilité.ageExploitation),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ExpertRecommendationsSection(analysis: ExpertAnalysisResult) {
    Column {
        Text(
            text = stringResource(R.string.expertibp_recommendations_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Essences optimales
        Text(
            text = stringResource(R.string.expertibp_optimal_species),
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
            Text(stringResource(R.string.expertibp_planting_density))
            Text(
                text = stringResource(R.string.expertibp_planting_density_value, analysis.recommendations.densitePlantation),
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Âge première éclaircie
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.expertibp_first_thinning))
            Text(
                text = stringResource(R.string.expertibp_first_thinning_value, analysis.recommendations.agePremiereEclaircie),
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Recommandations détaillées
        Text(
            text = stringResource(R.string.expertibp_recommendations_label),
            fontWeight = FontWeight.Medium
        )
        
        analysis.recommendations.recommandations.forEach { recommendation ->
            Text(
                text = stringResource(R.string.expertibp_recommendation_bullet, recommendation),
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
