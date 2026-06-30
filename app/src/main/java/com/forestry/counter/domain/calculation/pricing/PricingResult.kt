package com.forestry.counter.domain.calculation.pricing

/**
 * Résultat du calcul de prix professionnel avec breakdown transparent.
 *
 * Chaque coefficient appliqué est documenté avec sa valeur et sa source,
 * permettant à l'utilisateur de comprendre exactement comment le prix
 * est calculé (transparence pour les professionnels).
 */
data class PricingResult(
    val finalPricePerM3: Double,
    val basePricePerM3: Double,
    val breakdown: PricingBreakdown,
    val appliedDefects: List<AppliedDefect>,
    val warnings: List<String>
) {
    /**
     * Vérifie si le calcul a utilisé des valeurs de fallback (prix non trouvé).
     */
    val usedFallback: Boolean get() = breakdown.basePriceSource.contains("Fallback")

    /**
     * Résumé lisible du calcul pour affichage rapide.
     */
    fun summary(): String = buildString {
        appendLine("Prix final : ${"%.2f".format(finalPricePerM3)} €/m³")
        appendLine("Prix base  : ${"%.2f".format(basePricePerM3)} €/m³")
        appendLine("Qualité    : ×${"%.2f".format(breakdown.qualityCoefficient)} (${breakdown.qualitySource})")
        appendLine("Région     : ×${"%.2f".format(breakdown.regionalCoefficient)} (${breakdown.regionalSource})")
        appendLine("Défauts    : ${"%.0f".format(breakdown.defectDepreciation * 100)}% (${breakdown.defectSource})")
        appendLine("Position   : ×${"%.2f".format(breakdown.positionCoefficient)} (${breakdown.positionSource})")
        appendLine("Accessib.  : ×${"%.2f".format(breakdown.accessibilityCoefficient)} (${breakdown.accessibilitySource})")
        appendLine("Saison     : ×${"%.2f".format(breakdown.seasonCoefficient)} (${breakdown.seasonSource})")
        appendLine("Certif.    : ×${"%.2f".format(breakdown.certificationCoefficient)} (${breakdown.certificationSource})")
        appendLine("Lot        : ×${"%.2f".format(breakdown.lotSizeCoefficient)} (${breakdown.lotSizeSource})")
    }
}

/**
 * Détail de chaque coefficient appliqué dans le calcul.
 */
data class PricingBreakdown(
    val basePricePerM3: Double,
    val basePriceSource: String,
    val qualityCoefficient: Double,
    val qualitySource: String,
    val defectDepreciation: Double,
    val defectSource: String,
    val regionalCoefficient: Double,
    val regionalSource: String,
    val accessibilityCoefficient: Double,
    val accessibilitySource: String,
    val seasonCoefficient: Double,
    val seasonSource: String,
    val certificationCoefficient: Double,
    val certificationSource: String,
    val lotSizeCoefficient: Double,
    val lotSizeSource: String,
    val positionCoefficient: Double,
    val positionSource: String
) {
    /**
     * Coefficient net des défauts = (1 - dépréciation cumulée).
     */
    val defectNetCoefficient: Double get() = 1.0 - defectDepreciation

    /**
     * Coefficient total = produit de tous les coefficients.
     */
    val totalCoefficient: Double
        get() = qualityCoefficient *
            defectNetCoefficient *
            regionalCoefficient *
            accessibilityCoefficient *
            seasonCoefficient *
            certificationCoefficient *
            lotSizeCoefficient *
            positionCoefficient
}

/**
 * Défaut appliqué avec sa dépréciation effective.
 */
data class AppliedDefect(
    val defect: WoodDefect,
    val severity: DefectSeverity,
    val depreciation: Double,
    val source: String
)
