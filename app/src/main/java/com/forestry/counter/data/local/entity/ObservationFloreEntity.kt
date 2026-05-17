package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "observations_flore",
    foreignKeys = [
        ForeignKey(
            entity = ParcelleEntity::class,
            parentColumns = ["parcelleId"],
            childColumns = ["parcelleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PlacetteEntity::class,
            parentColumns = ["placetteId"],
            childColumns = ["placetteId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = InventaireSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(name = "index_flore_parcelleId", value = ["parcelleId"]),
        Index(name = "index_flore_placetteId", value = ["placetteId"]),
        Index(name = "index_flore_sessionId", value = ["sessionId"]),
        Index(name = "index_flore_codeEspece", value = ["codeEspece"])
    ]
)
data class ObservationFloreEntity(
    @PrimaryKey
    val observationId: String,
    val parcelleId: String,
    val placetteId: String?,
    val sessionId: String?,
    val codeEspece: String,
    val nomScientifique: String,
    val nomCommun: String?,
    val abundanceDominance: String,
    val strate: String,
    val sociabilite: Int?,
    val indicateurEllenbergL: Int?,
    val indicateurEllenbergT: Int?,
    val indicateurEllenbergR: Int?,
    val indicateurEllenbergF: Int?,
    val indicateurEllenbergN: Int?,
    val isEspeceProtegee: Boolean,
    val isEspeceIndicatrice: Boolean,
    val dateSaisie: Long,
    val createdAt: Long = System.currentTimeMillis()
)
