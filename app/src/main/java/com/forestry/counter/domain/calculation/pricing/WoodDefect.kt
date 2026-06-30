package com.forestry.counter.domain.calculation.pricing

/**
 * Catalogue des défauts du bois et leur impact sur le prix.
 *
 * Source : NF EN 1310 (mesure des singularités), NF EN 1316-1 (chêne/hêtre),
 * NF EN 1927 (résineux), NF EN 1309-3 (altérations biologiques),
 * documentation CNPF/ONF/FCBA.
 *
 * Chaque défaut a un impact chiffré (dépréciation en %) qui se cumule
 * dans l'évaluation finale, avec un plafond de -90% (le bois conserve
 * toujours une valeur minimale pour le chauffage/industrie).
 *
 * La sévérité permet de moduler l'impact : MINEUR (borne basse),
 * MODERE (milieu), MAJEUR (borne haute).
 */
enum class WoodDefect(
    val code: String,
    val labelFr: String,
    val labelEn: String,
    val category: DefectCategory,
    val depreciationRange: ClosedFloatingPointRange<Double>,
    val normReference: String
) {
    // ── Défauts de forme et structure (NF EN 1316-1, NF EN 1927) ──
    COURBURE(
        code = "COURBURE",
        labelFr = "Courbure / Flèche",
        labelEn = "Bow / Sweep",
        category = DefectCategory.FORME,
        depreciationRange = 0.10..0.40,
        normReference = "NF EN 1316-1, NF EN 1927"
    ),
    OVALISATION(
        code = "OVALISATION",
        labelFr = "Ovalisation / Excentricité",
        labelEn = "Ovality / Eccentricity",
        category = DefectCategory.FORME,
        depreciationRange = 0.05..0.20,
        normReference = "NF EN 1309-3"
    ),
    CONICITE(
        code = "CONICITE",
        labelFr = "Conicité / Défilement",
        labelEn = "Taper",
        category = DefectCategory.FORME,
        depreciationRange = 0.10..0.30,
        normReference = "NF EN 1316-1"
    ),
    FOURCHE(
        code = "FOURCHE",
        labelFr = "Fourche",
        labelEn = "Fork",
        category = DefectCategory.FORME,
        depreciationRange = 0.25..0.50,
        normReference = "Guide ONF martelage"
    ),
    CANNELURE(
        code = "CANNELURE",
        labelFr = "Cannelure",
        labelEn = "Fluting",
        category = DefectCategory.FORME,
        depreciationRange = 0.05..0.15,
        normReference = "Guide évaluation qualité"
    ),
    MEPLAT(
        code = "MEPLAT",
        labelFr = "Méplat",
        labelEn = "Flat side",
        category = DefectCategory.FORME,
        depreciationRange = 0.10..0.25,
        normReference = "NF EN 1316-1"
    ),
    EMPATTEMENT(
        code = "EMPATTEMENT",
        labelFr = "Empattement / Contrefort",
        labelEn = "Root flare / Buttress",
        category = DefectCategory.FORME,
        depreciationRange = 0.15..0.35,
        normReference = "Guide évaluation qualité"
    ),

    // ── Défauts de croissance (NF EN 1310) ──
    NOEUDS_SAINS(
        code = "NOEUDS_SAINS",
        labelFr = "Nœuds sains adhérents",
        labelEn = "Sound knots",
        category = DefectCategory.CROISSANCE,
        depreciationRange = 0.05..0.30,
        normReference = "NF EN 1310"
    ),
    NOEUDS_NOIRS(
        code = "NOEUDS_NOIRS",
        labelFr = "Nœuds noirs / morts",
        labelEn = "Dead / black knots",
        category = DefectCategory.CROISSANCE,
        depreciationRange = 0.15..0.40,
        normReference = "NF EN 1310"
    ),
    NOEUDS_SAUTANTS(
        code = "NOEUDS_SAUTANTS",
        labelFr = "Nœuds sautants / tombants",
        labelEn = "Loose / falling knots",
        category = DefectCategory.CROISSANCE,
        depreciationRange = 0.25..0.50,
        normReference = "NF B 52-001-1"
    ),
    NOEUDS_POURRIS(
        code = "NOEUDS_POURRIS",
        labelFr = "Nœuds pourris",
        labelEn = "Rotten knots",
        category = DefectCategory.CROISSANCE,
        depreciationRange = 0.40..0.70,
        normReference = "NF EN 1310"
    ),
    GOURMANDS(
        code = "GOURMANDS",
        labelFr = "Gourmands / Picots",
        labelEn = "Epicormic shoots",
        category = DefectCategory.CROISSANCE,
        depreciationRange = 0.05..0.20,
        normReference = "Guide CNPF"
    ),
    BROUSSINS(
        code = "BROUSSINS",
        labelFr = "Broussins / Brogne",
        labelEn = "Burry / Burr grain",
        category = DefectCategory.CROISSANCE,
        depreciationRange = 0.20..0.40,
        normReference = "Guide CNPF"
    ),
    FIBRE_TORSE(
        code = "FIBRE_TORSE",
        labelFr = "Fibre torse / Bois ondé",
        labelEn = "Twist / Wavy grain",
        category = DefectCategory.CROISSANCE,
        depreciationRange = 0.10..0.30,
        normReference = "NF EN 1310"
    ),

    // ── Fentes et fissures ──
    GELIVURE(
        code = "GELIVURE",
        labelFr = "Gélivure",
        labelEn = "Frost crack",
        category = DefectCategory.FENTES,
        depreciationRange = 0.10..0.35,
        normReference = "Guide CNPF"
    ),
    GERCES(
        code = "GERCES",
        labelFr = "Gerces / Gerçures",
        labelEn = "Shakes / Surface checks",
        category = DefectCategory.FENTES,
        depreciationRange = 0.05..0.20,
        normReference = "NF EN 1310"
    ),
    FENTES_RETRAIT(
        code = "FENTES_RETRAIT",
        labelFr = "Fentes de retrait",
        labelEn = "Seasoning checks",
        category = DefectCategory.FENTES,
        depreciationRange = 0.15..0.40,
        normReference = "Guide CNPF"
    ),
    FENTE_COEUR(
        code = "FENTE_COEUR",
        labelFr = "Fente de cœur",
        labelEn = "Heart shake",
        category = DefectCategory.FENTES,
        depreciationRange = 0.20..0.45,
        normReference = "Guide CNPF"
    ),
    COEUR_ETOILE(
        code = "COEUR_ETOILE",
        labelFr = "Cœur étoilé / Cadranure",
        labelEn = "Star shake",
        category = DefectCategory.FENTES,
        depreciationRange = 0.25..0.50,
        normReference = "Guide CNPF"
    ),
    ROULURE(
        code = "ROULURE",
        labelFr = "Roulure",
        labelEn = "Ring shake",
        category = DefectCategory.FENTES,
        depreciationRange = 0.15..0.40,
        normReference = "NF EN 1310"
    ),

    // ── Altérations biologiques (NF EN 1309-3) ──
    POURRITURE_CUBIQUE(
        code = "POURRITURE_CUBIQUE",
        labelFr = "Pourriture cubique",
        labelEn = "Brown rot / Cubic rot",
        category = DefectCategory.BIOLOGIQUE,
        depreciationRange = 0.80..1.00,
        normReference = "NF EN 1309-3"
    ),
    POURRITURE_FIBREUSE(
        code = "POURRITURE_FIBREUSE",
        labelFr = "Pourriture fibreuse",
        labelEn = "White rot / Fibrous rot",
        category = DefectCategory.BIOLOGIQUE,
        depreciationRange = 0.80..1.00,
        normReference = "NF EN 1309-3"
    ),
    ECHAUFFURE(
        code = "ECHAUFFURE",
        labelFr = "Échauffure / Bois passé",
        labelEn = "Incipient decay / Stained wood",
        category = DefectCategory.BIOLOGIQUE,
        depreciationRange = 0.10..0.25,
        normReference = "NF EN 1309-3"
    ),
    BLEUISSEMENT(
        code = "BLEUISSEMENT",
        labelFr = "Bleuissement / Moisissure",
        labelEn = "Blue stain / Mould",
        category = DefectCategory.BIOLOGIQUE,
        depreciationRange = 0.05..0.20,
        normReference = "NF EN 1309-3"
    ),
    COEUR_ROUGE(
        code = "COEUR_ROUGE",
        labelFr = "Cœur rouge (hêtre)",
        labelEn = "Red heart (beech)",
        category = DefectCategory.BIOLOGIQUE,
        depreciationRange = 0.20..0.40,
        normReference = "NF EN 1316-1"
    ),
    COEUR_COLORE(
        code = "COEUR_COLORE",
        labelFr = "Cœur coloré (chêne)",
        labelEn = "Coloured heart (oak)",
        category = DefectCategory.BIOLOGIQUE,
        depreciationRange = 0.10..0.30,
        normReference = "NF EN 1316-1"
    ),
    PIQURES_INSECTES(
        code = "PIQURES_INSECTES",
        labelFr = "Piqûres d'insectes",
        labelEn = "Insect borer holes",
        category = DefectCategory.BIOLOGIQUE,
        depreciationRange = 0.05..0.15,
        normReference = "NF EN 1309-3"
    ),
    GALERIES_CAPRICORNE(
        code = "GALERIES_CAPRICORNE",
        labelFr = "Galeries capricorne",
        labelEn = "Longhorn beetle galleries",
        category = DefectCategory.BIOLOGIQUE,
        depreciationRange = 0.40..0.80,
        normReference = "NF EN 1309-3"
    ),
    CHANCRE(
        code = "CHANCRE",
        labelFr = "Chancre / Chaudron",
        labelEn = "Canker",
        category = DefectCategory.BIOLOGIQUE,
        depreciationRange = 0.20..0.50,
        normReference = "NF EN 1309-3"
    ),

    // ── Défauts spécifiques résineux ──
    BOIS_COMPRESSION(
        code = "BOIS_COMPRESSION",
        labelFr = "Bois de compression",
        labelEn = "Compression wood",
        category = DefectCategory.RESINEUX,
        depreciationRange = 0.15..0.40,
        normReference = "NF B 52-001-1"
    ),
    POCHE_RESINE(
        code = "POCHE_RESINE",
        labelFr = "Poche de résine",
        labelEn = "Resin pocket",
        category = DefectCategory.RESINEUX,
        depreciationRange = 0.05..0.20,
        normReference = "NF EN 1927"
    ),

    // ── Défauts spécifiques feuillus ──
    ENTRE_ECORCE(
        code = "ENTRE_ECORCE",
        labelFr = "Entre-écorce",
        labelEn = "Included bark",
        category = DefectCategory.FEUILLUS,
        depreciationRange = 0.20..0.50,
        normReference = "NF EN 1316-1"
    ),
    DOUBLE_AUBIER(
        code = "DOUBLE_AUBIER",
        labelFr = "Double aubier / Lunure",
        labelEn = "Double sapwood / Moon ring",
        category = DefectCategory.FEUILLUS,
        depreciationRange = 0.05..0.15,
        normReference = "Guide CNPF"
    ),
    AUBIER_EXCESSIF(
        code = "AUBIER_EXCESSIF",
        labelFr = "Aubier excessif",
        labelEn = "Excessive sapwood",
        category = DefectCategory.FEUILLUS,
        depreciationRange = 0.10..0.25,
        normReference = "NF EN 350-2"
    );

    /**
     * Calcule la dépréciation (en fraction 0-1) selon la sévérité.
     */
    fun depreciation(severity: DefectSeverity): Double {
        val (min, max) = depreciationRange.start to depreciationRange.endInclusive
        return when (severity) {
            DefectSeverity.MINEUR -> min
            DefectSeverity.MODERE -> (min + max) / 2.0
            DefectSeverity.MAJEUR -> max
        }
    }

    companion object {
        /** Plafond de dépréciation cumulée : le bois conserve toujours 10% de sa valeur (chauffage). */
        const val MAX_TOTAL_DEPRECIATION: Double = 0.90

        /**
         * Recherche un défaut par son code (insensible à la casse).
         */
        fun fromCode(code: String): WoodDefect? {
            val upper = code.trim().uppercase()
            return entries.firstOrNull { it.code == upper }
        }

        /**
         * Calcule la dépréciation cumulée plafonnée pour une liste de défauts.
         * Retourne une fraction 0-1 (ex: 0.35 = -35%).
         */
        fun cumulativeDepreciation(defects: List<Pair<WoodDefect, DefectSeverity>>): Double {
            if (defects.isEmpty()) return 0.0
            val raw = defects.sumOf { (defect, severity) -> defect.depreciation(severity) }
            return raw.coerceAtMost(MAX_TOTAL_DEPRECIATION)
        }
    }
}

enum class DefectCategory(val labelFr: String, val labelEn: String) {
    FORME("Forme et structure", "Form and structure"),
    CROISSANCE("Croissance", "Growth"),
    FENTES("Fentes et fissures", "Shakes and cracks"),
    BIOLOGIQUE("Altérations biologiques", "Biological alterations"),
    RESINEUX("Spécifiques résineux", "Conifer-specific"),
    FEUILLUS("Spécifiques feuillus", "Broadleaf-specific")
}

enum class DefectSeverity(val labelFr: String, val labelEn: String) {
    MINEUR("Mineur", "Minor"),
    MODERE("Modéré", "Moderate"),
    MAJEUR("Majeur", "Major")
}
