package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ibp_evaluations",
    foreignKeys = [
        ForeignKey(
            entity = PlacetteEntity::class,
            parentColumns = ["placetteId"],
            childColumns = ["placetteId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ParcelleEntity::class,
            parentColumns = ["parcelleId"],
            childColumns = ["parcelleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "index_ibp_placetteId", value = ["placetteId"]),
        Index(name = "index_ibp_parcelleId", value = ["parcelleId"])
    ]
)
data class IbpEvaluationEntity(
    @PrimaryKey val id: String,
    val placetteId: String,
    val parcelleId: String,
    val observationDate: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val evaluatorName: String = "",
    val answersJson: String = "{}",
    val globalNote: String = "",
    val growthConditions: String = "LOWLAND",
    val ibpMode: String = "COMPLET",
    val latitude: Double? = null,
    val longitude: Double? = null
)
