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
        Index(name = "index_placettes_sessionId", value = ["sessionId"]),
        Index(name = "index_placettes_uuid", value = ["uuid"], unique = true)
    ]
)
data class PlacetteEntity(
    @PrimaryKey
    val placetteId: String,
    /** UUID normalisé (RFC 4122) pour interop GSIE serveur — backfill asynchrone. */
    val uuid: String? = null,
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
    val updatedAt: Long = System.currentTimeMillis(),

    // Metadata spec GeoSylva 3.0 (GEOSYLVA-003 §3.1)
    val deletedAt: Long? = null,
    val auteur: String? = null,
    val source: String? = null,
    @ColumnInfo(name = "version", defaultValue = "1")
    val version: Int = 1,
    val samplingPlanJson: String? = null,
    val footprintGeoJson: String? = null,
)
