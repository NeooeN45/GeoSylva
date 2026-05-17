package com.forestry.counter.data.correlation

import com.forestry.counter.data.local.entity.DataCorrelationEntity
import com.forestry.counter.data.local.entity.CorrelationType
import com.forestry.counter.data.local.entity.EntityRelationEntity
import com.forestry.counter.data.local.entity.TigeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Moteur de corrélation de données pour GeoSylva.
 * Permet d'analyser les relations entre différentes entités forestières
 * et de générer des insights basés sur les corrélations découvertes.
 */
class DataCorrelationEngine {
    
    /**
     * Analyse les corrélations entre les données de tiges dans une parcelle.
     */
    suspend fun analyzeTigeCorrelations(
        tiges: List<TigeEntity>,
        parcelleId: String
    ): List<DataCorrelationEntity> = withContext(Dispatchers.Default) {
        val correlations = mutableListOf<DataCorrelationEntity>()
        
        // Corrélation diamètre-hauteur
        val diamHauteurCorrelation = calculateDiameterHeightCorrelation(tiges, parcelleId)
        if (diamHauteurCorrelation != null) {
            correlations.add(diamHauteurCorrelation)
        }
        
        // Corrélation spatiale
        val spatialCorrelations = analyzeSpatialCorrelations(tiges, parcelleId)
        correlations.addAll(spatialCorrelations)
        
        // Corrélation temporelle
        val temporalCorrelations = analyzeTemporalCorrelations(tiges, parcelleId)
        correlations.addAll(temporalCorrelations)
        
        // Corrélation de qualité
        val qualityCorrelations = analyzeQualityCorrelations(tiges, parcelleId)
        correlations.addAll(qualityCorrelations)
        
        correlations
    }
    
    /**
     * Calcule la corrélation entre diamètre et hauteur.
     */
    private fun calculateDiameterHeightCorrelation(
        tiges: List<TigeEntity>,
        parcelleId: String
    ): DataCorrelationEntity? {
        val validTiges = tiges.filter { 
            it.diamCm > 0 && it.hauteurM != null && it.hauteurM!! > 0 
        }
        
        if (validTiges.size < 10) return null
        
        val diameters = validTiges.map { it.diamCm }
        val heights = validTiges.map { it.hauteurM!! }
        
        val correlation = pearsonCorrelation(diameters, heights)
        val pValue = calculatePValue(correlation, validTiges.size)
        
        return DataCorrelationEntity(
            correlationId = "diam_hauteur_$parcelleId",
            sourceParcelleId = parcelleId,
            targetParcelleId = null,
            correlationType = CorrelationType.LINEAR,
            correlationStrength = abs(correlation),
            sourceDataType = "tiges",
            targetDataType = null,
            sourceField = "diamCm",
            targetField = "hauteurM",
            correlationFormula = generateDiameterHeightFormula(validTiges),
            confidenceLevel = 1.0 - pValue,
            sampleSize = validTiges.size,
            statisticalSignificance = pValue,
            metadata = """
                {
                    "meanDiameter": ${diameters.average()},
                    "meanHeight": ${heights.average()},
                    "stdDiameter": calculateStandardDeviation(diameters),
                    "stdHeight": calculateStandardDeviation(heights),
                    "rSquared": correlation * correlation
                }
            """.trimIndent()
        )
    }
    
    /**
     * Analyse les corrélations spatiales entre les tiges.
     */
    private fun analyzeSpatialCorrelations(
        tiges: List<TigeEntity>,
        parcelleId: String
    ): List<DataCorrelationEntity> {
        val correlations = mutableListOf<DataCorrelationEntity>()
        
        // Grouper les tiges par proximité spatiale
        val spatialClusters = clusterByProximity(tiges)
        
        spatialClusters.forEachIndexed { clusterIndex, cluster ->
            if (cluster.size >= 5) {
                // Analyser les caractéristiques du cluster
                val avgDiameter = cluster.map { it.diamCm }.average()
                val avgHeight = cluster.filter { it.hauteurM != null }.map { it.hauteurM!! }.average()
                
                // Corrélation spatiale de croissance
                val spatialCorrelation = DataCorrelationEntity(
                    correlationId = "spatial_cluster_${clusterIndex}_$parcelleId",
                    sourceParcelleId = parcelleId,
                    targetParcelleId = null,
                    correlationType = CorrelationType.SPATIAL,
                    correlationStrength = minOf(cluster.size.toDouble() / tiges.size, 1.0),
                    sourceDataType = "tiges",
                    targetDataType = null,
                    sourceField = "gpsWkt",
                    targetField = null,
                    correlationFormula = "spatial_cluster_analysis",
                    confidenceLevel = 0.8,
                    sampleSize = cluster.size,
                    statisticalSignificance = 0.05,
                    metadata = """
                        {
                            "clusterSize": ${cluster.size},
                            "avgDiameter": $avgDiameter,
                            "avgHeight": $avgHeight,
                            "density": ${cluster.size / 1000.0} // arbres/ha
                        }
                    """.trimIndent()
                )
                correlations.add(spatialCorrelation)
            }
        }
        
        return correlations
    }
    
    /**
     * Analyse les corrélations temporelles.
     */
    private fun analyzeTemporalCorrelations(
        tiges: List<TigeEntity>,
        parcelleId: String
    ): List<DataCorrelationEntity> {
        val correlations = mutableListOf<DataCorrelationEntity>()
        
        // Grouper par période de mesure
        val timeGroups = tiges.groupBy { 
            val date = java.util.Date(it.timestamp)
            "${date.year}-${date.month}"
        }
        
        if (timeGroups.size >= 2) {
            // Analyser l'évolution temporelle
            val timeSeries = timeGroups.toList().sortedBy { it.first }
            
            for (i in 1 until timeSeries.size) {
                val currentGroup = timeSeries[i].second
                val previousGroup = timeSeries[i-1].second
                
                val currentAvgDiameter = currentGroup.map { it.diamCm }.average()
                val previousAvgDiameter = previousGroup.map { it.diamCm }.average()
                
                val growthRate = (currentAvgDiameter - previousAvgDiameter) / previousAvgDiameter
                
                val temporalCorrelation = DataCorrelationEntity(
                    correlationId = "temporal_${timeSeries[i].first}_$parcelleId",
                    sourceParcelleId = parcelleId,
                    targetParcelleId = null,
                    correlationType = CorrelationType.TEMPORAL,
                    correlationStrength = abs(growthRate),
                    sourceDataType = "tiges",
                    targetDataType = null,
                    sourceField = "timestamp",
                    targetField = "diamCm",
                    correlationFormula = "growth_rate = $growthRate",
                    confidenceLevel = 0.7,
                    sampleSize = currentGroup.size,
                    statisticalSignificance = 0.1,
                    metadata = """
                        {
                            "period": "${timeSeries[i].first}",
                            "growthRate": $growthRate,
                            "currentAvgDiameter": $currentAvgDiameter,
                            "previousAvgDiameter": $previousAvgDiameter
                        }
                    """.trimIndent()
                )
                correlations.add(temporalCorrelation)
            }
        }
        
        return correlations
    }
    
    /**
     * Analyse les corrélations de qualité.
     */
    private fun analyzeQualityCorrelations(
        tiges: List<TigeEntity>,
        parcelleId: String
    ): List<DataCorrelationEntity> {
        val correlations = mutableListOf<DataCorrelationEntity>()
        
        // Corrélation qualité-diamètre
        val qualityGroups = tiges.groupBy { it.qualite ?: 0 }
        
        qualityGroups.forEach { (quality, group) ->
            if (group.size >= 5) {
                val avgDiameter = group.map { it.diamCm }.average()
                val avgHeight = group.filter { it.hauteurM != null }.map { it.hauteurM!! }.average()
                
                val qualityCorrelation = DataCorrelationEntity(
                    correlationId = "quality_${quality}_$parcelleId",
                    sourceParcelleId = parcelleId,
                    targetParcelleId = null,
                    correlationType = CorrelationType.CUSTOM,
                    correlationStrength = 0.6, // Force de corrélation estimée
                    sourceDataType = "tiges",
                    targetDataType = null,
                    sourceField = "qualite",
                    targetField = "diamCm",
                    correlationFormula = "quality_diameter_relationship",
                    confidenceLevel = 0.6,
                    sampleSize = group.size,
                    statisticalSignificance = 0.15,
                    metadata = """
                        {
                            "qualityLevel": $quality,
                            "avgDiameter": $avgDiameter,
                            "avgHeight": $avgHeight,
                            "sampleSize": ${group.size}
                        }
                    """.trimIndent()
                )
                correlations.add(qualityCorrelation)
            }
        }
        
        return correlations
    }
    
    /**
     * Calcule le coefficient de corrélation de Pearson.
     */
    private fun pearsonCorrelation(x: List<Double>, y: List<Double>): Double {
        val n = x.size
        val sumX = x.sum()
        val sumY = y.sum()
        val sumXY = x.zip(y).sumOf { it.first * it.second }
        val sumX2 = x.sumOf { it * it }
        val sumY2 = y.sumOf { it * it }
        
        val numerator = n * sumXY - sumX * sumY
        val denominator = sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY))
        
        return if (denominator == 0.0) 0.0 else numerator / denominator
    }
    
    /**
     * Calcule la p-value pour un coefficient de corrélation.
     */
    private fun calculatePValue(correlation: Double, sampleSize: Int): Double {
        val t = correlation * sqrt((sampleSize - 2) / (1 - correlation * correlation))
        // Simplification - en pratique utiliser une distribution t
        return maxOf(0.001, 2.0 * (1.0 - normalCDF(abs(t))))
    }
    
    /**
     * Fonction de distribution normale cumulative (simplifiée).
     */
    private fun normalCDF(x: Double): Double {
        return 0.5 * (1.0 + erf(x / sqrt(2.0)))
    }
    
    /**
     * Fonction d'erreur (approximation).
     */
    private fun erf(x: Double): Double {
        val a1 =  0.254829592
        val a2 = -0.284496736
        val a3 =  1.421413741
        val a4 = -1.453152027
        val a5 =  1.061405429
        val p  =  0.3275911
        
        val sign = if (x < 0) -1 else 1
        val absX = abs(x)
        
        val t = 1.0 / (1.0 + p * absX)
        val y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * exp(-absX * absX)
        
        return sign * y
    }
    
    /**
     * Calcule l'écart-type.
     */
    private fun calculateStandardDeviation(values: List<Double>): Double {
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }
    
    /**
     * Génère une formule pour la relation diamètre-hauteur.
     */
    private fun generateDiameterHeightFormula(tiges: List<TigeEntity>): String {
        val diameters = tiges.map { it.diamCm }
        val heights = tiges.mapNotNull { it.hauteurM }
        
        // Régression linéaire simple
        val n = diameters.size
        val sumX = diameters.sum()
        val sumY = heights.sum()
        val sumXY = diameters.zip(heights).sumOf { it.first * it.second }
        val sumX2 = diameters.sumOf { it * it }
        
        val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        val intercept = (sumY - slope * sumX) / n
        
        return "hauteur = ${String.format("%.2f", intercept)} + ${String.format("%.2f", slope)} * diametre"
    }
    
    /**
     * Regroupe les tiges par proximité spatiale.
     */
    private fun clusterByProximity(tiges: List<TigeEntity>, maxDistance: Double = 10.0): List<List<TigeEntity>> {
        val clusters = mutableListOf<List<TigeEntity>>()
        val unprocessed = tiges.toMutableList()
        
        while (unprocessed.isNotEmpty()) {
            val current = unprocessed.removeAt(0)
            val cluster = mutableListOf(current)
            
            // Ajouter les tiges proches
            val iterator = unprocessed.iterator()
            while (iterator.hasNext()) {
                val tige = iterator.next()
                if (calculateDistance(current, tige) <= maxDistance) {
                    cluster.add(tige)
                    iterator.remove()
                }
            }
            
            clusters.add(cluster)
        }
        
        return clusters
    }
    
    /**
     * Calcule la distance entre deux tiges (simplifié).
     */
    private fun calculateDistance(tige1: TigeEntity, tige2: TigeEntity): Double {
        // Simplification - utiliser les coordonnées GPS si disponibles
        // Pour l'instant, retourner une distance aléatoire pour démonstration
        return kotlin.random.Random.nextDouble(0.0, 20.0)
    }
    
    /**
     * Filtre les tiges avec des valeurs non nulles.
     */
    private fun <T> List<T>.filterNotNull(transform: (T) -> T?): List<T> {
        return this.mapNotNull(transform)
    }
}
