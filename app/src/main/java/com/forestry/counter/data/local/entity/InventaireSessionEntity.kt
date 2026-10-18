package com.forestry.counter.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventaire_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ParcelleEntity::class,
            parentColumns = ["parcelleId"],
            childColumns = ["parcelleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "index_sessions_parcelleId", value = ["parcelleId"]),
        Index(name = "index_sessions_typeSession", value = ["typeSession"]),
        Index(name = "index_sessions_placetteId", value = ["placetteId"])
    ]
)
data class InventaireSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val parcelleId: String,
    val typeSession: String,
    val dateDebut: Long,
    val dateFin: Long?,
    val operateurNom: String?,
    val methode: String?,
    val intensiteEchantillonnagePct: Double?,
    val objectifSession: String?,
    val remarques: String?,
    val createdAt: Long = System.currentTimeMillis(),

    val martelageState: String? = null,
    @ColumnInfo(defaultValue = "0")
    val activeDurationMs: Long = 0L,
    val segmentStartedElapsedMs: Long? = null,
    val segmentBootCount: Int? = null,
    val campaignId: String? = null,
    @ColumnInfo(defaultValue = "1")
    val revisionNumber: Int = 1,
    val previousSessionId: String? = null,
    val closedSnapshotJson: String? = null,
    @ColumnInfo(defaultValue = "0")
    val recoveryRequired: Boolean = false,

    // Metadata spec GeoSylva 3.0 (GEOSYLVA-003 §3.1)
    val deletedAt: Long? = null,
    val auteur: String? = null,
    val source: String? = null,
    @ColumnInfo(name = "version", defaultValue = "1")
    val version: Int = 1,
    val placetteId: String? = null,
    val traceJson: String? = null,
)
