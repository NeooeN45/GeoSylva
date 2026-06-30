package com.forestry.counter.domain.calculation.pricing

import com.forestry.counter.domain.calculation.PriceCalculator
import com.forestry.counter.domain.calculation.PriceEntry
import com.forestry.counter.domain.calculation.quality.DefaultProductPrices
import com.forestry.counter.domain.calculation.quality.WoodQualityGrade

/**
 * Moteur de calcul de prix professionnel — formule à 8 coefficients.
 *
 * Formule :
 * ```
 * Prix final = PrixRéférence(essence, produit, diam, région)
 *           × CoefficientQualité(essence, grade A/B/C/D)        [NF EN 1316/1927]
 *           × (1 - ΣDépréciationDéfauts)                          [NF EN 1310]
 *           × CoefficientRégional(GRECO)                          [écarts FBF]
 *           × CoefficientAccessibilité(pente, distance)           [CNPF]
 *           × CoefficientSaison(mois)                             [CIBE]
 *           × CoefficientCertification(PEFC/FSC)                  [+5-15%]
 *           × CoefficientLot(volume)                              [économie d'échelle]
 *           × CoefficientPosition(sur pied / bord route / usine)  [+25-80%]
 * ```
 *
 * Chaque coefficient est documenté avec sa source officielle.
 * Le résultat inclut un breakdown complet pour transparence.
 *
 * Sources globales :
 * - NF EN 1316-1 (chêne/hêtre), NF EN 1927 (résineux) : classement qualité
 * - NF EN 1310 : mesure des singularités (défauts)
 * - France Bois Forêt / ONF / CNPF / CEEB / CIBE : prix et méthodologie
 * - IGN : 12 GRECO (régions écologiques officielles)
 */
object ProPricingEngine {

    /**
     * Calcule le prix professionnel avec breakdown complet.
     *
     * @param context contexte de calcul (essence, produit, qualité, région, etc.)
     * @return résultat avec prix final et détail de chaque coefficient
     */
    fun calculate(context: PricingContext): PricingResult {
        val warnings = mutableListOf<String>()

        // 1. Prix de référence (lookup dans PriceEntry)
        val basePrice = findBasePrice(context) ?: run {
            warnings.add("Aucun prix de référence trouvé pour ${context.essenceCode}/${context.product}/${context.diamCm}cm — utilisation du fallback DefaultProductPrices")
            fallbackPrice(context)
        }
        val baseSource = if (basePrice == fallbackPrice(context)) {
            "Fallback DefaultProductPrices (essence=${context.essenceCode})"
        } else {
            "PriceEntry (${context.essenceCode}/${context.product}, ${context.diamCm}cm, ${context.year})"
        }

        // 2. Coefficient qualité (NF EN 1316-1 / NF EN 1927)
        val qualityCoef = PriceCalculator.getQualityCoefficient(context.essenceCode, context.qualityGrade)
        val qualitySource = if (context.qualityGrade != null) {
            "NF EN 1316-1/1927 — Grade ${context.qualityGrade}"
        } else {
            "Aucun grade spécifié (×1.0)"
        }

        // 3. Dépréciation des défauts (NF EN 1310)
        val appliedDefects = context.defects.map { (defect, severity) ->
            AppliedDefect(
                defect = defect,
                severity = severity,
                depreciation = defect.depreciation(severity),
                source = defect.normReference
            )
        }
        val totalDepreciation = WoodDefect.cumulativeDepreciation(context.defects)
        val defectSource = if (context.defects.isEmpty()) {
            "Aucun défaut signalé"
        } else {
            "${context.defects.size} défaut(s) — plafonné à ${"%.0f".format(WoodDefect.MAX_TOTAL_DEPRECIATION * 100)}%"
        }

        // 4. Coefficient régional (GRECO)
        val regionalCoef = context.region?.let { RegionalCoefficients.coefficient(context.essenceCode, it) } ?: 1.0
        val regionalSource = context.region?.let {
            "GRECO ${it.code} (${it.labelFr}) — FBF/Cartes écarts régionaux"
        } ?: "Aucune région spécifiée (×1.0)"

        // 5. Coefficient accessibilité
        val accessCoef = context.accessibility.coefficient
        val accessSource = context.accessibility.source

        // 6. Coefficient saison
        val seasonCoef = context.season.coefficient
        val seasonSource = context.season.source

        // 7. Coefficient certification
        val certCoef = context.certification.coefficient
        val certSource = context.certification.source

        // 8. Coefficient taille de lot
        val lotCoef = LotSizeCoefficients.coefficient(context.lotVolumeM3)
        val lotSource = LotSizeCoefficients.source

        // 9. Coefficient position (sur pied / bord route / usine)
        val positionCoef = context.position.coefficient
        val positionSource = context.position.source

        // Assemblage du breakdown
        val breakdown = PricingBreakdown(
            basePricePerM3 = basePrice,
            basePriceSource = baseSource,
            qualityCoefficient = qualityCoef,
            qualitySource = qualitySource,
            defectDepreciation = totalDepreciation,
            defectSource = defectSource,
            regionalCoefficient = regionalCoef,
            regionalSource = regionalSource,
            accessibilityCoefficient = accessCoef,
            accessibilitySource = accessSource,
            seasonCoefficient = seasonCoef,
            seasonSource = seasonSource,
            certificationCoefficient = certCoef,
            certificationSource = certSource,
            lotSizeCoefficient = lotCoef,
            lotSizeSource = lotSource,
            positionCoefficient = positionCoef,
            positionSource = positionSource
        )

        // Calcul du prix final
        val finalPrice = basePrice * breakdown.totalCoefficient

        return PricingResult(
            finalPricePerM3 = finalPrice,
            basePricePerM3 = basePrice,
            breakdown = breakdown,
            appliedDefects = appliedDefects,
            warnings = warnings
        )
    }

    /**
     * Recherche le prix de référence dans la table PriceEntry.
     * Priorité : essence exacte + qualité exacte > essence exacte sans qualité >
     *            wildcard essence + qualité > wildcard essence sans qualité.
     */
    private fun findBasePrice(context: PricingContext): Double? {
        val prices = context.prices
        if (prices.isEmpty()) return null

        val essence = context.essenceCode.trim().uppercase()
        val product = context.product.trim().uppercase()
        val diam = context.diamCm
        val quality = context.qualityGrade?.trim()?.uppercase()

        // Pass 1 : essence exacte, qualité exacte
        prices.firstOrNull { entry ->
            entry.essence.trim().equals(essence, true) &&
                entry.product.trim().equals(product, true) &&
                diam >= entry.min && diam <= entry.max &&
                entry.quality?.trim()?.uppercase() == quality
        }?.let { return it.eurPerM3 }

        // Pass 2 : essence exacte, qualité agnostique (quality null ou "*")
        prices.firstOrNull { entry ->
            entry.essence.trim().equals(essence, true) &&
                entry.product.trim().equals(product, true) &&
                diam >= entry.min && diam <= entry.max &&
                (entry.quality == null || entry.quality.trim() == "*")
        }?.let { return it.eurPerM3 }

        // Pass 3 : wildcard essence "*", qualité exacte
        if (quality != null) {
            prices.firstOrNull { entry ->
                entry.essence.trim() == "*" &&
                    entry.product.trim().equals(product, true) &&
                    diam >= entry.min && diam <= entry.max &&
                    entry.quality?.trim()?.uppercase() == quality
            }?.let { return it.eurPerM3 }
        }

        // Pass 4 : wildcard essence "*", qualité agnostique
        prices.firstOrNull { entry ->
            entry.essence.trim() == "*" &&
                entry.product.trim().equals(product, true) &&
                diam >= entry.min && diam <= entry.max &&
                (entry.quality == null || entry.quality.trim() == "*")
        }?.let { return it.eurPerM3 }

        // Pass 5 : wildcard produit "*"
        prices.firstOrNull { entry ->
            entry.essence.trim().equals(essence, true) &&
                entry.product.trim() == "*" &&
                diam >= entry.min && diam <= entry.max
        }?.let { return it.eurPerM3 }

        return null
    }

    /**
     * Fallback : utilise DefaultProductPrices si aucune entrée PriceEntry trouvée.
     */
    private fun fallbackPrice(context: PricingContext): Double {
        val grade = context.qualityGrade?.let { code ->
            WoodQualityGrade.entries.firstOrNull { it.code.equals(code, true) }
        } ?: WoodQualityGrade.C
        return DefaultProductPrices.priceFor(context.product, context.essenceCode, grade)
    }

    /**
     * Calcule un prix simple (sans breakdown) — pour usage rapide dans les boucles.
     */
    fun quickPrice(
        essenceCode: String,
        product: String,
        diamCm: Int,
        qualityGrade: String? = null,
        prices: List<PriceEntry> = emptyList(),
        region: GrecoRegion? = null,
        position: SalePosition = SalePosition.SUR_PIED
    ): Double {
        val context = PricingContext(
            essenceCode = essenceCode,
            product = product,
            diamCm = diamCm,
            qualityGrade = qualityGrade,
            prices = prices,
            region = region,
            position = position
        )
        return calculate(context).finalPricePerM3
    }

    /**
     * Calcule le prix uniquement si trouvé dans PriceEntry (sans fallback DefaultProductPrices).
     * Retourne null si aucun prix PriceEntry ne correspond.
     * Applique le coefficient qualité et les autres coefficients.
     *
     * Utilisé par ForestryCalculator pour préserver la logique de fallback existante
     * (essai defaultProd → DefaultProductPrices).
     *
     * @param essenceCandidates liste de codes d'essence candidats (alias inclus)
     */
    fun calculateFromEntryOnly(
        essenceCode: String,
        product: String,
        diamCm: Int,
        qualityGrade: String? = null,
        prices: List<PriceEntry> = emptyList(),
        position: SalePosition = SalePosition.SUR_PIED,
        essenceCandidates: List<String> = listOf(essenceCode)
    ): Double? {
        val basePrice = findBasePriceWithCandidates(essenceCandidates, product, diamCm, qualityGrade, prices) ?: return null
        val qualityCoef = PriceCalculator.getQualityCoefficient(essenceCode, qualityGrade)
        val defectNet = 1.0 - WoodDefect.cumulativeDepreciation(emptyList())
        val lotCoef = LotSizeCoefficients.coefficient(null)
        return basePrice * qualityCoef * defectNet *
            Accessibility.FACILE.coefficient * SaleSeason.NEUTRE.coefficient *
            Certification.AUCUNE.coefficient * lotCoef * position.coefficient
    }

    /**
     * Recherche le prix de référence dans PriceEntry en essayant plusieurs codes d'essence candidats.
     * Priorité : essence exacte + qualité exacte > essence exacte sans qualité >
     *            wildcard essence + qualité > wildcard essence sans qualité.
     */
    private fun findBasePriceWithCandidates(
        essenceCandidates: List<String>,
        product: String,
        diam: Int,
        quality: String?,
        prices: List<PriceEntry>
    ): Double? {
        if (prices.isEmpty()) return null
        val productUpper = product.trim().uppercase()
        val qualityUpper = quality?.trim()?.uppercase()

        for (essence in essenceCandidates) {
            val essenceUpper = essence.trim().uppercase()

            // Pass 1 : essence exacte, qualité exacte
            prices.firstOrNull { entry ->
                entry.essence.trim().equals(essenceUpper, true) &&
                    entry.product.trim().equals(productUpper, true) &&
                    diam >= entry.min && diam <= entry.max &&
                    entry.quality?.trim()?.uppercase() == qualityUpper
            }?.let { return it.eurPerM3 }

            // Pass 2 : essence exacte, qualité agnostique
            prices.firstOrNull { entry ->
                entry.essence.trim().equals(essenceUpper, true) &&
                    entry.product.trim().equals(productUpper, true) &&
                    diam >= entry.min && diam <= entry.max &&
                    (entry.quality == null || entry.quality.trim() == "*")
            }?.let { return it.eurPerM3 }
        }

        // Pass 3 : wildcard essence "*", qualité exacte
        if (qualityUpper != null) {
            prices.firstOrNull { entry ->
                entry.essence.trim() == "*" &&
                    entry.product.trim().equals(productUpper, true) &&
                    diam >= entry.min && diam <= entry.max &&
                    entry.quality?.trim()?.uppercase() == qualityUpper
            }?.let { return it.eurPerM3 }
        }

        // Pass 4 : wildcard essence "*", qualité agnostique
        prices.firstOrNull { entry ->
            entry.essence.trim() == "*" &&
                entry.product.trim().equals(productUpper, true) &&
                diam >= entry.min && diam <= entry.max &&
                (entry.quality == null || entry.quality.trim() == "*")
        }?.let { return it.eurPerM3 }

        // Pass 5 : wildcard produit "*" pour chaque candidat essence
        for (essence in essenceCandidates) {
            val essenceUpper = essence.trim().uppercase()
            prices.firstOrNull { entry ->
                entry.essence.trim().equals(essenceUpper, true) &&
                    entry.product.trim() == "*" &&
                    diam >= entry.min && diam <= entry.max
            }?.let { return it.eurPerM3 }
        }

        return null
    }
}
