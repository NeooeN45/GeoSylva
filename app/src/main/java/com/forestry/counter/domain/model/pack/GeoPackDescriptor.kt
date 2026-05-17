package com.forestry.counter.domain.model.pack

/**
 * Modèle de description d'un pack territorial GeoSylva.
 *
 * Hiérarchie : SOCLE_NATIONAL → REGIONAL → DEPARTEMENTAL
 * Le plus local surcharge le plus global.
 *
 * Exemple : France → Nouvelle-Aquitaine → Corrèze
 */

// ─────────────────────────────────────────────────────────────────────────────
//  Types de packs
// ─────────────────────────────────────────────────────────────────────────────

enum class PackLevel(val labelFr: String, val priority: Int) {
    SOCLE_NATIONAL("Socle national",       0),
    REGIONAL(      "Pack régional",        1),
    DEPARTEMENTAL( "Pack départemental",   2)
}

enum class PackStatus(val labelFr: String) {
    EMBEDDED(       "Embarqué (intégré dans l'app)"),
    INSTALLED(      "Installé"),
    AVAILABLE(      "Disponible — non installé"),
    UPDATE_PENDING( "Mise à jour disponible"),
    DOWNLOADING(    "Téléchargement en cours"),
    ERROR(          "Erreur d'installation")
}

// ─────────────────────────────────────────────────────────────────────────────
//  Identifiants géographiques
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 13 régions métropolitaines + 5 DOM-TOM
 */
enum class RegionFrance(val labelFr: String, val codeINSEE: String) {
    AUVERGNE_RHONE_ALPES(       "Auvergne-Rhône-Alpes",         "84"),
    BOURGOGNE_FRANCHE_COMTE(    "Bourgogne-Franche-Comté",      "27"),
    BRETAGNE(                   "Bretagne",                     "53"),
    CENTRE_VAL_DE_LOIRE(        "Centre-Val de Loire",          "24"),
    CORSE(                      "Corse",                        "94"),
    GRAND_EST(                  "Grand Est",                    "44"),
    HAUTS_DE_FRANCE(            "Hauts-de-France",              "32"),
    ILE_DE_FRANCE(              "Île-de-France",                "11"),
    NORMANDIE(                  "Normandie",                    "28"),
    NOUVELLE_AQUITAINE(         "Nouvelle-Aquitaine",           "75"),
    OCCITANIE(                  "Occitanie",                    "76"),
    PAYS_DE_LA_LOIRE(           "Pays de la Loire",             "52"),
    PROVENCE_ALPES_COTE_AZUR(   "Provence-Alpes-Côte d'Azur",  "93"),
    GUADELOUPE(                 "Guadeloupe",                   "01"),
    MARTINIQUE(                 "Martinique",                   "02"),
    GUYANE(                     "Guyane",                       "03"),
    LA_REUNION(                 "La Réunion",                   "04"),
    MAYOTTE(                    "Mayotte",                      "06")
}

// ─────────────────────────────────────────────────────────────────────────────
//  Descripteur principal d'un pack
// ─────────────────────────────────────────────────────────────────────────────

data class GeoPackDescriptor(
    val id: String,                     // ex: "fr.region.84", "fr.dept.63"
    val level: PackLevel,
    val name: String,                   // ex: "Auvergne-Rhône-Alpes"
    val codeINSEE: String,             // code INSEE région ou département
    val parentId: String? = null,       // ID du pack parent (région → national)
    val version: String,               // ex: "1.3.2"
    val buildDate: String,             // ex: "2025-11"
    val sizeKb: Long,                  // taille estimée en Ko
    val status: PackStatus,
    val downloadUrl: String? = null,
    val checksum: String? = null,
    val features: PackFeatures,
    val metaInfo: PackMetaInfo = PackMetaInfo()
)

/**
 * Capacités fonctionnelles incluses dans ce pack.
 */
data class PackFeatures(
    val hasFloraDatabase: Boolean       = false,  // base d'espèces locales
    val hasStationRules: Boolean        = false,  // règles stationnelles
    val hasRipisylveRules: Boolean      = false,  // règles ripisylve
    val hasSylviculturalRules: Boolean  = false,  // règles sylvicoles
    val hasDriasProjets: Boolean        = false,  // projections DRIAS locales
    val hasFtsIndex: Boolean            = false,  // index full-text flora
    val hasGpsContextCache: Boolean     = false,  // cache GPS contextes
    val hasRegionalSRGS: Boolean        = false,  // SRGS régional embarqué
    val floraSpeciesCount: Int          = 0,
    val stationTypesCount: Int          = 0,
    val essencesCount: Int              = 0
)

data class PackMetaInfo(
    val author: String          = "GeoSylva Team",
    val license: String         = "Internal",
    val notes: String           = "",
    val minAppVersion: String   = "1.0.0",
    val featureFlags: Map<String, Boolean> = emptyMap(),
    // TODO_METIER : ajouter des indicateurs de qualité terrain (validé par expert régional ?)
    val expertValidated: Boolean = false
)

// ─────────────────────────────────────────────────────────────────────────────
//  Résultat d'une résolution de règles
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Valeur résolue après superposition socle → région → département.
 */
data class ResolvedValue<T>(
    val value: T,
    val resolvedFrom: PackLevel,
    val packId: String
)

/**
 * Contexte territorial actif pour une parcelle donnée.
 */
data class TerritorialContext(
    val regionCode: String?,
    val deptCode: String?,
    val activePacks: List<GeoPackDescriptor>,
    val appliedLevel: PackLevel
)
