package com.forestry.counter.data.interpretation

import com.forestry.counter.data.local.entity.DataInterpretationEntity
import com.forestry.counter.data.local.entity.InterpretationType
import com.forestry.counter.data.local.entity.Priority
import com.forestry.counter.data.local.entity.DataCorrelationEntity
import com.forestry.counter.data.local.entity.TigeEntity
import com.forestry.counter.data.local.entity.ParcelleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Moteur d'interprétation des données pour GeoSylva.
 * Génère des insights, analyses et recommandations basées sur les données forestières.
 */
class DataInterpretationEngine {
    
    /**
     * Génère des interprétations complètes pour une parcelle.
     */
    suspend fun generateInterpretations(
        parcelle: ParcelleEntity,
        tiges: List<TigeEntity>,
        correlations: List<DataCorrelationEntity>
    ): List<DataInterpretationEntity> = withContext(Dispatchers.Default) {
        val interpretations = mutableListOf<DataInterpretationEntity>()
        
        // Analyse de croissance
        val growthAnalysis = analyzeGrowth(parcelle, tiges)
        interpretations.addAll(growthAnalysis)
        
        // Analyse de vitalité
        val vitalityAnalysis = analyzeVitality(parcelle, tiges)
        interpretations.addAll(vitalityAnalysis)
        
        // Analyse de rendement
        val yieldAnalysis = analyzeYield(parcelle, tiges)
        interpretations.addAll(yieldAnalysis)
        
        // Analyse des risques
        val riskAnalysis = analyzeRisks(parcelle, tiges, correlations)
        interpretations.addAll(riskAnalysis)
        
        // Recommandations sylvicoles
        val silviculturalRecommendations = generateSilviculturalRecommendations(parcelle, tiges, correlations)
        interpretations.addAll(silviculturalRecommendations)
        
        // Analyse économique
        val economicAnalysis = analyzeEconomicValue(parcelle, tiges)
        interpretations.addAll(economicAnalysis)
        
        interpretations
    }
    
    /**
     * Analyse la croissance des arbres dans la parcelle.
     */
    private fun analyzeGrowth(parcelle: ParcelleEntity, tiges: List<TigeEntity>): List<DataInterpretationEntity> {
        val interpretations = mutableListOf<DataInterpretationEntity>()
        
        if (tiges.isEmpty()) return interpretations
        
        val avgDiameter = tiges.map { it.diamCm }.average()
        val avgHeight = tiges.mapNotNull { it.hauteurM }.ifEmpty { listOf(0.0) }.average()
        val density = tiges.size / (parcelle.surfaceHa ?: 1.0)
        
        // Analyse de la croissance diamétrale
        val diameterInterpretation = when {
            avgDiameter < 20 -> DataInterpretationEntity(
                interpretationId = "growth_diameter_low_${parcelle.parcelleId}",
                parcelleId = parcelle.parcelleId,
                interpretationType = InterpretationType.GROWTH_ANALYSIS,
                title = "Croissance diamétrale faible",
                description = "Le diamètre moyen des arbres ($avgDiameter cm) est inférieur aux attentes pour cette station.",
                confidenceScore = 0.8,
                priority = Priority.HIGH,
                dataSource = "tiges",
                analysisMethod = "statistical_analysis",
                parameters = """{"avgDiameter": $avgDiameter, "threshold": 20}""",
                results = """{"status": "low_growth", "recommendation": "consider_thinning"}""",
                recommendations = "Envisager une éclaircie pour améliorer la croissance des arbres restants.",
                actionable = true,
                validUntil = null,
                tags = null
            )
            avgDiameter > 50 -> DataInterpretationEntity(
                interpretationId = "growth_diameter_high_${parcelle.parcelleId}",
                parcelleId = parcelle.parcelleId,
                interpretationType = InterpretationType.GROWTH_ANALYSIS,
                title = "Croissance diamétrale excellente",
                description = "Le diamètre moyen des arbres ($avgDiameter cm) est excellent pour cette station.",
                confidenceScore = 0.9,
                priority = Priority.INFO,
                dataSource = "tiges",
                analysisMethod = "statistical_analysis",
                parameters = """{"avgDiameter": $avgDiameter, "threshold": 50}""",
                results = """{"status": "excellent_growth", "market_ready": true}""",
                recommendations = "Les arbres atteignent une taille commerciale intéressante.",
                actionable = false,
                validUntil = null,
                tags = null
            )
            else -> DataInterpretationEntity(
                interpretationId = "growth_diameter_normal_${parcelle.parcelleId}",
                parcelleId = parcelle.parcelleId,
                interpretationType = InterpretationType.GROWTH_ANALYSIS,
                title = "Croissance diamétrale normale",
                description = "Le diamètre moyen des arbres ($avgDiameter cm) est dans la norme pour cette station.",
                confidenceScore = 0.7,
                priority = Priority.INFO,
                dataSource = "tiges",
                analysisMethod = "statistical_analysis",
                parameters = """{"avgDiameter": $avgDiameter}""",
                results = """{"status": "normal_growth"}""",
                recommendations = "Maintenir les pratiques sylvicoles actuelles.",
                actionable = false,
                validUntil = null,
                tags = null
            )
        }
        interpretations.add(diameterInterpretation)
        
        // Analyse de la densité
        val densityInterpretation = when {
            density < 400 -> DataInterpretationEntity(
                interpretationId = "density_low_${parcelle.parcelleId}",
                parcelleId = parcelle.parcelleId,
                interpretationType = InterpretationType.GROWTH_ANALYSIS,
                title = "Densité faible",
                description = "La densité actuelle (${density.toInt()} tiges/ha) est faible, ce qui peut indiquer une sous-utilisation du potentiel de la station.",
                confidenceScore = 0.8,
                priority = Priority.MEDIUM,
                dataSource = "tiges",
                analysisMethod = "spatial_analysis",
                parameters = """{"density": $density, "threshold": 400}""",
                results = """{"status": "low_density", "underutilized": true}""",
                recommendations = "Envisager des plantations complémentaires ou une régénération naturelle assistée.",
                actionable = true,
                validUntil = null,
                tags = null
            )
            density > 1200 -> DataInterpretationEntity(
                interpretationId = "density_high_${parcelle.parcelleId}",
                parcelleId = parcelle.parcelleId,
                interpretationType = InterpretationType.GROWTH_ANALYSIS,
                title = "Densité élevée",
                description = "La densité actuelle (${density.toInt()} tiges/ha) est élevée, ce qui peut limiter la croissance individuelle.",
                confidenceScore = 0.9,
                priority = Priority.HIGH,
                dataSource = "tiges",
                analysisMethod = "spatial_analysis",
                parameters = """{"density": $density, "threshold": 1200}""",
                results = """{"status": "high_density", "competition": true}""",
                recommendations = "Programmer une éclaircie pour réduire la compétition.",
                actionable = true,
                validUntil = null,
                tags = null
            )
            else -> null
        }
        densityInterpretation?.let { interpretations.add(it) }
        
        return interpretations
    }
    
    /**
     * Analyse la vitalité des arbres.
     */
    private fun analyzeVitality(parcelle: ParcelleEntity, tiges: List<TigeEntity>): List<DataInterpretationEntity> {
        val interpretations = mutableListOf<DataInterpretationEntity>()
        
        if (tiges.isEmpty()) return interpretations
        
        // Analyse de la distribution des qualités
        val qualityDistribution = tiges.groupBy { it.qualite ?: 1 }
        val totalTrees = tiges.size
        val highQualityTrees = qualityDistribution.filter { it.key >= 3 }.values.sumOf { it.size }
        val highQualityPercentage = (highQualityTrees.toDouble() / totalTrees) * 100
        
        val vitalityInterpretation = when {
            highQualityPercentage < 30 -> DataInterpretationEntity(
                interpretationId = "vitality_low_${parcelle.parcelleId}",
                parcelleId = parcelle.parcelleId,
                interpretationType = InterpretationType.VITALITY_ASSESSMENT,
                title = "Vitalité faible",
                description = "Seulement ${highQualityPercentage.toInt()}% des arbres présentent une bonne qualité.",
                confidenceScore = 0.8,
                priority = Priority.HIGH,
                dataSource = "tiges",
                analysisMethod = "quality_distribution",
                parameters = """{"highQualityPercentage": $highQualityPercentage}""",
                results = """{"status": "low_vitality", "action_required": true}""",
                recommendations = "Investiguer les causes de la faible vitalité (sol, climat, maladies).",
                actionable = true,
                validUntil = null,
                tags = null
            )
            highQualityPercentage > 70 -> DataInterpretationEntity(
                interpretationId = "vitality_high_${parcelle.parcelleId}",
                parcelleId = parcelle.parcelleId,
                interpretationType = InterpretationType.VITALITY_ASSESSMENT,
                title = "Vitalité excellente",
                description = "${highQualityPercentage.toInt()}% des arbres présentent une excellente qualité.",
                confidenceScore = 0.9,
                priority = Priority.INFO,
                dataSource = "tiges",
                analysisMethod = "quality_distribution",
                parameters = """{"highQualityPercentage": $highQualityPercentage}""",
                results = """{"status": "excellent_vitality", "peak_condition": true}""",
                recommendations = "Maintenir les conditions actuelles et planifier la récolte au moment optimal.",
                actionable = false,
                validUntil = null,
                tags = null
            )
            else -> null
        }
        vitalityInterpretation?.let { interpretations.add(it) }
        
        return interpretations
    }
    
    /**
     * Analyse le potentiel de rendement.
     */
    private fun analyzeYield(parcelle: ParcelleEntity, tiges: List<TigeEntity>): List<DataInterpretationEntity> {
        val interpretations = mutableListOf<DataInterpretationEntity>()
        
        if (tiges.isEmpty()) return interpretations
        
        // Estimation du volume total
        val totalVolume = tiges.sumOf { tige ->
            val diameter = tige.diamCm
            val height = tige.hauteurM ?: 20.0 // Valeur par défaut
            // Formule de Schumacher-Hall simplifiée
            exp(1.5 + 2.0 * ln(diameter) + 1.0 * ln(height))
        }
        
        val volumePerHectare = totalVolume / (parcelle.surfaceHa ?: 1.0)
        
        val yieldInterpretation = when {
            volumePerHectare < 100 -> DataInterpretationEntity(
                interpretationId = "yield_low_${parcelle.parcelleId}",
                parcelleId = parcelle.parcelleId,
                interpretationType = InterpretationType.YIELD_PREDICTION,
                title = "Rendement faible",
                description = "Le volume estimé est de ${volumePerHectare.toInt()} m³/ha, ce qui est faible pour cette station.",
                confidenceScore = 0.7,
                priority = Priority.MEDIUM,
                dataSource = "tiges",
                analysisMethod = "volume_estimation",
                parameters = """{"volumePerHectare": $volumePerHectare}""",
                results = """{"status": "low_yield", "improvement_potential": true}""",
                recommendations = "Considérer des amendements ou des traitements sylvicoles pour améliorer le rendement.",
                actionable = true,
                validUntil = null,
                tags = null
            )
            volumePerHectare > 400 -> DataInterpretationEntity(
                interpretationId = "yield_high_${parcelle.parcelleId}",
                parcelleId = parcelle.parcelleId,
                interpretationType = InterpretationType.YIELD_PREDICTION,
                title = "Rendement excellent",
                description = "Le volume estimé est de ${volumePerHectare.toInt()} m³/ha, ce qui est excellent.",
                confidenceScore = 0.8,
                priority = Priority.INFO,
                dataSource = "tiges",
                analysisMethod = "volume_estimation",
                parameters = """{"volumePerHectare": $volumePerHectare}""",
                results = """{"status": "high_yield", "commercial_ready": true}""",
                recommendations = "Le peuplement est prêt pour une récolte commerciale.",
                actionable = false,
                validUntil = null,
                tags = null
            )
            else -> null
        }
        yieldInterpretation?.let { interpretations.add(it) }
        
        return interpretations
    }
    
    /**
     * Analyse les risques potentiels.
     */
    private fun analyzeRisks(
        parcelle: ParcelleEntity,
        tiges: List<TigeEntity>,
        correlations: List<DataCorrelationEntity>
    ): List<DataInterpretationEntity> {
        val interpretations = mutableListOf<DataInterpretationEntity>()
        
        // Risque de vent basé sur la pente
        parcelle.slopePct?.let { slope ->
            if (slope > 30) {
                interpretations.add(DataInterpretationEntity(
                    interpretationId = "risk_wind_${parcelle.parcelleId}",
                    parcelleId = parcelle.parcelleId,
                    interpretationType = InterpretationType.RISK_ASSESSMENT,
                    title = "Risque de vent élevé",
                    description = "La pente de ${slope.toInt()}% augmente le risque de chablis.",
                    confidenceScore = 0.8,
                    priority = Priority.HIGH,
                    dataSource = "parcelle",
                    analysisMethod = "topographic_analysis",
                    parameters = """{"slope": $slope}""",
                    results = """{"status": "high_wind_risk", "slope": $slope}""",
                    recommendations = "Envisager des éclaircies progressives pour réduire la prise au vent.",
                    actionable = true,
                    validUntil = null,
                    tags = null
                ))
            }
        }

        val weakCorrelations = correlations.filter { it.correlationStrength < 0.3 }
        if (weakCorrelations.size > correlations.size * 0.5) {
            interpretations.add(DataInterpretationEntity(
                interpretationId = "risk_correlation_${parcelle.parcelleId}",
                parcelleId = parcelle.parcelleId,
                interpretationType = InterpretationType.RISK_ASSESSMENT,
                title = "Corrélations anormales",
                description = "Plus de la moitié des corrélations sont faibles, ce qui peut indiquer des problèmes.",
                confidenceScore = 0.6,
                priority = Priority.MEDIUM,
                dataSource = "correlations",
                analysisMethod = "correlation_analysis",
                parameters = """{"weakCorrelations": ${weakCorrelations.size}, "total": ${correlations.size}}""",
                results = """{"status": "abnormal_correlations", "investigation_needed": true}""",
                recommendations = "Investiguer les causes des corrélations anormales (stress hydrique, maladies).",
                actionable = true,
                validUntil = null,
                tags = null
            ))
        }
        
        return interpretations
    }
    
    /**
     * Génère des recommandations sylvicoles.
     */
    private fun generateSilviculturalRecommendations(
        parcelle: ParcelleEntity,
        tiges: List<TigeEntity>,
        correlations: List<DataCorrelationEntity>
    ): List<DataInterpretationEntity> {
        val interpretations = mutableListOf<DataInterpretationEntity>()
        
        if (tiges.isEmpty()) return interpretations
        
        val density = tiges.size / (parcelle.surfaceHa ?: 1.0)
        val avgDiameter = tiges.map { it.diamCm }.average()
        
        // Recommandation d'éclaircie
        if (density > 800 && avgDiameter > 25) {
            interpretations.add(DataInterpretationEntity(
                interpretationId = "silvicultural_thinning_${parcelle.parcelleId}",
                parcelleId = parcelle.parcelleId,
                interpretationType = InterpretationType.SILVICULTURAL_RECOMMENDATION,
                title = "Éclaircie recommandée",
                description = "La densité (${density.toInt()} tiges/ha) et le diamètre moyen (${avgDiameter.toInt()} cm) justifient une éclaircie.",
                confidenceScore = 0.9,
                priority = Priority.HIGH,
                dataSource = "tiges",
                analysisMethod = "silvicultural_rules",
                parameters = """{"density": $density, "avgDiameter": $avgDiameter}""",
                results = """{"recommendation": "thinning", "intensity": "25-30%"}""",
                recommendations = "Programmer une éclaircie de 25-30% pour améliorer la croissance des arbres restants.",
                actionable = true,
                validUntil = null,
                tags = null
            ))
        }
        
        // Recommandation de plantation
        if (density < 300) {
            interpretations.add(DataInterpretationEntity(
                interpretationId = "silvicultural_planting_${parcelle.parcelleId}",
                parcelleId = parcelle.parcelleId,
                interpretationType = InterpretationType.SILVICULTURAL_RECOMMENDATION,
                title = "Plantation complémentaire",
                description = "La faible densité (${density.toInt()} tiges/ha) nécessite des plantations complémentaires.",
                confidenceScore = 0.8,
                priority = Priority.MEDIUM,
                dataSource = "tiges",
                analysisMethod = "silvicultural_rules",
                parameters = """{"density": $density}""",
                results = """{"recommendation": "planting", "target_density": 600}""",
                recommendations = "Planter des arbres pour atteindre une densité cible de 600 tiges/ha.",
                actionable = true,
                validUntil = null,
                tags = null
            ))
        }
        
        return interpretations
    }
    
    /**
     * Analyse la valeur économique.
     */
    private fun analyzeEconomicValue(parcelle: ParcelleEntity, tiges: List<TigeEntity>): List<DataInterpretationEntity> {
        val interpretations = mutableListOf<DataInterpretationEntity>()
        
        if (tiges.isEmpty()) return interpretations
        
        // Estimation de la valeur totale
        val totalValue = tiges.sumOf { tige ->
            val diameter = tige.diamCm
            val height = tige.hauteurM ?: 20.0
            val volume = exp(1.5 + 2.0 * ln(diameter) + 1.0 * ln(height))
            val pricePerM3 = when {
                diameter < 30 -> 50.0
                diameter < 50 -> 80.0
                else -> 120.0
            }
            volume * pricePerM3
        }
        
        val valuePerHectare = totalValue / (parcelle.surfaceHa ?: 1.0)
        
        interpretations.add(DataInterpretationEntity(
            interpretationId = "economic_value_${parcelle.parcelleId}",
            parcelleId = parcelle.parcelleId,
            interpretationType = InterpretationType.ECONOMIC_VALUATION,
            title = "Valeur économique estimée",
            description = "La valeur estimée du peuplement est de ${valuePerHectare.toInt()} €/ha.",
            confidenceScore = 0.7,
            priority = Priority.INFO,
            dataSource = "tiges",
            analysisMethod = "economic_model",
            parameters = """{"valuePerHectare": $valuePerHectare}""",
            results = """{"totalValue": $totalValue, "valuePerHectare": $valuePerHectare}""",
            recommendations = "La valeur actuelle peut être optimisée par des interventions sylvicoles appropriées.",
            actionable = false,
            validUntil = null,
            tags = null
        ))
        
        return interpretations
    }
}
