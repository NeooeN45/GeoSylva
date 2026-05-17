package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stations_environnementales",
    foreignKeys = [
        ForeignKey(
            entity = ParcelleEntity::class,
            parentColumns = ["parcelleId"],
            childColumns = ["parcelleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "index_station_parcelleId", value = ["parcelleId"], unique = true)
    ]
)
data class StationEnvironnementaleEntity(
    @PrimaryKey
    val stationId: String,
    val parcelleId: String,

    // Topographie (DEM SRTM embarqué)
    val altitudeM: Double?,
    val slopePct: Double?,
    val aspectDeg: Double?,
    val aspectLabel: String?,

    // Sol — données embarquées IDW
    val soilPh: Double?,
    val soilRumMm: Double?,
    val soilRufMm: Double?,
    val soilTexture: String?,
    val soilDrainage: String?,

    // Sol — saisie terrain
    val soilProfondeurCm: Int?,
    val soilHydromorphieCm: Int?,
    val soilTypeWrb: String?,
    val soilPhTerrain: Double?,

    // Sol — couches GisSol INRAE (WMS BDGSF)
    val rumClasseBdgsf: String?,
    val profondeurSolClasse: String?,
    val phSolForestier: Double?,
    val cOrganiqueTha: Double?,
    val typeWrbBdgsf: String?,
    val pierrositeClassePct: String?,

    // Géologie embarquée
    val rocheMere: String?,
    val lithologie: String?,
    val phIndicatif: Double?,

    // Climat — normales embarquées
    val tempMoyC: Double?,
    val tempMinJanvC: Double?,
    val tempMaxJuillC: Double?,
    val precipMmAn: Double?,
    val precipEteMm: Double?,
    val etpMm: Double?,
    val joursGel: Int?,
    val joursSecs: Int?,
    val ensoleilH: Double?,
    val climateType: String?,

    // Indices calculés
    val idhe: Double?,
    val spei6Score: Double?,
    val indiceProductivite: Int?,
    val scoreVulnCC2050: Int?,

    // SylvoÉcoRégion (IFN/IGN)
    val codeSer: String?,
    val nomSer: String?,

    // DVF foncier (Cerema open)
    val dvfPrixMedianEurM2: Double?,
    val dvfNbTransactions: Int?,
    val dvfDateFetch: Long?,

    // Vulnérabilité BioClimSol-like
    val vulnerabiliteActuelle: Int?,
    val vulnerabilite2050: Int?,

    // Zonages réglementaires
    val natura2000Code: String?,
    val natura2000Nom: String?,
    val znieffType1: Boolean,
    val znieffType2: Boolean,
    val isForetAncienne: Boolean,
    val risqueIncendieZone: String?,
    val risqueInondation: String?,

    // Cadastre enrichi (IGN API)
    val surfaceCadastraleHa: Double?,
    val geometrieWkt: String?,
    val natureCadastraleCode: String?,

    // Qualité et date des données
    val sourceDataQualityJson: String?,
    val fetchedAt: Long?
)
