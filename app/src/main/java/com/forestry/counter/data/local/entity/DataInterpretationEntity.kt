package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entité pour stocker les interprétations et analyses des données forestières.
 * Permet de générer des insights et recommandations basées sur les données.
 */
@Entity(
    tableName = "data_interpretations",
    foreignKeys = [
        ForeignKey(
            entity = ParcelleEntity::class,
            parentColumns = ["parcelleId"],
            childColumns = ["parcelleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "index_interpretations_parcelle", value = ["parcelleId"]),
        Index(name = "index_interpretations_type", value = ["interpretationType"]),
        Index(name = "index_interpretations_confidence", value = ["confidenceScore"]),
        Index(name = "index_interpretations_priority", value = ["priority"])
    ]
)
data class DataInterpretationEntity(
    @PrimaryKey
    val interpretationId: String,
    val parcelleId: String,
    val interpretationType: InterpretationType,
    val title: String,
    val description: String,
    val confidenceScore: Double, // 0.0 to 1.0
    val priority: Priority,
    val dataSource: String, // "tiges", "placettes", "environnement", etc.
    val analysisMethod: String, // "statistical", "ml", "expert_system", etc.
    val parameters: String?, // JSON avec paramètres d'analyse
    val results: String?, // JSON avec résultats détaillés
    val recommendations: String?, // Recommandations basées sur l'analyse
    val actionable: Boolean, // Si l'interprétation peut mener à une action
    val validUntil: Long?, // Période de validité de l'interprétation
    val tags: String?, // Tags pour classification
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Types d'interprétations disponibles.
 */
enum class InterpretationType {
    GROWTH_ANALYSIS,      // Analyse de croissance
    VITALITY_ASSESSMENT,  // Évaluation de vitalité
    HEALTH_DIAGNOSIS,     // Diagnostic de santé
    YIELD_PREDICTION,     // Prédiction de rendement
    RISK_ASSESSMENT,      // Évaluation des risques
    ENVIRONMENTAL_IMPACT, // Impact environnemental
    ECONOMIC_VALUATION,   // Valorisation économique
    SILVICULTURAL_RECOMMENDATION, // Recommandations sylvicoles
    BIODIVERSITY_INDEX,   // indice de biodiversité
    CARBON_SEQUESTRATION, // Séquestration de carbone
    SOIL_ANALYSIS,        // Analyse de sol
    CLIMATE_RESILIENCE,   // Résilience climatique
    MARKET_ANALYSIS,      // Analyse de marché
    COMPLIANCE_CHECK,     // Vérification de conformité
    CUSTOM                // Interprétation personnalisée
}

/**
 * Niveaux de priorité pour les interprétations.
 */
enum class Priority {
    CRITICAL,  // Action immédiate requise
    HIGH,      // Action recommandée bientôt
    MEDIUM,    // Action à considérer
    LOW,       // Information uniquement
    INFO       // Purement informatif
}
