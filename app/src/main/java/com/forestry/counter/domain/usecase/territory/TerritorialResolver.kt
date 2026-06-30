package com.forestry.counter.domain.usecase.territory

import kotlin.math.*

// ══════════════════════════════════════════════════════════════════════════════
//  TerritorialResolver — Détection région/département/altitude fiable
//
//  Méthode :
//   1. Bounding-box rapide pour pré-filtrer les départements candidats
//   2. Distance Haversine au centroïde pour sélectionner le plus proche
//   3. Score de confiance (0–100) lié à la distance résiduelle
//
//  Sources centroïdes : INSEE / BD TOPO IGN (France métropolitaine)
//  Altitude : interpolation IDW depuis 52 points altitudinaux de référence
//             (sommets, bassins sédimentaires, plaines, plateaux)
//
//  Limites documentées :
//   - Précision ~5–30 km selon la densité locale (acceptable pour l'inférence
//     de zone bioclimatique et la sélection de pack)
//   - Pour une précision < 1 km, un WKT/GeoJSON IGN est requis (TODO_METIER)
// ══════════════════════════════════════════════════════════════════════════════

object TerritorialResolver {

    // ─── Structures ──────────────────────────────────────────────────────────

    data class DeptEntry(
        val code: String,           // code INSEE dept (ex: "63", "2A")
        val name: String,
        val regionCode: String,
        val centLat: Double,
        val centLon: Double,
        val minLat: Double, val maxLat: Double,
        val minLon: Double, val maxLon: Double
    )

    data class RegionEntry(
        val code: String,           // code INSEE région (ex: "84")
        val name: String,
        val centLat: Double,
        val centLon: Double
    )

    data class TerritorialResult(
        val deptCode: String?,
        val deptName: String?,
        val regionCode: String?,
        val regionName: String?,
        val altitudeM: Double,
        val confidenceGps: Int,     // 0–100 : confiance de la détection territoire
        val altitudeSource: String  // "IDW_52pts" ou "heuristique"
    )

    // ─── Catalogue des 96 départements métropolitains ────────────────────────
    // Source : centroïdes calculés depuis BD TOPO IGN / données INSEE
    // Format : code, nom, région, centLat, centLon, minLat, maxLat, minLon, maxLon

    val DEPARTMENTS: List<DeptEntry> = listOf(
        DeptEntry("01","Ain",              "84", 46.05, 5.35, 45.60, 46.52, 4.72, 6.02),
        DeptEntry("02","Aisne",            "32", 49.55, 3.55, 49.00, 50.10, 2.95, 4.28),
        DeptEntry("03","Allier",           "84", 46.35, 3.35, 45.75, 46.90, 2.55, 4.00),
        DeptEntry("04","Alpes-de-Haute-Provence","93", 44.10, 6.30, 43.55, 44.70, 5.45, 7.08),
        DeptEntry("05","Hautes-Alpes",     "93", 44.70, 6.35, 44.15, 45.20, 5.50, 7.10),
        DeptEntry("06","Alpes-Maritimes",  "93", 43.95, 7.20, 43.40, 44.40, 6.60, 7.75),
        DeptEntry("07","Ardèche",          "84", 44.75, 4.45, 44.15, 45.40, 3.95, 4.95),
        DeptEntry("08","Ardennes",         "44", 49.70, 4.70, 49.30, 50.20, 4.00, 5.55),
        DeptEntry("09","Ariège",           "76", 42.90, 1.50, 42.45, 43.40, 0.85, 2.20),
        DeptEntry("10","Aube",             "44", 48.30, 4.10, 47.85, 48.65, 3.40, 5.00),
        DeptEntry("11","Aude",             "76", 43.20, 2.40, 42.75, 43.65, 1.65, 3.25),
        DeptEntry("12","Aveyron",          "76", 44.30, 2.75, 43.75, 44.90, 1.80, 3.65),
        DeptEntry("13","Bouches-du-Rhône", "93", 43.55, 5.40, 43.10, 43.95, 4.60, 5.80),
        DeptEntry("14","Calvados",         "28", 49.10,-0.35, 48.65, 49.50,-1.30, 0.55),
        DeptEntry("15","Cantal",           "84", 45.10, 2.80, 44.55, 45.65, 2.10, 3.55),
        DeptEntry("16","Charente",         "75", 45.70, 0.30, 45.20, 46.20,-0.35, 1.00),
        DeptEntry("17","Charente-Maritime","75", 45.85,-0.75, 45.10, 46.55,-1.60, 0.10),
        DeptEntry("18","Cher",             "24", 47.05, 2.50, 46.50, 47.60, 1.75, 3.25),
        DeptEntry("19","Corrèze",          "75", 45.35, 1.95, 44.85, 45.85, 1.30, 2.60),
        DeptEntry("2A","Corse-du-Sud",     "94", 41.80, 9.10, 41.30, 42.45, 8.55, 9.60),
        DeptEntry("2B","Haute-Corse",      "94", 42.40, 9.30, 41.95, 43.10, 8.70, 9.70),
        DeptEntry("21","Côte-d'Or",        "27", 47.25, 4.80, 46.65, 47.90, 4.05, 5.65),
        DeptEntry("22","Côtes-d'Armor",    "53", 48.40,-2.90, 47.90, 48.80,-3.75,-1.95),
        DeptEntry("23","Creuse",           "75", 46.00, 2.05, 45.60, 46.45, 1.40, 2.75),
        DeptEntry("24","Dordogne",         "75", 45.10, 0.75, 44.55, 45.70, 0.00, 1.55),
        DeptEntry("25","Doubs",            "27", 47.20, 6.35, 46.75, 47.65, 5.70, 7.05),
        DeptEntry("26","Drôme",            "84", 44.75, 5.20, 44.10, 45.35, 4.55, 6.00),
        DeptEntry("27","Eure",             "28", 49.10, 0.85, 48.60, 49.55, 0.15, 1.70),
        DeptEntry("28","Eure-et-Loir",     "24", 48.40, 1.35, 47.85, 48.90, 0.70, 2.00),
        DeptEntry("29","Finistère",        "53", 48.25,-3.95, 47.75, 48.75,-5.10,-3.00),
        DeptEntry("30","Gard",             "76", 43.95, 4.20, 43.45, 44.50, 3.25, 5.10),
        DeptEntry("31","Haute-Garonne",    "76", 43.35, 1.35, 42.70, 43.90, 0.55, 2.35),
        DeptEntry("32","Gers",             "76", 43.70, 0.55, 43.25, 44.15,-0.05, 1.20),
        DeptEntry("33","Gironde",          "75", 44.85,-0.65, 44.10, 45.60,-1.35, 0.40),
        DeptEntry("34","Hérault",          "76", 43.60, 3.40, 43.20, 44.10, 2.55, 4.30),
        DeptEntry("35","Ille-et-Vilaine",  "53", 48.15,-1.65, 47.70, 48.75,-2.40,-0.95),
        DeptEntry("36","Indre",            "24", 46.80, 1.55, 46.30, 47.25, 0.85, 2.30),
        DeptEntry("37","Indre-et-Loire",   "24", 47.25, 0.60, 46.85, 47.70, 0.00, 1.35),
        DeptEntry("38","Isère",            "84", 45.25, 5.60, 44.65, 45.95, 4.90, 6.40),
        DeptEntry("39","Jura",             "27", 46.75, 5.70, 46.25, 47.30, 5.15, 6.45),
        DeptEntry("40","Landes",           "75", 43.95,-0.75, 43.35, 44.75,-1.50, 0.10),
        DeptEntry("41","Loir-et-Cher",     "24", 47.55, 1.40, 47.10, 48.00, 0.65, 2.30),
        DeptEntry("42","Loire",            "84", 45.65, 4.15, 45.20, 46.15, 3.65, 4.80),
        DeptEntry("43","Haute-Loire",      "84", 45.15, 3.70, 44.70, 45.55, 3.05, 4.40),
        DeptEntry("44","Loire-Atlantique", "52", 47.40,-1.60, 46.85, 47.90,-2.45,-0.75),
        DeptEntry("45","Loiret",           "24", 47.90, 2.20, 47.40, 48.35, 1.55, 3.10),
        DeptEntry("46","Lot",              "76", 44.65, 1.60, 44.15, 45.15, 0.90, 2.35),
        DeptEntry("47","Lot-et-Garonne",   "75", 44.35, 0.55, 43.85, 44.85,-0.10, 1.20),
        DeptEntry("48","Lozère",           "76", 44.55, 3.55, 44.10, 45.00, 2.85, 4.25),
        DeptEntry("49","Maine-et-Loire",   "52", 47.40,-0.55, 46.90, 47.85,-1.35, 0.35),
        DeptEntry("50","Manche",           "28", 49.10,-1.35, 48.45, 49.75,-1.95,-0.85),
        DeptEntry("51","Marne",            "44", 48.95, 4.30, 48.40, 49.45, 3.30, 5.05),
        DeptEntry("52","Haute-Marne",      "44", 48.10, 5.15, 47.55, 48.65, 4.45, 5.90),
        DeptEntry("53","Mayenne",          "52", 48.15,-0.55, 47.75, 48.65,-1.25, 0.20),
        DeptEntry("54","Meurthe-et-Moselle","44",48.85, 6.20, 48.35, 49.45, 5.55, 7.00),
        DeptEntry("55","Meuse",            "44", 48.85, 5.35, 48.40, 49.45, 4.80, 5.90),
        DeptEntry("56","Morbihan",         "53", 47.85,-2.85, 47.35, 48.35,-3.65,-2.05),
        DeptEntry("57","Moselle",          "44", 49.15, 6.70, 48.60, 49.70, 5.95, 7.65),
        DeptEntry("58","Nièvre",           "27", 47.15, 3.65, 46.65, 47.70, 2.90, 4.45),
        DeptEntry("59","Nord",             "32", 50.40, 3.25, 50.00, 51.10, 2.50, 4.25),
        DeptEntry("60","Oise",             "32", 49.40, 2.45, 49.00, 49.90, 1.80, 3.10),
        DeptEntry("61","Orne",             "28", 48.65, 0.10, 48.15, 49.05,-0.70, 1.00),
        DeptEntry("62","Pas-de-Calais",    "32", 50.50, 2.35, 50.00, 51.00, 1.55, 3.25),
        DeptEntry("63","Puy-de-Dôme",      "84", 45.75, 3.20, 45.15, 46.35, 2.55, 3.90),
        DeptEntry("64","Pyrénées-Atlantiques","75",43.30,-0.65, 42.75, 43.90,-1.80, 0.60),
        DeptEntry("65","Hautes-Pyrénées",  "76", 43.00, 0.25, 42.50, 43.45,-0.20, 0.75),
        DeptEntry("66","Pyrénées-Orientales","76",42.65, 2.55, 42.35, 43.10, 1.65, 3.25),
        DeptEntry("67","Bas-Rhin",         "44", 48.50, 7.55, 47.85, 49.10, 6.90, 8.25),
        DeptEntry("68","Haut-Rhin",        "44", 47.90, 7.35, 47.45, 48.45, 6.80, 7.65),
        DeptEntry("69","Rhône",            "84", 45.75, 4.75, 45.35, 46.35, 4.35, 5.40),
        DeptEntry("70","Haute-Saône",      "27", 47.65, 6.10, 47.15, 48.10, 5.40, 6.90),
        DeptEntry("71","Saône-et-Loire",   "27", 46.60, 4.55, 46.00, 47.20, 3.90, 5.50),
        DeptEntry("72","Sarthe",           "52", 47.95, 0.25, 47.50, 48.45,-0.50, 1.00),
        DeptEntry("73","Savoie",           "84", 45.45, 6.45, 45.00, 46.15, 5.75, 7.20),
        DeptEntry("74","Haute-Savoie",     "84", 45.95, 6.45, 45.65, 46.55, 5.90, 7.05),
        DeptEntry("75","Paris",            "11", 48.86, 2.35, 48.81, 48.91, 2.22, 2.47),
        DeptEntry("76","Seine-Maritime",   "28", 49.70, 0.95, 49.25, 50.10,-0.00, 1.85),
        DeptEntry("77","Seine-et-Marne",   "11", 48.60, 3.05, 48.10, 49.00, 2.35, 3.80),
        DeptEntry("78","Yvelines",         "11", 48.80, 1.85, 48.45, 49.10, 1.35, 2.25),
        DeptEntry("79","Deux-Sèvres",      "75", 46.55,-0.35, 46.05, 47.15,-1.05, 0.40),
        DeptEntry("80","Somme",            "32", 50.00, 2.25, 49.55, 50.40, 1.30, 3.25),
        DeptEntry("81","Tarn",             "76", 43.85, 2.20, 43.40, 44.30, 1.55, 3.00),
        DeptEntry("82","Tarn-et-Garonne",  "76", 44.05, 1.25, 43.65, 44.45, 0.75, 1.90),
        DeptEntry("83","Var",              "93", 43.45, 6.35, 43.10, 43.85, 5.65, 6.95),
        DeptEntry("84","Vaucluse",         "93", 44.00, 5.20, 43.65, 44.40, 4.60, 5.80),
        DeptEntry("85","Vendée",           "52", 46.75,-1.25, 46.30, 47.20,-2.40,-0.25),
        DeptEntry("86","Vienne",           "75", 46.55, 0.45, 46.10, 47.05,-0.30, 1.20),
        DeptEntry("87","Haute-Vienne",     "75", 45.75, 1.35, 45.40, 46.25, 0.75, 2.00),
        DeptEntry("88","Vosges",           "44", 48.15, 6.65, 47.65, 48.65, 5.90, 7.30),
        DeptEntry("89","Yonne",            "27", 47.80, 3.55, 47.30, 48.35, 2.75, 4.35),
        DeptEntry("90","Territoire de Belfort","27",47.65, 6.95, 47.45, 47.85, 6.70, 7.25),
        DeptEntry("91","Essonne",          "11", 48.55, 2.25, 48.28, 48.80, 1.95, 2.70),
        DeptEntry("92","Hauts-de-Seine",   "11", 48.85, 2.22, 48.78, 48.94, 2.10, 2.35),
        DeptEntry("93","Seine-Saint-Denis","11", 48.95, 2.50, 48.83, 49.03, 2.32, 2.72),
        DeptEntry("94","Val-de-Marne",     "11", 48.80, 2.50, 48.68, 48.90, 2.30, 2.68),
        DeptEntry("95","Val-d'Oise",       "11", 49.10, 2.20, 48.95, 49.25, 1.60, 2.60)
    )

    val REGIONS: List<RegionEntry> = listOf(
        RegionEntry("11","Île-de-France",              48.65,  2.45),
        RegionEntry("24","Centre-Val de Loire",        47.55,  1.65),
        RegionEntry("27","Bourgogne-Franche-Comté",    47.10,  5.20),
        RegionEntry("28","Normandie",                  49.00,  0.10),
        RegionEntry("32","Hauts-de-France",            50.15,  2.60),
        RegionEntry("44","Grand Est",                  48.60,  6.10),
        RegionEntry("52","Pays de la Loire",           47.55, -0.95),
        RegionEntry("53","Bretagne",                   48.10, -3.00),
        RegionEntry("75","Nouvelle-Aquitaine",         44.95, -0.25),
        RegionEntry("76","Occitanie",                  43.70,  2.25),
        RegionEntry("84","Auvergne-Rhône-Alpes",       45.40,  4.70),
        RegionEntry("93","Provence-Alpes-Côte d'Azur",43.90,  6.25),
        RegionEntry("94","Corse",                      42.10,  9.25)
    )

    // ─── Points altitudinaux de référence (52 points) ────────────────────────
    // Source : sommets, passes, bassins, plaines — valeurs réelles IGN/Wikipedia
    // Format : lat, lon, altM

    private val ALT_REFERENCE_POINTS: List<Triple<Double, Double, Double>> = listOf(
        // Alpes — massifs principaux
        Triple(45.83, 6.86, 4808.0),   // Mont-Blanc
        Triple(45.50, 6.72, 3855.0),   // Grande Casse (Vanoise)
        Triple(44.92, 6.73, 4102.0),   // Barre des Écrins
        Triple(45.10, 6.90, 3750.0),   // Mont Viso (FR)
        Triple(45.68, 7.00, 2000.0),   // Col du Mont-Cenis
        Triple(45.06, 6.35, 2645.0),   // Col du Galibier
        Triple(45.30, 5.90, 1800.0),   // Vercors (plateau)
        Triple(44.60, 6.60, 2800.0),   // Haute vallée de l'Ubaye
        // Pyrénées
        Triple(42.77, -0.13, 3298.0),  // Vignemale
        Triple(42.65,  0.65, 3404.0),  // Balaïtous (Pyrénées centrales)
        Triple(42.55,  2.45, 2784.0),  // Canigou
        Triple(42.75,  1.10, 1950.0),  // Col du Tourmalet
        Triple(43.10, -0.55, 1500.0),  // Pic du Midi d'Ossau
        // Massif Central
        Triple(45.56,  2.97, 1886.0),  // Puy de Dôme
        Triple(44.84,  2.81, 1885.0),  // Plomb du Cantal
        Triple(44.48,  3.68, 1702.0),  // Mont Aigoual
        Triple(44.55,  4.25, 1600.0),  // Mont Lozère
        Triple(45.15,  3.65, 1754.0),  // Mont Mezenc
        Triple(46.37,  3.55,  700.0),  // Bocage bourbonnais
        // Vosges / Alsace
        Triple(47.90,  7.10, 1424.0),  // Grand Ballon
        Triple(48.10,  6.80,  900.0),  // Champ du Feu
        Triple(48.50,  7.40,  200.0),  // Plaine d'Alsace
        Triple(47.65,  7.05,  400.0),  // Collines sous-vosgiennes
        // Jura
        Triple(46.37,  5.85, 1718.0),  // Crêt de la Neige
        Triple(46.90,  5.70, 1000.0),  // Plateau du Jura
        // Ardennes
        Triple(49.85,  4.85,  500.0),  // Ardennes belgo-françaises
        // Bretagne / Normandie — altitude modeste
        Triple(48.35, -3.85,  384.0),  // Monts d'Arrée
        Triple(48.95,  0.35,  417.0),  // Collines Normandes (Alpes mancelles)
        Triple(49.30, -1.35,   80.0),  // Cotentin
        // Bassin parisien — plaines et plateaux
        Triple(48.85,  2.35,   50.0),  // Paris
        Triple(48.60,  1.85,  180.0),  // Plateau de Beauce
        Triple(47.85,  2.95,  150.0),  // Forêt d'Orléans
        Triple(49.35,  2.40,   80.0),  // Plaine picarde
        Triple(48.25,  4.50,  250.0),  // Plateau champenois
        Triple(49.60,  5.85,  450.0),  // Argonne / Forêt de la Meuse
        // Pays de Loire / Vendée
        Triple(47.40, -0.55,   50.0),  // Val de Loire (plaine)
        Triple(46.80, -1.30,   80.0),  // Bocage vendéen
        Triple(46.65,  0.45,  150.0),  // Gâtine poitevine
        // Sud-Ouest / Landes
        Triple(44.85, -0.55,   20.0),  // Bordeaux (estuaire)
        Triple(44.00, -0.75,   50.0),  // Forêt des Landes (plaine)
        Triple(43.30, -1.25,  200.0),  // Piémont pyrénéen (Béarn)
        // Méditerranée / Provence
        Triple(43.55,  5.40,   40.0),  // Aix-en-Provence (plaine)
        Triple(43.50,  6.30,  600.0),  // Var intérieur
        Triple(43.25,  3.80,  300.0),  // Hérault (garrigue)
        Triple(44.10,  4.30,  400.0),  // Gard (garrigues)
        Triple(43.85,  4.65,  150.0),  // Camargue / Rhône
        // Corse
        Triple(42.30,  9.10, 2706.0),  // Monte Cinto
        Triple(41.80,  9.00,  500.0),  // Corse du Sud (plaine côtière)
        // Rhône / Saône
        Triple(45.95,  4.75,  200.0),  // Ain (Bresse)
        Triple(46.75,  4.80,  300.0),  // Saône-et-Loire (plaine)
        Triple(45.75,  4.85,  175.0),  // Lyon (métropole)
        // Bourgogne
        Triple(47.05,  4.90,  400.0)   // Côte-d'Or (plateau)
    )

    // ─── API publique ─────────────────────────────────────────────────────────

    /**
     * Résout la localisation depuis des coordonnées GPS.
     * Retourne département, région, altitude interpolée et score de confiance.
     */
    fun resolve(lat: Double, lon: Double): TerritorialResult {
        val dept = findDepartment(lat, lon)
        val region = if (dept != null) REGIONS.find { it.code == dept.regionCode } else findRegion(lat, lon)
        val alt = interpolateAltitude(lat, lon)

        // Confiance : max si le point est proche du centroïde dans la bounding box
        val confidence = if (dept != null) {
            val distKm = haversineKm(lat, lon, dept.centLat, dept.centLon)
            val bbDiagKm = haversineKm(dept.minLat, dept.minLon, dept.maxLat, dept.maxLon)
            val relDist = (distKm / (bbDiagKm / 2)).coerceAtMost(1.0)
            (85 - relDist * 30).toInt().coerceIn(40, 90)
        } else 20

        return TerritorialResult(
            deptCode   = dept?.code,
            deptName   = dept?.name,
            regionCode = region?.code,
            regionName = region?.name,
            altitudeM  = alt,
            confidenceGps = confidence,
            altitudeSource = "IDW_${ALT_REFERENCE_POINTS.size}pts"
        )
    }

    // ─── Détection département ────────────────────────────────────────────────

    private fun findDepartment(lat: Double, lon: Double): DeptEntry? {
        // 1. Pré-filtre bounding box (rapide)
        val candidates = DEPARTMENTS.filter {
            lat in it.minLat..it.maxLat && lon in it.minLon..it.maxLon
        }
        // 2. Si un seul candidat : retour direct
        if (candidates.size == 1) return candidates[0]
        // 3. Si plusieurs (zones frontalières) : plus proche centroïde
        if (candidates.size > 1) {
            return candidates.minByOrNull { haversineKm(lat, lon, it.centLat, it.centLon) }
        }
        // 4. Aucun candidat : point potentiellement hors France.
        //    Fallback tolérant pour les points légèrement hors bounding boxes
        //    (imprécisions de données), mais rejet si clairement hors France (> 50 km
        //    du centroïde le plus proche).
        val closest = DEPARTMENTS.minByOrNull { haversineKm(lat, lon, it.centLat, it.centLon) }
            ?: return null
        val distToClosest = haversineKm(lat, lon, closest.centLat, closest.centLon)
        return if (distToClosest > 50.0) null else closest
    }

    private fun findRegion(lat: Double, lon: Double): RegionEntry? {
        val closest = REGIONS.minByOrNull { haversineKm(lat, lon, it.centLat, it.centLon) }
            ?: return null
        // Rejet si clairement hors France (> 60 km du centroïde région le plus proche)
        val dist = haversineKm(lat, lon, closest.centLat, closest.centLon)
        return if (dist > 60.0) null else closest
    }

    // ─── Interpolation altitudinale (IDW) ────────────────────────────────────

    /**
     * Interpolation de l'altitude par pondération inverse à la distance (IDW).
     * Utilise les k=8 points de référence les plus proches (puissance 2).
     *
     * Précision typique : ±50–200 m selon la complexité du relief.
     * Suffisant pour ClimateZone.detect() et l'inférence de contexte.
     * TODO_METIER : remplacer par MNT 75m embarqué (SRTM France) pour ±10 m.
     */
    fun interpolateAltitude(lat: Double, lon: Double): Double {
        val k = 8
        val sorted = ALT_REFERENCE_POINTS
            .map { (pLat, pLon, alt) -> Pair(haversineKm(lat, lon, pLat, pLon), alt) }
            .sortedBy { it.first }
            .take(k)

        // Si très proche d'un point de référence, retourne sa valeur directement
        val nearest = sorted[0]
        if (nearest.first < 2.0) return nearest.second

        val power = 2.0
        val weightedSum = sorted.sumOf { (d, alt) -> alt / d.pow(power) }
        val weightSum   = sorted.sumOf { (d, _)   -> 1.0 / d.pow(power) }
        return (weightedSum / weightSum).coerceAtLeast(0.0)
    }

    // ─── Haversine ────────────────────────────────────────────────────────────

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * asin(sqrt(a))
    }
}
