package com.forestry.counter.domain.calculation.pricing

import com.forestry.counter.domain.calculation.PriceEntry

/**
 * Contexte d'entrée pour le moteur de calcul professionnel.
 *
 * Contient tous les paramètres nécessaires pour calculer un prix
 * avec les 8 coefficients de la formule professionnelle.
 *
 * @param essenceCode Code essence (ex: "CH_SESSILE", "DOUGLAS_VERT")
 * @param product Code produit (BO, BI, BCh, PATE)
 * @param diamCm Diamètre à 1,30 m en cm
 * @param qualityGrade Grade qualité (A, B, C, D) — NF EN 1316-1 / NF EN 1927
 * @param region GRECO de la parcelle (A-L) — pour coefficient régional
 * @param position Position de vente (sur pied, bord de route, usine)
 * @param accessibility Niveau d'accessibilité de la parcelle
 * @param season Saison de vente/exploitation
 * @param certification Certification forestière (PEFC, FSC, aucune)
 * @param lotVolumeM3 Volume total du lot en m³ (pour économie d'échelle)
 * @param defects Liste des défauts présents avec leur sévérité
 * @param prices Table de prix de référence (PriceEntry) pour la région
 * @param year Année de référence pour les prix (ex: 2025)
 */
data class PricingContext(
    val essenceCode: String,
    val product: String,
    val diamCm: Int,
    val qualityGrade: String? = null,
    val region: GrecoRegion? = null,
    val position: SalePosition = SalePosition.SUR_PIED,
    val accessibility: Accessibility = Accessibility.FACILE,
    val season: SaleSeason = SaleSeason.NEUTRE,
    val certification: Certification = Certification.AUCUNE,
    val lotVolumeM3: Double? = null,
    val defects: List<Pair<WoodDefect, DefectSeverity>> = emptyList(),
    val prices: List<PriceEntry> = emptyList(),
    val year: Int = 2025
)

/**
 * Position de vente — impact majeur sur le prix (sur pied vs façonné vs livré).
 * Source : ONF - Modes de vente, Magazine Bois - Transport grumier.
 */
enum class SalePosition(
    val code: String,
    val labelFr: String,
    val labelEn: String,
    val coefficient: Double,
    val source: String
) {
    SUR_PIED(
        code = "SUR_PIED",
        labelFr = "Sur pied (référence)",
        labelEn = "Standing (reference)",
        coefficient = 1.0,
        source = "ONF - Modes de vente"
    ),
    BORD_ROUTE(
        code = "BORD_ROUTE",
        labelFr = "Bord de route (façonné)",
        labelEn = "Roadside (façonné)",
        coefficient = 1.32,
        source = "ONF - Coûts d'exploitation inclus (+25-40%)"
    ),
    USINE(
        code = "USINE",
        labelFr = "Livré usine",
        labelEn = "Delivered to mill",
        coefficient = 1.65,
        source = "Magazine Bois - Transport grumier (+50-80%)"
    );

    companion object {
        fun fromCode(code: String): SalePosition? {
            val upper = code.trim().uppercase()
            return entries.firstOrNull { it.code == upper }
        }
    }
}

/**
 * Accessibilité de la parcelle — impact sur les coûts d'exploitation.
 * Source : CNPF - Le prix des bois, Techniforet - Études prix, Coforet.
 */
enum class Accessibility(
    val code: String,
    val labelFr: String,
    val labelEn: String,
    val coefficient: Double,
    val source: String
) {
    FACILE(
        code = "FACILE",
        labelFr = "Facile (plaine, route <200m)",
        labelEn = "Easy (flat, road <200m)",
        coefficient = 1.0,
        source = "CNPF - Le prix des bois"
    ),
    NORMALE(
        code = "NORMALE",
        labelFr = "Normale (pente <15%, route <500m)",
        labelEn = "Normal (slope <15%, road <500m)",
        coefficient = 0.95,
        source = "CNPF - Le prix des bois"
    ),
    DIFFICILE(
        code = "DIFFICILE",
        labelFr = "Difficile (pente 15-30%, route >500m)",
        labelEn = "Difficult (slope 15-30%, road >500m)",
        coefficient = 0.88,
        source = "Coforet - Exploitation en forte pente"
    ),
    TRES_DIFFICILE(
        code = "TRES_DIFFICILE",
        labelFr = "Très difficile (pente >30%, câble/hélico)",
        labelEn = "Very difficult (slope >30%, cable/helicopter)",
        coefficient = 0.75,
        source = "Coforet - Exploitation en forte pente"
    );

    companion object {
        fun fromCode(code: String): Accessibility? {
            val upper = code.trim().uppercase()
            return entries.firstOrNull { it.code == upper }
        }
    }
}

/**
 * Saison de vente — impact surtout sur le bois énergie.
 * Source : CIBE - Prix du bois énergie, FBF - Indicateurs trimestriels.
 */
enum class SaleSeason(
    val code: String,
    val labelFr: String,
    val labelEn: String,
    val coefficient: Double,
    val source: String
) {
    NEUTRE(
        code = "NEUTRE",
        labelFr = "Neutre (toute saison)",
        labelEn = "Neutral (any season)",
        coefficient = 1.0,
        source = "CIBE - Prix du bois énergie"
    ),
    HIVER(
        code = "HIVER",
        labelFr = "Hiver (demande forte bois énergie)",
        labelEn = "Winter (high demand firewood)",
        coefficient = 1.15,
        source = "CIBE - Prix du bois énergie (+10-20%)"
    ),
    ETE(
        code = "ETE",
        labelFr = "Été (demande faible, chantiers faciles)",
        labelEn = "Summer (low demand, easy logging)",
        coefficient = 0.95,
        source = "CIBE - Prix du bois énergie (-5-10%)"
    ),
    AUTOMNE(
        code = "AUTOMNE",
        labelFr = "Automne (préparation hiver)",
        labelEn = "Autumn (winter preparation)",
        coefficient = 1.05,
        source = "CIBE - Prix du bois énergie"
    );

    companion object {
        fun fromCode(code: String): SaleSeason? {
            val upper = code.trim().uppercase()
            return entries.firstOrNull { it.code == upper }
        }
    }
}

/**
 * Certification forestière — premium pour gestion durable.
 * Source : PEFC France, FSC France, ONF - Certifications.
 */
enum class Certification(
    val code: String,
    val labelFr: String,
    val labelEn: String,
    val coefficient: Double,
    val source: String
) {
    AUCUNE(
        code = "AUCUNE",
        labelFr = "Aucune certification",
        labelEn = "No certification",
        coefficient = 1.0,
        source = "PEFC France / FSC France"
    ),
    PEFC(
        code = "PEFC",
        labelFr = "PEFC",
        labelEn = "PEFC",
        coefficient = 1.08,
        source = "PEFC France (+5-10%)"
    ),
    FSC(
        code = "FSC",
        labelFr = "FSC",
        labelEn = "FSC",
        coefficient = 1.10,
        source = "FSC France (+5-15%)"
    ),
    PEFC_FSC(
        code = "PEFC_FSC",
        labelFr = "PEFC + FSC (double certification)",
        labelEn = "PEFC + FSC (dual certification)",
        coefficient = 1.12,
        source = "PEFC France / FSC France (+10-15%)"
    );

    companion object {
        fun fromCode(code: String): Certification? {
            val upper = code.trim().uppercase()
            return entries.firstOrNull { it.code == upper }
        }
    }
}
