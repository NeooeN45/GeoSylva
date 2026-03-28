package com.forestry.counter.domain.calculation.tarifs

import kotlinx.serialization.Serializable

/**
 * Méthodes officielles de cubage utilisées en foresterie française.
 *
 * Références :
 * - Schaeffer (1 entrée) : Schaeffer, 1949 — Table de production, Annales ENEF, Nancy
 * - Schaeffer (2 entrées) : Schaeffer, 1949 — V = f(C130, H)
 * - Algan : Algan, 1958 — Tarifs de cubage pour Douglas, Épicéa, etc.
 * - Tarifs rapides IFN : Inventaire Forestier National — 36 tarifs à 1 entrée (D130)
 * - Tarifs lents IFN : Inventaire Forestier National — Tables à 2 entrées (D130 + H)
 * - FGH : V = F × G × H  (variante explicite de la méthode du coefficient de forme)
 * - Coefficient de forme : V = G × H × f  (méthode classique)
 * - Tarifs spécialisés CRPF/FCBA : tarifs régionaux ou par essence (Pin maritime, Douglas, etc.)
 */

/** Catégorie fonctionnelle d'un tarif de cubage pour le classement et l'affichage. */
enum class TarifCategory(val label: String, val description: String) {
    UNIVERSEL( "Universel",   "Applicable à toutes essences et régions"),
    REGIONAL(  "Régional",    "Calibré pour une région ou massif forestier spécifique"),
    SPECIALISE("Spécialisé",  "Dédié à une ou quelques essences — précision maximale sur ces essences")
}

enum class TarifMethod(
    val code: String,
    val label: String,
    val description: String,
    val entrees: Int,
    val category: TarifCategory = TarifCategory.UNIVERSEL,
    val reliability: Int = 3,                          // 1–5 ★ (5 = meilleure précision)
    val specializedEssences: List<String> = emptyList(), // vide = universel
    val regionLabel: String? = null
) {
    // ── Tarifs universels ──────────────────────────────────────────────────────
    SCHAEFFER_1E(
        code = "SCHAEFFER_1E",
        label = "Schaeffer 1 entrée",
        description = "V = a + b × C130²  — Tarif à 1 entrée (circonférence). Schaeffer, 1949. Simple mais moins précis sans hauteur.",
        entrees = 1, reliability = 2
    ),
    SCHAEFFER_2E(
        code = "SCHAEFFER_2E",
        label = "Schaeffer 2 entrées",
        description = "V = a + b × C130² × H  — Tarif à 2 entrées (circonférence + hauteur). Schaeffer, 1949. Méthode historique robuste.",
        entrees = 2, reliability = 3
    ),
    ALGAN(
        code = "ALGAN",
        label = "Algan (par essence)",
        description = "V = a × D^b × H^c  — Coefficients propres à chaque essence (Algan 1958, Pardé & Bouchon 1988). Très utilisé en France.",
        entrees = 2, reliability = 4
    ),
    IFN_RAPIDE(
        code = "IFN_RAPIDE",
        label = "Tarif rapide IFN",
        description = "Tarifs IFN à 1 entrée (n° 1–36). V = f(D130). Inventaire Forestier National. Pratique sans mesure de hauteur.",
        entrees = 1, reliability = 3
    ),
    IFN_LENT(
        code = "IFN_LENT",
        label = "Tarif lent IFN",
        description = "Tables IFN à 2 entrées (D130, H). V = f(D², D²×H). Inventaire Forestier National. Référence nationale — meilleure précision générique.",
        entrees = 2, reliability = 5
    ),
    FGH(
        code = "FGH",
        label = "FGH (coef. forme explicite)",
        description = "V = F × G × H  — Variante explicite du coefficient de forme. Permet de saisir F manuellement. Utile pour peuplements atypiques.",
        entrees = 2, reliability = 3
    ),
    COEF_FORME(
        code = "COEF_FORME",
        label = "Coefficient de forme",
        description = "V = G × H × f  — Méthode classique. Coefficient de décroissance par essence (Pardé & Bouchon 1988). Estimation rapide.",
        entrees = 2, reliability = 2
    ),
    CHAUDE(
        code = "CHAUDE",
        label = "Chaudé — arbres sur pied",
        description = "Tarif à décroissances variables — arbres sur pied. V = a × C^b (C₁₃₀ en dm). Pierre Chaudé, 1991. Classement par type sylvicole (F1–F4).",
        entrees = 1, reliability = 3
    ),
    CHAUDE_TAILLIS(
        code = "CHAUDE_TAILLIS",
        label = "Chaudé — taillis sur pied",
        description = "Tarif à décroissances variables — taillis sur pied. V = a × C^b (C₁₃₀ en dm). Pierre Chaudé, 1991. Classes T1–T3 (chêne, châtaignier, divers).",
        entrees = 1, reliability = 3
    ),

    // ── Tarifs spécialisés par essence ou région ───────────────────────────────
    CRPF_PIN_MARITIME(
        code = "CRPF_PIN_MARITIME",
        label = "CRPF NA — Pin maritime (Landes)",
        description = "Tarif 2 entrées spécifique au pin maritime des Landes de Gascogne. Calibré sur les peuplements du massif landais (plantations 1250–1700 tiges/ha). CRPF Nouvelle-Aquitaine / FCBA (ex-AFOCEL). V = a × D^b × H^c. Référence privilégiée pour le pin maritime aquitain.",
        entrees = 2,
        category = TarifCategory.SPECIALISE, reliability = 5,
        specializedEssences = listOf("PIN_MARITIME"),
        regionLabel = "Nouvelle-Aquitaine / Landes de Gascogne"
    ),
    FCBA_DOUGLAS(
        code = "FCBA_DOUGLAS",
        label = "FCBA — Douglas vert",
        description = "Tarif 2 entrées calibré sur les plantations françaises de Douglas vert (Pseudotsuga menziesii). FCBA (ex-AFOCEL/CTBA), rapport 2012. Optimisé pour futaies régulières — précision supérieure à l'Algan générique.",
        entrees = 2,
        category = TarifCategory.SPECIALISE, reliability = 5,
        specializedEssences = listOf("DOUGLAS_VERT"),
        regionLabel = "France (toutes régions)"
    ),
    FCBA_PEUPLIER(
        code = "FCBA_PEUPLIER",
        label = "FCBA — Peuplier hybride plantation",
        description = "Tarif 2 entrées pour peupliers hybrides en plantation intensive (I-214, Beaupré, Soligo, Koster…). FCBA / CTBA. Adapté aux cycles courts 10–18 ans, forte cylindricité (c ≈ 1).",
        entrees = 2,
        category = TarifCategory.SPECIALISE, reliability = 5,
        specializedEssences = listOf("PEUPLIER_HYBR", "PEUPLIER_NOIR"),
        regionLabel = "France (plaines alluviales)"
    ),
    ONF_HETRE(
        code = "ONF_HETRE",
        label = "ONF — Hêtre futaie",
        description = "Tables de production ONF pour hêtre (Fagus sylvatica). Tarif 2 entrées calibré sur les hêtraies françaises de futaie régulière. ONF, Guide sylvicole du hêtre (2006). Adapté plaine et montagne.",
        entrees = 2,
        category = TarifCategory.SPECIALISE, reliability = 5,
        specializedEssences = listOf("HETRE_COMMUN"),
        regionLabel = "France (hêtraies)"
    ),
    CRPF_SAPIN_ALPES(
        code = "CRPF_SAPIN_ALPES",
        label = "CRPF AURA — Sapin/Épicéa Alpes",
        description = "Tarif 2 entrées régional pour sapin pectiné et épicéa commun en conditions alpines et pré-alpines. CRPF Auvergne-Rhône-Alpes. Calibré sur peuplements irréguliers de montagne (800–1800 m).",
        entrees = 2,
        category = TarifCategory.REGIONAL, reliability = 4,
        specializedEssences = listOf("SAPIN_PECTINE", "EPICEA_COMMUN"),
        regionLabel = "Alpes / Préalpes / Jura"
    ),
    CRPF_EPICEA_VOSGES(
        code = "CRPF_EPICEA_VOSGES",
        label = "CRPF Grand Est — Épicéa Vosges",
        description = "Tarif 2 entrées pour épicéa commun des Vosges et du Grand Est. CRPF Grand Est. Calibré sur peuplements réguliers vosgiens (conditions atlantico-continentales, 400–1000 m).",
        entrees = 2,
        category = TarifCategory.REGIONAL, reliability = 4,
        specializedEssences = listOf("EPICEA_COMMUN"),
        regionLabel = "Vosges / Grand Est"
    ),
    CRPF_CHATAIGNIER(
        code = "CRPF_CHATAIGNIER",
        label = "CRPF — Châtaignier",
        description = "Tarif 2 entrées spécifique au châtaignier (Castanea sativa) en taillis et taillis-sous-futaie. CRPF Nouvelle-Aquitaine / CRPF Occitanie. Adapté aux peuplements atlantiques et méditerranéens.",
        entrees = 2,
        category = TarifCategory.SPECIALISE, reliability = 4,
        specializedEssences = listOf("CHATAIGNIER"),
        regionLabel = "Nouvelle-Aquitaine / Occitanie / Corse"
    ),
    CRPF_PIN_SYLVESTRE_MC(
        code = "CRPF_PIN_SYLVESTRE_MC",
        label = "CRPF AURA — Pin sylvestre MC",
        description = "Tarif 2 entrées pour pin sylvestre (Pinus sylvestris) du Massif Central. CRPF Auvergne-Rhône-Alpes. Coefficients distincts du pin sylvestre atlantique ou alpin — adapté aux reboisements d'après-guerre.",
        entrees = 2,
        category = TarifCategory.REGIONAL, reliability = 4,
        specializedEssences = listOf("PIN_SYLVESTRE"),
        regionLabel = "Massif Central"
    ),
    CRPF_LARICIO_CORSE(
        code = "CRPF_LARICIO_CORSE",
        label = "CRPF — Pin Laricio",
        description = "Tarif 2 entrées pour pin Laricio de Corse (Pinus nigra subsp. laricio). CRPF Corse. Calibré sur forêts domaniales corses et plantations continentales. Très haute cylindricité.",
        entrees = 2,
        category = TarifCategory.SPECIALISE, reliability = 4,
        specializedEssences = listOf("PIN_LARICIO"),
        regionLabel = "Corse / Vosges / Massif Central"
    ),
    CRPF_ROBINIER(
        code = "CRPF_ROBINIER",
        label = "CRPF — Robinier faux-acacia",
        description = "Tarif 2 entrées pour robinier faux-acacia (Robinia pseudoacacia). CRPF IDF / Centre. Adapté aux taillis à courtes rotations et futaies. Bois de cœur très durable, valorisation piquets.",
        entrees = 2,
        category = TarifCategory.SPECIALISE, reliability = 4,
        specializedEssences = listOf("ROBINIER"),
        regionLabel = "Centre / IDF / Normandie"
    ),
    CRPF_OAK_OCEANIC(
        code = "CRPF_OAK_OCEANIC",
        label = "CRPF — Chêne atlantique",
        description = "Tarif 2 entrées pour chênes sessile et pédonculé en zone atlantique (ouest France). CRPF Nouvelle-Aquitaine / Centre. Calibré sur futaies régulières plaine atlantique — valorisation bois d'œuvre.",
        entrees = 2,
        category = TarifCategory.REGIONAL, reliability = 4,
        specializedEssences = listOf("CH_SESSILE", "CH_PEDONCULE"),
        regionLabel = "Nouvelle-Aquitaine / Centre / Bretagne"
    ),
    FCBA_EUCALYPTUS(
        code = "FCBA_EUCALYPTUS",
        label = "FCBA — Eucalyptus plantation",
        description = "Tarif 2 entrées pour eucalyptus en plantation (E. gunnii, E. globulus). FCBA / CIRAD. Adapté aux rotations courtes (8–15 ans) du grand sud-ouest France. Forte cylindricité.",
        entrees = 2,
        category = TarifCategory.SPECIALISE, reliability = 4,
        specializedEssences = listOf("EUCALYPTUS_GUNNII", "EUCALYPTUS_GLOBULUS"),
        regionLabel = "Sud-Ouest France"
    );

    companion object {
        fun fromCode(code: String): TarifMethod? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }

        /**
         * Suggère le meilleur tarif pour un ensemble d'essences.
         * Retourne le tarif spécialisé si toutes les essences sont couvertes,
         * sinon le tarif universel le plus fiable pour 2 entrées.
         */
        fun suggestFor(essenceCodes: List<String>): TarifMethod {
            if (essenceCodes.isEmpty()) return IFN_LENT
            val up = essenceCodes.map { it.trim().uppercase() }
            // 1. Chercher un tarif spécialisé qui couvre TOUTES les essences
            val specialized = entries
                .filter { it.specializedEssences.isNotEmpty() }
                .sortedByDescending { it.reliability }
                .firstOrNull { method -> up.all { e -> method.specializedEssences.contains(e) } }
            if (specialized != null) return specialized
            // 2. Fallback : IFN_LENT (meilleure fiabilité universelle)
            return IFN_LENT
        }

        /**
         * Retourne les tarifs classés par pertinence pour un ensemble d'essences.
         * Ordre : spécialisés correspondants → universels 2E fiables → 1E → anciens.
         */
        fun rankedFor(essenceCodes: List<String>): List<TarifMethod> {
            val up = essenceCodes.map { it.trim().uppercase() }
            val specialized = entries.filter { m ->
                m.specializedEssences.isNotEmpty() &&
                up.any { e -> m.specializedEssences.contains(e) }
            }.sortedByDescending { it.reliability }
            val universal2E = listOf(IFN_LENT, ALGAN, SCHAEFFER_2E, FGH, COEF_FORME)
            val universal1E = listOf(IFN_RAPIDE, CHAUDE, CHAUDE_TAILLIS, SCHAEFFER_1E)
            return (specialized + universal2E + universal1E).distinct()
        }
    }
}

// ─────────────────────────────────────────────────────
// Coefficients Schaeffer 1 entrée : V = a + b × C²
// C en mètres, V en m³
// Source : Schaeffer 1949, tables publiées par l'ONF
// ─────────────────────────────────────────────────────
@Serializable
data class SchaefferOneEntryCoefs(
    val essence: String,
    val numero: Int,           // numéro du tarif Schaeffer (1–16)
    val a: Double,             // constante (m³)
    val b: Double              // coefficient sur C² (m³/m²)
) {
    fun volume(circonfCm: Double): Double {
        val cM = circonfCm / 100.0
        return (a + b * cM * cM).coerceAtLeast(0.0)
    }

    fun volumeFromDiam(diamCm: Double): Double {
        val cCm = diamCm * Math.PI
        return volume(cCm)
    }
}

// ─────────────────────────────────────────────────────
// Coefficients Schaeffer 2 entrées : V = a + b × C² × H
// C en mètres, H en mètres, V en m³
// ─────────────────────────────────────────────────────
@Serializable
data class SchaefferTwoEntryCoefs(
    val essence: String,
    val numero: Int,
    val a: Double,
    val b: Double
) {
    fun volume(circonfCm: Double, hauteurM: Double): Double {
        val cM = circonfCm / 100.0
        return (a + b * cM * cM * hauteurM).coerceAtLeast(0.0)
    }

    fun volumeFromDiam(diamCm: Double, hauteurM: Double): Double {
        val cCm = diamCm * Math.PI
        return volume(cCm, hauteurM)
    }
}

// ─────────────────────────────────────────────────────
// Coefficients Algan : V = a × D^b × H^c
// D en cm, H en mètres, V en m³
// Source : Algan 1958, peuplements réguliers
// ─────────────────────────────────────────────────────
@Serializable
data class AlganCoefs(
    val essence: String,
    val a: Double,
    val b: Double,
    val c: Double
) {
    fun volume(diamCm: Double, hauteurM: Double): Double {
        if (diamCm <= 0.0 || hauteurM <= 0.0) return 0.0
        return (a * Math.pow(diamCm, b) * Math.pow(hauteurM, c)).coerceAtLeast(0.0)
    }
}

// ─────────────────────────────────────────────────────
// Tarif rapide IFN : 36 tarifs numérotés
// V = a₀ + a₁×D + a₂×D²  (D en cm, V en dm³ → converti en m³)
// Source : IFN, documentation technique
// ─────────────────────────────────────────────────────
@Serializable
data class IfnRapideCoefs(
    val numero: Int,           // 1–36
    val a0: Double,            // dm³
    val a1: Double,            // dm³/cm
    val a2: Double             // dm³/cm²
) {
    fun volumeDm3(diamCm: Double): Double {
        return (a0 + a1 * diamCm + a2 * diamCm * diamCm).coerceAtLeast(0.0)
    }

    fun volumeM3(diamCm: Double): Double = volumeDm3(diamCm) / 1000.0
}

// ─────────────────────────────────────────────────────
// Tarif lent IFN : Tables à 2 entrées (D, H)
// V = a₀ + a₁×D² + a₂×D²×H  (D en cm, H en m, V en dm³ → m³)
// Source : IFN
// ─────────────────────────────────────────────────────
@Serializable
data class IfnLentCoefs(
    val numero: Int,
    val a0: Double,
    val a1: Double,
    val a2: Double
) {
    fun volumeDm3(diamCm: Double, hauteurM: Double): Double {
        val d2 = diamCm * diamCm
        return (a0 + a1 * d2 + a2 * d2 * hauteurM).coerceAtLeast(0.0)
    }

    fun volumeM3(diamCm: Double, hauteurM: Double): Double = volumeDm3(diamCm, hauteurM) / 1000.0
}

// ─────────────────────────────────────────────────────
// Coefficient de forme classique : V = G × H × f
// G = π/4 × (D/100)², H en m, f = coefficient de forme
// ─────────────────────────────────────────────────────
@Serializable
data class CoefFormeEntry(
    val essence: String,
    val minDiam: Int = 0,
    val maxDiam: Int = 999,
    val f: Double              // coefficient de forme (0.35 – 0.55 typiquement)
) {
    fun volume(diamCm: Double, hauteurM: Double): Double {
        if (diamCm <= 0.0 || hauteurM <= 0.0) return 0.0
        val g = Math.PI / 4.0 * Math.pow(diamCm / 100.0, 2.0)
        return g * hauteurM * f
    }
}

// ─────────────────────────────────────────────────────
// Tarif Chaudé (1991) : Tarif à décroissances variables
// V = a × C^b   (C = circonférence à 1m30, en dm, V en m³)
//
// Sept classes sylvicoles couvrant futaie et taillis :
//   F1 — Feuillus nobles futaie (chênes, hêtre)
//   F2 — Feuillus communs futaie (châtaignier, frêne, érable, charme)
//   F3 — Résineux communs (pins, épicéa, if, genévrier)
//   F4 — Résineux à forte croissance (douglas, sapin, mélèze, cèdre)
//   T1 — Taillis feuillu commun (charme, noisetier, tremble, bouleau)
//   T2 — Taillis de châtaignier
//   T3 — Taillis de chêne
//
// Source : Pierre Chaudé (1991). "Tarif de cubage à décroissances
// variables pour les arbres sur pied / Tarif de cubage des taillis
// sur pied."
// ─────────────────────────────────────────────────────
@Serializable
data class ChaudeCoefs(
    val classe: String,
    val a: Double,
    val b: Double,
    val description: String = ""
) {
    fun volumeFromCirconfDm(circonfDm: Double): Double {
        if (circonfDm <= 0.0) return 0.0
        return (a * Math.pow(circonfDm, b)).coerceAtLeast(0.0)
    }

    fun volumeFromDiam(diamCm: Double): Double {
        val cDm = Math.PI * diamCm / 10.0
        return volumeFromCirconfDm(cDm)
    }
}

// ─────────────────────────────────────────────────────
// Configuration de tarif sélectionné par l'utilisateur
// pour une parcelle ou globalement
// ─────────────────────────────────────────────────────
@Serializable
data class TarifSelection(
    val method: String,                       // code de TarifMethod
    val schaefferNumero: Int? = null,         // numéro Schaeffer si applicable
    val ifnNumero: Int? = null,               // numéro IFN si applicable
    val essenceOverrides: Map<String, String>? = null  // essence → method code pour override par essence
)

// ─────────────────────────────────────────────────────
// Système de découpe par produits — configurable par essence
// ─────────────────────────────────────────────────────

/** Types de produits forestiers standards */
enum class ProduitBois(val code: String, val label: String, val shortLabel: String) {
    BOIS_OEUVRE("BO", "Bois d'œuvre", "BO"),
    BOIS_INDUSTRIE("BI", "Bois d'industrie", "BI"),
    BOIS_CHAUFFAGE("BCh", "Bois de chauffage", "BCh"),
    BOIS_ENERGIE("BE", "Bois énergie", "BE"),
    PATE("PATE", "Bois de trituration / pâte", "Pâte"),
    PIQUET("PIQ", "Piquets", "Piq"),
    POTEAU("POT", "Poteaux", "Pot");

    companion object {
        fun fromCode(code: String): ProduitBois? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}

/**
 * Règle de découpe par produit, configurable par essence.
 * Permet de définir pour chaque essence/type d'arbre quelle proportion
 * du volume va dans quel produit selon la classe de diamètre.
 */
@Serializable
data class DecoupeRule(
    val essence: String,         // code essence ou "*" pour wildcard
    val categorie: String?,      // "Feuillu", "Résineux", "Conifère" ou null
    val minDiam: Int,
    val maxDiam: Int,
    val produit: String,         // code ProduitBois
    val pctVolume: Double = 100.0 // % du volume allant vers ce produit (permet ventilation)
)

/**
 * Prix par produit, essence et classe de diamètre.
 * Version enrichie avec distinction par qualité si besoin.
 */
@Serializable
data class PrixProduit(
    val essence: String,         // code ou "*"
    val categorie: String?,      // "Feuillu" / "Résineux" ou null
    val produit: String,         // code ProduitBois
    val minDiam: Int = 0,
    val maxDiam: Int = 999,
    val qualite: String? = null, // "A", "B", "C", "D" ou null
    val eurPerM3: Double
)
