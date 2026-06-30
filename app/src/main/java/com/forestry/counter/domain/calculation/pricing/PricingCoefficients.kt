package com.forestry.counter.domain.calculation.pricing

/**
 * Coefficients régionaux par GRECO — écarts documentés vs moyenne nationale.
 *
 * Source : France Bois Forêt - Cartes écarts régionaux,
 * Fibois Occitanie, Fibois Grand Est, observatoires régionaux.
 *
 * Les écarts sont exprimés en coefficient multiplicateur (1.0 = moyenne nationale).
 * Ils varient par essence mais une valeur moyenne par GRECO est utilisée ici,
 * ajustable par l'utilisateur dans les Settings.
 */
object RegionalCoefficients {

    /**
     * Coefficient régional moyen par GRECO.
     * Basé sur les écarts documentés FBF/Fibois (2024-2025).
     */
    val grecoCoefficients: Map<GrecoRegion, Double> = mapOf(
        GrecoRegion.A to 0.95,  // Grand Ouest : feuillus moyens, moins de demande
        GrecoRegion.B to 1.05,  // Centre Nord : chêne valorisé, proximité IDF
        GrecoRegion.C to 1.10,  // Grand Est : résineux attractifs, filière dense
        GrecoRegion.D to 1.12,  // Vosges : résineux premium, scieries nombreuses
        GrecoRegion.E to 1.15,  // Jura : chêne premium, hêtre, douglas
        GrecoRegion.F to 0.92,  // Sud-Ouest : pin maritime spécifique, douglas bas
        GrecoRegion.G to 0.88,  // Massif Central : douglas inférieur à moyenne
        GrecoRegion.H to 1.08,  // Alpes : résineux montagne, accès difficile
        GrecoRegion.I to 0.90,  // Pyrénées : marché local, accès difficile
        GrecoRegion.J to 1.05,  // Méditerranée : bois énergie premium, pin d'Alep
        GrecoRegion.K to 0.85,  // Corse : faible valeur économique
        GrecoRegion.L to 1.00   // Alluvions : moyenne nationale (azonal)
    )

    /**
     * Coefficients spécifiques par essence × GRECO (prioritaire sur la moyenne).
     * Source : Étude Douglas FBF 2019 (écart >50% Est vs Occitanie),
     * Fibois Occitanie 2025, observatoires régionaux.
     */
    val essenceGrecoCoefficients: Map<Pair<String, GrecoRegion>, Double> = mapOf(
        // Douglas : écart massif Est vs Occitanie/Massif Central
        ("DOUGLAS_VERT" to GrecoRegion.C) to 1.30,   // Grand Est : demande forte
        ("DOUGLAS_VERT" to GrecoRegion.D) to 1.35,   // Vosges : prix record
        ("DOUGLAS_VERT" to GrecoRegion.E) to 1.25,   // Jura : bon marché
        ("DOUGLAS_VERT" to GrecoRegion.G) to 0.70,   // Massif Central : prix bas
        ("DOUGLAS_VERT" to GrecoRegion.I) to 0.65,   // Occitanie/Pyrénées : très bas
        ("DOUGLAS_VERT" to GrecoRegion.F) to 0.75,   // Sud-Ouest : bas

        // Chêne : prime Bourgogne/BFC (Jura, E)
        ("CH_SESSILE" to GrecoRegion.E) to 1.25,     // Jura/BFC : grain fin, réputation
        ("CH_SESSILE" to GrecoRegion.B) to 1.15,     // Centre : demande industrielle
        ("CH_SESSILE" to GrecoRegion.C) to 1.10,     // Grand Est : bon marché
        ("CH_PEDONCULE" to GrecoRegion.E) to 1.20,
        ("CH_PEDONCULE" to GrecoRegion.B) to 1.10,

        // Hêtre : Est et BFC dominent
        ("HETRE_COMMUN" to GrecoRegion.C) to 1.15,
        ("HETRE_COMMUN" to GrecoRegion.E) to 1.20,
        ("HETRE_COMMUN" to GrecoRegion.D) to 1.10,

        // Pin maritime : spécifique Landes (F)
        ("PIN_MARITIME" to GrecoRegion.F) to 1.15,   // Landes : filière intégrée

        // Bois énergie : PACA et IDF plus chers
        // (appliqué via seasonCoefficient pour BCh, mais aussi régional)
    )

    /**
     * Index normalisé (code essence en MAJUSCULES) — évite les ratés silencieux
     * quand l'appelant passe un alias minuscule ou avec espaces (cf. bug casse A4).
     */
    private val normalizedEssenceGreco: Map<Pair<String, GrecoRegion>, Double> =
        essenceGrecoCoefficients.entries.associate { (key, value) ->
            (key.first.trim().uppercase() to key.second) to value
        }

    /**
     * Retourne le coefficient régional pour une essence dans une GRECO.
     * Priorité : coefficient spécifique essence×GRECO > coefficient GRECO moyen.
     * Insensible à la casse / aux espaces sur le code essence.
     */
    fun coefficient(essenceCode: String, region: GrecoRegion): Double {
        val key = essenceCode.trim().uppercase()
        val specific = normalizedEssenceGreco[key to region]
        if (specific != null) return specific
        return grecoCoefficients[region] ?: 1.0
    }
}

/**
 * Coefficient de taille de lot — économie d'échelle.
 * Source : CNPF - Estimer et vendre ses bois, FBF - Méthodologie indicateur.
 *
 * - <50 m³ : pénalité (coûts d'exploitation proportionnellement plus élevés)
 * - 50-200 m³ : prix de référence
 * - >200 m³ : prime (économie d'échelle, attractivité pour acheteurs)
 */
object LotSizeCoefficients {

    fun coefficient(lotVolumeM3: Double?): Double {
        if (lotVolumeM3 == null || lotVolumeM3 <= 0.0) return 1.0
        return when {
            lotVolumeM3 < 50.0 -> 0.85   // -10 à -20%
            lotVolumeM3 < 100.0 -> 0.95  // -5%
            lotVolumeM3 <= 200.0 -> 1.0  // référence
            lotVolumeM3 <= 500.0 -> 1.05 // +5%
            else -> 1.10                 // +10%
        }
    }

    val source: String = "CNPF - Estimer et vendre ses bois"
}
