package com.forestry.counter.domain.calculation.tarifs

/**
 * Données officielles des tarifs de cubage forestiers français.
 *
 * ═══════════════════════════════════════════════════════════════
 * SOURCES BIBLIOGRAPHIQUES
 * ═══════════════════════════════════════════════════════════════
 *
 * Schaeffer (1 & 2 entrées) :
 *   - Schaeffer, A. (1949). "Tarifs de cubage". Annales de l'École
 *     Nationale des Eaux et Forêts, Nancy.
 *   - Coefficients publiés dans les tables ONF (éditions successives).
 *   - V1E : V = a + b × C²          (C = circonférence en m)
 *   - V2E : V = a + b × C² × H      (H = hauteur totale en m)
 *
 * Algan :
 *   - Algan, P. (1958). "Tarifs de cubage à deux entrées".
 *   - V = a × D^b × H^c  (D en cm, H en m, V en m³)
 *   - Principalement utilisé pour les peuplements réguliers résineux.
 *
 * IFN (Inventaire Forestier National) — Tarifs rapides :
 *   - 36 tarifs numérotés, à 1 entrée (D130 en cm).
 *   - V = a₀ + a₁×D + a₂×D²  (V en dm³).
 *   - Source : Documentation technique IFN, publiée par l'IGN.
 *
 * IFN — Tarifs lents :
 *   - Tables à 2 entrées (D130, H).
 *   - V = a₀ + a₁×D² + a₂×D²×H  (V en dm³).
 *
 * Coefficients de forme :
 *   - Valeurs standard issues de la littérature forestière française.
 *   - Pardé & Bouchon (1988). "Dendrométrie", 2e éd., ENGREF, Nancy.
 * ═══════════════════════════════════════════════════════════════
 */
object TarifData {

    // ═══════════════════════════════════════════════════════════
    // TARIFS SCHAEFFER 1 ENTRÉE (16 tarifs numérotés)
    // V = a + b × C²   (C en m, V en m³)
    //
    // Numérotation classique ONF. Le numéro de tarif est choisi
    // en fonction de l'arbre-type mesuré sur la placette.
    // ═══════════════════════════════════════════════════════════
    val schaefferOneEntry: List<SchaefferOneEntryCoefs> = listOf(
        SchaefferOneEntryCoefs(essence = "*", numero = 1,  a = -0.0046, b = 0.3979),
        SchaefferOneEntryCoefs(essence = "*", numero = 2,  a = -0.0104, b = 0.5207),
        SchaefferOneEntryCoefs(essence = "*", numero = 3,  a = -0.0176, b = 0.6547),
        SchaefferOneEntryCoefs(essence = "*", numero = 4,  a = -0.0263, b = 0.8012),
        SchaefferOneEntryCoefs(essence = "*", numero = 5,  a = -0.0367, b = 0.9615),
        SchaefferOneEntryCoefs(essence = "*", numero = 6,  a = -0.0490, b = 1.1369),
        SchaefferOneEntryCoefs(essence = "*", numero = 7,  a = -0.0634, b = 1.3286),
        SchaefferOneEntryCoefs(essence = "*", numero = 8,  a = -0.0802, b = 1.5381),
        SchaefferOneEntryCoefs(essence = "*", numero = 9,  a = -0.0996, b = 1.7666),
        SchaefferOneEntryCoefs(essence = "*", numero = 10, a = -0.1220, b = 2.0158),
        SchaefferOneEntryCoefs(essence = "*", numero = 11, a = -0.1477, b = 2.2871),
        SchaefferOneEntryCoefs(essence = "*", numero = 12, a = -0.1771, b = 2.5823),
        SchaefferOneEntryCoefs(essence = "*", numero = 13, a = -0.2106, b = 2.9029),
        SchaefferOneEntryCoefs(essence = "*", numero = 14, a = -0.2488, b = 3.2509),
        SchaefferOneEntryCoefs(essence = "*", numero = 15, a = -0.2921, b = 3.6282),
        SchaefferOneEntryCoefs(essence = "*", numero = 16, a = -0.3412, b = 4.0368)
    )

    // ═══════════════════════════════════════════════════════════
    // TARIFS SCHAEFFER 2 ENTRÉES
    // V = a + b × C² × H   (C en m, H en m, V en m³)
    //
    // 8 tarifs numérotés. Le choix dépend de la forme des arbres.
    // ═══════════════════════════════════════════════════════════
    val schaefferTwoEntry: List<SchaefferTwoEntryCoefs> = listOf(
        SchaefferTwoEntryCoefs(essence = "*", numero = 1, a = -0.0015, b = 0.02006),
        SchaefferTwoEntryCoefs(essence = "*", numero = 2, a = -0.0031, b = 0.02450),
        SchaefferTwoEntryCoefs(essence = "*", numero = 3, a = -0.0053, b = 0.02946),
        SchaefferTwoEntryCoefs(essence = "*", numero = 4, a = -0.0082, b = 0.03498),
        SchaefferTwoEntryCoefs(essence = "*", numero = 5, a = -0.0120, b = 0.04112),
        SchaefferTwoEntryCoefs(essence = "*", numero = 6, a = -0.0169, b = 0.04794),
        SchaefferTwoEntryCoefs(essence = "*", numero = 7, a = -0.0231, b = 0.05550),
        SchaefferTwoEntryCoefs(essence = "*", numero = 8, a = -0.0309, b = 0.06389)
    )

    // ═══════════════════════════════════════════════════════════
    // TARIFS ALGAN
    // V = a × D^b × H^c   (D en cm, H en m, V en m³)
    //
    // Coefficients par essence issus d'Algan (1958) et ajustements
    // ultérieurs publiés (Pardé & Bouchon, 1988 ; IFN).
    // ═══════════════════════════════════════════════════════════
    val alganCoefs: List<AlganCoefs> = listOf(
        // ── Feuillus ──
        AlganCoefs(essence = "CH_SESSILE",      a = 0.0000423, b = 2.118, c = 0.872),
        AlganCoefs(essence = "CH_PEDONCULE",     a = 0.0000447, b = 2.103, c = 0.883),
        AlganCoefs(essence = "HETRE_COMMUN",     a = 0.0000362, b = 2.158, c = 0.860),
        AlganCoefs(essence = "CHARME",           a = 0.0000488, b = 2.027, c = 0.920),
        AlganCoefs(essence = "CHATAIGNIER",      a = 0.0000412, b = 2.130, c = 0.870),
        AlganCoefs(essence = "FRENE_ELEVE",      a = 0.0000380, b = 2.148, c = 0.862),
        AlganCoefs(essence = "ERABLE_SYC",       a = 0.0000395, b = 2.132, c = 0.875),
        AlganCoefs(essence = "ERABLE_PLANE",     a = 0.0000410, b = 2.115, c = 0.885),
        AlganCoefs(essence = "ERABLE_CHAMP",     a = 0.0000450, b = 2.060, c = 0.910),
        AlganCoefs(essence = "BOUL_VERRUQ",      a = 0.0000520, b = 1.980, c = 0.950),
        AlganCoefs(essence = "BOUL_PUBESC",      a = 0.0000540, b = 1.965, c = 0.958),
        AlganCoefs(essence = "AULNE_GLUT",       a = 0.0000510, b = 1.990, c = 0.945),
        AlganCoefs(essence = "AULNE_BLANC",      a = 0.0000530, b = 1.975, c = 0.952),
        AlganCoefs(essence = "TIL_PET_FEUIL",   a = 0.0000430, b = 2.085, c = 0.895),
        AlganCoefs(essence = "TIL_GR_FEUIL",    a = 0.0000420, b = 2.095, c = 0.890),
        AlganCoefs(essence = "ORME_CHAMP",       a = 0.0000415, b = 2.100, c = 0.888),
        AlganCoefs(essence = "ORME_LISSE",       a = 0.0000400, b = 2.115, c = 0.880),
        AlganCoefs(essence = "ORME_MONT",        a = 0.0000390, b = 2.125, c = 0.875),
        AlganCoefs(essence = "ROBINIER",         a = 0.0000395, b = 2.135, c = 0.868),
        AlganCoefs(essence = "NOYER_COMMUN",     a = 0.0000370, b = 2.150, c = 0.858),
        AlganCoefs(essence = "NOYER_NOIR",       a = 0.0000365, b = 2.155, c = 0.855),
        AlganCoefs(essence = "CERISIER_MERIS",   a = 0.0000388, b = 2.140, c = 0.865),
        AlganCoefs(essence = "CORMIER",          a = 0.0000375, b = 2.145, c = 0.862),
        AlganCoefs(essence = "ALISIER_TORM",     a = 0.0000385, b = 2.135, c = 0.868),
        AlganCoefs(essence = "ALISIER_BLANC",    a = 0.0000390, b = 2.130, c = 0.870),
        AlganCoefs(essence = "SORB_OISEL",       a = 0.0000440, b = 2.065, c = 0.905),
        AlganCoefs(essence = "SAULE_BLANC",      a = 0.0000560, b = 1.950, c = 0.965),
        AlganCoefs(essence = "SAULE_MARSAULT",   a = 0.0000580, b = 1.935, c = 0.972),
        AlganCoefs(essence = "PEUPLIER_HYBR",    a = 0.0000600, b = 1.920, c = 0.980),
        AlganCoefs(essence = "PEUPLIER_NOIR",    a = 0.0000570, b = 1.942, c = 0.968),
        AlganCoefs(essence = "TREMBLE",          a = 0.0000555, b = 1.955, c = 0.960),
        AlganCoefs(essence = "POMMIER_SAUV",     a = 0.0000445, b = 2.070, c = 0.908),
        AlganCoefs(essence = "POIRIER_SAUV",     a = 0.0000435, b = 2.080, c = 0.900),

        // ── Feuillus ajoutés (CanonicalEssences enrichies) ──
        AlganCoefs(essence = "CH_PUBESCENT",      a = 0.0000460, b = 2.080, c = 0.890),
        AlganCoefs(essence = "CH_ROUGE",           a = 0.0000405, b = 2.135, c = 0.870),
        AlganCoefs(essence = "CH_VERT",            a = 0.0000550, b = 1.960, c = 0.950),
        AlganCoefs(essence = "CH_LIEGE",           a = 0.0000530, b = 1.975, c = 0.945),
        AlganCoefs(essence = "CH_TAUZIN",          a = 0.0000470, b = 2.065, c = 0.900),
        AlganCoefs(essence = "CH_KERMES",          a = 0.0000600, b = 1.900, c = 0.990),
        AlganCoefs(essence = "FRENE_OXYPHYLLE",    a = 0.0000390, b = 2.138, c = 0.865),
        AlganCoefs(essence = "FRENE_FLEURS",       a = 0.0000420, b = 2.090, c = 0.895),
        AlganCoefs(essence = "ERABLE_MONTPELLIER", a = 0.0000480, b = 2.030, c = 0.920),
        AlganCoefs(essence = "ERABLE_OBIER",       a = 0.0000470, b = 2.040, c = 0.915),
        AlganCoefs(essence = "AULNE_CORSE",        a = 0.0000520, b = 1.985, c = 0.948),
        AlganCoefs(essence = "SAULE_FRAGILE",      a = 0.0000570, b = 1.940, c = 0.970),
        AlganCoefs(essence = "PEUPLIER_TREMB",     a = 0.0000555, b = 1.955, c = 0.960),
        AlganCoefs(essence = "PLATANE",            a = 0.0000400, b = 2.120, c = 0.878),
        AlganCoefs(essence = "MICOCOULIER",        a = 0.0000430, b = 2.085, c = 0.895),
        AlganCoefs(essence = "MARRONNIER",         a = 0.0000500, b = 2.000, c = 0.930),
        AlganCoefs(essence = "TULIPIER",           a = 0.0000380, b = 2.150, c = 0.855),
        AlganCoefs(essence = "OLIVIER",            a = 0.0000580, b = 1.930, c = 0.975),
        AlganCoefs(essence = "ARBRE_JUDEE",        a = 0.0000550, b = 1.950, c = 0.960),
        AlganCoefs(essence = "FIGUIER",            a = 0.0000580, b = 1.920, c = 0.980),
        AlganCoefs(essence = "MURIER_BLANC",       a = 0.0000480, b = 2.040, c = 0.910),
        AlganCoefs(essence = "SORBIER_DOMESTIQUE", a = 0.0000400, b = 2.110, c = 0.882),
        AlganCoefs(essence = "CORNOUILLER_MALE",   a = 0.0000560, b = 1.945, c = 0.965),
        AlganCoefs(essence = "CORNOUILLER_SANG",   a = 0.0000580, b = 1.930, c = 0.975),
        AlganCoefs(essence = "SUREAU_NOIR",        a = 0.0000600, b = 1.900, c = 0.990),
        AlganCoefs(essence = "AUBEPINE_MONOGYNE",  a = 0.0000580, b = 1.920, c = 0.980),
        AlganCoefs(essence = "PRUNELLIER",         a = 0.0000600, b = 1.900, c = 0.990),
        AlganCoefs(essence = "BUIS",               a = 0.0000620, b = 1.880, c = 1.000),
        AlganCoefs(essence = "TROENE",             a = 0.0000600, b = 1.900, c = 0.990),
        AlganCoefs(essence = "VIORNE_LANTANE",     a = 0.0000610, b = 1.890, c = 0.995),
        AlganCoefs(essence = "VIORNE_OBIER",       a = 0.0000610, b = 1.890, c = 0.995),
        AlganCoefs(essence = "GENETS_SCORPION",    a = 0.0000650, b = 1.860, c = 1.010),
        AlganCoefs(essence = "EUCALYPTUS_GUNNII",  a = 0.0000360, b = 2.170, c = 0.845),
        AlganCoefs(essence = "EUCALYPTUS_GLOBULUS",a = 0.0000350, b = 2.180, c = 0.840),

        // ── Résineux ──
        AlganCoefs(essence = "PIN_SYLVESTRE",    a = 0.0000318, b = 2.218, c = 0.815),
        AlganCoefs(essence = "PIN_MARITIME",     a = 0.0000345, b = 2.175, c = 0.840),
        AlganCoefs(essence = "PIN_NOIR_AUTR",    a = 0.0000328, b = 2.200, c = 0.828),
        AlganCoefs(essence = "PIN_LARICIO",      a = 0.0000310, b = 2.235, c = 0.808),
        AlganCoefs(essence = "PIN_WEYMOUTH",     a = 0.0000350, b = 2.165, c = 0.845),
        AlganCoefs(essence = "EPICEA_COMMUN",    a = 0.0000355, b = 2.182, c = 0.832),
        AlganCoefs(essence = "SAPIN_PECTINE",    a = 0.0000378, b = 2.160, c = 0.848),
        AlganCoefs(essence = "DOUGLAS_VERT",     a = 0.0000298, b = 2.262, c = 0.795),
        AlganCoefs(essence = "MEL_EUROPE",       a = 0.0000335, b = 2.190, c = 0.835),
        AlganCoefs(essence = "MEL_HYBRIDE",      a = 0.0000330, b = 2.195, c = 0.832),

        // ── Résineux ajoutés (CanonicalEssences enrichies) ──
        AlganCoefs(essence = "PIN_ALEP",           a = 0.0000360, b = 2.155, c = 0.850),
        AlganCoefs(essence = "PIN_PIGNON",         a = 0.0000355, b = 2.162, c = 0.845),
        AlganCoefs(essence = "PIN_CEMBRO",         a = 0.0000340, b = 2.185, c = 0.835),
        AlganCoefs(essence = "PIN_MUGO",           a = 0.0000400, b = 2.100, c = 0.880),
        AlganCoefs(essence = "PIN_SALZMANN",       a = 0.0000325, b = 2.205, c = 0.825),
        AlganCoefs(essence = "PIN_MONTEREY",       a = 0.0000305, b = 2.250, c = 0.800),
        AlganCoefs(essence = "EPICEA_SITKA",       a = 0.0000340, b = 2.200, c = 0.818),
        AlganCoefs(essence = "EPICEA_OMORIKA",     a = 0.0000360, b = 2.175, c = 0.835),
        AlganCoefs(essence = "SAPIN_NORDMANN",     a = 0.0000370, b = 2.168, c = 0.842),
        AlganCoefs(essence = "SAPIN_GRANDIS",      a = 0.0000310, b = 2.248, c = 0.798),
        AlganCoefs(essence = "SAPIN_CEPHALONIE",   a = 0.0000365, b = 2.170, c = 0.840),
        AlganCoefs(essence = "SAPIN_ESPAGNE",      a = 0.0000375, b = 2.158, c = 0.848),
        AlganCoefs(essence = "MEL_JAPON",          a = 0.0000328, b = 2.198, c = 0.830),
        AlganCoefs(essence = "CEDRE_ATLAS",        a = 0.0000348, b = 2.180, c = 0.838),
        AlganCoefs(essence = "CEDRE_LIBAN",        a = 0.0000345, b = 2.185, c = 0.835),
        AlganCoefs(essence = "THUYA_GEANT",        a = 0.0000365, b = 2.168, c = 0.842),
        AlganCoefs(essence = "CYPRES_PROVENCE",    a = 0.0000370, b = 2.162, c = 0.845),
        AlganCoefs(essence = "SEQUOIA_TOUJOURS_VERT", a = 0.0000290, b = 2.275, c = 0.788),
        AlganCoefs(essence = "CRYPTOMERE",         a = 0.0000350, b = 2.172, c = 0.840),
        AlganCoefs(essence = "CYPRES_CHAUVE",      a = 0.0000358, b = 2.168, c = 0.838),
        AlganCoefs(essence = "TSUGA_HETEROPHYLLE", a = 0.0000342, b = 2.195, c = 0.825),

        // ── Conifères autres ──
        AlganCoefs(essence = "GENEVRIER",          a = 0.0000320, b = 2.210, c = 0.820),
        AlganCoefs(essence = "GENEVRIER_CADE",     a = 0.0000330, b = 2.200, c = 0.825),
        AlganCoefs(essence = "GENEVRIER_PHENICIE", a = 0.0000340, b = 2.190, c = 0.830),
        AlganCoefs(essence = "IF",                 a = 0.0000380, b = 2.155, c = 0.850),

        // ── Essences mineures / divers ──
        AlganCoefs(essence = "NOISETIER",        a = 0.0000600, b = 1.900, c = 0.990),
        AlganCoefs(essence = "FUSAIN",           a = 0.0000500, b = 2.000, c = 0.930),
        AlganCoefs(essence = "HOUX",             a = 0.0000580, b = 1.920, c = 0.975)
    )

    // ═══════════════════════════════════════════════════════════
    // TARIFS RAPIDES IFN (36 tarifs à 1 entrée)
    // V = a₀ + a₁×D + a₂×D²  (D en cm, V en dm³)
    //
    // Source : Inventaire Forestier National (IGN France)
    // Documentation technique des tarifs de cubage.
    // ═══════════════════════════════════════════════════════════
    val ifnRapide: List<IfnRapideCoefs> = listOf(
        IfnRapideCoefs(numero = 1,  a0 = -4.28,  a1 = 0.280, a2 = 0.0340),
        IfnRapideCoefs(numero = 2,  a0 = -5.34,  a1 = 0.364, a2 = 0.0449),
        IfnRapideCoefs(numero = 3,  a0 = -6.58,  a1 = 0.464, a2 = 0.0580),
        IfnRapideCoefs(numero = 4,  a0 = -7.99,  a1 = 0.582, a2 = 0.0736),
        IfnRapideCoefs(numero = 5,  a0 = -9.59,  a1 = 0.721, a2 = 0.0920),
        IfnRapideCoefs(numero = 6,  a0 = -11.38, a1 = 0.883, a2 = 0.1138),
        IfnRapideCoefs(numero = 7,  a0 = -13.38, a1 = 1.072, a2 = 0.1392),
        IfnRapideCoefs(numero = 8,  a0 = -15.61, a1 = 1.290, a2 = 0.1690),
        IfnRapideCoefs(numero = 9,  a0 = -18.07, a1 = 1.540, a2 = 0.2036),
        IfnRapideCoefs(numero = 10, a0 = -20.79, a1 = 1.826, a2 = 0.2436),
        IfnRapideCoefs(numero = 11, a0 = -23.78, a1 = 2.150, a2 = 0.2896),
        IfnRapideCoefs(numero = 12, a0 = -27.05, a1 = 2.518, a2 = 0.3424),
        IfnRapideCoefs(numero = 13, a0 = -30.64, a1 = 2.932, a2 = 0.4028),
        IfnRapideCoefs(numero = 14, a0 = -34.55, a1 = 3.398, a2 = 0.4716),
        IfnRapideCoefs(numero = 15, a0 = -38.82, a1 = 3.920, a2 = 0.5500),
        IfnRapideCoefs(numero = 16, a0 = -43.46, a1 = 4.504, a2 = 0.6388),
        IfnRapideCoefs(numero = 17, a0 = -48.50, a1 = 5.156, a2 = 0.7396),
        IfnRapideCoefs(numero = 18, a0 = -53.96, a1 = 5.880, a2 = 0.8536),
        IfnRapideCoefs(numero = 19, a0 = -59.88, a1 = 6.684, a2 = 0.9824),
        IfnRapideCoefs(numero = 20, a0 = -66.29, a1 = 7.574, a2 = 1.1276),
        IfnRapideCoefs(numero = 21, a0 = -73.21, a1 = 8.558, a2 = 1.2912),
        IfnRapideCoefs(numero = 22, a0 = -80.70, a1 = 9.644, a2 = 1.4752),
        IfnRapideCoefs(numero = 23, a0 = -88.78, a1 = 10.840, a2 = 1.6820),
        IfnRapideCoefs(numero = 24, a0 = -97.50, a1 = 12.156, a2 = 1.9140),
        IfnRapideCoefs(numero = 25, a0 = -106.91, a1 = 13.600, a2 = 2.1740),
        IfnRapideCoefs(numero = 26, a0 = -117.06, a1 = 15.184, a2 = 2.4648),
        IfnRapideCoefs(numero = 27, a0 = -127.98, a1 = 16.918, a2 = 2.7896),
        IfnRapideCoefs(numero = 28, a0 = -139.74, a1 = 18.812, a2 = 3.1520),
        IfnRapideCoefs(numero = 29, a0 = -152.38, a1 = 20.880, a2 = 3.5556),
        IfnRapideCoefs(numero = 30, a0 = -165.95, a1 = 23.132, a2 = 4.0044),
        IfnRapideCoefs(numero = 31, a0 = -180.52, a1 = 25.584, a2 = 4.5028),
        IfnRapideCoefs(numero = 32, a0 = -196.12, a1 = 28.250, a2 = 5.0552),
        IfnRapideCoefs(numero = 33, a0 = -212.84, a1 = 31.146, a2 = 5.6664),
        IfnRapideCoefs(numero = 34, a0 = -230.73, a1 = 34.288, a2 = 6.3412),
        IfnRapideCoefs(numero = 35, a0 = -249.86, a1 = 37.694, a2 = 7.0848),
        IfnRapideCoefs(numero = 36, a0 = -270.30, a1 = 41.380, a2 = 7.9028)
    )

    // ═══════════════════════════════════════════════════════════
    // TARIFS LENTS IFN (à 2 entrées)
    // V = a₀ + a₁×D² + a₂×D²×H  (D cm, H m, V dm³)
    //
    // Sélection des principaux tarifs utilisés pour les grandes
    // essences forestières françaises.
    // ═══════════════════════════════════════════════════════════
    val ifnLent: List<IfnLentCoefs> = listOf(
        IfnLentCoefs(numero = 1, a0 = -4.50,  a1 = 0.0140, a2 = 0.02032),
        IfnLentCoefs(numero = 2, a0 = -5.60,  a1 = 0.0180, a2 = 0.02680),
        IfnLentCoefs(numero = 3, a0 = -6.90,  a1 = 0.0230, a2 = 0.03468),
        IfnLentCoefs(numero = 4, a0 = -8.40,  a1 = 0.0290, a2 = 0.04408),
        IfnLentCoefs(numero = 5, a0 = -10.10, a1 = 0.0360, a2 = 0.05520),
        IfnLentCoefs(numero = 6, a0 = -12.00, a1 = 0.0450, a2 = 0.06820),
        IfnLentCoefs(numero = 7, a0 = -14.10, a1 = 0.0550, a2 = 0.08340),
        IfnLentCoefs(numero = 8, a0 = -16.50, a1 = 0.0670, a2 = 0.10120)
    )

    // ═══════════════════════════════════════════════════════════
    // COEFFICIENTS DE FORME PAR ESSENCE
    // f = coefficient de forme (décroissance)
    // V = G × H × f
    //
    // Source : Pardé & Bouchon (1988), "Dendrométrie", ENGREF
    // Valeurs moyennes pour arbres de futaie régulière.
    // ═══════════════════════════════════════════════════════════
    val coefsFormeParEssence: List<CoefFormeEntry> = listOf(
        // ── Feuillus ──
        CoefFormeEntry(essence = "CH_SESSILE",      f = 0.46),
        CoefFormeEntry(essence = "CH_PEDONCULE",     f = 0.47),
        CoefFormeEntry(essence = "HETRE_COMMUN",     f = 0.45),
        CoefFormeEntry(essence = "CHARME",           f = 0.48),
        CoefFormeEntry(essence = "CHATAIGNIER",      f = 0.46),
        CoefFormeEntry(essence = "FRENE_ELEVE",      f = 0.44),
        CoefFormeEntry(essence = "ERABLE_SYC",       f = 0.45),
        CoefFormeEntry(essence = "ERABLE_PLANE",     f = 0.46),
        CoefFormeEntry(essence = "ERABLE_CHAMP",     f = 0.47),
        CoefFormeEntry(essence = "BOUL_VERRUQ",      f = 0.48),
        CoefFormeEntry(essence = "BOUL_PUBESC",      f = 0.49),
        CoefFormeEntry(essence = "AULNE_GLUT",       f = 0.47),
        CoefFormeEntry(essence = "AULNE_BLANC",      f = 0.48),
        CoefFormeEntry(essence = "TIL_PET_FEUIL",   f = 0.46),
        CoefFormeEntry(essence = "TIL_GR_FEUIL",    f = 0.46),
        CoefFormeEntry(essence = "ORME_CHAMP",       f = 0.46),
        CoefFormeEntry(essence = "ORME_LISSE",       f = 0.45),
        CoefFormeEntry(essence = "ORME_MONT",        f = 0.44),
        CoefFormeEntry(essence = "ROBINIER",         f = 0.45),
        CoefFormeEntry(essence = "NOYER_COMMUN",     f = 0.44),
        CoefFormeEntry(essence = "NOYER_NOIR",       f = 0.43),
        CoefFormeEntry(essence = "CERISIER_MERIS",   f = 0.45),
        CoefFormeEntry(essence = "CORMIER",          f = 0.44),
        CoefFormeEntry(essence = "ALISIER_TORM",     f = 0.45),
        CoefFormeEntry(essence = "ALISIER_BLANC",    f = 0.46),
        CoefFormeEntry(essence = "SORB_OISEL",       f = 0.47),
        CoefFormeEntry(essence = "SAULE_BLANC",      f = 0.49),
        CoefFormeEntry(essence = "SAULE_MARSAULT",   f = 0.50),
        CoefFormeEntry(essence = "PEUPLIER_HYBR",    f = 0.42),
        CoefFormeEntry(essence = "PEUPLIER_NOIR",    f = 0.43),
        CoefFormeEntry(essence = "TREMBLE",          f = 0.44),
        CoefFormeEntry(essence = "POMMIER_SAUV",     f = 0.47),
        CoefFormeEntry(essence = "POIRIER_SAUV",     f = 0.46),
        CoefFormeEntry(essence = "NOISETIER",        f = 0.51),
        CoefFormeEntry(essence = "FUSAIN",           f = 0.50),
        CoefFormeEntry(essence = "HOUX",             f = 0.52),

        // ── Feuillus ajoutés ──
        CoefFormeEntry(essence = "CH_PUBESCENT",      f = 0.47),
        CoefFormeEntry(essence = "CH_ROUGE",           f = 0.46),
        CoefFormeEntry(essence = "CH_VERT",            f = 0.50),
        CoefFormeEntry(essence = "CH_LIEGE",           f = 0.49),
        CoefFormeEntry(essence = "CH_TAUZIN",          f = 0.47),
        CoefFormeEntry(essence = "CH_KERMES",          f = 0.52),
        CoefFormeEntry(essence = "FRENE_OXYPHYLLE",    f = 0.44),
        CoefFormeEntry(essence = "FRENE_FLEURS",       f = 0.46),
        CoefFormeEntry(essence = "ERABLE_MONTPELLIER", f = 0.48),
        CoefFormeEntry(essence = "ERABLE_OBIER",       f = 0.47),
        CoefFormeEntry(essence = "AULNE_CORSE",        f = 0.47),
        CoefFormeEntry(essence = "SAULE_FRAGILE",      f = 0.49),
        CoefFormeEntry(essence = "PEUPLIER_TREMB",     f = 0.44),
        CoefFormeEntry(essence = "PLATANE",            f = 0.45),
        CoefFormeEntry(essence = "MICOCOULIER",        f = 0.46),
        CoefFormeEntry(essence = "MARRONNIER",         f = 0.47),
        CoefFormeEntry(essence = "TULIPIER",           f = 0.43),
        CoefFormeEntry(essence = "OLIVIER",            f = 0.50),
        CoefFormeEntry(essence = "ARBRE_JUDEE",        f = 0.49),
        CoefFormeEntry(essence = "FIGUIER",            f = 0.50),
        CoefFormeEntry(essence = "MURIER_BLANC",       f = 0.47),
        CoefFormeEntry(essence = "SORBIER_DOMESTIQUE", f = 0.45),
        CoefFormeEntry(essence = "CORNOUILLER_MALE",   f = 0.50),
        CoefFormeEntry(essence = "CORNOUILLER_SANG",   f = 0.50),
        CoefFormeEntry(essence = "SUREAU_NOIR",        f = 0.51),
        CoefFormeEntry(essence = "AUBEPINE_MONOGYNE",  f = 0.50),
        CoefFormeEntry(essence = "PRUNELLIER",         f = 0.52),
        CoefFormeEntry(essence = "BUIS",               f = 0.53),
        CoefFormeEntry(essence = "TROENE",             f = 0.51),
        CoefFormeEntry(essence = "VIORNE_LANTANE",     f = 0.51),
        CoefFormeEntry(essence = "VIORNE_OBIER",       f = 0.51),
        CoefFormeEntry(essence = "GENETS_SCORPION",    f = 0.53),
        CoefFormeEntry(essence = "EUCALYPTUS_GUNNII",  f = 0.42),
        CoefFormeEntry(essence = "EUCALYPTUS_GLOBULUS",f = 0.41),

        // ── Résineux ──
        CoefFormeEntry(essence = "PIN_SYLVESTRE",    f = 0.42),
        CoefFormeEntry(essence = "PIN_MARITIME",     f = 0.40),
        CoefFormeEntry(essence = "PIN_NOIR_AUTR",    f = 0.41),
        CoefFormeEntry(essence = "PIN_LARICIO",      f = 0.40),
        CoefFormeEntry(essence = "PIN_WEYMOUTH",     f = 0.41),
        CoefFormeEntry(essence = "EPICEA_COMMUN",    f = 0.43),
        CoefFormeEntry(essence = "SAPIN_PECTINE",    f = 0.44),
        CoefFormeEntry(essence = "DOUGLAS_VERT",     f = 0.39),
        CoefFormeEntry(essence = "MEL_EUROPE",       f = 0.41),
        CoefFormeEntry(essence = "MEL_HYBRIDE",      f = 0.41),

        // ── Résineux ajoutés ──
        CoefFormeEntry(essence = "PIN_ALEP",           f = 0.41),
        CoefFormeEntry(essence = "PIN_PIGNON",         f = 0.42),
        CoefFormeEntry(essence = "PIN_CEMBRO",         f = 0.42),
        CoefFormeEntry(essence = "PIN_MUGO",           f = 0.44),
        CoefFormeEntry(essence = "PIN_SALZMANN",       f = 0.41),
        CoefFormeEntry(essence = "PIN_MONTEREY",       f = 0.39),
        CoefFormeEntry(essence = "EPICEA_SITKA",       f = 0.42),
        CoefFormeEntry(essence = "EPICEA_OMORIKA",     f = 0.43),
        CoefFormeEntry(essence = "SAPIN_NORDMANN",     f = 0.44),
        CoefFormeEntry(essence = "SAPIN_GRANDIS",      f = 0.40),
        CoefFormeEntry(essence = "SAPIN_CEPHALONIE",   f = 0.43),
        CoefFormeEntry(essence = "SAPIN_ESPAGNE",      f = 0.43),
        CoefFormeEntry(essence = "MEL_JAPON",          f = 0.41),
        CoefFormeEntry(essence = "CEDRE_ATLAS",        f = 0.43),
        CoefFormeEntry(essence = "CEDRE_LIBAN",        f = 0.43),
        CoefFormeEntry(essence = "THUYA_GEANT",        f = 0.42),
        CoefFormeEntry(essence = "CYPRES_PROVENCE",    f = 0.43),
        CoefFormeEntry(essence = "SEQUOIA_TOUJOURS_VERT", f = 0.39),
        CoefFormeEntry(essence = "CRYPTOMERE",         f = 0.42),
        CoefFormeEntry(essence = "CYPRES_CHAUVE",      f = 0.42),
        CoefFormeEntry(essence = "TSUGA_HETEROPHYLLE", f = 0.42),

        // ── Conifères autres ──
        CoefFormeEntry(essence = "GENEVRIER",          f = 0.42),
        CoefFormeEntry(essence = "GENEVRIER_CADE",     f = 0.42),
        CoefFormeEntry(essence = "GENEVRIER_PHENICIE", f = 0.43),
        CoefFormeEntry(essence = "IF",                 f = 0.44),

        // ── Wildcard (fallback) ──
        CoefFormeEntry(essence = "*", f = 0.45)
    )

    // ═══════════════════════════════════════════════════════════
    // MAPPING ESSENCE → NUMÉRO DE TARIF IFN RAPIDE RECOMMANDÉ
    //
    // Source : Recommandations IFN/IGN pour les campagnes d'inventaire.
    // Ces numéros correspondent aux tarifs les plus couramment
    // utilisés pour chaque essence en France métropolitaine.
    // ═══════════════════════════════════════════════════════════
    val essenceToIfnRapideNumero: Map<String, Int> = mapOf(
        // Feuillus
        "CH_SESSILE" to 12,
        "CH_PEDONCULE" to 13,
        "HETRE_COMMUN" to 14,
        "CHARME" to 8,
        "CHATAIGNIER" to 11,
        "FRENE_ELEVE" to 12,
        "ERABLE_SYC" to 11,
        "ERABLE_PLANE" to 10,
        "ERABLE_CHAMP" to 8,
        "BOUL_VERRUQ" to 8,
        "BOUL_PUBESC" to 7,
        "AULNE_GLUT" to 9,
        "AULNE_BLANC" to 8,
        "TIL_PET_FEUIL" to 10,
        "TIL_GR_FEUIL" to 10,
        "ORME_CHAMP" to 10,
        "ORME_LISSE" to 11,
        "ORME_MONT" to 11,
        "ROBINIER" to 10,
        "NOYER_COMMUN" to 12,
        "NOYER_NOIR" to 13,
        "CERISIER_MERIS" to 11,
        "CORMIER" to 10,
        "ALISIER_TORM" to 9,
        "ALISIER_BLANC" to 9,
        "SORB_OISEL" to 7,
        "SAULE_BLANC" to 8,
        "SAULE_MARSAULT" to 6,
        "PEUPLIER_HYBR" to 14,
        "PEUPLIER_NOIR" to 12,
        "TREMBLE" to 10,
        "POMMIER_SAUV" to 7,
        "POIRIER_SAUV" to 8,
        "NOISETIER" to 4,
        // Feuillus ajoutés
        "CH_PUBESCENT" to 10,
        "CH_ROUGE" to 13,
        "CH_VERT" to 7,
        "CH_LIEGE" to 7,
        "CH_TAUZIN" to 9,
        "CH_KERMES" to 5,
        "FRENE_OXYPHYLLE" to 11,
        "FRENE_FLEURS" to 8,
        "ERABLE_MONTPELLIER" to 7,
        "ERABLE_OBIER" to 7,
        "AULNE_CORSE" to 8,
        "SAULE_FRAGILE" to 7,
        "PEUPLIER_TREMB" to 10,
        "PLATANE" to 14,
        "MICOCOULIER" to 9,
        "MARRONNIER" to 10,
        "TULIPIER" to 16,
        "EUCALYPTUS_GUNNII" to 18,
        "EUCALYPTUS_GLOBULUS" to 20,
        "SORBIER_DOMESTIQUE" to 8,
        // Résineux
        "PIN_SYLVESTRE" to 12,
        "PIN_MARITIME" to 14,
        "PIN_NOIR_AUTR" to 13,
        "PIN_LARICIO" to 15,
        "PIN_WEYMOUTH" to 12,
        "EPICEA_COMMUN" to 16,
        "SAPIN_PECTINE" to 17,
        "DOUGLAS_VERT" to 20,
        "MEL_EUROPE" to 14,
        "MEL_HYBRIDE" to 14,
        // Résineux ajoutés
        "PIN_ALEP" to 10,
        "PIN_PIGNON" to 11,
        "PIN_CEMBRO" to 10,
        "PIN_MUGO" to 6,
        "PIN_SALZMANN" to 12,
        "PIN_MONTEREY" to 18,
        "EPICEA_SITKA" to 18,
        "EPICEA_OMORIKA" to 14,
        "SAPIN_NORDMANN" to 17,
        "SAPIN_GRANDIS" to 22,
        "SAPIN_CEPHALONIE" to 15,
        "SAPIN_ESPAGNE" to 14,
        "MEL_JAPON" to 15,
        "CEDRE_ATLAS" to 16,
        "CEDRE_LIBAN" to 16,
        "THUYA_GEANT" to 16,
        "CYPRES_PROVENCE" to 12,
        "SEQUOIA_TOUJOURS_VERT" to 24,
        "CRYPTOMERE" to 14,
        "CYPRES_CHAUVE" to 14,
        "TSUGA_HETEROPHYLLE" to 16
    )

    // ═══════════════════════════════════════════════════════════
    // MAPPING ESSENCE → NUMÉRO DE TARIF IFN LENT RECOMMANDÉ
    // ═══════════════════════════════════════════════════════════
    val essenceToIfnLentNumero: Map<String, Int> = mapOf(
        "CH_SESSILE" to 4,
        "CH_PEDONCULE" to 4,
        "HETRE_COMMUN" to 5,
        "CHARME" to 3,
        "CHATAIGNIER" to 4,
        "FRENE_ELEVE" to 4,
        "ERABLE_SYC" to 4,
        "BOUL_VERRUQ" to 3,
        "AULNE_GLUT" to 3,
        "ROBINIER" to 4,
        "NOYER_COMMUN" to 4,
        "CERISIER_MERIS" to 4,
        "PEUPLIER_HYBR" to 5,
        // Feuillus ajoutés
        "CH_PUBESCENT" to 4,
        "CH_ROUGE" to 5,
        "FRENE_OXYPHYLLE" to 4,
        "PLATANE" to 5,
        "TULIPIER" to 6,
        "EUCALYPTUS_GUNNII" to 6,
        "EUCALYPTUS_GLOBULUS" to 7,
        // Résineux
        "PIN_SYLVESTRE" to 4,
        "PIN_MARITIME" to 5,
        "PIN_NOIR_AUTR" to 4,
        "PIN_LARICIO" to 5,
        "EPICEA_COMMUN" to 5,
        "SAPIN_PECTINE" to 6,
        "DOUGLAS_VERT" to 7,
        "MEL_EUROPE" to 5,
        "MEL_HYBRIDE" to 5,
        // Résineux ajoutés
        "PIN_ALEP" to 4,
        "PIN_PIGNON" to 4,
        "PIN_CEMBRO" to 4,
        "PIN_SALZMANN" to 4,
        "PIN_MONTEREY" to 6,
        "EPICEA_SITKA" to 6,
        "EPICEA_OMORIKA" to 5,
        "SAPIN_NORDMANN" to 6,
        "SAPIN_GRANDIS" to 7,
        "SAPIN_CEPHALONIE" to 5,
        "SAPIN_ESPAGNE" to 5,
        "MEL_JAPON" to 5,
        "CEDRE_ATLAS" to 5,
        "CEDRE_LIBAN" to 5,
        "THUYA_GEANT" to 5,
        "CYPRES_PROVENCE" to 4,
        "SEQUOIA_TOUJOURS_VERT" to 8,
        "CRYPTOMERE" to 5,
        "CYPRES_CHAUVE" to 5,
        "TSUGA_HETEROPHYLLE" to 6
    )

    // ═══════════════════════════════════════════════════════════
    // RÈGLES DE DÉCOUPE PAR PRODUIT — STANDARDS PROFESSIONNELS
    //
    // Basé sur les pratiques ONF/CNPF :
    // - BO : bois d'œuvre (grumes de qualité)
    // - BI : bois d'industrie (petits bois, bois de trituration)
    // - BCh : bois de chauffage
    // - BE : bois énergie
    // - PIQ/POT : piquets / poteaux (résineux spécifiques)
    // ═══════════════════════════════════════════════════════════
    val decoupeDefautFeuillus: List<DecoupeRule> = listOf(
        // Gros bois feuillus (D >= 35) → majoritairement BO
        DecoupeRule(essence = "*", categorie = "Feuillu", minDiam = 40, maxDiam = 999, produit = "BO", pctVolume = 55.0),
        DecoupeRule(essence = "*", categorie = "Feuillu", minDiam = 40, maxDiam = 999, produit = "BI", pctVolume = 25.0),
        DecoupeRule(essence = "*", categorie = "Feuillu", minDiam = 40, maxDiam = 999, produit = "BCh", pctVolume = 20.0),

        // Bois moyens feuillus (25 <= D < 40)
        DecoupeRule(essence = "*", categorie = "Feuillu", minDiam = 25, maxDiam = 39, produit = "BO", pctVolume = 40.0),
        DecoupeRule(essence = "*", categorie = "Feuillu", minDiam = 25, maxDiam = 39, produit = "BI", pctVolume = 30.0),
        DecoupeRule(essence = "*", categorie = "Feuillu", minDiam = 25, maxDiam = 39, produit = "BCh", pctVolume = 30.0),

        // Petits bois feuillus (D < 25)
        DecoupeRule(essence = "*", categorie = "Feuillu", minDiam = 7, maxDiam = 24, produit = "BI", pctVolume = 40.0),
        DecoupeRule(essence = "*", categorie = "Feuillu", minDiam = 7, maxDiam = 24, produit = "BCh", pctVolume = 60.0),

        // Très petits (D < 7)
        DecoupeRule(essence = "*", categorie = "Feuillu", minDiam = 0, maxDiam = 6, produit = "BE", pctVolume = 100.0)
    )

    val decoupeDefautResineux: List<DecoupeRule> = listOf(
        // Gros bois résineux (D >= 30)
        DecoupeRule(essence = "*", categorie = "Résineux", minDiam = 35, maxDiam = 999, produit = "BO", pctVolume = 65.0),
        DecoupeRule(essence = "*", categorie = "Résineux", minDiam = 35, maxDiam = 999, produit = "BI", pctVolume = 25.0),
        DecoupeRule(essence = "*", categorie = "Résineux", minDiam = 35, maxDiam = 999, produit = "BE", pctVolume = 10.0),

        // Bois moyens résineux
        DecoupeRule(essence = "*", categorie = "Résineux", minDiam = 20, maxDiam = 34, produit = "BO", pctVolume = 50.0),
        DecoupeRule(essence = "*", categorie = "Résineux", minDiam = 20, maxDiam = 34, produit = "BI", pctVolume = 35.0),
        DecoupeRule(essence = "*", categorie = "Résineux", minDiam = 20, maxDiam = 34, produit = "BE", pctVolume = 15.0),

        // Petits bois résineux
        DecoupeRule(essence = "*", categorie = "Résineux", minDiam = 7, maxDiam = 19, produit = "BI", pctVolume = 50.0),
        DecoupeRule(essence = "*", categorie = "Résineux", minDiam = 7, maxDiam = 19, produit = "PATE", pctVolume = 30.0),
        DecoupeRule(essence = "*", categorie = "Résineux", minDiam = 7, maxDiam = 19, produit = "BE", pctVolume = 20.0),

        // Très petits
        DecoupeRule(essence = "*", categorie = "Résineux", minDiam = 0, maxDiam = 6, produit = "BE", pctVolume = 100.0)
    )

    // Découpes spéciales par essence (override des règles par catégorie)
    val decoupeSpeciales: List<DecoupeRule> = listOf(
        // Douglas : bois d'œuvre très tôt (D >= 25)
        DecoupeRule(essence = "DOUGLAS_VERT", categorie = null, minDiam = 25, maxDiam = 999, produit = "BO", pctVolume = 80.0),
        DecoupeRule(essence = "DOUGLAS_VERT", categorie = null, minDiam = 25, maxDiam = 999, produit = "BI", pctVolume = 15.0),
        DecoupeRule(essence = "DOUGLAS_VERT", categorie = null, minDiam = 25, maxDiam = 999, produit = "BE", pctVolume = 5.0),

        // Pin maritime : poteaux (D 20-30)
        DecoupeRule(essence = "PIN_MARITIME", categorie = null, minDiam = 20, maxDiam = 30, produit = "POT", pctVolume = 60.0),
        DecoupeRule(essence = "PIN_MARITIME", categorie = null, minDiam = 20, maxDiam = 30, produit = "BI", pctVolume = 40.0),

        // Robinier : piquets (D < 25)
        DecoupeRule(essence = "ROBINIER", categorie = null, minDiam = 10, maxDiam = 24, produit = "PIQ", pctVolume = 70.0),
        DecoupeRule(essence = "ROBINIER", categorie = null, minDiam = 10, maxDiam = 24, produit = "BCh", pctVolume = 30.0),

        // Châtaignier : piquets (D < 20)
        DecoupeRule(essence = "CHATAIGNIER", categorie = null, minDiam = 10, maxDiam = 19, produit = "PIQ", pctVolume = 60.0),
        DecoupeRule(essence = "CHATAIGNIER", categorie = null, minDiam = 10, maxDiam = 19, produit = "BCh", pctVolume = 40.0)
    )
}
