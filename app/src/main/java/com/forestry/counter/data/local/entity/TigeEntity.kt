package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tiges",
    foreignKeys = [
        ForeignKey(
            entity = ParcelleEntity::class,
            parentColumns = ["parcelleId"],
            childColumns = ["parcelleOwnerId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PlacetteEntity::class,
            parentColumns = ["placetteId"],
            childColumns = ["placetteOwnerId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = EssenceEntity::class,
            parentColumns = ["code"],
            childColumns = ["essenceCode"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(name = "index_tiges_parcelleOwnerId", value = ["parcelleOwnerId"]),
        Index(name = "index_tiges_placetteOwnerId", value = ["placetteOwnerId"]),
        Index(name = "index_tiges_essenceCode", value = ["essenceCode"]),
        Index(name = "index_tiges_diamCm", value = ["diamCm"])
    ]
)
data class TigeEntity(
    @PrimaryKey
    val tigeId: String,
    val parcelleOwnerId: String,
    val placetteOwnerId: String?,
    val essenceCode: String,
    val diamCm: Double,
    val hauteurM: Double?,
    val gpsWkt: String?,
    val precisionM: Double?,
    val altitudeM: Double?,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String?,
    val produit: String?,
    val fCoef: Double?,
    val valueEur: Double?,
    val numero: Int?,
    val categorie: String?,
    val qualite: Int?,
    val defauts: String?,
    val photoUri: String?,
    val qualiteDetail: String?,
    val destination: String?
)
