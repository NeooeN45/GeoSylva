package com.forestry.counter.data.local.entity

import androidx.room.ColumnInfo
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
        ),
        ForeignKey(
            entity = InventaireSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(name = "index_tiges_parcelleOwnerId", value = ["parcelleOwnerId"]),
        Index(name = "index_tiges_placetteOwnerId", value = ["placetteOwnerId"]),
        Index(name = "index_tiges_essenceCode", value = ["essenceCode"]),
        Index(name = "index_tiges_diamCm", value = ["diamCm"]),
        Index(name = "index_tiges_sessionId", value = ["sessionId"]),
        Index(name = "index_tiges_uuid", value = ["uuid"], unique = true)
    ]
)
data class TigeEntity(
    @PrimaryKey
    val tigeId: String,
    /** UUID normalisé (RFC 4122) pour interop GSIE serveur — backfill asynchrone. */
    val uuid: String? = null,
    val parcelleOwnerId: String,
    val placetteOwnerId: String?,
    val sessionId: String?,
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

    // Sylviculture avancée
    val classeKraft: Int?,
    val etatSanitaire: String?,
    val vigueur: String?,
    val origine: String?,
    val typeCoupe: String?,

    // Biomasse & carbone (allométrie embarquée)
    val biomasseFusTonnes: Double?,
    val carboneFusTonnes: Double?,

    // Dendrométrie complémentaire
    val coefficientElancement: Double?,
    val houppierM: Double?,
    val houppierPct: Double?,
    val isTigeHabitat: Boolean,

    /** Original observation retained when a correction receives a new local ID. */
    val originTigeId: String? = null,

    // Metadata spec GeoSylva 3.0 (GEOSYLVA-003 §3.1)
    val deletedAt: Long? = null,
    val auteur: String? = null,
    val source: String? = null,
    @ColumnInfo(name = "version", defaultValue = "1")
    val version: Int = 1
)
