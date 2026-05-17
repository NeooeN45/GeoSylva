package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "diagnostics_sylvicoles",
    foreignKeys = [
        ForeignKey(
            entity = ParcelleEntity::class,
            parentColumns = ["parcelleId"],
            childColumns = ["parcelleId"],
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
        Index(name = "index_diag_parcelleId", value = ["parcelleId"]),
        Index(name = "index_diag_sessionId", value = ["sessionId"])
    ]
)
data class DiagnosticSylvicoleEntity(
    @PrimaryKey
    val diagnosticId: String,
    val parcelleId: String,
    val sessionId: String?,
    val dateCreation: Long,
    val operateurNom: String?,

    // Scores principaux 0-100
    val scoreStation: Int?,
    val scorePeuplement: Int?,
    val scoreBiodiversite: Int?,
    val scoreRisque: Int?,
    val scoreGlobal: Int?,

    // Indicateurs dendrométriques calculés
    val gHa: Double?,
    val nHa: Int?,
    val vHa: Double?,
    val hoM: Double?,
    val hgM: Double?,
    val dgCm: Double?,
    val siteIndex: Double?,
    val accroissementIg: Double?,
    val accroissementIv: Double?,
    val biomasseTotalTonnes: Double?,
    val carboneTotalTonnes: Double?,

    // Résultats qualitatifs JSON
    val essencesRecommandeesJson: String?,
    val essencesDeconseillees: String?,
    val essencesVigilanceJson: String?,
    val risquesDetectesJson: String?,
    val recommandationsSylvicolesJson: String?,
    val typeSylviculturePreco: String?,
    val volumeEclairciePreco: Double?,
    val delaiInterventionAns: Int?,
    val syntheseTextuelle: String?,

    // Versioning
    val algoVersion: String,
    val dataSourcesJson: String?,
    val remarques: String?,
    val updatedAt: Long = System.currentTimeMillis()
)
