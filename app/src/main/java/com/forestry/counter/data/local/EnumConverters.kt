package com.forestry.counter.data.local

import androidx.room.TypeConverter
import com.forestry.counter.data.local.entity.CalculationPriority
import com.forestry.counter.data.local.entity.CalculationStatus
import com.forestry.counter.data.local.entity.CalculationType
import com.forestry.counter.data.local.entity.CorrelationType
import com.forestry.counter.data.local.entity.EntityType
import com.forestry.counter.data.local.entity.InterpretationType
import com.forestry.counter.data.local.entity.Priority
import com.forestry.counter.data.local.entity.RelationDirection
import com.forestry.counter.data.local.entity.RelationType

/**
 * TypeConverters Room pour les enums des entités de la migration 30 -> 31.
 *
 * Stockage : chaque enum est persiste en TEXT (son nom) via [Enum.name].
 * La conversion inverse est defensive : toute valeur invalide presente en base
 * (renommage, suppression d'entry, corruption) retombe sur la premiere valeur
 * de l'enum plutot que de crasher l'application.
 *
 * Entites couvertes :
 * - DataCorrelationEntity      -> CorrelationType
 * - DataInterpretationEntity   -> InterpretationType, Priority
 * - EntityRelationEntity       -> EntityType, RelationType, RelationDirection
 * - AdvancedCalculationEntity  -> CalculationType, CalculationStatus, CalculationPriority
 */
class EnumConverters {

    // -- DataCorrelationEntity : CorrelationType --
    @TypeConverter
    fun fromCorrelationType(type: CorrelationType): String = type.name

    @TypeConverter
    fun toCorrelationType(value: String): CorrelationType =
        try {
            CorrelationType.valueOf(value)
        } catch (_: Exception) {
            CorrelationType.LINEAR
        }

    // -- DataInterpretationEntity : InterpretationType --
    @TypeConverter
    fun fromInterpretationType(type: InterpretationType): String = type.name

    @TypeConverter
    fun toInterpretationType(value: String): InterpretationType =
        try {
            InterpretationType.valueOf(value)
        } catch (_: Exception) {
            InterpretationType.GROWTH_ANALYSIS
        }

    // -- DataInterpretationEntity : Priority --
    @TypeConverter
    fun fromPriority(priority: Priority): String = priority.name

    @TypeConverter
    fun toPriority(value: String): Priority =
        try {
            Priority.valueOf(value)
        } catch (_: Exception) {
            Priority.CRITICAL
        }

    // -- EntityRelationEntity : EntityType --
    @TypeConverter
    fun fromEntityType(type: EntityType): String = type.name

    @TypeConverter
    fun toEntityType(value: String): EntityType =
        try {
            EntityType.valueOf(value)
        } catch (_: Exception) {
            EntityType.PARCELLE
        }

    // -- EntityRelationEntity : RelationType --
    @TypeConverter
    fun fromRelationType(type: RelationType): String = type.name

    @TypeConverter
    fun toRelationType(value: String): RelationType =
        try {
            RelationType.valueOf(value)
        } catch (_: Exception) {
            RelationType.PARENT_OF
        }

    // -- EntityRelationEntity : RelationDirection --
    @TypeConverter
    fun fromRelationDirection(direction: RelationDirection): String = direction.name

    @TypeConverter
    fun toRelationDirection(value: String): RelationDirection =
        try {
            RelationDirection.valueOf(value)
        } catch (_: Exception) {
            RelationDirection.UNIDIRECTIONAL
        }

    // -- AdvancedCalculationEntity : CalculationType --
    @TypeConverter
    fun fromCalculationType(type: CalculationType): String = type.name

    @TypeConverter
    fun toCalculationType(value: String): CalculationType =
        try {
            CalculationType.valueOf(value)
        } catch (_: Exception) {
            CalculationType.GROWTH_MODEL
        }

    // -- AdvancedCalculationEntity : CalculationStatus --
    @TypeConverter
    fun fromCalculationStatus(status: CalculationStatus): String = status.name

    @TypeConverter
    fun toCalculationStatus(value: String): CalculationStatus =
        try {
            CalculationStatus.valueOf(value)
        } catch (_: Exception) {
            CalculationStatus.PENDING
        }

    // -- AdvancedCalculationEntity : CalculationPriority --
    @TypeConverter
    fun fromCalculationPriority(priority: CalculationPriority): String = priority.name

    @TypeConverter
    fun toCalculationPriority(value: String): CalculationPriority =
        try {
            CalculationPriority.valueOf(value)
        } catch (_: Exception) {
            CalculationPriority.CRITICAL
        }
}
