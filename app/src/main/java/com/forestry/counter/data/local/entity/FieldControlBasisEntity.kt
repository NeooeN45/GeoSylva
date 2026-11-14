package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Reconstruction du contrat de MIGRATION_46_47 ; le code d'origine n'a pas été récupéré. */
@Entity(
    tableName = "field_control_bases",
    foreignKeys = [ForeignKey(
        entity = FieldEntryEntity::class,
        parentColumns = ["id"],
        childColumns = ["controlEntryId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(
        name = "index_field_control_bases_ownerKey_objectId",
        value = ["ownerKey", "objectId"],
    )],
)
data class FieldControlBasisEntity(
    @PrimaryKey val controlEntryId: String,
    val ownerKey: String,
    val objectId: String,
    val depotId: String,
    val payload: String,
    val sha256: String,
)
