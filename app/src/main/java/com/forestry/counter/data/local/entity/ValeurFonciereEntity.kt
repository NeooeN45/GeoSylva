package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "valeurs_foncieres",
    foreignKeys = [
        ForeignKey(
            entity = ParcelleEntity::class,
            parentColumns = ["parcelleId"],
            childColumns = ["parcelleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "index_valeur_parcelleId", value = ["parcelleId"], unique = true)
    ]
)
data class ValeurFonciereEntity(
    @PrimaryKey
    val valeurId: String,
    val parcelleId: String,
    val dateEstimation: Long,

    // Valeur foncière nue (DVF Cerema)
    val valeurFonciereNuEurHa: Double?,
    val sourceValeurFonciere: String?,
    val prixMarcheRegionalEurHa: Double?,

    // Valeur du bois sur pied
    val volumeCommercialisableM3: Double?,
    val valeurBoisSurPiedEur: Double?,

    // Carbone — Label Bas Carbone
    val carboneTotalTonnes: Double?,
    val valeurCarboneLabelBcEur: Double?,

    // Valeur patrimoniale totale
    val valeurTotalePatrimoineEur: Double?,

    // Coûts prévisionnels
    val coutEclaircieEstimeEur: Double?,
    val coutRenouvellementEstimeEur: Double?,
    val revenuBrutAnnuelMoyenEur: Double?,

    // Régime fiscal
    val eligiblePsg: Boolean,
    val eligibleDefiForet: Boolean,
    val eligibleIfiExoneration: Boolean,
    val eligibleDpa: Boolean,
    val alertesFiscalesJson: String?,

    val remarques: String?,
    val updatedAt: Long = System.currentTimeMillis()
)
