package com.forestry.counter.data.local.entity

import androidx.room.*

/**
 * Entité FTS4 pour la recherche pleine-texte des espèces floristiques.
 * Permet une recherche rapide et offline sans parcourir toute la liste.
 *
 * RÈGLE MIGRATION : pas de DEFAULT dans le SQL de migration.
 * Tous les champs sont NOT NULL avec valeurs vides gérées en Kotlin.
 */
@Fts4
@Entity(tableName = "flora_fts")
data class FloraFtsEntity(
    @ColumnInfo(name = "species_id")    val speciesId: String,
    @ColumnInfo(name = "nom_francais")  val nomFrancais: String,
    @ColumnInfo(name = "nom_scientifique") val nomScientifique: String,
    @ColumnInfo(name = "vernaculaires") val vernaculaires: String,   // pipe-séparé
    @ColumnInfo(name = "synonymes")     val synonymes: String,       // pipe-séparé
    @ColumnInfo(name = "type_milieu")   val typeMilieu: String,      // pipe-séparé
    @ColumnInfo(name = "strate")        val strate: String
)

/**
 * Entité cache des contextes GPS calculés.
 * Évite de recalculer les mêmes analyses pour des positions déjà visitées.
 *
 * Granularité ~1 km² (arrondi lat/lon à 2 décimales).
 */
@Entity(
    tableName = "gps_context_cache",
    indices = [Index(value = ["lat_key", "lon_key"], unique = true)]
)
data class GpsContextCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "lat_key")           val latKey: Double,
    @ColumnInfo(name = "lon_key")           val lonKey: Double,
    @ColumnInfo(name = "region_code")       val regionCode: String,
    @ColumnInfo(name = "dept_code")         val deptCode: String,
    @ColumnInfo(name = "altitude_approx_m") val altitudeApproxM: Double,
    @ColumnInfo(name = "topo_hint")         val topoHint: String,
    @ColumnInfo(name = "zone_humide_prob")  val zoneHumideProb: Double,
    @ColumnInfo(name = "pack_id_active")    val packIdActive: String,
    @ColumnInfo(name = "computed_at")       val computedAt: Long,
    // v25 — MNT SRTM
    @ColumnInfo(name = "slope_pct",      defaultValue = "-1") val slopePct: Int = -1,
    @ColumnInfo(name = "aspect_deg",     defaultValue = "-1") val aspectDeg: Int = -1,
    @ColumnInfo(name = "aspect_label",   defaultValue = "")  val aspectLabel: String = "",
    // v25 — Climat OpenMeteo
    @ColumnInfo(name = "precip_mm_an",   defaultValue = "-1") val precipMmAn: Int = -1,
    @ColumnInfo(name = "temp_moy_c",     defaultValue = "-99") val tempMoyC: Double = -99.0,
    @ColumnInfo(name = "climate_type",   defaultValue = "INCONNU") val climateType: String = "INCONNU",
    // v26 — Sol embarqué (EmbeddedSoilService)
    @ColumnInfo(name = "soil_ph",        defaultValue = "-1")       val soilPh: Double = -1.0,
    @ColumnInfo(name = "soil_rum_mm",    defaultValue = "-1")       val soilRumMm: Int = -1,
    @ColumnInfo(name = "soil_texture",   defaultValue = "INCONNUE") val soilTexture: String = "INCONNUE",
    @ColumnInfo(name = "soil_drainage",  defaultValue = "NORMAL")   val soilDrainage: String = "NORMAL"
)
