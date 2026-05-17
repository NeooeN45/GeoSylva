package com.forestry.counter.domain.usecase.autecology

import com.forestry.counter.domain.model.ClimateZone

enum class CompatibilityLevel(val label: String) {
    OPTIMUM("Optimal"),
    BON("Bon"),
    ACCEPTABLE("Acceptable"),
    TOLERATED("Toléré"),
    LIMITE("Limite"),
    INCOMPATIBLE("Incompatible")
}

/**
 * Autécologie complète d'une essence forestière.
 * Sources : guides CNPF, données IFN, références Rameau/Dumé/Mansion.
 *
 * @param code          Code forestier standard (EPIC, QUPE…)
 * @param nameFr        Nom vernaculaire français
 * @param nameLatin     Nom latin binomial
 * @param optimalZones  Zones bioclimatiques de pleine production
 * @param acceptableZones Zones de tolérance (peuplement viable mais limité)
 * @param climateChangeResilience Score 1–5 face au CC (5=très résilient)
 * @param minHydric     Indice hydrique Ellenberg minimum (1=xérophile, 9=hygrophile)
 * @param maxHydric     Indice hydrique Ellenberg maximum
 * @param toleratesHydromorphy Supporte le sol engorgé temporairement
 * @param phMin         pH sol minimum toléré
 * @param phMax         pH sol maximum toléré
 * @param minTrophic    Niveau trophique minimum (1=oligotrophe, 5=eutrophe)
 * @param maxTrophic    Niveau trophique maximum
 * @param maxAltitude   Altitude maximale de production (m) — null = non limitant
 * @param minAltitude   Altitude minimale (m, pour les montagnards stricts)
 * @param tMaxToleranceC Température maximale absolue tolérée sans dommage (°C)
 * @param droughtSensitivity Sensibilité à la sécheresse estivale (1=faible, 5=très forte)
 * @param frostSensitivity Sensibilité au gel tardif printanier (1=résistant, 5=très sensible)
 * @param specificAlerts Alertes phytosanitaires et risques particuliers
 * @param keyPathogenes Codes pathogènes majeurs à surveiller (PathoEntomoDatabase)
 * @param cnpfGuide     Référence guide CNPF
 */
data class EssenceAutecology(
    val code: String,
    val nameFr: String,
    val nameLatin: String = "",
    val optimalZones: Set<ClimateZone> = emptySet(),
    val acceptableZones: Set<ClimateZone> = emptySet(),
    val climateChangeResilience: Int = 3,
    val minHydric: Int = 2,
    val maxHydric: Int = 7,
    val toleratesHydromorphy: Boolean = false,
    val phMin: Double = 4.0,
    val phMax: Double = 8.0,
    val minTrophic: Int = 2,
    val maxTrophic: Int = 5,
    val maxAltitude: Int? = null,
    val minAltitude: Int = 0,
    val tMaxToleranceC: Double = 38.0,
    val droughtSensitivity: Int = 3,
    val frostSensitivity: Int = 2,
    val specificAlerts: List<String> = emptyList(),
    val keyPathogenes: List<String> = emptyList(),
    val cnpfGuide: String = ""
)

object AutecologyDatabase {

    private val A = ClimateZone.ATLANTIQUE
    private val SO = ClimateZone.SEMI_OCEANIQUE
    private val C = ClimateZone.CONTINENTALE
    private val M = ClimateZone.MONTAGNARDE
    private val MED = ClimateZone.MEDITERRANEENNE

    private val ALL: List<EssenceAutecology> = listOf(

        // ── CHÊNES ──────────────────────────────────────────────────────────────
        EssenceAutecology(
            code = "QUPE", nameFr = "Chêne pédonculé", nameLatin = "Quercus robur",
            optimalZones = setOf(A, SO), acceptableZones = setOf(C),
            climateChangeResilience = 3, minHydric = 3, maxHydric = 8,
            toleratesHydromorphy = true, phMin = 4.5, phMax = 7.5,
            minTrophic = 3, maxTrophic = 5, droughtSensitivity = 3, frostSensitivity = 1,
            keyPathogenes = listOf("OÏDIUM_CHÊNE", "CHENILLE_PROCESSIONNAIRE"),
            cnpfGuide = "Chênes blancs"
        ),
        EssenceAutecology(
            code = "QURO", nameFr = "Chêne sessile", nameLatin = "Quercus petraea",
            optimalZones = setOf(SO, C), acceptableZones = setOf(A, M),
            climateChangeResilience = 4, minHydric = 2, maxHydric = 6,
            phMin = 4.0, phMax = 7.5, minTrophic = 2, maxTrophic = 5,
            droughtSensitivity = 2, frostSensitivity = 1, maxAltitude = 1400,
            keyPathogenes = listOf("OÏDIUM_CHÊNE", "AGRILE_CHÊNE"),
            cnpfGuide = "Chênes blancs"
        ),
        EssenceAutecology(
            code = "QUPU", nameFr = "Chêne pubescent", nameLatin = "Quercus pubescens",
            optimalZones = setOf(MED, SO), acceptableZones = setOf(C),
            climateChangeResilience = 5, minHydric = 1, maxHydric = 4,
            phMin = 5.0, phMax = 8.5, minTrophic = 1, maxTrophic = 4,
            droughtSensitivity = 1, frostSensitivity = 1, tMaxToleranceC = 42.0,
            cnpfGuide = "Chênes blancs"
        ),
        EssenceAutecology(
            code = "QUIL", nameFr = "Chêne vert", nameLatin = "Quercus ilex",
            optimalZones = setOf(MED), acceptableZones = setOf(SO),
            climateChangeResilience = 5, minHydric = 1, maxHydric = 4,
            phMin = 5.5, phMax = 8.5, minTrophic = 1, maxTrophic = 3,
            droughtSensitivity = 1, frostSensitivity = 3, tMaxToleranceC = 44.0, maxAltitude = 900,
            cnpfGuide = "Chêne vert"
        ),
        EssenceAutecology(
            code = "QUCE", nameFr = "Chêne liège", nameLatin = "Quercus suber",
            optimalZones = setOf(MED), acceptableZones = setOf(A),
            climateChangeResilience = 5, minHydric = 1, maxHydric = 4,
            phMin = 4.5, phMax = 6.5, minTrophic = 1, maxTrophic = 3,
            droughtSensitivity = 1, frostSensitivity = 4, tMaxToleranceC = 45.0,
            cnpfGuide = "Chêne liège"
        ),
        EssenceAutecology(
            code = "QURU", nameFr = "Chêne rouge d'Amérique", nameLatin = "Quercus rubra",
            optimalZones = setOf(A, SO), acceptableZones = setOf(C),
            climateChangeResilience = 4, minHydric = 2, maxHydric = 6,
            phMin = 4.0, phMax = 6.5, minTrophic = 2, maxTrophic = 5,
            droughtSensitivity = 2, frostSensitivity = 1,
            specificAlerts = listOf("Espèce exotique — invasivité potentielle"),
            cnpfGuide = "Chêne rouge"
        ),

        // ── HÊTRE ───────────────────────────────────────────────────────────────
        EssenceAutecology(
            code = "FASY", nameFr = "Hêtre commun", nameLatin = "Fagus sylvatica",
            optimalZones = setOf(M, SO), acceptableZones = setOf(A),
            climateChangeResilience = 2, minHydric = 3, maxHydric = 7,
            phMin = 4.5, phMax = 7.5, minTrophic = 3, maxTrophic = 5, maxAltitude = 1700,
            droughtSensitivity = 4, frostSensitivity = 2,
            specificAlerts = listOf("Très sensible aux sécheresses estivales répétées", "Dépérissement observé depuis 2017"),
            keyPathogenes = listOf("BUPESTRE_HÊTRE", "ARMILLAIRE"),
            cnpfGuide = "Hêtre"
        ),

        // ── CHARME, AULNE, FRÊNE ────────────────────────────────────────────────
        EssenceAutecology(
            code = "CABE", nameFr = "Charme commun", nameLatin = "Carpinus betulus",
            optimalZones = setOf(SO, C), acceptableZones = setOf(A),
            climateChangeResilience = 3, minHydric = 3, maxHydric = 7, toleratesHydromorphy = true,
            phMin = 4.5, phMax = 7.5, minTrophic = 3, maxTrophic = 5,
            droughtSensitivity = 2, frostSensitivity = 1
        ),
        EssenceAutecology(
            code = "ALGL", nameFr = "Aulne glutineux", nameLatin = "Alnus glutinosa",
            optimalZones = setOf(A, SO), acceptableZones = setOf(C),
            climateChangeResilience = 3, minHydric = 6, maxHydric = 9, toleratesHydromorphy = true,
            phMin = 5.0, phMax = 7.5, minTrophic = 3, maxTrophic = 5,
            droughtSensitivity = 4, frostSensitivity = 1,
            keyPathogenes = listOf("PHYTOPHTHORA_AULNE")
        ),
        EssenceAutecology(
            code = "FREX", nameFr = "Frêne commun", nameLatin = "Fraxinus excelsior",
            optimalZones = setOf(A, SO), acceptableZones = setOf(C, M),
            climateChangeResilience = 2, minHydric = 4, maxHydric = 8, toleratesHydromorphy = true,
            phMin = 5.5, phMax = 8.0, minTrophic = 4, maxTrophic = 5,
            droughtSensitivity = 3, frostSensitivity = 2,
            specificAlerts = listOf("Chalarose : mortalité jusqu'à 90% dans certains peuplements"),
            keyPathogenes = listOf("CHALAROSE_FRÊNE")
        ),

        // ── ÉRABLES ─────────────────────────────────────────────────────────────
        EssenceAutecology(
            code = "ACPS", nameFr = "Érable sycomore", nameLatin = "Acer pseudoplatanus",
            optimalZones = setOf(M, SO), acceptableZones = setOf(A, C),
            climateChangeResilience = 3, minHydric = 3, maxHydric = 7,
            phMin = 5.0, phMax = 8.0, minTrophic = 3, maxTrophic = 5, maxAltitude = 1600,
            droughtSensitivity = 2, frostSensitivity = 2,
            keyPathogenes = listOf("SUIE_ÉRABLE")
        ),
        EssenceAutecology(
            code = "ACCA", nameFr = "Érable champêtre", nameLatin = "Acer campestre",
            optimalZones = setOf(SO, C), acceptableZones = setOf(A, MED),
            climateChangeResilience = 4, minHydric = 2, maxHydric = 6,
            phMin = 5.5, phMax = 8.5, minTrophic = 2, maxTrophic = 5,
            droughtSensitivity = 2, frostSensitivity = 1
        ),

        // ── CHÂTAIGNIER, NOYER ───────────────────────────────────────────────────
        EssenceAutecology(
            code = "CASA", nameFr = "Châtaignier", nameLatin = "Castanea sativa",
            optimalZones = setOf(A, SO, MED), acceptableZones = setOf(C),
            climateChangeResilience = 4, minHydric = 2, maxHydric = 6,
            phMin = 4.0, phMax = 6.5, minTrophic = 2, maxTrophic = 4, maxAltitude = 900,
            droughtSensitivity = 2, frostSensitivity = 2,
            keyPathogenes = listOf("ENCRE_CHÂTAIGNIER", "CHANCRE_CHÂTAIGNIER"),
            cnpfGuide = "Châtaignier"
        ),
        EssenceAutecology(
            code = "JURE", nameFr = "Noyer commun", nameLatin = "Juglans regia",
            optimalZones = setOf(SO, C), acceptableZones = setOf(A),
            climateChangeResilience = 3, minHydric = 3, maxHydric = 7,
            phMin = 5.5, phMax = 8.0, minTrophic = 4, maxTrophic = 5, maxAltitude = 900,
            droughtSensitivity = 2, frostSensitivity = 3,
            cnpfGuide = "Noyers"
        ),
        EssenceAutecology(
            code = "JUNI", nameFr = "Noyer hybride (MJ209)", nameLatin = "Juglans nigra × regia",
            optimalZones = setOf(A, SO), acceptableZones = setOf(C),
            climateChangeResilience = 4, minHydric = 3, maxHydric = 7,
            phMin = 5.5, phMax = 8.0, minTrophic = 4, maxTrophic = 5, maxAltitude = 700,
            droughtSensitivity = 2, frostSensitivity = 3,
            cnpfGuide = "Noyers"
        ),

        // ── ROBINIER, PEUPLIER ───────────────────────────────────────────────────
        EssenceAutecology(
            code = "ROPS", nameFr = "Robinier faux-acacia", nameLatin = "Robinia pseudoacacia",
            optimalZones = setOf(SO, C), acceptableZones = setOf(A, MED),
            climateChangeResilience = 5, minHydric = 1, maxHydric = 5,
            phMin = 4.5, phMax = 8.5, minTrophic = 1, maxTrophic = 4, maxAltitude = 800,
            droughtSensitivity = 1, frostSensitivity = 3,
            specificAlerts = listOf("Espèce fixatrice d'azote — peut déséquilibrer la flore locale"),
            cnpfGuide = "Robinier"
        ),
        EssenceAutecology(
            code = "PONI", nameFr = "Peuplier noir", nameLatin = "Populus nigra",
            optimalZones = setOf(A, SO), acceptableZones = setOf(C),
            climateChangeResilience = 3, minHydric = 5, maxHydric = 9, toleratesHydromorphy = true,
            phMin = 5.0, phMax = 8.0, minTrophic = 4, maxTrophic = 5,
            droughtSensitivity = 3, frostSensitivity = 2,
            cnpfGuide = "Peuplier"
        ),

        // ── PINS ─────────────────────────────────────────────────────────────────
        EssenceAutecology(
            code = "PISY", nameFr = "Pin sylvestre", nameLatin = "Pinus sylvestris",
            optimalZones = setOf(C, M), acceptableZones = setOf(SO),
            climateChangeResilience = 3, minHydric = 1, maxHydric = 5,
            phMin = 4.0, phMax = 7.5, minTrophic = 1, maxTrophic = 4, maxAltitude = 2200,
            droughtSensitivity = 2, frostSensitivity = 1,
            keyPathogenes = listOf("ARMILLAIRE", "FOMES", "SCLERODERRIS"),
            cnpfGuide = "Pin sylvestre"
        ),
        EssenceAutecology(
            code = "PIPN", nameFr = "Pin noir d'Autriche", nameLatin = "Pinus nigra nigra",
            optimalZones = setOf(SO, C), acceptableZones = setOf(M, MED),
            climateChangeResilience = 4, minHydric = 1, maxHydric = 4,
            phMin = 5.0, phMax = 8.5, minTrophic = 1, maxTrophic = 3, maxAltitude = 1800,
            droughtSensitivity = 2, frostSensitivity = 1,
            cnpfGuide = "Pin noir/laricio"
        ),
        EssenceAutecology(
            code = "PILA", nameFr = "Pin laricio de Corse", nameLatin = "Pinus laricio corsicana",
            optimalZones = setOf(MED, SO), acceptableZones = setOf(A),
            climateChangeResilience = 4, minHydric = 1, maxHydric = 4,
            phMin = 5.0, phMax = 7.5, minTrophic = 1, maxTrophic = 3, maxAltitude = 1800,
            droughtSensitivity = 2, frostSensitivity = 1,
            cnpfGuide = "Pin noir/laricio"
        ),
        EssenceAutecology(
            code = "PIPI", nameFr = "Pin maritime", nameLatin = "Pinus pinaster",
            optimalZones = setOf(A, MED), acceptableZones = setOf(SO),
            climateChangeResilience = 3, minHydric = 1, maxHydric = 4,
            phMin = 4.0, phMax = 6.5, minTrophic = 1, maxTrophic = 3, maxAltitude = 700,
            droughtSensitivity = 2, frostSensitivity = 3,
            specificAlerts = listOf("Risque incendie élevé en zone méditerranéenne et landaise"),
            keyPathogenes = listOf("NOCTUELLE_PIN", "PROCESSIONNAIRE_PIN"),
            cnpfGuide = "Pin maritime"
        ),
        EssenceAutecology(
            code = "PIHA", nameFr = "Pin d'Alep", nameLatin = "Pinus halepensis",
            optimalZones = setOf(MED), acceptableZones = emptySet(),
            climateChangeResilience = 5, minHydric = 1, maxHydric = 3,
            phMin = 5.5, phMax = 8.5, minTrophic = 1, maxTrophic = 2, maxAltitude = 800,
            droughtSensitivity = 1, frostSensitivity = 3, tMaxToleranceC = 44.0,
            specificAlerts = listOf("Risque incendie très élevé"),
            cnpfGuide = "Pin d'Alep"
        ),

        // ── SAPINS, ÉPICÉAS ──────────────────────────────────────────────────────
        EssenceAutecology(
            code = "ABAL", nameFr = "Sapin pectiné", nameLatin = "Abies alba",
            optimalZones = setOf(M), acceptableZones = setOf(SO),
            climateChangeResilience = 2, minHydric = 4, maxHydric = 7,
            phMin = 4.5, phMax = 7.0, minTrophic = 3, maxTrophic = 5, maxAltitude = 1900, minAltitude = 400,
            droughtSensitivity = 4, frostSensitivity = 3,
            specificAlerts = listOf("Très sensible aux sécheresses estivales et au réchauffement"),
            keyPathogenes = listOf("TYPOGRAPHE_SAPIN", "ARMILLAIRE"),
            cnpfGuide = "Sapin pectiné/Nordmann"
        ),
        EssenceAutecology(
            code = "ABNO", nameFr = "Sapin de Nordmann", nameLatin = "Abies nordmanniana",
            optimalZones = setOf(M), acceptableZones = setOf(SO),
            climateChangeResilience = 3, minHydric = 4, maxHydric = 7,
            phMin = 4.5, phMax = 7.0, minTrophic = 3, maxTrophic = 5, maxAltitude = 1800, minAltitude = 300,
            droughtSensitivity = 3, frostSensitivity = 2,
            cnpfGuide = "Sapin pectiné/Nordmann"
        ),
        EssenceAutecology(
            code = "PIAB", nameFr = "Épicéa commun", nameLatin = "Picea abies",
            optimalZones = setOf(M, C), acceptableZones = emptySet(),
            climateChangeResilience = 1, minHydric = 4, maxHydric = 7,
            phMin = 4.0, phMax = 6.5, minTrophic = 2, maxTrophic = 4, maxAltitude = 2000, minAltitude = 400,
            droughtSensitivity = 5, frostSensitivity = 2, tMaxToleranceC = 34.0,
            specificAlerts = listOf("Risque scolyte (Ips typographus) massif en cas de sécheresse", "Chablis fréquents — élancement à surveiller"),
            keyPathogenes = listOf("IPS_TYPOGRAPHUS", "FOMES", "ARMILLAIRE"),
            cnpfGuide = "Épicéa commun"
        ),
        EssenceAutecology(
            code = "PISI", nameFr = "Épicéa de Sitka", nameLatin = "Picea sitchensis",
            optimalZones = setOf(A), acceptableZones = setOf(SO),
            climateChangeResilience = 2, minHydric = 5, maxHydric = 8,
            phMin = 4.0, phMax = 6.5, minTrophic = 2, maxTrophic = 4, maxAltitude = 600,
            droughtSensitivity = 4, frostSensitivity = 2,
            cnpfGuide = "Épicéa Sitka"
        ),

        // ── DOUGLAS, MÉLÈZES, CÈDRE ─────────────────────────────────────────────
        EssenceAutecology(
            code = "PSME", nameFr = "Douglas", nameLatin = "Pseudotsuga menziesii",
            optimalZones = setOf(SO, A), acceptableZones = setOf(C, M),
            climateChangeResilience = 4, minHydric = 3, maxHydric = 6,
            phMin = 4.5, phMax = 7.0, minTrophic = 2, maxTrophic = 5, maxAltitude = 1600,
            droughtSensitivity = 2, frostSensitivity = 3,
            keyPathogenes = listOf("RHABDOCLINE", "PUCERON_DOUGLAS"),
            cnpfGuide = "Douglas"
        ),
        EssenceAutecology(
            code = "LADE", nameFr = "Mélèze d'Europe", nameLatin = "Larix decidua",
            optimalZones = setOf(M), acceptableZones = setOf(C),
            climateChangeResilience = 4, minHydric = 2, maxHydric = 6,
            phMin = 4.5, phMax = 7.5, minTrophic = 2, maxTrophic = 4, maxAltitude = 2500, minAltitude = 500,
            droughtSensitivity = 2, frostSensitivity = 1,
            keyPathogenes = listOf("CHALCOPHORE_MÉLÈZE"),
            cnpfGuide = "Mélèzes"
        ),
        EssenceAutecology(
            code = "LAKA", nameFr = "Mélèze du Japon", nameLatin = "Larix kaempferi",
            optimalZones = setOf(SO, M), acceptableZones = setOf(C, A),
            climateChangeResilience = 4, minHydric = 2, maxHydric = 6,
            phMin = 4.0, phMax = 7.0, minTrophic = 2, maxTrophic = 4, maxAltitude = 1600,
            droughtSensitivity = 2, frostSensitivity = 2,
            cnpfGuide = "Mélèzes"
        ),
        EssenceAutecology(
            code = "CECD", nameFr = "Cèdre de l'Atlas", nameLatin = "Cedrus atlantica",
            optimalZones = setOf(MED, SO), acceptableZones = setOf(C),
            climateChangeResilience = 5, minHydric = 1, maxHydric = 4,
            phMin = 5.5, phMax = 8.5, minTrophic = 1, maxTrophic = 3, maxAltitude = 1600,
            droughtSensitivity = 1, frostSensitivity = 2, tMaxToleranceC = 42.0,
            cnpfGuide = "Cèdre Atlas"
        ),
        EssenceAutecology(
            code = "CEDE", nameFr = "Cèdre du Liban", nameLatin = "Cedrus libani",
            optimalZones = setOf(MED, SO), acceptableZones = setOf(C),
            climateChangeResilience = 5, minHydric = 1, maxHydric = 4,
            phMin = 5.5, phMax = 8.5, minTrophic = 1, maxTrophic = 3, maxAltitude = 1400,
            droughtSensitivity = 1, frostSensitivity = 2
        ),

        // ── TILLEUL, ORME, MERISIER ──────────────────────────────────────────────
        EssenceAutecology(
            code = "TICO", nameFr = "Tilleul à grandes feuilles", nameLatin = "Tilia platyphyllos",
            optimalZones = setOf(SO, C), acceptableZones = setOf(A, M),
            climateChangeResilience = 3, minHydric = 3, maxHydric = 7,
            phMin = 5.0, phMax = 8.0, minTrophic = 3, maxTrophic = 5, maxAltitude = 1200,
            droughtSensitivity = 2, frostSensitivity = 2
        ),
        EssenceAutecology(
            code = "ULGL", nameFr = "Orme champêtre", nameLatin = "Ulmus minor",
            optimalZones = setOf(A, SO), acceptableZones = setOf(C),
            climateChangeResilience = 2, minHydric = 4, maxHydric = 8, toleratesHydromorphy = true,
            phMin = 5.5, phMax = 8.5, minTrophic = 4, maxTrophic = 5,
            droughtSensitivity = 2, frostSensitivity = 1,
            specificAlerts = listOf("Graphiose : mortalité quasi-totale des adultes en 1–5 ans"),
            keyPathogenes = listOf("GRAPHIOSE_ORME")
        ),
        EssenceAutecology(
            code = "PRAV", nameFr = "Merisier", nameLatin = "Prunus avium",
            optimalZones = setOf(SO, C), acceptableZones = setOf(A, M),
            climateChangeResilience = 3, minHydric = 3, maxHydric = 7,
            phMin = 5.0, phMax = 8.0, minTrophic = 3, maxTrophic = 5, maxAltitude = 1200,
            droughtSensitivity = 2, frostSensitivity = 3,
            cnpfGuide = "Feuillus précieux"
        ),

        // ── DOUGLAS DE GRANDE-BRETAGNE → déjà PSME, AUTRES FEUILLUS PRÉCIEUX ───
        EssenceAutecology(
            code = "ACPL", nameFr = "Érable plane", nameLatin = "Acer platanoides",
            optimalZones = setOf(C, SO), acceptableZones = setOf(A, M),
            climateChangeResilience = 3, minHydric = 3, maxHydric = 7,
            phMin = 5.0, phMax = 8.0, minTrophic = 3, maxTrophic = 5, maxAltitude = 1400,
            droughtSensitivity = 2, frostSensitivity = 1,
            cnpfGuide = "Feuillus précieux"
        ),
        EssenceAutecology(
            code = "SOAR", nameFr = "Sorbier des oiseleurs", nameLatin = "Sorbus aucuparia",
            optimalZones = setOf(M, C), acceptableZones = setOf(SO),
            climateChangeResilience = 3, minHydric = 3, maxHydric = 7,
            phMin = 4.0, phMax = 7.0, minTrophic = 2, maxTrophic = 4, maxAltitude = 2000,
            droughtSensitivity = 2, frostSensitivity = 1
        ),
        EssenceAutecology(
            code = "BEBE", nameFr = "Bouleau verruqueux", nameLatin = "Betula pendula",
            optimalZones = setOf(SO, C), acceptableZones = setOf(A, M),
            climateChangeResilience = 3, minHydric = 2, maxHydric = 6,
            phMin = 4.0, phMax = 7.0, minTrophic = 1, maxTrophic = 4, maxAltitude = 1600,
            droughtSensitivity = 3, frostSensitivity = 1
        ),
        EssenceAutecology(
            code = "BEPU", nameFr = "Bouleau pubescent", nameLatin = "Betula pubescens",
            optimalZones = setOf(A, M), acceptableZones = setOf(SO, C),
            climateChangeResilience = 3, minHydric = 4, maxHydric = 8, toleratesHydromorphy = true,
            phMin = 4.0, phMax = 7.0, minTrophic = 1, maxTrophic = 3, maxAltitude = 2000,
            droughtSensitivity = 3, frostSensitivity = 1
        ),

        // ── PEUPLIERS CULTIVÉS ───────────────────────────────────────────────────
        EssenceAutecology(
            code = "POHY", nameFr = "Peuplier hybride (cultivar)", nameLatin = "Populus × euramericana",
            optimalZones = setOf(A, SO), acceptableZones = setOf(C),
            climateChangeResilience = 3, minHydric = 5, maxHydric = 9, toleratesHydromorphy = true,
            phMin = 5.5, phMax = 8.0, minTrophic = 4, maxTrophic = 5, maxAltitude = 600,
            droughtSensitivity = 4, frostSensitivity = 2,
            keyPathogenes = listOf("MARSSONINA_PEUPLIER", "XANTHOMONAS_PEUPLIER"),
            cnpfGuide = "Peuplier"
        ),

        // ── CHÊNE ROUGE DÉJÀ PRÉSENT, AJOUTER PINS RESTANTS ─────────────────────
        EssenceAutecology(
            code = "PICE", nameFr = "Pin cembrot", nameLatin = "Pinus cembra",
            optimalZones = setOf(M), acceptableZones = emptySet(),
            climateChangeResilience = 3, minHydric = 2, maxHydric = 5,
            phMin = 4.0, phMax = 7.5, minTrophic = 1, maxTrophic = 3, maxAltitude = 2600, minAltitude = 1600,
            droughtSensitivity = 2, frostSensitivity = 1
        )
    )

    val species: List<EssenceAutecology> get() = ALL

    fun get(code: String): EssenceAutecology? =
        ALL.find { it.code.equals(code, ignoreCase = true) }

    fun getByCodeOrName(query: String): EssenceAutecology? =
        ALL.find { it.code.equals(query, ignoreCase = true) }
            ?: ALL.find { it.nameFr.equals(query, ignoreCase = true) }
            ?: ALL.find { it.nameLatin.equals(query, ignoreCase = true) }
            ?: ALL.find { it.nameFr.contains(query, ignoreCase = true) }

    fun getAll(): List<EssenceAutecology> = ALL

    fun getByZone(zone: ClimateZone): List<EssenceAutecology> =
        ALL.filter { zone in it.optimalZones || zone in it.acceptableZones }

    fun getClimateResilient(minScore: Int = 4): List<EssenceAutecology> =
        ALL.filter { it.climateChangeResilience >= minScore }
            .sortedByDescending { it.climateChangeResilience }

    fun getDroughtTolerant(): List<EssenceAutecology> =
        ALL.filter { it.droughtSensitivity <= 2 }
            .sortedBy { it.droughtSensitivity }
}
