package com.forestry.counter.data.parameters

import com.forestry.counter.domain.calculation.PriceEntry

/**
 * Prix indicatifs du marché du bois par grande région forestière française.
 *
 * Sources :
 * - France Bois Forêt / FCBA : Observatoire économique
 * - Indicateurs ONF : ventes publiques de bois
 * - CNPF : indices régionaux de prix
 *
 * Ces prix sont des moyennes indicatives (€/m³ bord de route) et varient
 * fortement selon la qualité, le diamètre et les conditions locales.
 * L'utilisateur peut les ajuster dans l'éditeur de prix.
 *
 * Régions disponibles :
 * - NATIONAL : moyenne France métropolitaine
 * - NORD_EST : Grand Est, Bourgogne-Franche-Comté
 * - NORD_OUEST : Normandie, Bretagne, Pays de la Loire
 * - CENTRE : Centre-Val de Loire, Île-de-France
 * - SUD_OUEST : Nouvelle-Aquitaine, Occitanie ouest
 * - SUD_EST : Auvergne-Rhône-Alpes, PACA, Occitanie est
 * - MASSIF_CENTRAL : Auvergne, Limousin, Cévennes
 */
object RegionalPricePresets {

    data class RegionalPreset(
        val code: String,
        val labelFr: String,
        val labelEn: String,
        val prices: List<PriceEntry>
    )

    val ALL: List<RegionalPreset> = listOf(
        nationalPreset(),
        nordEstPreset(),
        nordOuestPreset(),
        centrePreset(),
        sudOuestPreset(),
        sudEstPreset(),
        massifCentralPreset()
    )

    // ═════════════════════════════════════════════════════
    // NATIONAL — Moyenne France métropolitaine
    // ═════════════════════════════════════════════════════
    private fun nationalPreset() = RegionalPreset(
        code = "NATIONAL",
        labelFr = "France entière (moyenne)",
        labelEn = "France average",
        prices = listOf(
            // ── Chênes (BO par qualité) ──
            PriceEntry("CH_SESSILE", "BO", 35, 999, 280.0, "A"),
            PriceEntry("CH_SESSILE", "BO", 35, 999, 160.0, "B"),
            PriceEntry("CH_SESSILE", "BO", 35, 999, 90.0, "C"),
            PriceEntry("CH_SESSILE", "BI", 20, 34, 45.0),
            PriceEntry("CH_SESSILE", "BCh", 0, 19, 28.0),
            PriceEntry("CH_PEDONCULE", "BO", 35, 999, 260.0, "A"),
            PriceEntry("CH_PEDONCULE", "BO", 35, 999, 150.0, "B"),
            PriceEntry("CH_PEDONCULE", "BO", 35, 999, 85.0, "C"),
            PriceEntry("CH_PEDONCULE", "BI", 20, 34, 42.0),
            PriceEntry("CH_PEDONCULE", "BCh", 0, 19, 26.0),
            // ── Hêtre ──
            PriceEntry("HETRE_COMMUN", "BO", 40, 999, 110.0, "A"),
            PriceEntry("HETRE_COMMUN", "BO", 40, 999, 70.0, "B"),
            PriceEntry("HETRE_COMMUN", "BI", 20, 39, 38.0),
            PriceEntry("HETRE_COMMUN", "BCh", 0, 19, 25.0),
            // ── Feuillus divers ──
            PriceEntry("FRENE_ELEVE", "BO", 35, 999, 120.0, "A"),
            PriceEntry("FRENE_ELEVE", "BO", 35, 999, 80.0, "B"),
            PriceEntry("FRENE_ELEVE", "BI", 20, 34, 40.0),
            PriceEntry("ERABLE_SYC", "BO", 35, 999, 90.0),
            PriceEntry("ERABLE_SYC", "BI", 20, 34, 42.0),
            PriceEntry("CHARME", "*", 0, 999, 32.0),
            PriceEntry("CHATAIGNIER", "BO", 30, 999, 75.0),
            PriceEntry("CHATAIGNIER", "BI", 15, 29, 40.0),
            PriceEntry("ROBINIER", "BO", 25, 999, 95.0),
            PriceEntry("ROBINIER", "BI", 10, 24, 55.0),
            PriceEntry("NOYER_COMMUN", "BO", 30, 999, 350.0),
            PriceEntry("CERISIER_MERIS", "BO", 30, 999, 140.0),
            PriceEntry("PEUPLIER_HYBR", "BO", 30, 999, 48.0),
            PriceEntry("PEUPLIER_HYBR", "BI", 15, 29, 25.0),
            // ── Résineux ──
            PriceEntry("DOUGLAS_VERT", "BO", 30, 999, 82.0),
            PriceEntry("DOUGLAS_VERT", "BI", 15, 29, 35.0),
            PriceEntry("DOUGLAS_VERT", "BCh", 0, 14, 18.0),
            PriceEntry("EPICEA_COMMUN", "BO", 25, 999, 52.0),
            PriceEntry("EPICEA_COMMUN", "BI", 15, 24, 28.0),
            PriceEntry("SAPIN_PECTINE", "BO", 25, 999, 55.0),
            PriceEntry("SAPIN_PECTINE", "BI", 15, 24, 30.0),
            PriceEntry("PIN_SYLVESTRE", "BO", 25, 999, 42.0),
            PriceEntry("PIN_SYLVESTRE", "BI", 15, 24, 22.0),
            PriceEntry("PIN_MARITIME", "BO", 25, 999, 38.0),
            PriceEntry("PIN_MARITIME", "BI", 15, 24, 20.0),
            PriceEntry("PIN_NOIR_AUTR", "BO", 25, 999, 40.0),
            PriceEntry("PIN_LARICIO", "BO", 25, 999, 52.0),
            PriceEntry("MEL_EUROPE", "BO", 25, 999, 62.0),
            PriceEntry("MEL_EUROPE", "BI", 15, 24, 30.0),
            PriceEntry("CEDRE_ATLAS", "BO", 30, 999, 55.0),
            PriceEntry("SAPIN_GRANDIS", "BO", 25, 999, 48.0),
            // ── Wildcard fallback ──
            PriceEntry("*", "BO", 35, 999, 55.0),
            PriceEntry("*", "BI", 20, 34, 30.0),
            PriceEntry("*", "BCh", 0, 19, 22.0),
            PriceEntry("*", "PATE", 0, 999, 12.0)
        )
    )

    // ═════════════════════════════════════════════════════
    // NORD-EST — Grand Est, Bourgogne-Franche-Comté
    // Forêts de chêne et hêtre dominantes, résineux Vosges/Jura
    // ═════════════════════════════════════════════════════
    private fun nordEstPreset() = RegionalPreset(
        code = "NORD_EST",
        labelFr = "Nord-Est (Grand Est, BFC)",
        labelEn = "North-East (Grand Est, BFC)",
        prices = listOf(
            PriceEntry("CH_SESSILE", "BO", 35, 999, 200.0, "A"),
            PriceEntry("CH_SESSILE", "BO", 35, 999, 135.0, "B"),
            PriceEntry("CH_SESSILE", "BO", 35, 999, 70.0, "C"),
            PriceEntry("CH_SESSILE", "BI", 20, 34, 48.0),
            PriceEntry("CH_SESSILE", "BCh", 0, 19, 30.0),
            PriceEntry("CH_PEDONCULE", "BO", 35, 999, 185.0, "A"),
            PriceEntry("CH_PEDONCULE", "BO", 35, 999, 125.0, "B"),
            PriceEntry("CH_PEDONCULE", "BI", 20, 34, 45.0),
            PriceEntry("HETRE_COMMUN", "BO", 40, 999, 105.0, "A"),
            PriceEntry("HETRE_COMMUN", "BO", 40, 999, 65.0, "B"),
            PriceEntry("HETRE_COMMUN", "BI", 20, 39, 40.0),
            PriceEntry("HETRE_COMMUN", "BCh", 0, 19, 28.0),
            PriceEntry("EPICEA_COMMUN", "BO", 25, 999, 58.0),
            PriceEntry("EPICEA_COMMUN", "BI", 15, 24, 32.0),
            PriceEntry("SAPIN_PECTINE", "BO", 25, 999, 60.0),
            PriceEntry("SAPIN_PECTINE", "BI", 15, 24, 33.0),
            PriceEntry("DOUGLAS_VERT", "BO", 30, 999, 75.0),
            PriceEntry("DOUGLAS_VERT", "BI", 15, 29, 38.0),
            PriceEntry("PIN_SYLVESTRE", "BO", 25, 999, 45.0),
            PriceEntry("MEL_EUROPE", "BO", 25, 999, 65.0),
            PriceEntry("FRENE_ELEVE", "BO", 35, 999, 85.0),
            PriceEntry("ERABLE_SYC", "BO", 35, 999, 95.0),
            PriceEntry("NOYER_COMMUN", "BO", 30, 999, 280.0),
            PriceEntry("*", "BO", 35, 999, 60.0),
            PriceEntry("*", "BI", 20, 34, 32.0),
            PriceEntry("*", "BCh", 0, 19, 24.0),
            PriceEntry("*", "PATE", 0, 999, 13.0)
        )
    )

    // ═════════════════════════════════════════════════════
    // NORD-OUEST — Normandie, Bretagne, Pays de la Loire
    // Hêtre normand, chêne, douglas en plantation
    // ═════════════════════════════════════════════════════
    private fun nordOuestPreset() = RegionalPreset(
        code = "NORD_OUEST",
        labelFr = "Nord-Ouest (Normandie, Bretagne, PdL)",
        labelEn = "North-West (Normandy, Brittany, PdL)",
        prices = listOf(
            PriceEntry("CH_SESSILE", "BO", 35, 999, 175.0, "A"),
            PriceEntry("CH_SESSILE", "BO", 35, 999, 115.0, "B"),
            PriceEntry("CH_SESSILE", "BI", 20, 34, 43.0),
            PriceEntry("CH_PEDONCULE", "BO", 35, 999, 165.0, "A"),
            PriceEntry("CH_PEDONCULE", "BI", 20, 34, 40.0),
            PriceEntry("HETRE_COMMUN", "BO", 40, 999, 90.0, "A"),
            PriceEntry("HETRE_COMMUN", "BO", 40, 999, 55.0, "B"),
            PriceEntry("HETRE_COMMUN", "BI", 20, 39, 35.0),
            PriceEntry("HETRE_COMMUN", "BCh", 0, 19, 23.0),
            PriceEntry("DOUGLAS_VERT", "BO", 30, 999, 68.0),
            PriceEntry("DOUGLAS_VERT", "BI", 15, 29, 33.0),
            PriceEntry("EPICEA_COMMUN", "BO", 25, 999, 50.0),
            PriceEntry("PIN_MARITIME", "BO", 25, 999, 35.0),
            PriceEntry("CHATAIGNIER", "BO", 30, 999, 80.0),
            PriceEntry("PEUPLIER_HYBR", "BO", 30, 999, 50.0),
            PriceEntry("*", "BO", 35, 999, 52.0),
            PriceEntry("*", "BI", 20, 34, 28.0),
            PriceEntry("*", "BCh", 0, 19, 20.0),
            PriceEntry("*", "PATE", 0, 999, 11.0)
        )
    )

    // ═════════════════════════════════════════════════════
    // CENTRE — Centre-Val de Loire, Île-de-France
    // Chêne de Sologne/Orléanais, pin sylvestre
    // ═════════════════════════════════════════════════════
    private fun centrePreset() = RegionalPreset(
        code = "CENTRE",
        labelFr = "Centre (CVL, Île-de-France)",
        labelEn = "Centre (CVL, Île-de-France)",
        prices = listOf(
            PriceEntry("CH_SESSILE", "BO", 35, 999, 190.0, "A"),
            PriceEntry("CH_SESSILE", "BO", 35, 999, 130.0, "B"),
            PriceEntry("CH_SESSILE", "BO", 35, 999, 68.0, "C"),
            PriceEntry("CH_SESSILE", "BI", 20, 34, 46.0),
            PriceEntry("CH_PEDONCULE", "BO", 35, 999, 180.0, "A"),
            PriceEntry("CH_PEDONCULE", "BO", 35, 999, 120.0, "B"),
            PriceEntry("CH_PEDONCULE", "BI", 20, 34, 44.0),
            PriceEntry("HETRE_COMMUN", "BO", 40, 999, 85.0),
            PriceEntry("HETRE_COMMUN", "BI", 20, 39, 36.0),
            PriceEntry("PIN_SYLVESTRE", "BO", 25, 999, 40.0),
            PriceEntry("PIN_SYLVESTRE", "BI", 15, 24, 20.0),
            PriceEntry("PIN_LARICIO", "BO", 25, 999, 50.0),
            PriceEntry("DOUGLAS_VERT", "BO", 30, 999, 70.0),
            PriceEntry("CHARME", "*", 0, 999, 30.0),
            PriceEntry("CHATAIGNIER", "BO", 30, 999, 72.0),
            PriceEntry("*", "BO", 35, 999, 55.0),
            PriceEntry("*", "BI", 20, 34, 30.0),
            PriceEntry("*", "BCh", 0, 19, 22.0),
            PriceEntry("*", "PATE", 0, 999, 12.0)
        )
    )

    // ═════════════════════════════════════════════════════
    // SUD-OUEST — Nouvelle-Aquitaine, Occitanie ouest
    // Pin maritime des Landes, chêne pédonculé, douglas plateau
    // ═════════════════════════════════════════════════════
    private fun sudOuestPreset() = RegionalPreset(
        code = "SUD_OUEST",
        labelFr = "Sud-Ouest (N-Aquitaine, Occitanie O.)",
        labelEn = "South-West (N-Aquitaine, Occitanie W.)",
        prices = listOf(
            PriceEntry("PIN_MARITIME", "BO", 25, 999, 42.0),
            PriceEntry("PIN_MARITIME", "BI", 15, 24, 22.0),
            PriceEntry("PIN_MARITIME", "PATE", 0, 14, 14.0),
            PriceEntry("CH_PEDONCULE", "BO", 35, 999, 160.0, "A"),
            PriceEntry("CH_PEDONCULE", "BO", 35, 999, 105.0, "B"),
            PriceEntry("CH_PEDONCULE", "BI", 20, 34, 40.0),
            PriceEntry("CH_SESSILE", "BO", 35, 999, 170.0, "A"),
            PriceEntry("CH_SESSILE", "BO", 35, 999, 115.0, "B"),
            PriceEntry("DOUGLAS_VERT", "BO", 30, 999, 68.0),
            PriceEntry("DOUGLAS_VERT", "BI", 15, 29, 32.0),
            PriceEntry("CHATAIGNIER", "BO", 30, 999, 82.0),
            PriceEntry("CHATAIGNIER", "BI", 15, 29, 42.0),
            PriceEntry("ROBINIER", "BO", 25, 999, 100.0),
            PriceEntry("NOYER_COMMUN", "BO", 30, 999, 260.0),
            PriceEntry("PEUPLIER_HYBR", "BO", 30, 999, 45.0),
            PriceEntry("*", "BO", 35, 999, 50.0),
            PriceEntry("*", "BI", 20, 34, 28.0),
            PriceEntry("*", "BCh", 0, 19, 20.0),
            PriceEntry("*", "PATE", 0, 999, 11.0)
        )
    )

    // ═════════════════════════════════════════════════════
    // SUD-EST — Auvergne-Rhône-Alpes, PACA, Occitanie est
    // Résineux montagne (sapin, épicéa), chêne pubescent, pin noir
    // ═════════════════════════════════════════════════════
    private fun sudEstPreset() = RegionalPreset(
        code = "SUD_EST",
        labelFr = "Sud-Est (ARA, PACA, Occitanie E.)",
        labelEn = "South-East (ARA, PACA, Occitanie E.)",
        prices = listOf(
            PriceEntry("SAPIN_PECTINE", "BO", 25, 999, 58.0),
            PriceEntry("SAPIN_PECTINE", "BI", 15, 24, 32.0),
            PriceEntry("EPICEA_COMMUN", "BO", 25, 999, 55.0),
            PriceEntry("EPICEA_COMMUN", "BI", 15, 24, 30.0),
            PriceEntry("DOUGLAS_VERT", "BO", 30, 999, 75.0),
            PriceEntry("DOUGLAS_VERT", "BI", 15, 29, 36.0),
            PriceEntry("MEL_EUROPE", "BO", 25, 999, 68.0),
            PriceEntry("MEL_EUROPE", "BI", 15, 24, 32.0),
            PriceEntry("PIN_NOIR_AUTR", "BO", 25, 999, 42.0),
            PriceEntry("PIN_SYLVESTRE", "BO", 25, 999, 38.0),
            PriceEntry("CEDRE_ATLAS", "BO", 30, 999, 58.0),
            PriceEntry("SAPIN_NORDMANN", "BO", 25, 999, 50.0),
            PriceEntry("CH_PUBESCENT", "BO", 30, 999, 55.0),
            PriceEntry("CH_PUBESCENT", "BCh", 0, 29, 25.0),
            PriceEntry("HETRE_COMMUN", "BO", 40, 999, 88.0),
            PriceEntry("HETRE_COMMUN", "BI", 20, 39, 35.0),
            PriceEntry("NOYER_COMMUN", "BO", 30, 999, 270.0),
            PriceEntry("*", "BO", 35, 999, 52.0),
            PriceEntry("*", "BI", 20, 34, 28.0),
            PriceEntry("*", "BCh", 0, 19, 22.0),
            PriceEntry("*", "PATE", 0, 999, 12.0)
        )
    )

    // ═════════════════════════════════════════════════════
    // MASSIF CENTRAL — Auvergne, Limousin, Cévennes
    // Douglas dominant, sapin, épicéa, hêtre
    // ═════════════════════════════════════════════════════
    private fun massifCentralPreset() = RegionalPreset(
        code = "MASSIF_CENTRAL",
        labelFr = "Massif Central (Auvergne, Limousin)",
        labelEn = "Massif Central (Auvergne, Limousin)",
        prices = listOf(
            PriceEntry("DOUGLAS_VERT", "BO", 30, 999, 78.0),
            PriceEntry("DOUGLAS_VERT", "BI", 15, 29, 38.0),
            PriceEntry("DOUGLAS_VERT", "BCh", 0, 14, 18.0),
            PriceEntry("EPICEA_COMMUN", "BO", 25, 999, 50.0),
            PriceEntry("EPICEA_COMMUN", "BI", 15, 24, 28.0),
            PriceEntry("SAPIN_PECTINE", "BO", 25, 999, 52.0),
            PriceEntry("SAPIN_PECTINE", "BI", 15, 24, 28.0),
            PriceEntry("PIN_SYLVESTRE", "BO", 25, 999, 40.0),
            PriceEntry("HETRE_COMMUN", "BO", 40, 999, 82.0),
            PriceEntry("HETRE_COMMUN", "BI", 20, 39, 34.0),
            PriceEntry("HETRE_COMMUN", "BCh", 0, 19, 24.0),
            PriceEntry("CHATAIGNIER", "BO", 30, 999, 70.0),
            PriceEntry("CH_SESSILE", "BO", 35, 999, 165.0, "A"),
            PriceEntry("CH_SESSILE", "BO", 35, 999, 110.0, "B"),
            PriceEntry("CH_SESSILE", "BI", 20, 34, 42.0),
            PriceEntry("MEL_EUROPE", "BO", 25, 999, 60.0),
            PriceEntry("*", "BO", 35, 999, 48.0),
            PriceEntry("*", "BI", 20, 34, 26.0),
            PriceEntry("*", "BCh", 0, 19, 20.0),
            PriceEntry("*", "PATE", 0, 999, 11.0)
        )
    )
}
