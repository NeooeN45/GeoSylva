package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "parcelles",
    indices = [
        Index(name = "index_parcelles_name", value = ["name"]),
        Index(name = "index_parcelles_forestOwnerId", value = ["forestOwnerId"])
    ]
)
data class ParcelleEntity(
    @PrimaryKey
    val parcelleId: String,
    val forestOwnerId: String?,
    val name: String,
    val surfaceHa: Double?,
    val shape: String?,
    val slopePct: Double?,
    val aspect: String?,
    val access: String?,
    val altitudeM: Double?,
    val commune: String?,
    val objectifType: String?,
    val objectifVal: Double?,
    val tolerancePct: Double?,
    val samplingMode: String?,        // e.g., CIRCULAR
    val sampleAreaM2: Double?,        // e.g., 2000
    val targetSpeciesCsv: String?,    // comma-separated essence codes
    val srid: Int?,
    val remarks: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
