package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entité pour stocker les corrélations de données entre différentes entités.
 * Permet de créer des relations complexes et d'analyser les dépendances.
 */
@Entity(
    tableName = "data_correlations",
    foreignKeys = [
        ForeignKey(
            entity = ParcelleEntity::class,
            parentColumns = ["parcelleId"],
            childColumns = ["sourceParcelleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ParcelleEntity::class,
            parentColumns = ["parcelleId"],
            childColumns = ["targetParcelleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "index_correlations_source", value = ["sourceParcelleId"]),
        Index(name = "index_correlations_target", value = ["targetParcelleId"]),
        Index(name = "index_correlations_type", value = ["correlationType"]),
        Index(name = "index_correlations_strength", value = ["correlationStrength"])
    ]
)
data class DataCorrelationEntity(
    @PrimaryKey
    val correlationId: String,
    val sourceParcelleId: String,
    val targetParcelleId: String?,
    val correlationType: CorrelationType,
    val correlationStrength: Double, // 0.0 to 1.0
    val sourceDataType: String, // "tiges", "placettes", "essences", etc.
    val targetDataType: String?,
    val sourceField: String, // "diamCm", "hauteurM", etc.
    val targetField: String?,
    val correlationFormula: String?, // Formule mathématique de corrélation
    val confidenceLevel: Double, // 0.0 to 1.0
    val sampleSize: Int,
    val statisticalSignificance: Double, // p-value
    val metadata: String?, // JSON avec métadonnées supplémentaires
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Types de corrélations supportées.
 */
enum class CorrelationType {
    LINEAR,        // Corrélation linéaire
    EXPONENTIAL,   // Corrélation exponentielle
    LOGARITHMIC,   // Corrélation logarithmique
    POLYNOMIAL,    // Corrélation polynomiale
    TEMPORAL,      // Corrélation temporelle
    SPATIAL,       // Corrélation spatiale
    ENVIRONMENTAL, // Corrélation environnementale
    GENETIC,       // Corrélation génétique
    ECONOMIC,      // Corrélation économique
    CUSTOM         // Corrélation personnalisée
}
