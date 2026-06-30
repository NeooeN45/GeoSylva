package com.forestry.counter.data.calculation

import com.forestry.counter.data.local.entity.AdvancedCalculationEntity
import com.forestry.counter.data.local.entity.CalculationType
import com.forestry.counter.data.local.entity.CalculationStatus
import com.forestry.counter.data.local.entity.TigeEntity
import com.forestry.counter.data.local.entity.ParcelleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Moteur de calcul avancé pour GeoSylva.
 * Permet d'effectuer des calculs complexes sur les données forestières
 * avec optimisation et gestion des dépendances.
 */
class AdvancedCalculationEngine {
    
    private val calculationCache = mutableMapOf<String, AdvancedCalculationEntity>()

    // ─────────────────────────────────────────────────────────────────────
    // Biomasse & carbone — approche Volume × densité × BEF × (1+RER)
    // ─────────────────────────────────────────────────────────────────────
    // Méthodologie conforme à l'IPCC 2006 (GPG-LULUCF) :
    //   1. Volume fût (m³) = a × D^b × H^c  (équation de type Schumacher-Hall
    //      simplifiée, avec D en cm et H en m ; b=2.0, c=1.0).
    //   2. Biomasse fût sec (kg) = Volume × densité bois (t/m³) × 1000.
    //   3. Biomasse aérienne (kg) = biomasse fût × BEF (branches + houppier).
    //   4. Biomasse totale (kg) = biomasse aérienne × (1 + RER) (racines).
    //   5. Carbone (kg) = biomasse × fraction carbone (0.50, IPCC 2006).
    //   6. CO2-équivalent (kg) = carbone × 3.67 (ratio moléculaire CO2/C).
    // Densités : infradensité (masse anhydre / volume vert) par essence.
    // BEF : 1.65 feuillus, 1.45 résineux. RER : 0.25 (Cairns et al. 1997).
    // Les calculs sont menés en kg ; conversion en tonnes pour le stockage.
    private val carbonFraction = 0.50
    private val co2ConversionFactor = 3.67
    private val rootToShootRatio = 0.25 // RER — Cairns et al. 1997

    // Codes d'essences conifères (pour le fallback « autres conifères »).
    private val coniferCodes: Set<String> = setOf(
        "SAPIN", "SAPIN_PECTINE", "SAPIN_NORDMANN", "SAPIN_GRANDIS",
        "EPICEA", "EPICEA_COMMUN", "EPICEA_SITKA", "EPICEA_OMORIKA",
        "DOUGLAS", "DOUGLAS_VERT",
        "PIN", "PIN_SYLVESTRE", "PIN_MARITIME", "PIN_NOIR_AUTR", "PIN_LARICIO",
        "MELEZE", "MEL_EUROPE", "MEL_HYBRIDE", "MEL_JAPON",
        "CEDRE", "CEDRE_ATLAS", "CEDRE_LIBAN",
        "CYPRES", "CYPRES_PROVENCE", "CYPRES_CHAUVE",
        "IF", "GENEVRIER", "GENEVRIER_CADE", "GENEVRIER_PHENICIE"
    )

    /**
     * Coefficients (a, b, c) de l'équation de volume fût V = a × D^b × H^c
     * (D en cm, H en m, V en m³). b=2.0 et c=1.0 pour tous les groupes ;
     * seul 'a' varie par essence (valeurs réalistes pour volume commercial).
     */
    internal fun volumeCoefficients(essenceCode: String): Triple<Double, Double, Double> {
        val code = essenceCode.trim().uppercase()
        val a = when (code) {
            // Chênes
            "CHENE", "CH_SESSILE", "CH_PEDONCULE",
            "QUPE", "QUPES", "QUPU", "QUIL", "QURU" -> 0.00005
            // Hêtre
            "HETRE", "HETRE_COMMUN", "FASY" -> 0.000045
            // Sapin / Épicéa
            "SAPIN", "SAPIN_PECTINE", "SAPIN_NORDMANN", "SAPIN_GRANDIS",
            "EPICEA", "EPICEA_COMMUN", "EPICEA_SITKA", "EPICEA_OMORIKA",
            "ABAL" -> 0.00004
            // Douglas
            "DOUGLAS", "DOUGLAS_VERT" -> 0.000042
            // Pin sylvestre
            "PIN_SYLVESTRE" -> 0.000038
            // Pin maritime
            "PIN_MARITIME" -> 0.000040
            // Mélèze
            "MELEZE", "MEL_EUROPE", "MEL_HYBRIDE", "MEL_JAPON" -> 0.000040
            else -> if (code in coniferCodes) 0.000040 else 0.000045
        }
        return Triple(a, 2.0, 1.0)
    }

    /**
     * Densité du bois (infradensité, t/m³ = g/cm³) par essence.
     * Source : tables d'infradensité CIRAD/IGN.
     */
    internal fun woodDensity(essenceCode: String): Double {
        val code = essenceCode.trim().uppercase()
        return when (code) {
            // Chênes sessile / pédonculé
            "CHENE", "CH_SESSILE", "CH_PEDONCULE",
            "QUPE", "QUPES", "QUPU", "QUIL", "QURU" -> 0.61
            // Hêtre
            "HETRE", "HETRE_COMMUN", "FASY" -> 0.68
            // Sapin pectiné
            "SAPIN", "SAPIN_PECTINE", "SAPIN_NORDMANN", "SAPIN_GRANDIS", "ABAL" -> 0.43
            // Épicéa commun
            "EPICEA", "EPICEA_COMMUN", "EPICEA_SITKA", "EPICEA_OMORIKA" -> 0.43
            // Douglas
            "DOUGLAS", "DOUGLAS_VERT" -> 0.47
            // Pin sylvestre
            "PIN_SYLVESTRE" -> 0.52
            // Pin maritime
            "PIN_MARITIME" -> 0.44
            // Mélèze
            "MELEZE", "MEL_EUROPE", "MEL_HYBRIDE", "MEL_JAPON" -> 0.56
            // Noyer
            "NOYER" -> 0.61
            // Frêne
            "FRENE" -> 0.68
            // Érable
            "ERABLE" -> 0.56
            // Charme
            "CHARME" -> 0.78
            // Bouleau
            "BOULEAU" -> 0.65
            // Aulne
            "AULNE" -> 0.52
            // Châtaignier
            "CHATAIGNIER" -> 0.58
            else -> if (code in coniferCodes) 0.45 else 0.60 // défaut résineux / feuillu
        }
    }

    /**
     * Facteur d'expansion de la biomasse (BEF) — branches et houppier.
     * Feuillus : 1.65 ; Résineux : 1.45 (IPCC 2006, valeurs par défaut).
     */
    internal fun biomassExpansionFactor(essenceCode: String): Double {
        val code = essenceCode.trim().uppercase()
        return if (code in coniferCodes) 1.45 else 1.65
    }

    /**
     * Calcule la biomasse totale d'une tige (kg) selon la chaîne IPCC 2006 :
     * Volume fût × densité × BEF × (1 + RER).
     *
     * @return biomasse totale (fût + branches + racines) en kg
     */
    private fun computeTreeBiomassKg(tige: TigeEntity): Double {
        val diameter = tige.diamCm
        val height = tige.hauteurM ?: 20.0
        if (diameter <= 0.0 || height <= 0.0) return 0.0

        // Étape 1 : Volume fût (m³) — V = a × D^b × H^c
        val (a, b, c) = volumeCoefficients(tige.essenceCode)
        val volumeM3 = a * diameter.pow(b) * height.pow(c)

        // Étape 2 : Densité du bois (t/m³) — infradensité par essence
        val density = woodDensity(tige.essenceCode)

        // Étape 3 : Biomasse sèche fût (kg) = Volume × densité × 1000 (t → kg)
        val stemBiomassKg = volumeM3 * density * 1000.0

        // Étape 4 : BEF pour branches/houppier
        val bef = biomassExpansionFactor(tige.essenceCode)

        // Étape 5 : RER pour racines (Cairns et al. 1997)
        val rer = rootToShootRatio

        // Biomasse totale (kg) = fût × BEF × (1 + RER)
        return stemBiomassKg * bef * (1.0 + rer)
    }

    /**
     * Décompose la biomasse d'une tige en ses trois composantes (kg) :
     * fût, aérienne (branches + houppier) et racinaire.
     */
    internal fun computeTreeBiomassBreakdownKg(tige: TigeEntity): TreeBiomassBreakdown {
        val diameter = tige.diamCm
        val height = tige.hauteurM ?: 20.0
        if (diameter <= 0.0 || height <= 0.0) {
            return TreeBiomassBreakdown(0.0, 0.0, 0.0, 0.0)
        }
        val (a, b, c) = volumeCoefficients(tige.essenceCode)
        val volumeM3 = a * diameter.pow(b) * height.pow(c)
        val density = woodDensity(tige.essenceCode)
        val stemBiomassKg = volumeM3 * density * 1000.0
        val bef = biomassExpansionFactor(tige.essenceCode)
        val abovegroundBiomassKg = stemBiomassKg * bef
        val belowgroundBiomassKg = abovegroundBiomassKg * rootToShootRatio
        val totalBiomassKg = abovegroundBiomassKg + belowgroundBiomassKg
        return TreeBiomassBreakdown(
            totalBiomassKg = totalBiomassKg,
            stemBiomassKg = stemBiomassKg,
            abovegroundBiomassKg = abovegroundBiomassKg,
            belowgroundBiomassKg = belowgroundBiomassKg
        )
    }

    /** Décomposition de la biomasse d'une tige (kg). */
    data class TreeBiomassBreakdown(
        val totalBiomassKg: Double,
        val stemBiomassKg: Double,
        val abovegroundBiomassKg: Double,
        val belowgroundBiomassKg: Double
    )
    
    /**
     * Exécute un calcul avancé.
     */
    suspend fun executeCalculation(
        calculation: AdvancedCalculationEntity,
        tiges: List<TigeEntity>,
        parcelle: ParcelleEntity?
    ): AdvancedCalculationEntity = withContext(Dispatchers.Default) {
        
        val startTime = System.currentTimeMillis()
        
        try {
            val result = when (calculation.calculationType) {
                CalculationType.GROWTH_MODEL ->
                    calculateGrowthModel(calculation, tiges, parcelle)
                CalculationType.YIELD_PREDICTION ->
                    calculateYieldPrediction(calculation, tiges, parcelle)
                CalculationType.VOLUME_CALCULATION ->
                    calculateVolume(calculation, tiges, parcelle)
                CalculationType.BIOMASS_ESTIMATION ->
                    calculateBiomass(calculation, tiges, parcelle)
                CalculationType.CARBON_SEQUESTRATION ->
                    calculateCarbonSequestration(calculation, tiges, parcelle)
                CalculationType.ECONOMIC_VALUATION ->
                    calculateEconomicValuation(calculation, tiges, parcelle)
                CalculationType.RISK_ASSESSMENT ->
                    calculateRiskAssessment(calculation, tiges, parcelle)
                CalculationType.BIODIVERSITY_INDEX ->
                    calculateBiodiversityIndex(calculation, tiges, parcelle)
                CalculationType.STATISTICAL_ANALYSIS ->
                    calculateStatisticalAnalysis(calculation, tiges, parcelle)
                CalculationType.CORRELATION_ANALYSIS ->
                    calculateCorrelationAnalysis(calculation, tiges, parcelle)
                CalculationType.SPATIAL_ANALYSIS ->
                    calculateSpatialAnalysis(calculation, tiges, parcelle)
                else ->
                    calculateCustom(calculation, tiges, parcelle)
            }
            
            val executionTime = System.currentTimeMillis() - startTime
            
            result.copy(
                status = CalculationStatus.COMPLETED,
                result = result.result,
                executionTime = executionTime,
                updatedAt = System.currentTimeMillis()
            )
            
        } catch (e: Exception) {
            calculation.copy(
                status = CalculationStatus.FAILED,
                error = e.message ?: "Erreur inconnue",
                executionTime = System.currentTimeMillis() - startTime,
                updatedAt = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Calcule un modèle de croissance.
     */
    private fun calculateGrowthModel(
        calculation: AdvancedCalculationEntity,
        tiges: List<TigeEntity>,
        parcelle: ParcelleEntity?
    ): AdvancedCalculationEntity {
        if (tiges.isEmpty()) {
            return calculation.copy(
                result = 0.0,
                error = "Aucune donnée de tige disponible"
            )
        }
        
        // Modèle de croissance de Richards simplifié
        val avgDiameter = tiges.map { it.diamCm }.average()
        
        // Paramètres du modèle (à calibrer selon les essences)
        val asymptote = 50.0 // Diamètre asymptotique
        val growthRate = 0.1 // Taux de croissance
        val shapeParameter = 2.0 // Paramètre de forme
        
        // Calcul de la croissance prédite
        val time = 10.0 // Période de projection (années)
        val predictedDiameter = asymptote / (1.0 + exp(-growthRate * (time - shapeParameter)))
        
        val growthPotential = (predictedDiameter - avgDiameter) / avgDiameter
        
        return calculation.copy(
            result = growthPotential,
            resultMetadata = """
                {
                    "currentAvgDiameter": $avgDiameter,
                    "predictedDiameter": $predictedDiameter,
                    "growthPotential": $growthPotential,
                    "timeHorizon": $time,
                    "model": "Richards"
                }
            """.trimIndent(),
            confidence = 0.8,
            accuracy = 0.85
        )
    }
    
    /**
     * Calcule une prédiction de rendement.
     */
    private fun calculateYieldPrediction(
        calculation: AdvancedCalculationEntity,
        tiges: List<TigeEntity>,
        parcelle: ParcelleEntity?
    ): AdvancedCalculationEntity {
        if (tiges.isEmpty()) {
            return calculation.copy(
                result = 0.0,
                error = "Aucune donnée de tige disponible"
            )
        }
        
        val surface = parcelle?.surfaceHa ?: 1.0
        val currentVolume = calculateTotalVolume(tiges)
        
        // Prédiction de rendement sur 10 ans
        val growthFactor = 1.5 // Facteur de croissance moyen
        val mortalityRate = 0.05 // Taux de mortalité annuel
        val thinningYield = currentVolume * 0.3 // Rendement des éclaircies
        
        val futureVolume = currentVolume * growthFactor * exp(-mortalityRate * 10)
        val totalYield = thinningYield + futureVolume
        val yieldPerHectare = totalYield / surface
        
        return calculation.copy(
            result = yieldPerHectare,
            resultMetadata = """
                {
                    "currentVolume": $currentVolume,
                    "futureVolume": $futureVolume,
                    "thinningYield": $thinningYield,
                    "totalYield": $totalYield,
                    "yieldPerHectare": $yieldPerHectare,
                    "timeHorizon": 10
                }
            """.trimIndent(),
            confidence = 0.75,
            accuracy = 0.8
        )
    }
    
    /**
     * Calcule le volume total.
     */
    private fun calculateVolume(
        calculation: AdvancedCalculationEntity,
        tiges: List<TigeEntity>,
        parcelle: ParcelleEntity?
    ): AdvancedCalculationEntity {
        val totalVolume = calculateTotalVolume(tiges)
        val volumePerHectare = totalVolume / (parcelle?.surfaceHa ?: 1.0)
        
        return calculation.copy(
            result = totalVolume,
            resultMetadata = """
                {
                    "totalVolume": $totalVolume,
                    "volumePerHectare": $volumePerHectare,
                    "treeCount": ${tiges.size},
                    "avgVolumePerTree": ${totalVolume / tiges.size}
                }
            """.trimIndent(),
            confidence = 0.9,
            accuracy = 0.95
        )
    }
    
    /**
     * Estime la biomasse.
     */
    private fun calculateBiomass(
        calculation: AdvancedCalculationEntity,
        tiges: List<TigeEntity>,
        parcelle: ParcelleEntity?
    ): AdvancedCalculationEntity {
        if (tiges.isEmpty()) {
            return calculation.copy(
                result = 0.0,
                error = "Aucune donnée de tige disponible"
            )
        }
        
        // Biomasse totale (kg) — chaîne IPCC 2006 :
        // Volume fût × densité × BEF × (1 + RER)
        val breakdowns = tiges.map { computeTreeBiomassBreakdownKg(it) }
        val totalBiomassKg = breakdowns.sumOf { it.totalBiomassKg }
        val totalStemKg = breakdowns.sumOf { it.stemBiomassKg }
        val totalAbovegroundKg = breakdowns.sumOf { it.abovegroundBiomassKg }
        val totalBelowgroundKg = breakdowns.sumOf { it.belowgroundBiomassKg }

        val surfaceHa = parcelle?.surfaceHa ?: 1.0
        val biomassPerHectare = totalBiomassKg / surfaceHa

        return calculation.copy(
            result = totalBiomassKg,
            resultMetadata = """
                {
                    "totalBiomassKg": $totalBiomassKg,
                    "stemBiomassKg": $totalStemKg,
                    "abovegroundBiomassKg": $totalAbovegroundKg,
                    "belowgroundBiomassKg": $totalBelowgroundKg,
                    "biomassPerHectare": $biomassPerHectare,
                    "biomassPerTree": ${totalBiomassKg / tiges.size},
                    "method": "Volume * density * BEF * (1 + RER) (IPCC 2006)"
                }
            """.trimIndent(),
            confidence = 0.8,
            accuracy = 0.85
        )
    }
    
    /**
     * Calcule la séquestration de carbone.
     */
    private fun calculateCarbonSequestration(
        calculation: AdvancedCalculationEntity,
        tiges: List<TigeEntity>,
        parcelle: ParcelleEntity?
    ): AdvancedCalculationEntity {
        if (tiges.isEmpty()) {
            return calculation.copy(
                result = 0.0,
                error = "Aucune donnée de tige disponible"
            )
        }
        
        // Biomasse totale (kg) — chaîne IPCC 2006 :
        // Volume fût × densité × BEF × (1 + RER)
        val breakdowns = tiges.map { computeTreeBiomassBreakdownKg(it) }
        val totalBiomassKg = breakdowns.sumOf { it.totalBiomassKg }
        val totalStemKg = breakdowns.sumOf { it.stemBiomassKg }
        val totalAbovegroundKg = breakdowns.sumOf { it.abovegroundBiomassKg }
        val totalBelowgroundKg = breakdowns.sumOf { it.belowgroundBiomassKg }

        // Fraction carbone IPCC 2006 (0.50) — calculs en kg
        val totalCarbonKg = totalBiomassKg * carbonFraction
        val surfaceHa = parcelle?.surfaceHa ?: 1.0
        val carbonPerHectare = totalCarbonKg / surfaceHa

        // CO2 équivalent (kg) = carbone × 3.67 (ratio moléculaire CO2/C)
        val co2EquivalentKg = totalCarbonKg * co2ConversionFactor

        return calculation.copy(
            result = co2EquivalentKg,
            resultMetadata = """
                {
                    "totalBiomassKg": $totalBiomassKg,
                    "stemBiomassKg": $totalStemKg,
                    "abovegroundBiomassKg": $totalAbovegroundKg,
                    "belowgroundBiomassKg": $totalBelowgroundKg,
                    "totalCarbonKg": $totalCarbonKg,
                    "carbonPerHectare": $carbonPerHectare,
                    "co2EquivalentKg": $co2EquivalentKg,
                    "carbonFraction": $carbonFraction,
                    "co2ConversionFactor": $co2ConversionFactor,
                    "rootToShootRatio": $rootToShootRatio,
                    "method": "Volume * density * BEF * (1 + RER) * Cfrac (IPCC 2006)"
                }
            """.trimIndent(),
            confidence = 0.8,
            accuracy = 0.85
        )
    }
    
    /**
     * Calcule la valorisation économique.
     */
    private fun calculateEconomicValuation(
        calculation: AdvancedCalculationEntity,
        tiges: List<TigeEntity>,
        parcelle: ParcelleEntity?
    ): AdvancedCalculationEntity {
        if (tiges.isEmpty()) {
            return calculation.copy(
                result = 0.0,
                error = "Aucune donnée de tige disponible"
            )
        }
        
        val totalValue = tiges.sumOf { tige ->
            val diameter = tige.diamCm
            val height = tige.hauteurM ?: 20.0
            val volume = exp(1.5 + 2.0 * ln(diameter) + 1.0 * ln(height))
            
            // Prix au m³ selon la qualité
            val pricePerM3 = when (tige.qualite) {
                1 -> 30.0 // Bois de chauffage
                2 -> 60.0 // Bois d'œuvre courant
                3 -> 90.0 // Bois d'œuvre de qualité
                4, 5 -> 120.0 // Bois d'œuvre supérieur
                else -> 50.0 // Par défaut
            }
            
            volume * pricePerM3
        }
        
        val valuePerHectare = totalValue / (parcelle?.surfaceHa ?: 1.0)
        
        return calculation.copy(
            result = totalValue,
            resultMetadata = """
                {
                    "totalValue": $totalValue,
                    "valuePerHectare": $valuePerHectare,
                    "avgValuePerTree": ${totalValue / tiges.size}
                }
            """.trimIndent(),
            confidence = 0.7,
            accuracy = 0.8
        )
    }
    
    /**
     * Calcule une évaluation des risques.
     */
    private fun calculateRiskAssessment(
        calculation: AdvancedCalculationEntity,
        tiges: List<TigeEntity>,
        parcelle: ParcelleEntity?
    ): AdvancedCalculationEntity {
        var riskScore = 0.0
        
        // Risque lié à la pente
        parcelle?.slopePct?.let { slope ->
            if (slope > 30) riskScore += 0.3
            else if (slope > 20) riskScore += 0.2
        }
        
        // Risque lié à la densité
        val density = tiges.size / (parcelle?.surfaceHa ?: 1.0)
        if (density > 1200) riskScore += 0.3
        else if (density < 300) riskScore += 0.2
        
        // Risque lié à la qualité
        val avgQuality = tiges.map { it.qualite ?: 1 }.average()
        if (avgQuality < 2) riskScore += 0.2
        
        // Risque lié à l'âge (estimé par le diamètre)
        val avgDiameter = tiges.map { it.diamCm }.average()
        if (avgDiameter > 60) riskScore += 0.2 // Risque de sénescence
        
        return calculation.copy(
            result = riskScore,
            resultMetadata = """
                {
                    "riskScore": $riskScore,
                    "slopeRisk": ${parcelle?.slopePct?.let { if (it > 30) 0.3 else if (it > 20) 0.2 else 0.0 } ?: 0.0},
                    "densityRisk": ${if (density > 1200) 0.3 else if (density < 300) 0.2 else 0.0},
                    "qualityRisk": ${if (avgQuality < 2) 0.2 else 0.0},
                    "ageRisk": ${if (avgDiameter > 60) 0.2 else 0.0}
                }
            """.trimIndent(),
            confidence = 0.7,
            accuracy = 0.75
        )
    }
    
    /**
     * Calcule un indice de biodiversité.
     */
    private fun calculateBiodiversityIndex(
        calculation: AdvancedCalculationEntity,
        tiges: List<TigeEntity>,
        parcelle: ParcelleEntity?
    ): AdvancedCalculationEntity {
        if (tiges.isEmpty()) {
            return calculation.copy(
                result = 0.0,
                error = "Aucune donnée de tige disponible"
            )
        }
        
        // Indice de Shannon-Wiener basé sur les essences
        val essenceGroups = tiges.groupBy { it.essenceCode }
        val totalTrees = tiges.size.toDouble()
        
        val shannonIndex = essenceGroups.values.sumOf { group ->
            val proportion = group.size / totalTrees
            -proportion * ln(proportion)
        }
        
        // Normalisation entre 0 et 1
        val maxDiversity = ln(essenceGroups.size.toDouble())
        val biodiversityIndex = if (maxDiversity > 0) shannonIndex / maxDiversity else 0.0
        
        return calculation.copy(
            result = biodiversityIndex,
            resultMetadata = """
                {
                    "shannonIndex": $shannonIndex,
                    "biodiversityIndex": $biodiversityIndex,
                    "speciesCount": ${essenceGroups.size},
                    "treeCount": ${tiges.size}
                }
            """.trimIndent(),
            confidence = 0.8,
            accuracy = 0.85
        )
    }
    
    /**
     * Calcule une analyse statistique.
     */
    private fun calculateStatisticalAnalysis(
        calculation: AdvancedCalculationEntity,
        tiges: List<TigeEntity>,
        parcelle: ParcelleEntity?
    ): AdvancedCalculationEntity {
        if (tiges.isEmpty()) {
            return calculation.copy(
                result = 0.0,
                error = "Aucune donnée de tige disponible"
            )
        }
        
        val diameters = tiges.map { it.diamCm }
        val heights = tiges.mapNotNull { it.hauteurM }
        
        val stats = mapOf(
            "diameter_mean" to diameters.average(),
            "diameter_std" to calculateStandardDeviation(diameters),
            "diameter_cv" to (calculateStandardDeviation(diameters) / diameters.average()),
            "height_mean" to heights.average(),
            "height_std" to calculateStandardDeviation(heights),
            "tree_count" to tiges.size.toDouble()
        )
        
        return calculation.copy(
            result = stats["diameter_mean"] ?: 0.0,
            resultMetadata = stats.toString(),
            confidence = 0.95,
            accuracy = 0.98
        )
    }
    
    /**
     * Calcule une analyse de corrélation.
     */
    private fun calculateCorrelationAnalysis(
        calculation: AdvancedCalculationEntity,
        tiges: List<TigeEntity>,
        parcelle: ParcelleEntity?
    ): AdvancedCalculationEntity {
        val validTiges = tiges.mapNotNull { tige ->
            val h = tige.hauteurM ?: return@mapNotNull null
            if (tige.diamCm > 0 && h > 0) tige to h else null
        }

        if (validTiges.size < 10) {
            return calculation.copy(
                result = 0.0,
                error = "Pas assez de données pour l'analyse de corrélation"
            )
        }

        val diameters = validTiges.map { it.first.diamCm }
        val heights = validTiges.map { it.second }
        
        val correlation = calculatePearsonCorrelation(diameters, heights)
        
        return calculation.copy(
            result = correlation,
            resultMetadata = """
                {
                    "correlation": $correlation,
                    "sampleSize": ${validTiges.size},
                    "rSquared": ${correlation * correlation}
                }
            """.trimIndent(),
            confidence = 0.9,
            accuracy = 0.95
        )
    }
    
    /**
     * Calcule une analyse spatiale.
     */
    private fun calculateSpatialAnalysis(
        calculation: AdvancedCalculationEntity,
        tiges: List<TigeEntity>,
        parcelle: ParcelleEntity?
    ): AdvancedCalculationEntity {
        if (tiges.isEmpty()) {
            return calculation.copy(
                result = 0.0,
                error = "Aucune donnée de tige disponible"
            )
        }
        
        val surface = parcelle?.surfaceHa ?: 1.0
        val density = tiges.size / surface
        
        // Indice de dispersion (simplifié)
        val dispersionIndex = when {
            density < 300 -> 0.3 // Sous-dispersé
            density > 1200 -> 0.8 // Sur-dispersé
            else -> 0.5 // Normal
        }
        
        return calculation.copy(
            result = dispersionIndex,
            resultMetadata = """
                {
                    "density": $density,
                    "dispersionIndex": $dispersionIndex,
                    "treeCount": ${tiges.size},
                    "surface": $surface
                }
            """.trimIndent(),
            confidence = 0.7,
            accuracy = 0.8
        )
    }
    
    /**
     * Calcule un calcul personnalisé.
     */
    private fun calculateCustom(
        calculation: AdvancedCalculationEntity,
        tiges: List<TigeEntity>,
        parcelle: ParcelleEntity?
    ): AdvancedCalculationEntity {
        // Implémentation basique - à étendre selon les besoins
        return calculation.copy(
            result = 0.0,
            error = "Calcul personnalisé non implémenté",
            confidence = 0.5,
            accuracy = 0.5
        )
    }
    
    /**
     * Calcule le volume total des tiges.
     */
    private fun calculateTotalVolume(tiges: List<TigeEntity>): Double {
        return tiges.sumOf { tige ->
            val diameter = tige.diamCm
            val height = tige.hauteurM ?: 20.0
            // Formule de Schumacher-Hall
            exp(1.5 + 2.0 * ln(diameter) + 1.0 * ln(height))
        }
    }
    
    /**
     * Calcule l'écart-type.
     */
    private fun calculateStandardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }
    
    /**
     * Calcule le coefficient de corrélation de Pearson.
     */
    private fun calculatePearsonCorrelation(x: List<Double>, y: List<Double>): Double {
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
}
