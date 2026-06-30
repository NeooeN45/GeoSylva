package com.forestry.counter.domain.calculation

/**
 * Calculateur de prix intégrant la qualité du bois et les produits.
 *
 * Coefficients de qualité conformes aux normes :
 * - NF EN 1316-1:2012 — Chêne et hêtre (classes A/B/C/D)
 * - NF EN 1927-1/2/3:2008 — Résineux (épicéa/sapin, pins, mélèze/douglas)
 * - NF B 52-001-1:2018 — Classement structurel (ST-I/C30, ST-II/C24, ST-III/C18)
 *
 * Sources prix :
 * - France Bois Forêt / FCBA : Observatoire économique du bois 2024-2025
 * - ONF : Barèmes des ventes publiques de bois 2023-2025
 * - CNPF / IFC : « Estimer et vendre ses bois » (Fiche Gestion 21)
 * - CEEB : Prix et indices nationaux sciages 2025
 *
 * La qualité a un impact majeur sur le prix (C = référence 1.0) :
 * - Chêne sessile A (merrain) → ×2.80 | D (chauffage) → ×0.55 (écart 5x)
 * - Douglas A (ST-I/C30) → ×1.55 | D (trituration) → ×0.50 (écart 3x)
 * - Noyer A (ébénisterie) → ×3.20 | D (chauffage) → ×0.38 (écart 8x)
 *
 * Exemple concret : Douglas BO base 72 €/m³ (C, référence)
 *   A → 112 €/m³ | B → 86 €/m³ | C → 72 €/m³ | D → 36 €/m³
 */
object PriceCalculator {

    // ═══════════════════════════════════════════════════════════
    // Coefficients de qualité par essence — NF EN 1316-1 / NF EN 1927
    //
    // Sources :
    //  • NF EN 1316-1:2012 — Chêne et hêtre (classes A/B/C/D)
    //  • NF EN 1927-1/2/3:2008 — Résineux (épicéa/sapin, pins, mélèze/douglas)
    //  • ONF « Résultats des ventes de bois » 2023-2025
    //  • France Bois Forêt / FCBA « Observatoire économique » 2024-2025
    //  • CNPF / IFC « Estimer et vendre ses bois » (Fiche Gestion 21)
    //  • CEEB « Prix et indices nationaux sciages » 2025
    //
    // Les coefficients reflètent l'écart de prix réel entre grades :
    //  • Chêne : A (merrain/tranchage) = 4-5× D (chauffage) — écart normé
    //  • Hêtre : A (déroulage) = 3× D (palette) — NF EN 1316-1
    //  • Résineux : ST-I/C30 = 1.5-1.6× ST-II/C24 — NF B 52-001-1
    //  • Feuillus précieux : A = 3-4× D — marché ébénisterie
    //
    // Format : essence → (A, B, C, D) multiplicateurs (C = référence 1.0)
    // ═══════════════════════════════════════════════════════════
    private val qualityCoefficients: Map<String, Map<String, Double>> = mapOf(
        // Chênes — écart A/D = 4-5x (merrain/tranchage vs chauffage)
        // Source : NF EN 1316-1, CEEB 2025 (PLOTS BOULES QBA 1532€ vs QBD ~300€)
        "CH_SESSILE"    to mapOf("A" to 2.80, "B" to 1.80, "C" to 1.00, "D" to 0.55),
        "CH_PEDONCULE"  to mapOf("A" to 2.50, "B" to 1.70, "C" to 1.00, "D" to 0.58),
        "CH_PUBESCENT"  to mapOf("A" to 1.80, "B" to 1.40, "C" to 1.00, "D" to 0.65),
        "CH_ROUGE"      to mapOf("A" to 2.00, "B" to 1.50, "C" to 1.00, "D" to 0.62),
        // Hêtre — écart A/D = 3x (déroulage A vs palette/chauffage D)
        // Source : NF EN 1316-1, FBF 2025
        "HETRE_COMMUN"  to mapOf("A" to 2.20, "B" to 1.50, "C" to 1.00, "D" to 0.45),
        // Douglas — ST-I/C30 = 1.55x ST-II/C24, ST-III/C18 = 0.75x
        // Source : NF EN 1927-3, NF B 52-001-1, FBF 2025 (douglas ~72€/m³ vol.unit. 1.2m³)
        "DOUGLAS_VERT"  to mapOf("A" to 1.55, "B" to 1.20, "C" to 1.00, "D" to 0.50),
        // Sapins / épicéas — ST-I/C30 = 1.55x, ST-III/C18 = 0.75x
        // Source : NF EN 1927-1, NF B 52-001-1
        "SAPIN_PECTINE" to mapOf("A" to 1.55, "B" to 1.20, "C" to 1.00, "D" to 0.55),
        "EPICEA_COMMUN" to mapOf("A" to 1.55, "B" to 1.20, "C" to 1.00, "D" to 0.60),
        "SAPIN_GRANDIS" to mapOf("A" to 1.50, "B" to 1.18, "C" to 1.00, "D" to 0.55),
        // Pins — ST-I/C30 = 1.5x, écart plus faible que sapin/épicéa
        // Source : NF EN 1927-2, NF B 52-001-1
        "PIN_SYLVESTRE" to mapOf("A" to 1.50, "B" to 1.18, "C" to 1.00, "D" to 0.65),
        "PIN_MARITIME"  to mapOf("A" to 1.40, "B" to 1.15, "C" to 1.00, "D" to 0.68),
        "PIN_NOIR_AUTR" to mapOf("A" to 1.45, "B" to 1.15, "C" to 1.00, "D" to 0.65),
        "PIN_LARICIO"   to mapOf("A" to 1.50, "B" to 1.18, "C" to 1.00, "D" to 0.62),
        // Mélèzes — durabilité naturelle classe 1-2, bardage recherché
        // Source : NF EN 1927-3
        "MEL_EUROPE"    to mapOf("A" to 1.65, "B" to 1.25, "C" to 1.00, "D" to 0.55),
        "MEL_JAPON"     to mapOf("A" to 1.55, "B" to 1.22, "C" to 1.00, "D" to 0.55),
        // Feuillus précieux — écart A/D = 3-4x (ébénisterie/lutherie vs chauffage)
        // Source : marché ébénisterie, CNPF
        "FRENE_ELEVE"     to mapOf("A" to 2.30, "B" to 1.60, "C" to 1.00, "D" to 0.45),
        "ERABLE_SYC"      to mapOf("A" to 2.50, "B" to 1.70, "C" to 1.00, "D" to 0.40),
        "NOYER_COMMUN"    to mapOf("A" to 3.20, "B" to 2.00, "C" to 1.00, "D" to 0.38),
        "CERISIER_MERIS"  to mapOf("A" to 2.80, "B" to 1.90, "C" to 1.00, "D" to 0.40),
        "ALISIER_TORMINAL" to mapOf("A" to 2.80, "B" to 1.90, "C" to 1.00, "D" to 0.42),
        "CORMIER"         to mapOf("A" to 3.20, "B" to 2.00, "C" to 1.00, "D" to 0.38),
        // Autres feuillus — écart plus faible
        "CHARME"       to mapOf("A" to 1.30, "B" to 1.10, "C" to 1.00, "D" to 0.70),
        "CHATAIGNIER"  to mapOf("A" to 1.80, "B" to 1.35, "C" to 1.00, "D" to 0.55),
        "ROBINIER"     to mapOf("A" to 2.00, "B" to 1.45, "C" to 1.00, "D" to 0.50),
        "PEUPLIER_HYBR" to mapOf("A" to 1.60, "B" to 1.25, "C" to 1.00, "D" to 0.60),
        // Résineux spéciaux
        "CEDRE_ATLAS"            to mapOf("A" to 1.70, "B" to 1.30, "C" to 1.00, "D" to 0.55),
        "SEQUOIA_TOUJOURS_VERT" to mapOf("A" to 1.60, "B" to 1.25, "C" to 1.00, "D" to 0.60),
        // Wildcard — toute essence non listée (écart moyen A/D = 2.5x)
        "*" to mapOf("A" to 1.80, "B" to 1.30, "C" to 1.00, "D" to 0.55)
    )

    /**
     * Retourne le coefficient de qualité pour une essence et une qualité.
     * Retourne 1.0 si quality == null.
     */
    fun getQualityCoefficient(essenceCode: String, quality: String?): Double {
        if (quality.isNullOrBlank()) return 1.0
        val q = quality.uppercase().firstOrNull()?.toString() ?: return 1.0
        val coefs = qualityCoefficients[essenceCode] ?: qualityCoefficients["*"] ?: return 1.0
        return coefs[q] ?: 1.0
    }

    /**
     * Trouve le meilleur prix de base dans la table pour (essence, produit, diamètre).
     * Résolution : essenceCode exact → wildcard "*" → null.
     */
    fun findBasePrice(
        prices: List<PriceEntry>,
        essenceCode: String,
        product: String,
        diamCm: Int
    ): Double? {
        // Exact match : même essence, même produit, diamètre dans [min..max]
        prices.firstOrNull {
            it.essence.equals(essenceCode, ignoreCase = true) &&
                it.product.equals(product, ignoreCase = true) &&
                diamCm >= it.min && diamCm <= it.max
        }?.let { return it.eurPerM3 }

        // Wildcard essence "*"
        prices.firstOrNull {
            it.essence == "*" &&
                it.product.equals(product, ignoreCase = true) &&
                diamCm >= it.min && diamCm <= it.max
        }?.let { return it.eurPerM3 }

        return null
    }

    /**
     * Prix ajusté = prix_base × coeff_qualité.
     */
    fun adjustedPrice(
        prices: List<PriceEntry>,
        essenceCode: String,
        product: String,
        diamCm: Int,
        quality: String? = null
    ): Double? {
        val base = findBasePrice(prices, essenceCode, product, diamCm) ?: return null
        return base * getQualityCoefficient(essenceCode, quality)
    }

    /**
     * Calcule la ventilation par produit avec prix ajustés à la qualité.
     *
     * @param prices  liste de PriceEntry depuis les paramètres
     * @param essenceCode code essence
     * @param volumeByProduct map produit → volume (ex: {"BO" -> 1.2, "BI" -> 0.3})
     * @param diamCm diamètre moyen pour le lookup prix
     * @param quality qualité globale (A/B/C/D ou null)
     */
    fun buildBreakdown(
        prices: List<PriceEntry>,
        essenceCode: String,
        volumeByProduct: Map<String, Double>,
        diamCm: Int,
        quality: String? = null
    ): List<ProductBreakdownRow> {
        return volumeByProduct.map { (product, volume) ->
            val pricePerM3 = adjustedPrice(prices, essenceCode, product, diamCm, quality) ?: 0.0
            ProductBreakdownRow(
                product = product,
                volumeM3 = volume,
                pricePerM3 = pricePerM3,
                totalEur = pricePerM3 * volume
            )
        }
    }

    /**
     * Version enrichie de [buildBreakdown] qui génère aussi le rapport de calcul professionnel
     * (breakdown transparent des 8 coefficients) pour chaque produit.
     *
     * @param greco région écologique détectée automatiquement (null = moyenne nationale)
     * @param essenceCandidates codes d'essence candidats (alias inclus)
     */
    fun buildBreakdownWithReport(
        prices: List<PriceEntry>,
        essenceCode: String,
        volumeByProduct: Map<String, Double>,
        diamCm: Int,
        quality: String? = null,
        greco: com.forestry.counter.domain.calculation.pricing.GrecoRegion? = null,
        essenceCandidates: List<String> = listOf(essenceCode)
    ): List<ProductBreakdownRow> {
        return volumeByProduct.map { (product, volume) ->
            val context = com.forestry.counter.domain.calculation.pricing.PricingContext(
                essenceCode = essenceCode,
                product = product,
                diamCm = diamCm,
                qualityGrade = quality,
                prices = prices,
                region = greco,
                position = com.forestry.counter.domain.calculation.pricing.SalePosition.SUR_PIED
            )
            val result = com.forestry.counter.domain.calculation.pricing.ProPricingEngine.calculate(context)
            ProductBreakdownRow(
                product = product,
                volumeM3 = volume,
                pricePerM3 = result.finalPricePerM3,
                totalEur = result.finalPricePerM3 * volume,
                pricingReport = result
            )
        }
    }
}

data class ProductBreakdownRow(
    val product: String,
    val volumeM3: Double,
    val pricePerM3: Double,
    val totalEur: Double,
    /** Rapport de calcul professionnel (breakdown des coefficients). Null si non disponible. */
    val pricingReport: com.forestry.counter.domain.calculation.pricing.PricingResult? = null
)
