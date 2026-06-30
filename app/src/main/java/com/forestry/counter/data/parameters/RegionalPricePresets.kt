package com.forestry.counter.data.parameters

import com.forestry.counter.domain.calculation.PriceEntry

/**
 * Prix indicatifs du marché du bois par GRECO (Grandes Régions Écologiques).
 *
 * Architecture :
 * - Les prix sont exprimés en €/m³ SUR PIED, qualité C (référence = 1.0).
 * - Le moteur ProPricingEngine applique le coefficient qualité (NF EN 1316/1927)
 *   pour obtenir le prix final selon le grade A/B/C/D.
 * - Les variations régionales sont intégrées dans les prix de chaque GRECO.
 * - L'utilisateur peut ajuster ces prix dans l'éditeur de prix.
 *
 * Sources :
 * - France Bois Forêt / FCBA : Observatoire économique 2024-2025
 * - ONF : Indice de prix moyen des bois vendus sur pied 2023-2025
 * - CNPF / IFC : « Estimer et vendre ses bois » (Fiche Gestion 21)
 * - CEEB : Prix et indices nationaux sciages 2025
 * - Fibois régionaux : observatoires écarts régionaux
 * - IGN : 12 GRECO (régions écologiques officielles A-L)
 *
 * Prix de référence (qualité C, sur pied, €/m³) — marché 2024-2025 :
 * - Chêne sessile BO : 90 €/m³ → A=252€, B=162€, D=49.5€ (×2.80/1.80/0.55)
 * - Hêtre BO : 60 €/m³ → A=132€, B=90€, D=27€ (×2.20/1.50/0.45)
 * - Douglas BO : 72 €/m³ → A=112€, B=86€, D=36€ (×1.55/1.20/0.50)
 * - Sapin/Épicéa BO : 55-60 €/m³ → A=85-93€, B=66-72€, D=33€
 * - Pin sylvestre BO : 42 €/m³ → A=63€, B=50€, D=27€
 * - Bois énergie (BCh) : 25-35 €/m³
 * - Bois industrie (BI) : 30-45 €/m³
 */
object RegionalPricePresets {

    data class RegionalPreset(
        val code: String,
        val labelFr: String,
        val labelEn: String,
        val prices: List<PriceEntry>
    )

    /**
     * Tous les presets disponibles : NATIONAL + 12 GRECO (A-L).
     */
    val ALL: List<RegionalPreset> = listOf(
        nationalPreset(),
        grecoAPreset(),
        grecoBPreset(),
        grecoCPreset(),
        grecoDPreset(),
        grecoEPreset(),
        grecoFPreset(),
        grecoGPreset(),
        grecoHPreset(),
        grecoIPreset(),
        grecoJPreset(),
        grecoKPreset(),
        grecoLPreset()
    )

    // ═════════════════════════════════════════════════════════════════
    // PRIX DE RÉFÉRENCE NATIONAUX (qualité C, sur pied, €/m³, 2024-2025)
    // ═════════════════════════════════════════════════════════════════

    private const val SRC = "FBF/ONF/CNPF 2025"
    private const val UNIT = "EUR/m3 sur pied"
    private const val YEAR = 2025
    private const val UPDATED = "2025-01-15"

    /**
     * Génère la liste de prix de référence nationale (qualité C, sur pied).
     * Utilisée comme base pour tous les presets GRECO.
     */
    private fun nationalBasePrices(): List<PriceEntry> = listOf(
        // ── Chênes (BO = bois d'œuvre, qualité C référence) ──
        PriceEntry("CH_SESSILE", "BO", 35, 999, 90.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CH_SESSILE", "BI", 20, 34, 42.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CH_SESSILE", "BCh", 0, 19, 28.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CH_PEDONCULE", "BO", 35, 999, 80.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CH_PEDONCULE", "BI", 20, 34, 40.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CH_PEDONCULE", "BCh", 0, 19, 26.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CH_PUBESCENT", "BO", 30, 999, 55.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CH_PUBESCENT", "BCh", 0, 29, 25.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CH_ROUGE", "BO", 35, 999, 70.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CH_ROUGE", "BI", 20, 34, 38.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        // ── Hêtre ──
        PriceEntry("HETRE_COMMUN", "BO", 40, 999, 60.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("HETRE_COMMUN", "BI", 20, 39, 38.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("HETRE_COMMUN", "BCh", 0, 19, 25.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        // ── Feuillus précieux ──
        PriceEntry("FRENE_ELEVE", "BO", 35, 999, 80.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("FRENE_ELEVE", "BI", 20, 34, 40.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("ERABLE_SYC", "BO", 35, 999, 70.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("ERABLE_SYC", "BI", 20, 34, 42.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("NOYER_COMMUN", "BO", 30, 999, 120.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CERISIER_MERIS", "BO", 30, 999, 100.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("ALISIER_TORMINAL", "BO", 30, 999, 110.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CORMIER", "BO", 30, 999, 120.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        // ── Autres feuillus ──
        PriceEntry("CHARME", "*", 0, 999, 30.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CHATAIGNIER", "BO", 30, 999, 65.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CHATAIGNIER", "BI", 15, 29, 40.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("ROBINIER", "BO", 25, 999, 85.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("ROBINIER", "BI", 10, 24, 55.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PEUPLIER_HYBR", "BO", 30, 999, 45.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PEUPLIER_HYBR", "BI", 15, 29, 25.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        // ── Résineux ──
        PriceEntry("DOUGLAS_VERT", "BO", 30, 999, 72.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("DOUGLAS_VERT", "BI", 15, 29, 35.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("DOUGLAS_VERT", "BCh", 0, 14, 18.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("EPICEA_COMMUN", "BO", 25, 999, 55.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("EPICEA_COMMUN", "BI", 15, 24, 28.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("EPICEA_COMMUN", "BCh", 0, 14, 16.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("SAPIN_PECTINE", "BO", 25, 999, 60.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("SAPIN_PECTINE", "BI", 15, 24, 30.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("SAPIN_PECTINE", "BCh", 0, 14, 17.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("SAPIN_GRANDIS", "BO", 25, 999, 50.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("SAPIN_NORDMANN", "BO", 25, 999, 50.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PIN_SYLVESTRE", "BO", 25, 999, 42.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PIN_SYLVESTRE", "BI", 15, 24, 22.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PIN_SYLVESTRE", "BCh", 0, 14, 15.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PIN_MARITIME", "BO", 25, 999, 38.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PIN_MARITIME", "BI", 15, 24, 20.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PIN_MARITIME", "PATE", 0, 14, 12.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PIN_NOIR_AUTR", "BO", 25, 999, 40.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PIN_LARICIO", "BO", 25, 999, 52.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("MEL_EUROPE", "BO", 25, 999, 62.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("MEL_EUROPE", "BI", 15, 24, 30.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("MEL_JAPON", "BO", 25, 999, 58.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CEDRE_ATLAS", "BO", 30, 999, 55.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CEDRE_LIBAN", "BO", 30, 999, 55.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("SEQUOIA_TOUJOURS_VERT", "BO", 30, 999, 50.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("SAPIN_GRANDIS", "BO", 25, 999, 50.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("SAPIN_NORDMANN", "BO", 25, 999, 50.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("SAPIN_CEPHALONIE", "BO", 25, 999, 45.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("SAPIN_ESPAGNE", "BO", 25, 999, 48.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("MEL_HYBRIDE", "BO", 25, 999, 60.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("EPICEA_SITKA", "BO", 25, 999, 52.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("EPICEA_OMORIKA", "BO", 25, 999, 50.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PIN_WEYMOUTH", "BO", 25, 999, 45.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PIN_ALEP", "BO", 25, 999, 35.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PIN_PIGNON", "BO", 25, 999, 40.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PIN_CEMBRO", "BO", 25, 999, 55.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PIN_MUGO", "BCh", 0, 999, 20.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PIN_SALZMANN", "BO", 25, 999, 38.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PIN_MONTEREY", "BO", 25, 999, 42.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("THUYA_GEANT", "BO", 25, 999, 45.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CYPRES_PROVENCE", "BO", 25, 999, 40.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CRYPTOMERE", "BO", 25, 999, 48.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CYPRES_CHAUVE", "BO", 25, 999, 38.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("TSUGA_HETEROPHYLLE", "BO", 25, 999, 45.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        // ── Feuillus divers (complément) ──
        PriceEntry("CH_VERT", "BCh", 0, 999, 30.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CH_LIEGE", "BCh", 0, 999, 25.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CH_TAUZIN", "BO", 30, 999, 60.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CH_KERMES", "BCh", 0, 999, 20.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("FRENE_OXYPHYLLE", "BO", 30, 999, 65.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("FRENE_OXYPHYLLE", "BI", 15, 29, 35.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("FRENE_FLEURS", "BCh", 0, 999, 22.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("ERABLE_PLANE", "BO", 30, 999, 65.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("ERABLE_PLANE", "BI", 15, 29, 38.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("ERABLE_CHAMP", "BCh", 0, 999, 25.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("ERABLE_MONTPELLIER", "BCh", 0, 999, 22.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("ERABLE_OBIER", "BCh", 0, 999, 22.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("BOUL_VERRUQ", "BO", 25, 999, 45.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("BOUL_VERRUQ", "BI", 15, 24, 25.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("BOUL_PUBESC", "BI", 15, 24, 22.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("AULNE_GLUT", "BO", 25, 999, 50.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("AULNE_GLUT", "BI", 15, 24, 28.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("AULNE_BLANC", "BCh", 0, 999, 20.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("AULNE_CORSE", "BCh", 0, 999, 22.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("TIL_PET_FEUIL", "BO", 30, 999, 55.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("TIL_PET_FEUIL", "BI", 15, 29, 30.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("TIL_GR_FEUIL", "BO", 30, 999, 55.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("ORME_CHAMP", "BO", 30, 999, 70.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("ORME_LISSE", "BO", 30, 999, 70.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("ORME_MONT", "BO", 30, 999, 65.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("NOYER_NOIR", "BO", 30, 999, 100.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PEUPLIER_NOIR", "BI", 15, 29, 22.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PEUPLIER_TREMB", "BI", 15, 29, 20.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("TREMBLE", "BI", 15, 29, 20.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("SAULE_BLANC", "BI", 15, 29, 18.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("SAULE_FRAGILE", "BCh", 0, 999, 18.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("SAULE_MARSAULT", "BCh", 0, 999, 18.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PLATANE", "BO", 30, 999, 50.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PLATANE", "BI", 15, 29, 28.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("MICOCOULIER", "BO", 25, 999, 55.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("ACACIA_3EPINES", "BI", 10, 24, 45.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("TULIPIER", "BO", 30, 999, 45.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("EUCALYPTUS_GUNNII", "BI", 10, 24, 25.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("EUCALYPTUS_GLOBULUS", "BI", 10, 24, 25.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        // ── Arbustes et petits bois (uniquement bois de chauffage) ──
        PriceEntry("NOISETIER", "BCh", 0, 999, 18.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("SORB_OISEL", "BCh", 0, 999, 22.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("ALISIER_BLANC", "BCh", 0, 999, 22.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("POMMIER_SAUV", "BCh", 0, 999, 25.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("POIRIER_SAUV", "BCh", 0, 999, 25.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("FUSAIN", "BCh", 0, 999, 15.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("HOUX", "BCh", 0, 999, 18.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("MARRONNIER", "BCh", 0, 999, 20.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CORNOUILLER_MALE", "BCh", 0, 999, 18.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("CORNOUILLER_SANG", "BCh", 0, 999, 15.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("SUREAU_NOIR", "BCh", 0, 999, 15.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("AUBEPINE_MONOGYNE", "BCh", 0, 999, 15.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("PRUNELLIER", "BCh", 0, 999, 15.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("BUIS", "BCh", 0, 999, 30.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("TROENE", "BCh", 0, 999, 18.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("VIORNE_LANTANE", "BCh", 0, 999, 15.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("VIORNE_OBIER", "BCh", 0, 999, 15.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("GENETS_SCORPION", "BCh", 0, 999, 12.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("MURIER_BLANC", "BCh", 0, 999, 18.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("FIGUIER", "BCh", 0, 999, 15.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("ARBRE_JUDEE", "BCh", 0, 999, 15.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        // ── Wildcard fallback ──
        PriceEntry("*", "BO", 35, 999, 55.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("*", "BI", 20, 34, 30.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("*", "BCh", 0, 19, 22.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED),
        PriceEntry("*", "PATE", 0, 999, 12.0, null, SRC, "NATIONAL", YEAR, UNIT, UPDATED)
    )

    /**
     * Ajuste les prix de référence nationale par un facteur régional.
     */
    private fun adjustedPrices(basePrices: List<PriceEntry>, factor: Double, regionCode: String): List<PriceEntry> {
        return basePrices.map { entry ->
            entry.copy(
                eurPerM3 = entry.eurPerM3 * factor,
                region = regionCode
            )
        }
    }

    // ═════════════════════════════════════════════════════
    // NATIONAL — Moyenne France métropolitaine (référence)
    // ═════════════════════════════════════════════════════
    private fun nationalPreset() = RegionalPreset(
        code = "NATIONAL",
        labelFr = "France entière (moyenne nationale)",
        labelEn = "France (national average)",
        prices = nationalBasePrices()
    )

    // ═════════════════════════════════════════════════════
    // GRECO A — Grand Ouest cristallin et océanique
    // Bretagne, Normandie, Pays de la Loire — hêtre, chêne, douglas
    // Coefficient : 0.95 (légèrement sous la moyenne)
    // ═════════════════════════════════════════════════════
    private fun grecoAPreset() = RegionalPreset(
        code = "A",
        labelFr = "GRECO A — Grand Ouest (Bretagne, Normandie, PdL)",
        labelEn = "GRECO A — Great West (Brittany, Normandy, PdL)",
        prices = adjustedPrices(nationalBasePrices(), 0.95, "A")
    )

    // ═════════════════════════════════════════════════════
    // GRECO B — Centre Nord semi-océanique
    // Centre-Val de Loire, IDF — chêne de Sologne, pin sylvestre
    // Coefficient : 1.05 (chêne valorisé, proximité IDF)
    // ═════════════════════════════════════════════════════
    private fun grecoBPreset() = RegionalPreset(
        code = "B",
        labelFr = "GRECO B — Centre Nord (CVL, Île-de-France)",
        labelEn = "GRECO B — North-Centre (CVL, IDF)",
        prices = adjustedPrices(nationalBasePrices(), 1.05, "B")
    )

    // ═════════════════════════════════════════════════════
    // GRECO C — Grand Est semi-continental
    // Grand Est — résineux attractifs, filière dense
    // Coefficient : 1.10 (résineux, douglas premium)
    // ═════════════════════════════════════════════════════
    private fun grecoCPreset() = RegionalPreset(
        code = "C",
        labelFr = "GRECO C — Grand Est (Alsace, Lorraine, Champagne)",
        labelEn = "GRECO C — Great East (Alsace, Lorraine, Champagne)",
        prices = adjustedPrices(nationalBasePrices(), 1.10, "C")
    )

    // ═════════════════════════════════════════════════════
    // GRECO D — Vosges
    // Vosges — résineux premium, scieries nombreuses
    // Coefficient : 1.12
    // ═════════════════════════════════════════════════════
    private fun grecoDPreset() = RegionalPreset(
        code = "D",
        labelFr = "GRECO D — Vosges",
        labelEn = "GRECO D — Vosges mountains",
        prices = adjustedPrices(nationalBasePrices(), 1.12, "D")
    )

    // ═════════════════════════════════════════════════════
    // GRECO E — Jura
    // Bourgogne-Franche-Comté — chêne premium, hêtre, douglas
    // Coefficient : 1.15 (chêne BFC, grain fin réputation)
    // ═════════════════════════════════════════════════════
    private fun grecoEPreset() = RegionalPreset(
        code = "E",
        labelFr = "GRECO E — Jura (Bourgogne-Franche-Comté)",
        labelEn = "GRECO E — Jura (Burgundy-Franche-Comté)",
        prices = adjustedPrices(nationalBasePrices(), 1.15, "E")
    )

    // ═════════════════════════════════════════════════════
    // GRECO F — Sud-Ouest océanique
    // Nouvelle-Aquitaine — pin maritime des Landes, douglas plateau
    // Coefficient : 0.92 (pin maritime spécifique, douglas bas)
    // ═════════════════════════════════════════════════════
    private fun grecoFPreset() = RegionalPreset(
        code = "F",
        labelFr = "GRECO F — Sud-Ouest (Nouvelle-Aquitaine, Landes)",
        labelEn = "GRECO F — South-West (Nouvelle-Aquitaine, Landes)",
        prices = adjustedPrices(nationalBasePrices(), 0.92, "F")
    )

    // ═════════════════════════════════════════════════════
    // GRECO G — Massif Central
    // Auvergne, Limousin — douglas dominant, prix inférieurs à moyenne
    // Coefficient : 0.88 (douglas Massif Central très bas vs Est)
    // ═════════════════════════════════════════════════════
    private fun grecoGPreset() = RegionalPreset(
        code = "G",
        labelFr = "GRECO G — Massif Central (Auvergne, Limousin)",
        labelEn = "GRECO G — Massif Central (Auvergne, Limousin)",
        prices = adjustedPrices(nationalBasePrices(), 0.88, "G")
    )

    // ═════════════════════════════════════════════════════
    // GRECO H — Alpes
    // Auvergne-Rhône-Alpes, PACA — résineux montagne, accès difficile
    // Coefficient : 1.08
    // ═════════════════════════════════════════════════════
    private fun grecoHPreset() = RegionalPreset(
        code = "H",
        labelFr = "GRECO H — Alpes",
        labelEn = "GRECO H — Alps",
        prices = adjustedPrices(nationalBasePrices(), 1.08, "H")
    )

    // ═════════════════════════════════════════════════════
    // GRECO I — Pyrénées
    // Occitanie, Pyrénées — marché local, accès difficile
    // Coefficient : 0.90
    // ═════════════════════════════════════════════════════
    private fun grecoIPreset() = RegionalPreset(
        code = "I",
        labelFr = "GRECO I — Pyrénées (Occitanie)",
        labelEn = "GRECO I — Pyrenees (Occitanie)",
        prices = adjustedPrices(nationalBasePrices(), 0.90, "I")
    )

    // ═════════════════════════════════════════════════════
    // GRECO J — Méditerranée
    // PACA, Languedoc — bois énergie premium, pin d'Alep
    // Coefficient : 1.05
    // ═════════════════════════════════════════════════════
    private fun grecoJPreset() = RegionalPreset(
        code = "J",
        labelFr = "GRECO J — Méditerranée (PACA, Languedoc)",
        labelEn = "GRECO J — Mediterranean (PACA, Languedoc)",
        prices = adjustedPrices(nationalBasePrices(), 1.05, "J")
    )

    // ═════════════════════════════════════════════════════
    // GRECO K — Corse
    // Corse — faible valeur économique, intérêt patrimonial
    // Coefficient : 0.85
    // ═════════════════════════════════════════════════════
    private fun grecoKPreset() = RegionalPreset(
        code = "K",
        labelFr = "GRECO K — Corse",
        labelEn = "GRECO K — Corsica",
        prices = adjustedPrices(nationalBasePrices(), 0.85, "K")
    )

    // ═════════════════════════════════════════════════════
    // GRECO L — Alluvions récentes (grandes vallées)
    // Vallées des grands fleuves — azonal, moyenne nationale
    // Coefficient : 1.00
    // ═════════════════════════════════════════════════════
    private fun grecoLPreset() = RegionalPreset(
        code = "L",
        labelFr = "GRECO L — Alluvions récentes (grandes vallées)",
        labelEn = "GRECO L — Recent alluvial plains",
        prices = adjustedPrices(nationalBasePrices(), 1.00, "L")
    )
}
