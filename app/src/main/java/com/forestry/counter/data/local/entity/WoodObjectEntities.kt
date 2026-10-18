package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "wood_objects", foreignKeys = [ForeignKey(entity = WoodDepotEntity::class,
    parentColumns = ["depotId"], childColumns = ["depotId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["depotId"]), Index(value = ["ownerScope", "ownerAccountId", "archivedAt"])])
data class WoodObjectEntity(
    @PrimaryKey val objectId: String,
    val depotId: String,
    val ownerScope: String,
    val ownerAccountId: String?,
    val kind: String,
    val headerJson: String,
    val headerSha256: String,
    val revision: Int,
    val createdAt: Long,
    val createdBy: String,
    val updatedAt: Long,
    val updatedBy: String,
    val archivedAt: Long?,
)

@Entity(tableName = "wood_observations", foreignKeys = [
    ForeignKey(entity = WoodObjectEntity::class, parentColumns = ["objectId"], childColumns = ["objectId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = WoodObservationEntity::class, parentColumns = ["objectId", "observationId"],
        childColumns = ["objectId", "rootObservationId"], deferred = true),
    ForeignKey(entity = WoodObservationEntity::class, parentColumns = ["objectId", "observationId"],
        childColumns = ["objectId", "correctionOfObservationId"], deferred = true),
], indices = [Index(value = ["objectId", "observationId"], unique = true),
    Index(value = ["objectId", "rootObservationId", "observationRevision"], unique = true),
    Index(value = ["correctionOfObservationId"], unique = true),
    Index(value = ["objectId", "correctionOfObservationId"])])
data class WoodObservationEntity(
    @PrimaryKey val observationId: String,
    val objectId: String,
    val rootObservationId: String,
    val observationRevision: Int,
    val correctionOfObservationId: String?,
    val correctionReason: String?,
    val payloadJson: String,
    val payloadSha256: String,
    val recordedAt: Long,
    val recordedZoneId: String,
    val recordedOffsetSeconds: Int,
    val recordedBy: String,
)

@Entity(tableName = "wood_effective_observations", foreignKeys = [
    ForeignKey(entity = WoodObjectEntity::class, parentColumns = ["objectId"], childColumns = ["objectId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = WoodObservationEntity::class, parentColumns = ["objectId", "observationId"],
        childColumns = ["objectId", "observationId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index(value = ["objectId", "observationId"])])
data class WoodEffectiveObservationEntity(
    @PrimaryKey val objectId: String,
    val observationId: String,
    val selectedAt: Long,
    val selectedBy: String,
    val commandId: String,
)

@Entity(tableName = "wood_commands", foreignKeys = [
    ForeignKey(entity = WoodObjectEntity::class, parentColumns = ["objectId"], childColumns = ["objectId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = WoodDepotEntity::class, parentColumns = ["depotId"], childColumns = ["depotId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index(value = ["objectId"]), Index(value = ["depotId"])])
data class WoodCommandEntity(
    @PrimaryKey val commandId: String,
    val depotId: String,
    val objectId: String,
    val ownerScope: String,
    val ownerAccountId: String?,
    val actorId: String,
    val commandJson: String,
    val commandSha256: String,
    val appliedObjectRevision: Int,
    val createdObservationId: String?,
    val effectiveObservationId: String?,
    val appliedAt: Long,
)
