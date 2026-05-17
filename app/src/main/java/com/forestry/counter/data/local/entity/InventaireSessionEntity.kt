package com.forestry.counter.data.local.entity

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
        Index(name = "index_sessions_typeSession", value = ["typeSession"])
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
    val createdAt: Long = System.currentTimeMillis()
)
