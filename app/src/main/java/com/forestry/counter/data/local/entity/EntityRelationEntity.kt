package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entité pour stocker les relations complexes entre différentes entités forestières.
 * Permet de modéliser des relations hiérarchiques, spatiales, temporelles et fonctionnelles.
 */
@Entity(
    tableName = "entity_relations",
    foreignKeys = [
        ForeignKey(
            entity = ParcelleEntity::class,
            parentColumns = ["parcelleId"],
            childColumns = ["sourceEntityId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "index_relations_source", value = ["sourceEntityId"]),
        Index(name = "index_relations_target", value = ["targetEntityId"]),
        Index(name = "index_relations_type", value = ["relationType"]),
        Index(name = "index_relations_strength", value = ["relationStrength"]),
        Index(name = "index_relations_active", value = ["isActive"])
    ]
)
data class EntityRelationEntity(
    @PrimaryKey
    val relationId: String,
    val sourceEntityId: String,
    val sourceEntityType: EntityType,
    val targetEntityId: String,
    val targetEntityType: EntityType,
    val relationType: RelationType,
    val relationStrength: Double, // 0.0 to 1.0
    val direction: RelationDirection,
    val attributes: String?, // JSON avec attributs de la relation
    val conditions: String?, // Conditions pour que la relation soit active
    val isActive: Boolean,
    val validFrom: Long,
    val validUntil: Long?,
    val metadata: String?, // Métadonnées supplémentaires
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Types d'entités supportées dans le système.
 */
enum class EntityType {
    PARCELLE,       // Parcelle forestière
    PLACETTE,       // Placette d'échantillonnage
    TIGE,          // Arbre individuel
    ESSENCE,       // Essence forestière
    GROUPE,        // Groupe de données
    FORMULE,       // Formule de calcul
    PARAMETRE,     // Paramètre environnemental
    EVALUATION,    // Évaluation IBP
    CORRELATION,   // Corrélation de données
    INTERPRETATION, // Interprétation de données
    UTILISATEUR,   // Utilisateur
    SESSION,       // Session de travail
    DOCUMENT,      // Document ou rapport
    PHOTO,         // Photo ou image
    MESURE,        // Mesure spécifique
    OBSERVATION,   // Observation terrain
    CUSTOM         // Entité personnalisée
}

/**
 * Types de relations entre entités.
 */
enum class RelationType {
    // Relations hiérarchiques
    PARENT_OF,      // Relation parent-enfant
    CHILD_OF,       // Relation enfant-parent
    CONTAINS,       // Relation de contenance
    PART_OF,        // Relation de partie
    
    // Relations spatiales
    ADJACENT_TO,    // Adjacence spatiale
    WITHIN,         // À l'intérieur de
    NEAR,          // Proximité
    OVERLAPS,      // Chevauchement
    
    // Relations temporelles
    PRECEDES,      // Précède temporellement
    FOLLOWS,       // Suit temporellement
    CONCURRENT,    // Concurrent temporellement
    
    // Relations fonctionnelles
    DEPENDS_ON,    // Dépendance fonctionnelle
    INFLUENCES,    // Influence
    CAUSES,        // Causalité
    ENABLES,       // Permet/active
    
    // Relations de similarité
    SIMILAR_TO,    // Similarité
    EQUIVALENT_TO, // Équivalence
    RELATED_TO,    // Relation générique
    
    // Relations spécifiques forestières
    GROWS_IN,      // Pousse dans
    COMPETES_WITH, // Compétition
    ASSOCIATED_WITH, // Association écologique
    PREDICTS,      // Prédiction
    
    // Relations de données
    DERIVED_FROM,  // Dérivé de
    VALIDATES,     // Valide
    CONTRADICTS,   // Contredit
    SUPPORTS,      // Supporte
    
    CUSTOM         // Relation personnalisée
}

/**
 * Direction de la relation.
 */
enum class RelationDirection {
    UNIDIRECTIONAL, // Unidirectionnelle (A -> B)
    BIDIRECTIONAL, // Bidirectionnelle (A <-> B)
    REVERSIBLE     // Réversible (peut changer de direction)
}
