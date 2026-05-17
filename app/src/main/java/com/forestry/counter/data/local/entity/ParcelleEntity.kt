package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "parcelles",
    indices = [
        Index(name = "index_parcelles_name", value = ["name"]),
        Index(name = "index_parcelles_forestOwnerId", value = ["forestOwnerId"]),
        Index(name = "index_parcelles_foretId", value = ["foretId"]),
        Index(name = "index_parcelles_codeInsee", value = ["codeInseeCommune"])
    ]
)
data class ParcelleEntity(
    @PrimaryKey
    val parcelleId: String,
    val forestOwnerId: String?,
    val foretId: String?,
    val name: String,
    val surfaceHa: Double?,
    val shape: String?,
    val slopePct: Double?,
    val aspect: String?,
    val access: String?,
    val altitudeM: Double?,
    val objectifType: String?,
    val objectifVal: Double?,
    val tolerancePct: Double?,
    val samplingMode: String?,
    val sampleAreaM2: Double?,
    val targetSpeciesCsv: String?,
    val srid: Int?,
    val remarks: String?,

    // Données cadastrales (IGN reverse géocodage)
    val codeInseeCommune: String?,
    val nomCommune: String?,
    val sectionCadastrale: String?,
    val numeroCadastral: String?,
    val contenanceCadastraleHa: Double?,
    val geometrieIgnWkt: String?,
    val natureCadastraleCode: String?,
    val localisationMode: String?,

    // SylvoÉcoRégion (IFN/IGN)
    val codeSer: String?,
    val nomSer: String?,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
