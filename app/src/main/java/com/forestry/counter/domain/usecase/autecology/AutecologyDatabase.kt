package com.forestry.counter.domain.usecase.autecology

import com.forestry.counter.domain.model.ClimateZone

/**
 * Données autécologiques d'une essence forestière.
 * Utilisé par le moteur expert pour déterminer l'adéquation station-essence.
 *
 * Gradient hydrique  : 1=très sec → 5=très humide
 * Gradient trophique : 1=très acide/pauvre → 5=basique/riche
 * Résilience climatique : 1=très vulnérable → 5=très résilient (face au CC)
 */
data class EssenceAutecology(
    val code: String,
    val nameFr: String,
    val lightRequirement: LightRequirement,
    val minHydric: Int,
    val maxHydric: Int,
    val minTrophic: Int,
    val maxTrophic: Int,
    val maxAltitude: Int? = null,
    val toleratesHydromorphy: Boolean = false,
    val climateChangeResilience: Int = 3,       // 1–5
    val optimalZones: Set<ClimateZone> = emptySet(),
    val acceptableZones: Set<ClimateZone> = emptySet(),
    val specificAlerts: List<String> = emptyList()
)

enum class LightRequirement(val label: String) {
    HELIOPHILE("Héliophile (pleine lumière)"),
    DEMI_OMBRE("Demi-ombre"),
    SCIAPHILE("Sciaphile (ombre tolérée)")
}

enum class CompatibilityLevel(val label: String, val warning: Boolean) {
    OPTIMUM("Optimum", false),
    TOLERATED("Toléré", true),
    INCOMPATIBLE("Incompatible", true)
}

@Suppress("MemberVisibilityCanBePrivate")
object AutecologyDatabase {

    private fun z(vararg zones: ClimateZone) = setOf(*zones)

    private val ATL = ClimateZone.ATLANTIQUE
    private val SO  = ClimateZone.SEMI_OCEANIQUE
    private val CON = ClimateZone.CONTINENTALE
    private val MON = ClimateZone.MONTAGNARDE
    private val MED = ClimateZone.MEDITERRANEENNE

    val species: List<EssenceAutecology> = listOf(

        // ─── FEUILLUS NOBLES ──────────────────────────────────────────────────
        EssenceAutecology("CHENE_SESSILE", "Chêne sessile",
            LightRequirement.HELIOPHILE, 2, 4, 1, 4, 900,
            climateChangeResilience = 4,
            optimalZones = z(SO, ATL, CON),
            acceptableZones = z(MED, MON),
            specificAlerts = listOf("Sensible aux gelées tardives", "Craint l'engorgement permanent")),

        EssenceAutecology("CHENE_PEDONCULE", "Chêne pédonculé",
            LightRequirement.HELIOPHILE, 3, 5, 3, 5, 1000, true,
            climateChangeResilience = 3,
            optimalZones = z(SO, ATL),
            acceptableZones = z(CON, MON),
            specificAlerts = listOf("Exigeant en eau disponible", "Sensible à l'oïdium")),

        EssenceAutecology("CHENE_PUBESCENT", "Chêne pubescent",
            LightRequirement.HELIOPHILE, 1, 3, 2, 5, 800,
            climateChangeResilience = 5,
            optimalZones = z(MED, SO),
            acceptableZones = z(CON, ATL),
            specificAlerts = listOf("Essence xérophile pré-méditerranéenne", "Très résistant à la sécheresse")),

        EssenceAutecology("CHENE_ROUGE_AM", "Chêne rouge d'Amérique",
            LightRequirement.HELIOPHILE, 2, 4, 1, 3, 700,
            climateChangeResilience = 4,
            optimalZones = z(ATL, SO),
            acceptableZones = z(CON),
            specificAlerts = listOf("Introduit invasif potentiel en sous-bois", "Craint le calcaire actif")),

        EssenceAutecology("HETRE", "Hêtre",
            LightRequirement.SCIAPHILE, 2, 4, 2, 5, 1700,
            climateChangeResilience = 2,
            optimalZones = z(SO, MON, ATL),
            acceptableZones = z(CON),
            specificAlerts = listOf("Très vulnérable aux sécheresses répétées", "Sensible aux gelées tardives")),

        EssenceAutecology("CHARME", "Charme commun",
            LightRequirement.SCIAPHILE, 3, 4, 3, 5, 1000, true,
            climateChangeResilience = 3,
            optimalZones = z(SO, ATL, CON),
            acceptableZones = z(MED),
            specificAlerts = listOf("Sensible à la sécheresse intense")),

        EssenceAutecology("CHATAIGNIER", "Châtaignier",
            LightRequirement.DEMI_OMBRE, 2, 3, 1, 3, 900,
            climateChangeResilience = 4,
            optimalZones = z(ATL, SO),
            acceptableZones = z(MED),
            specificAlerts = listOf("Calcifuge strict (pH > 7 fatal)", "Sensible à l'encre et au chancre")),

        EssenceAutecology("ERABLE_SYCOMORE", "Érable sycomore",
            LightRequirement.DEMI_OMBRE, 3, 4, 4, 5, 1600,
            climateChangeResilience = 3,
            optimalZones = z(SO, MON, ATL),
            acceptableZones = z(CON),
            specificAlerts = listOf("Exigeant en nutriments", "Sensible aux gels tardifs")),

        EssenceAutecology("ERABLE_PLANE", "Érable plane",
            LightRequirement.DEMI_OMBRE, 2, 4, 3, 5, 1400,
            climateChangeResilience = 3,
            optimalZones = z(SO, CON, ATL),
            acceptableZones = z(MON),
            specificAlerts = listOf("Plus plastique que le sycomore")),

        EssenceAutecology("ERABLE_CHAMPETRE", "Érable champêtre",
            LightRequirement.DEMI_OMBRE, 2, 3, 3, 5, 1000,
            climateChangeResilience = 4,
            optimalZones = z(SO, ATL, MED),
            acceptableZones = z(CON),
            specificAlerts = emptyList()),

        EssenceAutecology("MERISIER", "Merisier",
            LightRequirement.HELIOPHILE, 3, 4, 3, 5, 1200,
            climateChangeResilience = 3,
            optimalZones = z(SO, ATL, CON),
            acceptableZones = z(MON),
            specificAlerts = listOf("Exigeant en sol profond et frais", "Tendance à fourcher")),

        EssenceAutecology("FRENE_COMMUN", "Frêne commun",
            LightRequirement.HELIOPHILE, 4, 5, 4, 5, 1400, true,
            climateChangeResilience = 2,
            optimalZones = z(SO, ATL),
            acceptableZones = z(CON),
            specificAlerts = listOf("⚠ Chalarose (Hymenoscyphus fraxineus) en expansion", "Exigeant en eau mobile")),

        EssenceAutecology("FRENE_FLEURS", "Frêne à fleurs (orne)",
            LightRequirement.HELIOPHILE, 1, 3, 2, 5, 800,
            climateChangeResilience = 5,
            optimalZones = z(MED),
            acceptableZones = z(SO),
            specificAlerts = listOf("Essence méditerranéenne thermophile")),

        EssenceAutecology("NOYER_COMMUN", "Noyer commun",
            LightRequirement.HELIOPHILE, 3, 4, 4, 5, 800,
            climateChangeResilience = 3,
            optimalZones = z(SO, ATL),
            acceptableZones = z(CON, MED),
            specificAlerts = listOf("Très sensible aux gelées printanières", "Allélopathique (juglone)")),

        EssenceAutecology("NOYER_NOIR", "Noyer noir d'Amérique",
            LightRequirement.HELIOPHILE, 3, 4, 3, 5, 700,
            climateChangeResilience = 4,
            optimalZones = z(SO, ATL),
            acceptableZones = z(CON, MED),
            specificAlerts = listOf("Allélopathique (juglone)", "Croissance plus rapide que le noyer commun")),

        EssenceAutecology("ORME_CHAMPETRE", "Orme champêtre",
            LightRequirement.DEMI_OMBRE, 3, 4, 4, 5, 1000,
            climateChangeResilience = 2,
            optimalZones = z(SO, ATL),
            acceptableZones = z(CON),
            specificAlerts = listOf("⚠ Sensible à la graphiose (Ophiostoma)")),

        EssenceAutecology("ORME_MONTAGNE", "Orme de montagne",
            LightRequirement.DEMI_OMBRE, 3, 4, 4, 5, 1400,
            climateChangeResilience = 2,
            optimalZones = z(MON, SO),
            acceptableZones = z(CON),
            specificAlerts = listOf("Sensible à la graphiose", "Sols de pente fraîche")),

        EssenceAutecology("ROBINIER", "Robinier faux-acacia",
            LightRequirement.HELIOPHILE, 1, 3, 1, 5, 800,
            climateChangeResilience = 5,
            optimalZones = z(SO, ATL, MED),
            acceptableZones = z(CON),
            specificAlerts = listOf("Fixateur d'azote, très résistant à la sécheresse", "Considéré invasif en Europe")),

        EssenceAutecology("TILLEUL_PETITES", "Tilleul à petites feuilles",
            LightRequirement.DEMI_OMBRE, 2, 4, 3, 5, 1200,
            climateChangeResilience = 3,
            optimalZones = z(SO, CON),
            acceptableZones = z(ATL, MON),
            specificAlerts = emptyList()),

        EssenceAutecology("TILLEUL_GRANDES", "Tilleul à grandes feuilles",
            LightRequirement.DEMI_OMBRE, 3, 4, 4, 5, 1300,
            climateChangeResilience = 3,
            optimalZones = z(SO, CON),
            acceptableZones = z(ATL, MON),
            specificAlerts = listOf("Plus exigeant en eau que le tilleul à petites feuilles")),

        EssenceAutecology("CORMIER", "Cormier",
            LightRequirement.DEMI_OMBRE, 1, 3, 3, 5, 1000,
            climateChangeResilience = 5,
            optimalZones = z(SO, MED, ATL),
            acceptableZones = z(CON),
            specificAlerts = listOf("Essence rare à bois précieux", "Sols calcaires ou neutres")),

        // ─── RIPISYLVE & HYGROPHILES ──────────────────────────────────────────
        EssenceAutecology("AULNE_GLUTINEUX", "Aulne glutineux",
            LightRequirement.HELIOPHILE, 4, 5, 2, 5, 1200, true,
            climateChangeResilience = 3,
            optimalZones = z(ATL, SO),
            acceptableZones = z(CON, MON),
            specificAlerts = listOf("Espèce ripicole stricte", "Fixateur d'azote", "⚠ Sensible à la phytophtora")),

        EssenceAutecology("AULNE_BLANC", "Aulne blanc",
            LightRequirement.DEMI_OMBRE, 3, 5, 2, 5, 1800, true,
            climateChangeResilience = 3,
            optimalZones = z(MON, SO),
            acceptableZones = z(ATL),
            specificAlerts = listOf("Montagnard, fixateur d'azote", "Ripisylves alpines et pyrénéennes")),

        EssenceAutecology("AULNE_CORSE", "Aulne de Corse",
            LightRequirement.DEMI_OMBRE, 3, 5, 2, 5, 1500, true,
            climateChangeResilience = 4,
            optimalZones = z(SO, ATL),
            acceptableZones = z(MED),
            specificAlerts = listOf("Fixateur d'azote, reboisement sur sols dégradés")),

        EssenceAutecology("FRENE_OXYPHYLLE", "Frêne oxyphylle",
            LightRequirement.HELIOPHILE, 4, 5, 3, 5, 600, true,
            climateChangeResilience = 3,
            optimalZones = z(MED),
            acceptableZones = z(SO),
            specificAlerts = listOf("Espèce méditerranéenne ripicole", "Sensible à la chalarose")),

        EssenceAutecology("PEUPLIER_NOIR", "Peuplier noir",
            LightRequirement.HELIOPHILE, 4, 5, 3, 5, 1000, true,
            climateChangeResilience = 3,
            optimalZones = z(ATL, SO),
            acceptableZones = z(CON, MED),
            specificAlerts = listOf("Espèce indigène des ripisylves", "En régression, hybridation avec cultivars")),

        EssenceAutecology("PEUPLIER_HYBR", "Peuplier hybride",
            LightRequirement.HELIOPHILE, 4, 5, 4, 5, 600, true,
            climateChangeResilience = 3,
            optimalZones = z(ATL, SO),
            acceptableZones = z(CON),
            specificAlerts = listOf("Populiculture : récolte 15-25 ans", "Sol alluvial fertile")),

        EssenceAutecology("PEUPLIER_TREMBLE", "Peuplier tremble",
            LightRequirement.HELIOPHILE, 3, 5, 2, 5, 1600, true,
            climateChangeResilience = 3,
            optimalZones = z(SO, CON, MON),
            acceptableZones = z(ATL),
            specificAlerts = listOf("Pionnier, se drageonne abondamment")),

        EssenceAutecology("SAULE_BLANC", "Saule blanc",
            LightRequirement.HELIOPHILE, 4, 5, 3, 5, 1000, true,
            climateChangeResilience = 3,
            optimalZones = z(ATL, SO),
            acceptableZones = z(CON, MED),
            specificAlerts = listOf("Espèce ripicole de berge")),

        EssenceAutecology("SAULE_MARSAULT", "Saule marsault",
            LightRequirement.HELIOPHILE, 3, 5, 2, 5, 1600, true,
            climateChangeResilience = 3,
            optimalZones = z(ATL, SO, MON),
            acceptableZones = z(CON),
            specificAlerts = listOf("Pioneer, zones humides")),

        // ─── PIONNIÈRES & SECONDAIRES ─────────────────────────────────────────
        EssenceAutecology("BOULEAU_VERRUQ", "Bouleau verruqueux",
            LightRequirement.HELIOPHILE, 2, 5, 1, 4, 2000, true,
            climateChangeResilience = 3,
            optimalZones = z(SO, CON, ATL),
            acceptableZones = z(MON),
            specificAlerts = listOf("Pionnier, sol acide à neutre", "Sols sableux à tourbeux")),

        EssenceAutecology("BOULEAU_PUBESC", "Bouleau pubescent",
            LightRequirement.HELIOPHILE, 3, 5, 1, 4, 2000, true,
            climateChangeResilience = 3,
            optimalZones = z(ATL, MON),
            acceptableZones = z(SO),
            specificAlerts = listOf("Tourbières et sols hydromorphes", "Plus nordique que le bouleau verruqueux")),

        EssenceAutecology("SORBIER_OISEL", "Sorbier des oiseleurs",
            LightRequirement.DEMI_OMBRE, 2, 4, 1, 5, 2000,
            climateChangeResilience = 4,
            optimalZones = z(MON, SO),
            acceptableZones = z(ATL, CON),
            specificAlerts = listOf("Essence de berge de montagne", "Bois dur apprécié")),

        EssenceAutecology("ALISIER_TORMINAL", "Alisier torminal",
            LightRequirement.DEMI_OMBRE, 2, 3, 3, 5, 900,
            climateChangeResilience = 4,
            optimalZones = z(SO, ATL),
            acceptableZones = z(CON, MED),
            specificAlerts = listOf("Bois précieux rare en gros diamètre")),

        EssenceAutecology("ALISIER_BLANC", "Alisier blanc",
            LightRequirement.DEMI_OMBRE, 1, 3, 3, 5, 800,
            climateChangeResilience = 5,
            optimalZones = z(SO, MED),
            acceptableZones = z(ATL, CON),
            specificAlerts = listOf("Sols calcaires secs, essence thermophile")),

        EssenceAutecology("TULIPIER", "Tulipier de Virginie",
            LightRequirement.DEMI_OMBRE, 3, 4, 3, 5, 600,
            climateChangeResilience = 3,
            optimalZones = z(ATL, SO),
            acceptableZones = z(MED),
            specificAlerts = listOf("Bois léger, croissance rapide")),

        // ─── RÉSINEUX PRINCIPAUX ──────────────────────────────────────────────
        EssenceAutecology("SAPIN_PECTINE", "Sapin pectiné",
            LightRequirement.SCIAPHILE, 3, 4, 2, 5, 1800,
            climateChangeResilience = 2,
            optimalZones = z(MON, SO),
            acceptableZones = z(ATL),
            specificAlerts = listOf("⚠ Vulnérable aux sécheresses estivales", "Sensible au gel tardif et aux champignons")),

        EssenceAutecology("SAPIN_GRANDIS", "Sapin de Vancouver (grandis)",
            LightRequirement.SCIAPHILE, 3, 4, 2, 4, 1000,
            climateChangeResilience = 3,
            optimalZones = z(ATL, SO),
            acceptableZones = z(MON),
            specificAlerts = listOf("Très haute productivité en climat océanique", "Sensible à la sécheresse")),

        EssenceAutecology("EPICEA_COMMUN", "Épicéa commun",
            LightRequirement.DEMI_OMBRE, 3, 4, 2, 5, 2200,
            climateChangeResilience = 2,
            optimalZones = z(MON),
            acceptableZones = z(SO, CON),
            specificAlerts = listOf("⚠ Forte vulnérabilité aux scolytes (crises climatiques)", "Enracinement superficiel")),

        EssenceAutecology("EPICEA_SITKA", "Épicéa de Sitka",
            LightRequirement.DEMI_OMBRE, 3, 5, 2, 4, 800,
            climateChangeResilience = 3,
            optimalZones = z(ATL),
            acceptableZones = z(SO),
            specificAlerts = listOf("Optimal en climat très océanique (Bretagne, Normandie)")),

        EssenceAutecology("DOUGLAS_VERT", "Douglas vert",
            LightRequirement.DEMI_OMBRE, 3, 4, 2, 4, 1200,
            climateChangeResilience = 4,
            optimalZones = z(SO, ATL, MON),
            acceptableZones = z(CON),
            specificAlerts = listOf("Craint le calcaire actif (chlorose)", "Essence d'avenir en reboisement")),

        EssenceAutecology("PIN_SYLVESTRE", "Pin sylvestre",
            LightRequirement.HELIOPHILE, 1, 4, 1, 4, 2000,
            climateChangeResilience = 3,
            optimalZones = z(SO, CON),
            acceptableZones = z(ATL, MON, MED),
            specificAlerts = listOf("Très plastique, pionnier robuste", "Scolytes en cas de stress")),

        EssenceAutecology("PIN_MARITIME", "Pin maritime",
            LightRequirement.HELIOPHILE, 1, 3, 1, 3, 800,
            climateChangeResilience = 4,
            optimalZones = z(ATL),
            acceptableZones = z(SO),
            specificAlerts = listOf("Craint le calcaire et les grands froids", "Sensible au feu")),

        EssenceAutecology("PIN_LARICIO", "Pin laricio de Corse",
            LightRequirement.HELIOPHILE, 1, 3, 1, 4, 1500,
            climateChangeResilience = 4,
            optimalZones = z(MED, SO),
            acceptableZones = z(ATL, CON),
            specificAlerts = listOf("Fût très droit, bois de qualité supérieure")),

        EssenceAutecology("PIN_NOIR_AUTR", "Pin noir d'Autriche",
            LightRequirement.HELIOPHILE, 1, 3, 3, 5, 1400,
            climateChangeResilience = 4,
            optimalZones = z(MED, SO, CON),
            acceptableZones = z(ATL),
            specificAlerts = listOf("Très résistant au calcaire et à la sécheresse")),

        EssenceAutecology("PIN_ALEP", "Pin d'Alep",
            LightRequirement.HELIOPHILE, 1, 2, 3, 5, 800,
            climateChangeResilience = 5,
            optimalZones = z(MED),
            acceptableZones = z(SO),
            specificAlerts = listOf("Essence méditerranéenne xérothermophile", "Très inflammable")),

        EssenceAutecology("PIN_CEMBRO", "Pin cembro (arolle)",
            LightRequirement.DEMI_OMBRE, 2, 3, 1, 4, 2800,
            climateChangeResilience = 3,
            optimalZones = z(MON),
            acceptableZones = emptySet(),
            specificAlerts = listOf("Haute montagne > 1700 m", "Longévité > 1000 ans")),

        EssenceAutecology("MELEZE_EUROPE", "Mélèze d'Europe",
            LightRequirement.HELIOPHILE, 2, 3, 2, 5, 2500,
            climateChangeResilience = 4,
            optimalZones = z(MON),
            acceptableZones = z(SO, CON),
            specificAlerts = listOf("Seul résineux caducifolié européen", "Bois très durable")),

        EssenceAutecology("MELEZE_HYBRIDE", "Mélèze hybride",
            LightRequirement.HELIOPHILE, 2, 3, 2, 5, 2000,
            climateChangeResilience = 4,
            optimalZones = z(MON, SO),
            acceptableZones = z(CON),
            specificAlerts = listOf("Vigueur hybride Europe × Japon")),

        EssenceAutecology("CEDRE_ATLAS", "Cèdre de l'Atlas",
            LightRequirement.HELIOPHILE, 1, 3, 2, 5, 1500,
            climateChangeResilience = 5,
            optimalZones = z(MED, SO),
            acceptableZones = z(ATL, CON),
            specificAlerts = listOf("Essence d'avenir face au changement climatique", "Résistant à la sécheresse estivale")),

        EssenceAutecology("CEDRE_LIBAN", "Cèdre du Liban",
            LightRequirement.HELIOPHILE, 1, 3, 2, 5, 1400,
            climateChangeResilience = 5,
            optimalZones = z(MED, SO),
            acceptableZones = z(ATL),
            specificAlerts = listOf("Bois similaire au cèdre de l'Atlas")),

        EssenceAutecology("THUYA_GEANT", "Thuya géant",
            LightRequirement.SCIAPHILE, 3, 5, 1, 4, 1000,
            climateChangeResilience = 3,
            optimalZones = z(ATL, SO),
            acceptableZones = z(MON),
            specificAlerts = listOf("Très tolérant à l'ombre", "Bois extrêmement durable")),

        EssenceAutecology("SEQUOIA_SV", "Séquoia sempervirens",
            LightRequirement.DEMI_OMBRE, 3, 4, 2, 4, 600,
            climateChangeResilience = 4,
            optimalZones = z(ATL),
            acceptableZones = z(SO),
            specificAlerts = listOf("Acclimaté en Bretagne et Sud-Ouest", "Croissance exceptionnelle")),

        EssenceAutecology("TSUGA_OUEST", "Tsuga de l'Ouest",
            LightRequirement.SCIAPHILE, 3, 5, 1, 3, 1000,
            climateChangeResilience = 3,
            optimalZones = z(ATL, SO),
            acceptableZones = z(MON),
            specificAlerts = listOf("Tolère très bien l'ombre", "Optimal en Bretagne/Normandie")),

        EssenceAutecology("CRYPTOMERE", "Cryptomère du Japon",
            LightRequirement.DEMI_OMBRE, 3, 5, 1, 4, 1000,
            climateChangeResilience = 3,
            optimalZones = z(ATL),
            acceptableZones = z(SO),
            specificAlerts = listOf("Bois léger aromatique", "Planté Bretagne et Pays basque")),

        EssenceAutecology("CYPRES_CHAUVE", "Cyprès chauve",
            LightRequirement.HELIOPHILE, 4, 5, 2, 5, 500, true,
            climateChangeResilience = 4,
            optimalZones = z(ATL, SO),
            acceptableZones = z(MED),
            specificAlerts = listOf("Tolérant à l'engorgement prolongé", "Pneumatophores caractéristiques")),

        // ─── MÉDITERRANÉENS & THERMOPHILES ────────────────────────────────────
        EssenceAutecology("CHENE_VERT", "Chêne vert",
            LightRequirement.HELIOPHILE, 1, 2, 2, 5, 600,
            climateChangeResilience = 5,
            optimalZones = z(MED),
            acceptableZones = z(SO),
            specificAlerts = listOf("Essence méditerranéenne sempervirente par excellence")),

        EssenceAutecology("CHENE_LIEGE", "Chêne liège",
            LightRequirement.HELIOPHILE, 1, 2, 1, 3, 600,
            climateChangeResilience = 5,
            optimalZones = z(MED),
            acceptableZones = z(ATL),
            specificAlerts = listOf("Calcifuge strict (pH > 7 interdit)", "Production de liège")),

        EssenceAutecology("PIN_SALZMANN", "Pin de Salzmann",
            LightRequirement.HELIOPHILE, 1, 3, 2, 5, 1500,
            climateChangeResilience = 5,
            optimalZones = z(MED),
            acceptableZones = z(SO),
            specificAlerts = listOf("Endémique Cévennes/Pyrénées orientales", "Très résistant à la sécheresse")),

        EssenceAutecology("SAPIN_CEPHALONIE", "Sapin de Céphalonie",
            LightRequirement.DEMI_OMBRE, 2, 3, 2, 5, 1500,
            climateChangeResilience = 5,
            optimalZones = z(MED, SO),
            acceptableZones = z(CON),
            specificAlerts = listOf("Essence d'avenir face au réchauffement", "Très résistant à la sécheresse")),

        EssenceAutecology("ROBINIER_CULTIVAR", "Robinier (reboisement)",
            LightRequirement.HELIOPHILE, 1, 3, 2, 5, 700,
            climateChangeResilience = 5,
            optimalZones = z(SO, MED, ATL),
            acceptableZones = z(CON),
            specificAlerts = listOf("Fixateur d'azote, séquestre carbone", "Gestion taillis à courte rotation")),

        EssenceAutecology("EUCALYPTUS_GUNNII", "Eucalyptus de Gunn",
            LightRequirement.HELIOPHILE, 2, 4, 1, 4, 400,
            climateChangeResilience = 5,
            optimalZones = z(ATL),
            acceptableZones = z(SO),
            specificAlerts = listOf("Rustique (-18°C), TCR biomasse", "Sols bien drainés"))
    )

    fun getByCodeOrName(query: String): EssenceAutecology? {
        val q = query.trim().uppercase()
        return species.find { it.code.uppercase() == q || it.nameFr.uppercase() == q }
    }

    fun getByZone(zone: ClimateZone): List<EssenceAutecology> =
        species.filter { zone in it.optimalZones || zone in it.acceptableZones }

    fun getResilients(minScore: Int = 4): List<EssenceAutecology> =
        species.filter { it.climateChangeResilience >= minScore }
}
