package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "arbres_habitat",
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
        Index(name = "index_arbre_hab_parcelleId", value = ["parcelleId"]),
        Index(name = "index_arbre_hab_placetteId", value = ["placetteId"]),
        Index(name = "index_arbre_hab_sessionId", value = ["sessionId"])
    ]
)
data class ArbreHabitatEntity(
    @PrimaryKey
    val arbreHabitatId: String,
    val parcelleId: String,
    val placetteId: String?,
    val sessionId: String?,
    val tigeId: String?,
    val essenceCode: String,
    val diamCm: Double,
    val hauteurM: Double?,
    val gpsWkt: String?,

    // Microhabitats TreM (16 types)
    val cavitesBranches: Int,
    val cavitesTronc: Int,
    val logenBois: Int,
    val ecorceDecolleeM2: Double?,
    val epiphytesM2: Double?,
    val bioticBoss: Boolean,
    val dendrothelme: Boolean,
    val lianes: Boolean,
    val fissures: Boolean,
    val boisMortSurPied: Boolean,
    val boisMortSolM3: Double?,
    val treemScore: Int?,

    // Classification
    val classeDiamHabitat: String?,
    val isArbreVivant: Boolean,
    val isArbreRemarquable: Boolean,
    val remarques: String?,
    val dateObservation: Long
)
