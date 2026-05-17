package com.forestry.counter.domain.calculation

import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.repository.ParameterRepository
import kotlin.math.*

/**
 * Calculateur forestier amélioré avec intégration scientifique
 * Étend les fonctionnalités existantes avec les modèles ONF/ENGREF
 */
class EnhancedForestryCalculator(
    private val baseCalculator: ForestryCalculator,
    private val expertCalculator: ExpertForestryCalculator,
    private val parameterRepository: ParameterRepository
) {
    
    /**
     * Calcule le volume avec méthode scientifique优先
     * Utilise Schumacher-Hall validé si disponible, sinon fallback sur système existant
     */
    suspend fun computeEnhancedVolume(
        essenceCode: String,
        diamCm: Double,
        hauteurM: Double?
    ): Double? {
        // Priorité au modèle scientifique Schumacher-Hall
        if (hauteurM != null && hauteurM > 0) {
            return try {
                expertCalculator.schumacherHallVolume(essenceCode, diamCm, hauteurM)
            } catch (e: Exception) {
                // Fallback sur calculateur existant
                baseCalculator.computeV(essenceCode, diamCm, hauteurM)
            }
        }
        
        return baseCalculator.computeV(essenceCode, diamCm, hauteurM)
    }
    
    /**
     * Analyse experte d'un peuplement
     * Combine les calculs existants avec l'analyse scientifique
     */
    suspend fun analyzePeuplement(
        tiges: List<Tige>,
        surfaceM2: Double,
        essenceCode: String,
        ageEstime: Int
    ): EnhancedPeuplementAnalysis? {
        if (tiges.isEmpty() || surfaceM2 <= 0) return null
        
        // Calculs de base existants
        val baseStats = computeMartelageStats(
            tigesInScope = tiges,
            surfaceM2 = surfaceM2,
            selectedEssenceCodes = setOf(essenceCode),
            martelageHeights = emptyMap(),
            synthesisParams = null,
            diameterClasses = (5..120 step 5).toList(),
            essences = emptyList(), // À charger depuis repository
            forestryCalculator = baseCalculator
        ) ?: return null
        
        // Analyse scientifique experte
        val diametres = tiges.map { it.diamCm }
        val diametreMoyen = diametres.average()
        val hauteurs = tiges.mapNotNull { it.hauteurM }
        val hauteurMoyenne = hauteurs.average().takeIf { hauteurs.isNotEmpty() } ?: 20.0
        
        val indiceStation = expertCalculator.calculateIndiceDeStation(
            essenceCode = essenceCode,
            age = ageEstime,
            hauteurMoyenne = hauteurMoyenne,
            diametreMoyen = diametreMoyen
        )
        
        val classeStation = expertCalculator.getClasseStation(indiceStation, essenceCode)
        val fertilité = expertCalculator.evaluateFertilityClass(indiceStation, essenceCode)
        
        // Comparaison avec les tables ONF
        val productionData = expertCalculator.getProductionData(essenceCode, classeStation, ageEstime)
        val conformiteONF = productionData?.let { prod ->
            ProductionConformity(
                hauteurConforme = abs(hauteurMoyenne - prod.hauteurMoyenne) / prod.hauteurMoyenne < 0.15,
                diametreConforme = abs(diametreMoyen - prod.diametreMoyen) / prod.diametreMoyen < 0.15,
                volumeConforme = baseStats.vPerHa.let { actual ->
                    prod.volumeTotal.let { theo -> abs(actual - theo) / theo < 0.20 }
                }
            )
        }
        
        // Recommandations sylvicoles
        val recommendations = expertCalculator.generateSylvicultureRecommendations(
            essenceCode = essenceCode,
            classeStation = classeStation,
            classeFertilite = fertilité,
            ageActuel = ageEstime,
            diametreMoyen = diametreMoyen
        )
        
        return EnhancedPeuplementAnalysis(
            baseStats = baseStats,
            indiceStation = indiceStation,
            classeStation = classeStation,
            fertilité = fertilité,
            productionData = productionData,
            conformiteONF = conformiteONF,
            recommendations = recommendations,
            diagnostic = generateDiagnostic(baseStats, fertilité, conformiteONF)
        )
    }
    
    /**
     * Calcule les paramètres optimaux de plantation
     */
    fun calculateOptimalPlantingParameters(
        essenceCode: String,
        classeStation: Int,
        surfaceHa: Double
    ): PlantingParameters {
        val fertilité = expertCalculator.evaluateFertilityClass(
            expertCalculator.calculateIndiceDeStation(essenceCode, 0, 20.0, 15.0),
            essenceCode
        )
        
        val recommendations = expertCalculator.generateSylvicultureRecommendations(
            essenceCode = essenceCode,
            classeStation = classeStation,
            classeFertilite = fertilité,
            ageActuel = 0,
            diametreMoyen = 0.0
        )
        
        return PlantingParameters(
            densiteOptimale = recommendations.densitePlantation,
            nombreTotalTiges = (recommendations.densitePlantation * surfaceHa).toInt(),
            espacementRecommande = calculateEspacement(recommendations.densitePlantation),
            essencesAssociees = recommendations.essencesOptimales,
            agePremiereEclaircie = recommendations.agePremiereEclaircie,
            diametreExploitation = recommendations.diametreExploitationOptimal
        )
    }
    
    /**
     * Simule la croissance future du peuplement
     */
    fun simulateGrowth(
        essenceCode: String,
        classeStation: Int,
        ageActuel: Int,
        ageCible: Int,
        diametreActuel: Double
    ): GrowthSimulation {
        val croissanceActuelle = expertCalculator.richardsGrowthModel(essenceCode, ageActuel, classeStation)
        val croissanceFuture = expertCalculator.richardsGrowthModel(essenceCode, ageCible, classeStation)
        
        val accroissementTotal = croissanceFuture - croissanceActuelle
        val accroissementAnnuelMoyen = accroissementTotal / (ageCible - ageActuel)
        
        // Prédiction de volume
        val hauteurActuelle = estimateHauteurFromDiameter(diametreActuel, essenceCode)
        val hauteurFuture = estimateHauteurFromDiameter(croissanceFuture, essenceCode)
        
        val volumeActuel = expertCalculator.schumacherHallVolume(essenceCode, diametreActuel, hauteurActuelle)
        val volumeFuture = expertCalculator.schumacherHallVolume(essenceCode, croissanceFuture, hauteurFuture)
        
        return GrowthSimulation(
            diametreActuel = diametreActuel,
            diametreFuture = croissanceFuture,
            diametreAccroissement = accroissementTotal,
            hauteurActuelle = hauteurActuelle,
            hauteurFuture = hauteurFuture,
            volumeActuel = volumeActuel,
            volumeFuture = volumeFuture,
            volumeAccroissement = volumeFuture - volumeActuel,
            accroissementAnnuelMoyen = accroissementAnnuelMoyen
        )
    }
    
    /**
     * Évalue la qualité du peuplement selon les critères ONF
     */
    fun evaluatePeuplementQuality(
        tiges: List<Tige>,
        surfaceM2: Double,
        essenceCode: String,
        ageEstime: Int
    ): QualityAssessment {
        val diametres = tiges.map { it.diamCm }
        val diametreMoyen = diametres.average()
        val cvDiametre = if (diametres.isNotEmpty()) {
            val variance = diametres.sumOf { (it - diametreMoyen) * (it - diametreMoyen) } / diametres.size
            sqrt(variance) / diametreMoyen * 100
        } else 0.0
        
        val surfaceTerriere = expertCalculator.computeSurfaceTerriere(diametres)
        val surfaceHa = surfaceM2 / 10000.0
        val gPerHa = surfaceTerriere / surfaceHa
        
        // Qualité selon normes ONF
        val scoreDensite = when {
            gPerHa < 15 -> 1.0
            gPerHa < 25 -> 2.0
            gPerHa < 35 -> 3.0
            gPerHa < 45 -> 4.0
            else -> 5.0
        }
        
        val scoreUniformite = when {
            cvDiametre > 40 -> 1.0
            cvDiametre > 30 -> 2.0
            cvDiametre > 20 -> 3.0
            cvDiametre > 10 -> 4.0
            else -> 5.0
        }
        
        val scoreVitalite = calculateVitaliteScore(tiges, ageEstime, essenceCode)
        
        val scoreGlobal = (scoreDensite + scoreUniformite + scoreVitalite) / 3.0
        
        return QualityAssessment(
            scoreGlobal = scoreGlobal,
            scoreDensite = scoreDensite,
            scoreUniformite = scoreUniformite,
            scoreVitalite = scoreVitalite,
            qualite = when {
                scoreGlobal >= 4.5 -> "Excellente"
                scoreGlobal >= 3.5 -> "Bonne"
                scoreGlobal >= 2.5 -> "Moyenne"
                scoreGlobal >= 1.5 -> "Médiocre"
                else -> "Mauvaise"
            },
            recommandations = generateQualityRecommendations(scoreDensite, scoreUniformite, scoreVitalite)
        )
    }
    
    // Méthodes utilitaires privées
    private fun generateDiagnostic(
        baseStats: MartelageStats,
        fertilité: com.forestry.counter.domain.calculation.FertilityClass,
        conformite: ProductionConformity?
    ): String {
        val diagnostic = mutableListOf<String>()
        
        // Diagnostic de fertilité
        diagnostic.add(when (fertilité.classe) {
            1, 2 -> "Station de faible fertilité - croissance limitée"
            3 -> "Station de fertilité moyenne - croissance équilibrée"
            4, 5 -> "Station de haute fertilité - excellent potentiel"
            else -> "Station non évaluée"
        })
        
        // Diagnostic de conformité ONF
        conformite?.let { conf ->
            val conformiteScore = listOf(conf.hauteurConforme, conf.diametreConforme, conf.volumeConforme).count { it }
            diagnostic.add(when (conformiteScore) {
                3 -> "Peuplement conforme aux normes ONF"
                2 -> "Peuplement légèrement en décalage avec les normes ONF"
                1, 0 -> "Peuplement significativement différent des normes ONF"
                else -> ""
            })
        }
        
        // Diagnostic de densité
        diagnostic.add(when {
            baseStats.gPerHa < 20 -> "Peuplement sous-peuplé - croissance individuelle favorisée"
            baseStats.gPerHa > 40 -> "Peuplement sur-peuplé - compétition élevée"
            else -> "Peuplement bien équilibré"
        })
        
        return diagnostic.joinToString(" | ")
    }
    
    private fun calculateEspacement(densite: Int): Double {
        // Espacement en mètres (carré)
        return sqrt(10000.0 / densite)
    }
    
    private fun estimateHauteurFromDiameter(diametre: Double, essenceCode: String): Double {
        // Relations hauteur/diamètre simplifiées selon essence
        return when (essenceCode.uppercase()) {
            "QUPE", "QUPES", "QUPU", "QUIL", "QURU" -> 1.2 * diametre.pow(0.6)
            "FASY" -> 1.5 * diametre.pow(0.5)
            "ABAL" -> 1.8 * diametre.pow(0.55)
            else -> 1.3 * diametre.pow(0.55)
        }.coerceIn(5.0, 50.0)
    }
    
    private fun calculateVitaliteScore(tiges: List<Tige>, age: Int, essenceCode: String): Double {
        if (tiges.isEmpty()) return 2.5
        
        val diametreMoyen = tiges.map { it.diamCm }.average()
        val diametreTheorique = expertCalculator.richardsGrowthModel(essenceCode, age, 3)
        
        val ratio = diametreMoyen / diametreTheorique
        
        return when {
            ratio >= 1.2 -> 5.0
            ratio >= 1.0 -> 4.0
            ratio >= 0.8 -> 3.0
            ratio >= 0.6 -> 2.0
            else -> 1.0
        }
    }
    
    private fun generateQualityRecommendations(
        scoreDensite: Double,
        scoreUniformite: Double,
        scoreVitalite: Double
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (scoreDensite < 3.0) {
            recommendations.add("Considérer un complémentage ou une densité de plantation plus élevée")
        } else if (scoreDensite > 4.0) {
            recommendations.add("Prévoir des éclaircies précoces pour réduire la compétition")
        }
        
        if (scoreUniformite < 3.0) {
            recommendations.add("Améliorer l'uniformité par des éclaircies sélectives")
        }
        
        if (scoreVitalite < 3.0) {
            recommendations.add("Investiguer les facteurs limitant la croissance (sol, climat, densité)")
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Peuplement de bonne qualité - maintenir les pratiques actuelles")
        }
        
        return recommendations
    }
}

// Classes de données pour les analyses améliorées
data class EnhancedPeuplementAnalysis(
    val baseStats: MartelageStats,
    val indiceStation: Double,
    val classeStation: Int,
    val fertilité: com.forestry.counter.domain.calculation.FertilityClass,
    val productionData: com.forestry.counter.domain.calculation.ProductionData?,
    val conformiteONF: ProductionConformity?,
    val recommendations: com.forestry.counter.domain.calculation.SylvicultureRecommendations,
    val diagnostic: String
)

data class ProductionConformity(
    val hauteurConforme: Boolean,
    val diametreConforme: Boolean,
    val volumeConforme: Boolean
)

data class PlantingParameters(
    val densiteOptimale: Int,
    val nombreTotalTiges: Int,
    val espacementRecommande: Double,
    val essencesAssociees: List<String>,
    val agePremiereEclaircie: Int,
    val diametreExploitation: Double
)

data class GrowthSimulation(
    val diametreActuel: Double,
    val diametreFuture: Double,
    val diametreAccroissement: Double,
    val hauteurActuelle: Double,
    val hauteurFuture: Double,
    val volumeActuel: Double,
    val volumeFuture: Double,
    val volumeAccroissement: Double,
    val accroissementAnnuelMoyen: Double
)

data class QualityAssessment(
    val scoreGlobal: Double,
    val scoreDensite: Double,
    val scoreUniformite: Double,
    val scoreVitalite: Double,
    val qualite: String,
    val recommandations: List<String>
)
