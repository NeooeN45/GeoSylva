package com.forestry.counter.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "placettes",
    foreignKeys = [
        ForeignKey(
            entity = ParcelleEntity::class,
            parentColumns = ["parcelleId"],
            childColumns = ["parcelleOwnerId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = InventaireSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(name = "index_placettes_parcelleOwnerId", value = ["parcelleOwnerId"]),
        Index(name = "index_placettes_sessionId", value = ["sessionId"])
    ]
)
data class PlacetteEntity(
    @PrimaryKey
    val placetteId: String,
    val parcelleOwnerId: String,
    val name: String?,
    val type: String?,
    val rayonM: Double?,
    val surfaceM2: Double?,
    val centerWkt: String?,
    val sessionId: String?,
    val typeReleve: String?,
    val referenceGpsWkt: String?,
    val azimutRef: Double?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
