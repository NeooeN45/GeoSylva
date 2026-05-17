package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entité pour stocker les calculs avancés et leurs résultats.
 * Permet de modéliser des calculs complexes avec dépendances et optimisation.
 */
@Entity(
    tableName = "advanced_calculations",
    foreignKeys = [
        ForeignKey(
            entity = ParcelleEntity::class,
            parentColumns = ["parcelleId"],
            childColumns = ["parcelleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "index_calculations_parcelle", value = ["parcelleId"]),
        Index(name = "index_calculations_type", value = ["calculationType"]),
        Index(name = "index_calculations_status", value = ["status"]),
        Index(name = "index_calculations_priority", value = ["priority"])
    ]
)
data class AdvancedCalculationEntity(
    @PrimaryKey
    val calculationId: String,
    val parcelleId: String?,
    val calculationType: CalculationType,
    val name: String,
    val description: String,
    val formula: String, // Formule mathématique ou algorithme
    val variables: String?, // JSON avec variables et leurs valeurs
    val parameters: String?, // JSON avec paramètres du calcul
    val dependencies: String?, // JSON avec dépendances à d'autres calculs
    val result: Double?, // Résultat du calcul
    val resultMetadata: String?, // JSON avec métadonnées du résultat
    val status: CalculationStatus,
    val priority: CalculationPriority,
    val executionTime: Long?, // Temps d'exécution en ms
    val accuracy: Double?, // Précision du calcul (0.0 to 1.0)
    val confidence: Double?, // Confiance dans le résultat (0.0 to 1.0)
    val error: String?, // Message d'erreur si échec
    val optimizationHints: String?, // Suggestions d'optimisation
    val validUntil: Long?, // Période de validité du résultat
    val tags: String?, // Tags pour classification
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Types de calculs avancés supportés.
 */
enum class CalculationType {
    // Calculs de croissance et rendement
    GROWTH_MODEL,           // Modèle de croissance
    YIELD_PREDICTION,       // Prédiction de rendement
    VOLUME_CALCULATION,      // Calcul de volume
    BIOMASS_ESTIMATION,     // Estimation de biomasse
    CARBON_SEQUESTRATION,   // Séquestration de carbone
    
    // Calculs économiques
    ECONOMIC_VALUATION,     // Valorisation économique
    COST_BENEFIT_ANALYSIS,  // Analyse coût-bénéfice
    MARKET_PRICE_PREDICTION, // Prédiction de prix de marché
    INVESTMENT_RETURN,      // Retour sur investissement
    
    // Calculs environnementaux
    ENVIRONMENTAL_IMPACT,   // Impact environnemental
    BIODIVERSITY_INDEX,     // Indice de biodiversité
    SOIL_QUALITY_INDEX,     // Indice de qualité de sol
    WATER_REQUIREMENTS,      // Besoins en eau
    
    // Calculs de risque et résilience
    RISK_ASSESSMENT,        // Évaluation des risques
    CLIMATE_RESILIENCE,     // Résilience climatique
    DISEASE_PREDICTION,     // Prédiction de maladies
    PEST_DAMAGE_MODEL,      // Modèle de dégâts de ravageurs
    
    // Calculs sylvicoles
    THINNING_OPTIMIZATION,  // Optimisation des éclaircies
    HARVEST_SCHEDULING,     // Planification des récoltes
    STAND_DENSITY_INDEX,    // Indice de densité de peuplement
    COMPETITION_INDEX,      // Indice de compétition
    
    // Calculs statistiques
    STATISTICAL_ANALYSIS,   // Analyse statistique
    CORRELATION_ANALYSIS,   // Analyse de corrélation
    REGRESSION_ANALYSIS,    // Analyse de régression
    TIME_SERIES_FORECAST,   // Prévision de séries temporelles
    
    // Calculs d'IA et machine learning
    NEURAL_NETWORK,         // Réseau neuronal
    DECISION_TREE,          // Arbre de décision
    CLUSTERING,             // Clustering
    ANOMALY_DETECTION,      // Détection d'anomalies
    
    // Calculs spatiaux
    SPATIAL_ANALYSIS,       // Analyse spatiale
    BUFFER_ANALYSIS,        // Analyse de buffer
    INTERPOLATION,          // Interpolation spatiale
    HOTSPOT_DETECTION,      // Détection de points chauds
    
    CUSTOM                  // Calcul personnalisé
}

/**
 * Statuts des calculs.
 */
enum class CalculationStatus {
    PENDING,        // En attente d'exécution
    RUNNING,        // En cours d'exécution
    COMPLETED,      // Terminé avec succès
    FAILED,         // Échec
    CANCELLED,      // Annulé
    OPTIMIZING,     // En cours d'optimisation
    VALIDATING,     // En cours de validation
    ARCHIVED        // Archivé
}

/**
 * Priorités des calculs.
 */
enum class CalculationPriority {
    CRITICAL,       // Exécution immédiate
    HIGH,           // Haute priorité
    MEDIUM,         // Priorité normale
    LOW,            // Basse priorité
    BACKGROUND      // Arrière-plan
}
