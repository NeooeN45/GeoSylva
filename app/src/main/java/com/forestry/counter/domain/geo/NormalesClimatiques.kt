package com.forestry.counter.domain.geo

/**
 * Normales climatiques françaises 1991–2020 par département — 100% offline.
 *
 * Sources : Météo-France (données publiques), INRAE,
 *           Données climatiques de référence ONF/CNPF.
 *
 * Paramètres :
 *   - T_moy_C    : Température moyenne annuelle (°C)
 *   - T_min_C    : Température minimale moyenne annuelle (°C)
 *   - T_max_C    : Température maximale moyenne annuelle (°C)
 *   - P_mm       : Précipitations totales annuelles (mm)
 *   - P_ete_mm   : Précipitations estivales (JJA, mm)
 *   - P_hiver_mm : Précipitations hivernales (DJF, mm)
 *   - ETP_mm     : Évapotranspiration potentielle annuelle (Turc, mm)
 *   - jGel       : Nombre de jours de gel/an (Tmin < 0°C)
 *   - jSec       : Nombre de jours secs/an (P < 1 mm)
 *   - ensoleilH  : Ensoleillement annuel (heures)
 *   - altMoyM    : Altitude moyenne du département (m)
 */
object NormalesClimatiques {

    data class NormalesDept(
        val numDept: String,
        val nomDept: String,
        val tMoyC: Double,      // °C
        val tMinC: Double,
        val tMaxC: Double,
        val pMmAn: Int,         // mm/an
        val pEteMm: Int,        // mm (JJA)
        val pHiverMm: Int,      // mm (DJF)
        val etpMm: Int,         // mm/an
        val jGel: Int,          // jours/an
        val jSec: Int,          // jours/an
        val ensoleilH: Int,     // heures/an
        val altMoyM: Int        // m
    )

    /** Retourne les normales du département (code INSEE 2 chiffres). */
    fun get(numDept: String): NormalesDept? = ALL.find { it.numDept == numDept }

    /** Retourne les normales les plus proches par IDW lat/lon (fallback si département inconnu). */
    fun getByLocation(lat: Double, lon: Double): NormalesDept {
        data class Pt(val d: Double, val norm: NormalesDept)
        val sorted = ALL.map { n ->
            val dlat = lat - DEPT_CENTROIDS[n.numDept]?.first!!
            val dlon = lon - DEPT_CENTROIDS[n.numDept]?.second!!
            val dist = Math.sqrt(dlat * dlat + dlon * dlon).coerceAtLeast(1e-6)
            Pt(dist, n)
        }.sortedBy { it.d }
        return sorted.first().norm
    }

    // ─── Centroides des départements ──────────────────────────────────────────
    // Format : num → Pair(lat, lon)
    private val DEPT_CENTROIDS = mapOf(
        "01" to Pair(46.0, 5.3), "02" to Pair(49.5, 3.5), "03" to Pair(46.3, 3.1),
        "04" to Pair(44.1, 6.2), "05" to Pair(44.7, 6.3), "06" to Pair(43.9, 7.1),
        "07" to Pair(44.7, 4.5), "08" to Pair(49.7, 4.7), "09" to Pair(43.0, 1.5),
        "10" to Pair(48.3, 4.1), "11" to Pair(43.2, 2.4), "12" to Pair(44.3, 2.7),
        "13" to Pair(43.5, 5.3), "14" to Pair(49.0, -0.3), "15" to Pair(45.0, 2.7),
        "16" to Pair(45.6, 0.2), "17" to Pair(45.8, -0.8), "18" to Pair(47.0, 2.5),
        "19" to Pair(45.3, 1.8), "21" to Pair(47.4, 4.8), "22" to Pair(48.4, -2.8),
        "23" to Pair(46.0, 2.0), "24" to Pair(45.0, 0.8), "25" to Pair(47.1, 6.3),
        "26" to Pair(44.7, 5.2), "27" to Pair(49.0, 1.0), "28" to Pair(48.4, 1.5),
        "29" to Pair(48.3, -4.0), "2A" to Pair(41.8, 9.0), "2B" to Pair(42.5, 9.2),
        "30" to Pair(43.9, 4.4), "31" to Pair(43.3, 1.3), "32" to Pair(43.6, 0.4),
        "33" to Pair(44.7, -0.5), "34" to Pair(43.6, 3.5), "35" to Pair(48.1, -1.7),
        "36" to Pair(46.7, 1.5), "37" to Pair(47.3, 0.7), "38" to Pair(45.2, 5.6),
        "39" to Pair(46.7, 5.8), "40" to Pair(44.0, -0.7), "41" to Pair(47.7, 1.3),
        "42" to Pair(45.5, 4.1), "43" to Pair(45.0, 3.8), "44" to Pair(47.3, -1.7),
        "45" to Pair(47.9, 2.0), "46" to Pair(44.7, 1.5), "47" to Pair(44.3, 0.5),
        "48" to Pair(44.5, 3.5), "49" to Pair(47.5, -0.5), "50" to Pair(49.1, -1.3),
        "51" to Pair(48.9, 4.0), "52" to Pair(48.0, 5.1), "53" to Pair(48.0, -0.7),
        "54" to Pair(48.7, 6.2), "55" to Pair(49.1, 5.3), "56" to Pair(47.8, -2.8),
        "57" to Pair(49.0, 6.6), "58" to Pair(47.1, 3.5), "59" to Pair(50.5, 3.0),
        "60" to Pair(49.4, 2.4), "61" to Pair(48.6, 0.1), "62" to Pair(50.5, 2.3),
        "63" to Pair(45.8, 3.1), "64" to Pair(43.3, -0.8), "65" to Pair(43.0, 0.2),
        "66" to Pair(42.6, 2.6), "67" to Pair(48.7, 7.5), "68" to Pair(47.8, 7.3),
        "69" to Pair(45.7, 4.7), "70" to Pair(47.6, 6.1), "71" to Pair(46.6, 4.6),
        "72" to Pair(48.0, 0.2), "73" to Pair(45.5, 6.5), "74" to Pair(46.0, 6.5),
        "75" to Pair(48.9, 2.3), "76" to Pair(49.5, 1.1), "77" to Pair(48.6, 3.1),
        "78" to Pair(48.8, 1.8), "79" to Pair(46.5, -0.3), "80" to Pair(49.9, 2.3),
        "81" to Pair(43.8, 2.2), "82" to Pair(44.1, 1.4), "83" to Pair(43.4, 6.2),
        "84" to Pair(43.9, 5.0), "85" to Pair(46.7, -1.3), "86" to Pair(46.6, 0.5),
        "87" to Pair(45.8, 1.3), "88" to Pair(48.1, 6.7), "89" to Pair(47.9, 3.5),
        "90" to Pair(47.6, 6.8), "91" to Pair(48.5, 2.2), "92" to Pair(48.9, 2.2),
        "93" to Pair(48.9, 2.5), "94" to Pair(48.8, 2.5), "95" to Pair(49.1, 2.1)
    )

    // ─── Base de données ──────────────────────────────────────────────────────

    val ALL: List<NormalesDept> = listOf(
        NormalesDept("01", "Ain",              11.5, 6.3, 17.0, 1100, 230, 280, 750, 60, 135, 1950, 450),
        NormalesDept("02", "Aisne",            10.5, 5.8, 15.5,  700, 175, 165, 720, 65, 155, 1600, 120),
        NormalesDept("03", "Allier",           11.0, 5.5, 17.0,  750, 195, 185, 730, 68, 150, 1700, 380),
        NormalesDept("04", "Alpes-de-Haute-Provence", 11.5, 4.5, 19.5, 800, 150, 200, 820, 70, 165, 2600, 900),
        NormalesDept("05", "Hautes-Alpes",      8.0, 2.0, 14.5,  850, 155, 250, 720, 95, 150, 2500, 1400),
        NormalesDept("06", "Alpes-Maritimes",  14.5, 9.0, 21.0,  900, 110, 330, 870, 25, 175, 2700, 650),
        NormalesDept("07", "Ardèche",          12.0, 5.8, 18.8,  950, 185, 265, 780, 55, 155, 2300, 520),
        NormalesDept("08", "Ardennes",          9.5, 4.5, 15.0,  750, 200, 175, 670, 75, 145, 1550, 280),
        NormalesDept("09", "Ariège",           10.5, 4.5, 17.0, 1200, 215, 340, 780, 65, 140, 1900, 900),
        NormalesDept("10", "Aube",             10.5, 5.5, 16.0,  680, 175, 160, 715, 70, 155, 1650, 170),
        NormalesDept("11", "Aude",             14.0, 8.0, 21.0,  650, 120, 210, 850, 28, 170, 2500, 350),
        NormalesDept("12", "Aveyron",          10.5, 4.8, 16.8,  950, 195, 275, 740, 70, 145, 2000, 720),
        NormalesDept("13", "Bouches-du-Rhône", 15.5, 9.5, 22.5,  550, 65, 200, 890, 15, 195, 2900, 200),
        NormalesDept("14", "Calvados",         11.0, 7.0, 15.5,  820, 195, 215, 700, 40, 135, 1700, 95),
        NormalesDept("15", "Cantal",            8.5, 3.0, 14.5,  950, 215, 280, 680, 88, 140, 1900, 900),
        NormalesDept("16", "Charente",         12.5, 6.8, 18.8,  780, 185, 215, 760, 40, 148, 2000, 110),
        NormalesDept("17", "Charente-Maritime",13.0, 7.5, 19.0,  780, 185, 210, 775, 35, 150, 2150, 35),
        NormalesDept("18", "Cher",             11.0, 5.8, 17.0,  680, 180, 155, 730, 65, 155, 1850, 175),
        NormalesDept("19", "Corrèze",          10.5, 5.0, 16.5,  980, 225, 270, 720, 62, 140, 1850, 520),
        NormalesDept("21", "Côte-d'Or",        11.0, 5.5, 17.0,  750, 185, 175, 735, 65, 150, 1850, 340),
        NormalesDept("22", "Côtes-d'Armor",    11.5, 7.2, 16.0,  900, 195, 250, 700, 35, 135, 1850, 100),
        NormalesDept("23", "Creuse",            9.5, 4.0, 15.5,  880, 195, 250, 690, 72, 142, 1800, 550),
        NormalesDept("24", "Dordogne",         12.5, 6.5, 19.0,  900, 205, 245, 760, 45, 145, 2000, 180),
        NormalesDept("25", "Doubs",            10.0, 4.5, 16.0,  1100, 255, 290, 710, 80, 140, 1800, 550),
        NormalesDept("26", "Drôme",            12.5, 6.0, 20.0,  900, 170, 250, 800, 55, 160, 2400, 430),
        NormalesDept("27", "Eure",             10.5, 5.5, 16.0,  710, 180, 180, 700, 55, 145, 1650, 95),
        NormalesDept("28", "Eure-et-Loir",     10.5, 5.2, 16.5,  650, 165, 155, 715, 62, 155, 1700, 145),
        NormalesDept("29", "Finistère",        12.0, 8.0, 16.5,  1150, 210, 325, 680, 25, 125, 1800, 70),
        NormalesDept("2A", "Corse-du-Sud",     15.5, 9.8, 22.0,  700, 65, 305, 860, 12, 185, 2800, 480),
        NormalesDept("2B", "Haute-Corse",      14.5, 8.5, 21.0,  800, 85, 310, 840, 18, 175, 2700, 700),
        NormalesDept("30", "Gard",             15.0, 8.5, 22.5,  750, 130, 250, 870, 22, 175, 2700, 225),
        NormalesDept("31", "Haute-Garonne",    13.0, 7.0, 19.5,  680, 155, 195, 800, 40, 160, 2100, 350),
        NormalesDept("32", "Gers",             13.0, 7.0, 19.5,  800, 175, 220, 795, 38, 155, 2100, 180),
        NormalesDept("33", "Gironde",          13.5, 8.0, 19.5,  900, 180, 240, 790, 30, 148, 2100, 60),
        NormalesDept("34", "Hérault",          15.0, 8.8, 22.5,  700, 115, 250, 870, 20, 178, 2700, 200),
        NormalesDept("35", "Ille-et-Vilaine",  11.5, 6.8, 16.5,  750, 185, 195, 700, 38, 138, 1850, 75),
        NormalesDept("36", "Indre",            11.5, 5.8, 17.5,  700, 185, 165, 730, 60, 150, 1900, 155),
        NormalesDept("37", "Indre-et-Loire",   12.0, 6.5, 18.0,  650, 175, 155, 735, 52, 150, 1950, 90),
        NormalesDept("38", "Isère",            11.0, 5.0, 17.5, 1050, 225, 280, 750, 65, 145, 2100, 750),
        NormalesDept("39", "Jura",             10.5, 4.8, 16.5, 1150, 245, 285, 710, 75, 138, 1850, 560),
        NormalesDept("40", "Landes",           14.0, 8.5, 20.0, 1050, 205, 270, 795, 28, 148, 2100, 45),
        NormalesDept("41", "Loir-et-Cher",     11.5, 5.8, 17.5,  650, 170, 155, 730, 58, 152, 1900, 105),
        NormalesDept("42", "Loire",            11.0, 5.5, 17.0,  750, 200, 175, 730, 65, 148, 1950, 480),
        NormalesDept("43", "Haute-Loire",       9.5, 3.5, 16.0,  850, 215, 250, 700, 82, 142, 2100, 950),
        NormalesDept("44", "Loire-Atlantique", 12.5, 7.5, 18.0,  800, 185, 215, 730, 35, 145, 1950, 45),
        NormalesDept("45", "Loiret",           11.0, 5.5, 17.0,  650, 170, 150, 725, 62, 155, 1850, 110),
        NormalesDept("46", "Lot",              12.5, 6.5, 19.0,  850, 190, 230, 760, 48, 150, 2100, 340),
        NormalesDept("47", "Lot-et-Garonne",   13.5, 7.5, 20.0,  780, 175, 215, 785, 35, 155, 2100, 105),
        NormalesDept("48", "Lozère",            9.5, 3.5, 16.0, 1050, 215, 320, 700, 82, 140, 2100, 1100),
        NormalesDept("49", "Maine-et-Loire",   12.0, 6.8, 17.8,  700, 185, 185, 720, 45, 148, 1950, 65),
        NormalesDept("50", "Manche",           11.5, 7.5, 15.8, 1000, 210, 275, 690, 35, 130, 1700, 85),
        NormalesDept("51", "Marne",            10.5, 5.0, 16.5,  680, 175, 155, 715, 68, 155, 1650, 130),
        NormalesDept("52", "Haute-Marne",      10.0, 4.5, 16.0,  750, 190, 175, 710, 72, 148, 1700, 350),
        NormalesDept("53", "Mayenne",          11.5, 6.5, 17.0,  750, 185, 195, 710, 48, 140, 1800, 95),
        NormalesDept("54", "Meurthe-et-Moselle", 10.0, 4.5, 16.0, 750, 195, 170, 700, 72, 150, 1650, 290),
        NormalesDept("55", "Meuse",            10.0, 4.5, 16.0,  760, 195, 175, 705, 72, 148, 1650, 330),
        NormalesDept("56", "Morbihan",         12.0, 7.5, 16.8,  900, 200, 250, 700, 28, 132, 1900, 60),
        NormalesDept("57", "Moselle",          10.0, 4.8, 16.0,  750, 190, 175, 700, 70, 150, 1650, 270),
        NormalesDept("58", "Nièvre",           11.0, 5.0, 17.5,  750, 195, 175, 725, 65, 150, 1800, 310),
        NormalesDept("59", "Nord",             10.8, 6.0, 15.8,  750, 195, 195, 680, 52, 140, 1650, 40),
        NormalesDept("60", "Oise",             10.5, 5.5, 16.0,  680, 175, 160, 705, 60, 150, 1650, 120),
        NormalesDept("61", "Orne",             11.0, 6.0, 16.5,  800, 195, 205, 700, 52, 138, 1700, 190),
        NormalesDept("62", "Pas-de-Calais",    10.5, 6.0, 15.5,  780, 200, 200, 670, 52, 140, 1600, 55),
        NormalesDept("63", "Puy-de-Dôme",     10.0, 4.0, 16.5,  850, 215, 240, 710, 75, 145, 2000, 720),
        NormalesDept("64", "Pyrénées-Atlantiques", 13.0, 7.5, 19.0, 1350, 255, 360, 780, 38, 130, 2000, 480),
        NormalesDept("65", "Hautes-Pyrénées", 11.0, 5.0, 17.5, 1100, 225, 290, 760, 55, 140, 2000, 850),
        NormalesDept("66", "Pyrénées-Orientales", 14.5, 8.0, 22.0, 600, 100, 210, 870, 28, 180, 2700, 580),
        NormalesDept("67", "Bas-Rhin",         10.5, 5.0, 16.5,  620, 175, 145, 720, 62, 152, 1800, 280),
        NormalesDept("68", "Haut-Rhin",        11.0, 5.5, 17.0,  650, 185, 150, 730, 60, 150, 1850, 350),
        NormalesDept("69", "Rhône",            12.0, 6.5, 18.5,  820, 200, 205, 750, 52, 148, 2050, 320),
        NormalesDept("70", "Haute-Saône",      10.5, 5.0, 16.5,  900, 215, 215, 710, 70, 145, 1800, 340),
        NormalesDept("71", "Saône-et-Loire",   11.5, 5.8, 17.8,  820, 200, 200, 730, 62, 148, 1900, 310),
        NormalesDept("72", "Sarthe",           11.5, 6.0, 17.5,  720, 185, 185, 715, 52, 145, 1850, 85),
        NormalesDept("73", "Savoie",            9.5, 3.0, 16.5, 1200, 265, 295, 730, 88, 138, 2100, 1200),
        NormalesDept("74", "Haute-Savoie",     10.0, 4.0, 17.0, 1350, 285, 320, 740, 80, 135, 2000, 1100),
        NormalesDept("75", "Paris",            12.5, 7.5, 18.0,  640, 165, 145, 730, 42, 155, 1680, 70),
        NormalesDept("76", "Seine-Maritime",   11.0, 6.5, 16.0,  800, 195, 210, 695, 45, 138, 1700, 90),
        NormalesDept("77", "Seine-et-Marne",   11.0, 5.5, 17.0,  680, 175, 155, 720, 60, 150, 1750, 110),
        NormalesDept("78", "Yvelines",         11.5, 6.0, 17.5,  660, 170, 150, 725, 55, 152, 1700, 125),
        NormalesDept("79", "Deux-Sèvres",      12.5, 7.0, 18.5,  780, 195, 205, 750, 42, 148, 1950, 140),
        NormalesDept("80", "Somme",            10.8, 6.0, 15.8,  700, 185, 175, 685, 52, 142, 1600, 85),
        NormalesDept("81", "Tarn",             13.0, 7.0, 19.5,  850, 185, 230, 800, 38, 160, 2200, 390),
        NormalesDept("82", "Tarn-et-Garonne",  13.5, 7.5, 20.0,  730, 160, 200, 795, 35, 160, 2200, 155),
        NormalesDept("83", "Var",              15.5, 9.8, 22.5,  780, 80, 310, 880, 12, 195, 2900, 400),
        NormalesDept("84", "Vaucluse",         14.5, 8.5, 21.5,  700, 110, 260, 865, 28, 185, 2850, 310),
        NormalesDept("85", "Vendée",           13.0, 7.5, 19.0,  800, 185, 215, 745, 32, 145, 2100, 45),
        NormalesDept("86", "Vienne",           12.5, 6.8, 18.8,  720, 185, 185, 745, 48, 148, 1950, 120),
        NormalesDept("87", "Haute-Vienne",     11.0, 5.5, 17.0,  950, 215, 260, 720, 58, 140, 1850, 380),
        NormalesDept("88", "Vosges",            9.5, 4.0, 15.5,  1050, 240, 255, 680, 82, 138, 1700, 500),
        NormalesDept("89", "Yonne",            11.0, 5.5, 17.0,  720, 185, 165, 720, 62, 150, 1800, 220),
        NormalesDept("90", "Territoire de Belfort", 10.5, 4.8, 16.5, 920, 215, 220, 710, 75, 142, 1750, 400),
        NormalesDept("91", "Essonne",          11.5, 6.0, 17.5,  660, 170, 150, 725, 55, 150, 1720, 110),
        NormalesDept("92", "Hauts-de-Seine",   12.5, 7.5, 18.0,  640, 165, 145, 730, 42, 155, 1680, 70),
        NormalesDept("93", "Seine-Saint-Denis",12.5, 7.5, 18.0,  640, 165, 145, 730, 42, 155, 1680, 70),
        NormalesDept("94", "Val-de-Marne",     12.5, 7.5, 18.0,  640, 165, 145, 730, 42, 155, 1680, 70),
        NormalesDept("95", "Val-d'Oise",       11.0, 5.8, 17.0,  660, 170, 150, 715, 58, 150, 1700, 100)
    )
}
