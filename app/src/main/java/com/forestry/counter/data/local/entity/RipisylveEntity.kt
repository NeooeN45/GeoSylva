package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ripisylve_diagnostics",
    indices = [Index(name = "index_ripisylve_parcelleId", value = ["parcelleId"])]
)
data class RipisylveEntity(
    @PrimaryKey val id: String,
    val parcelleId: String,
    val observerName: String = "",
    val observationDate: Long,
    val createdAt: Long,
    val updatedAt: Long,
    // Localisation
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeM: Double? = null,
    val sectionLengthM: Double = 50.0,
    val sectionNotes: String = "",
    // Critère 1 – Continuité
    val continuitePct: Double = 0.0,
    // Critère 2 – Largeur
    val largeurMode: String = "UNE_RANGEE",
    // Critère 3 – Strates
    val strateHerbacee: Boolean = false,
    val strateArbustive: Boolean = false,
    val strateArborescente: Boolean = false,
    // Critère 4 – Diversité
    val nbEspecesObservees: Int = 0,
    val especesObserveesCsv: String = "",
    // Critère 5 – Classes de diamètre
    val diamAutoFromDendro: Boolean = false,
    val hasTresPetitBois: Boolean = false,
    val hasPetitBois: Boolean = false,
    val hasMoyenBois: Boolean = false,
    val hasGrosBois: Boolean = false,
    // Critère 6 – Microhabitats
    val microhabitatCavites: Boolean = false,
    val microhabitatFissures: Boolean = false,
    val microhabitatDecollementEcorce: Boolean = false,
    val microhabitatChampignons: Boolean = false,
    val microhabitatBoisMort: Boolean = false,
    val microhabitatTresGrosBois: Boolean = false,
    // Critère 7 – Sanitaire
    val sanitairePct: Double = 0.0,
    // Critère 8 – Invasives
    val invasivesPct: Double = 0.0,
    val invasivesCsv: String = "",
    // Critère 9 – Inadaptées
    val inadapteesMode: String = "ABSENCE",
    // Critère 10 – Stabilité
    val stabilitePct: Double = 0.0,
    // Notes
    val globalNotes: String = "",
    val photoUrisCsv: String = ""
)
