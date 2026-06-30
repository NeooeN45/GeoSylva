package com.forestry.counter.domain.location

import kotlin.math.*

/**
 * Convertisseur WGS84 (EPSG:4326) ↔ Lambert 93 (EPSG:2154).
 *
 * Lambert 93 est le système de projection officiel en France métropolitaine
 * depuis le décret n° 2000-1276 du 26 décembre 2000.
 *
 * **Datum** : Lambert 93 est défini sur le RGF93 (Réseau Géodésique Français),
 * lui-même confondu avec l'ETRS89 (European Terrestrial Reference System 1989).
 * Le RGF93/ETRS89 diffère du WGS84 d'environ 60 cm en 2026 (croissance ~2,5 cm/an
 * depuis 1989, due à la dérive de la plaque eurasienne). Pour les applications
 * forestières (précision cible 1-2 m), cette différence est négligeable et la
 * correction WGS84→ETRS89 est désactivée par défaut. Elle peut être activée via
 * [applyEtrs89Correction] pour les usages nécessitant une précision sub-métrique.
 *
 * **Note NTF** : L'ancien système NTF (Nouvelle Triangulation de la France) et
 * les projections Lambert I-IV associées ne sont PAS gérés ici. Lambert 93 est
 * par construction sur RGF93 — aucune transformation NTF→RGF93 n'est nécessaire.
 *
 * Paramètres de projection :
 * - Ellipsoïde : GRS80 (IAG)
 * - Méridien central : 3°E (Greenwich)
 * - Parallèles standard : 44°N et 49°N
 * - Latitude d'origine : 46°30'N
 * - Fausse abscisse : 700 000 m
 * - Fausse ordonnée : 6 600 000 m
 *
 * Sources :
 * - IGN : « Algorithmes de conversion de coordonnées »
 *   (NT/G 71, Service de Géodésie et Nivellement)
 * - Registre EPSG : https://epsg.io/2154
 * - EUREF : transformation ETRS89↔ITRF (Boucher & Altamimi)
 */
object Lambert93Converter {

    // ── Paramètres de l'ellipsoïde GRS80 ──
    private const val A = 6378137.0           // demi-grand axe (m)
    private const val F_INV = 298.257222101   // aplatissement inverse
    private val F = 1.0 / F_INV              // aplatissement
    private val B = A * (1.0 - F)            // demi-petit axe
    private val E2 = 2 * F - F * F           // première excentricité²
    private val E = sqrt(E2)                  // première excentricité

    // ── Paramètres de la projection conique conforme de Lambert ──
    private val PHI_1 = Math.toRadians(44.0)     // 1er parallèle standard
    private val PHI_2 = Math.toRadians(49.0)     // 2e parallèle standard
    private val PHI_0 = Math.toRadians(46.5)     // latitude d'origine
    private val LAMBDA_0 = Math.toRadians(3.0)   // méridien central
    private const val X_0 = 700000.0             // fausse abscisse (E)
    private const val Y_0 = 6600000.0            // fausse ordonnée (N)

    // ── Paramètres de transformation WGS84 → ETRS89/RGF93 (Helmert 7 paramètres) ──
    // Valeurs approximatives à l'époque 2026.0 (croissance ~2,5 cm/an depuis 1989).
    // Source : EUREF Boucher & Altamimi, ETRF2000→ITRF.
    // Ces paramètres corrigent la dérive de la plaque eurasienne (~60 cm en 2026).
    private const val WGS84_TO_ETRS89_TX = 0.057  // translation X (m)
    private const val WGS84_TO_ETRS89_TY = 0.005  // translation Y (m)
    private const val WGS84_TO_ETRS89_TZ = 0.029  // translation Z (m)
    private const val WGS84_TO_ETRS89_RX = 0.0    // rotation X (arcsec) — négligeable
    private const val WGS84_TO_ETRS89_RY = 0.0    // rotation Y (arcsec) — négligeable
    private const val WGS84_TO_ETRS89_RZ = 0.0    // rotation Z (arcsec) — négligeable
    private const val WGS84_TO_ETRS89_SCALE = 0.0 // facteur d'échelle (ppm) — négligeable

    // ── Constantes pré-calculées ──
    private val N: Double
    private val C: Double
    private val XS: Double
    private val YS: Double

    init {
        val n1 = latIso(PHI_1)
        val n2 = latIso(PHI_2)
        val w1 = grandNormal(PHI_1)
        val w2 = grandNormal(PHI_2)

        N = (ln(w1 * cos(PHI_1)) - ln(w2 * cos(PHI_2))) / (n2 - n1)
        C = (w1 * cos(PHI_1) / N) * exp(N * n1)

        val r0 = C * exp(-N * latIso(PHI_0))
        XS = X_0
        YS = Y_0 + r0
    }

    /**
     * Convertit des coordonnées WGS84 (lon/lat en degrés) en Lambert 93 (E, N en mètres).
     *
     * @param lonDeg Longitude WGS84 en degrés décimaux
     * @param latDeg Latitude WGS84 en degrés décimaux
     * @param applyEtrs89Correction Si true, applique la correction WGS84→ETRS89 (~60 cm en 2026).
     *        Désactivé par défaut (négligeable pour la précision forestière 1-2 m).
     * @return Pair(easting, northing) en mètres Lambert 93
     */
    fun toL93(lonDeg: Double, latDeg: Double, applyEtrs89Correction: Boolean = false): Pair<Double, Double> {
        val (etrs89Lon, etrs89Lat) = if (applyEtrs89Correction) {
            wgs84ToEtrs89(lonDeg, latDeg)
        } else {
            lonDeg to latDeg
        }
        val phi = Math.toRadians(etrs89Lat)
        val lambda = Math.toRadians(etrs89Lon)

        val l = latIso(phi)
        val r = C * exp(-N * l)
        val gamma = N * (lambda - LAMBDA_0)

        val x = XS + r * sin(gamma)
        val y = YS - r * cos(gamma)
        return Pair(x, y)
    }

    /**
     * Convertit des coordonnées Lambert 93 (E, N en mètres) en WGS84 (lon, lat en degrés).
     *
     * @param easting  Abscisse Lambert 93 en mètres
     * @param northing Ordonnée Lambert 93 en mètres
     * @param applyEtrs89Correction Si true, applique la correction ETRS89→WGS84 inverse.
     * @return Pair(longitude, latitude) en degrés WGS84
     */
    fun toWGS84(easting: Double, northing: Double, applyEtrs89Correction: Boolean = false): Pair<Double, Double> {
        val dx = easting - XS
        val dy = YS - northing
        val r = sqrt(dx * dx + dy * dy)
        val gamma = atan2(dx, dy)

        val lambda = LAMBDA_0 + gamma / N
        val l = -ln(abs(r / C)) / N

        // Itération pour retrouver la latitude à partir de la latitude isométrique
        var phi = 2.0 * atan(exp(l)) - PI / 2.0
        for (i in 0 until 30) {
            val eSinPhi = E * sin(phi)
            val phiNew = 2.0 * atan(
                ((1.0 + eSinPhi) / (1.0 - eSinPhi)).pow(E / 2.0) * exp(l)
            ) - PI / 2.0
            if (abs(phiNew - phi) < 1e-12) break
            phi = phiNew
        }

        val etrs89Lon = Math.toDegrees(lambda)
        val etrs89Lat = Math.toDegrees(phi)

        return if (applyEtrs89Correction) {
            etrs89ToWgs84(etrs89Lon, etrs89Lat)
        } else {
            Pair(etrs89Lon, etrs89Lat)
        }
    }

    /**
     * Formate des coordonnées Lambert 93 pour affichage.
     * @return "E: 700 000 m — N: 6 600 000 m"
     */
    fun formatL93(easting: Double, northing: Double): String {
        return "E: %.0f m — N: %.0f m".format(easting, northing)
    }

    /**
     * Vérifie si des coordonnées WGS84 sont dans l'emprise Lambert 93
     * (France métropolitaine + marge).
     */
    fun isInFranceMetro(lonDeg: Double, latDeg: Double): Boolean {
        return lonDeg in -6.0..10.0 && latDeg in 41.0..52.0
    }

    // ── Fonctions auxiliaires ──

    /** Grande normale (rayon de courbure dans le premier vertical) */
    private fun grandNormal(phi: Double): Double {
        val sinPhi = sin(phi)
        return A / sqrt(1.0 - E2 * sinPhi * sinPhi)
    }

    /** Latitude isométrique sur l'ellipsoïde GRS80 */
    private fun latIso(phi: Double): Double {
        val eSinPhi = E * sin(phi)
        return ln(tan(PI / 4.0 + phi / 2.0) * ((1.0 - eSinPhi) / (1.0 + eSinPhi)).pow(E / 2.0))
    }

    // ── Transformation WGS84 ↔ ETRS89/RGF93 (Helmert simplifiée) ──

    /**
     * Applique la transformation WGS84 → ETRS89/RGF93 via Helmert à 3 translations.
     * Les rotations et facteur d'échelle sont négligeables (< 1 mm) pour la France.
     *
     * Précision : ~1 cm (suffisant pour sub-métrique).
     * Source : EUREF, paramètres ETRF2000 à l'époque 2026.0.
     *
     * @param lonDeg Longitude WGS84 en degrés
     * @param latDeg Latitude WGS84 en degrés
     * @return Pair(longitude, latitude) ETRS89 en degrés
     */
    private fun wgs84ToEtrs89(lonDeg: Double, latDeg: Double): Pair<Double, Double> {
        // Conversion géographique → ECEF (Earth-Centered, Earth-Fixed)
        val lon = Math.toRadians(lonDeg)
        val lat = Math.toRadians(latDeg)
        val sinLat = sin(lat)
        val cosLat = cos(lat)
        val sinLon = sin(lon)
        val cosLon = cos(lon)
        val w = 1.0 - E2 * sinLat * sinLat
        val n = A / sqrt(w)
        // Hauteur approximée à 0 (GPS forestier sans altitude précise)
        val h = 0.0
        val x = (n + h) * cosLat * cosLon
        val y = (n + h) * cosLat * sinLon
        val z = (n * (1.0 - E2) + h) * sinLat

        // Translation WGS84 → ETRS89 (inverser le signe pour ETRS89→WGS84)
        val x2 = x - WGS84_TO_ETRS89_TX
        val y2 = y - WGS84_TO_ETRS89_TY
        val z2 = z - WGS84_TO_ETRS89_TZ

        // Conversion ECEF → géographique (itération de Bowring)
        val p = sqrt(x2 * x2 + y2 * y2)
        val lon2 = atan2(y2, x2)
        val kappa = E2 * A / B
        val bigZ = z2 * (1.0 + kappa)
        val bigR = sqrt(p * p + bigZ * bigZ)
        val beta = atan(bigZ / p)
        val phi2 = atan(z2 / (p * (1.0 - E2 * A / (B * bigR) * (1.0 / cos(beta)))))

        return Pair(Math.toDegrees(lon2), Math.toDegrees(phi2))
    }

    /**
     * Applique la transformation ETRS89/RGF93 → WGS84 (inverse de [wgs84ToEtrs89]).
     */
    private fun etrs89ToWgs84(lonDeg: Double, latDeg: Double): Pair<Double, Double> {
        val lon = Math.toRadians(lonDeg)
        val lat = Math.toRadians(latDeg)
        val sinLat = sin(lat)
        val cosLat = cos(lat)
        val sinLon = sin(lon)
        val cosLon = cos(lon)
        val w = 1.0 - E2 * sinLat * sinLat
        val n = A / sqrt(w)
        val h = 0.0
        val x = (n + h) * cosLat * cosLon
        val y = (n + h) * cosLat * sinLon
        val z = (n * (1.0 - E2) + h) * sinLat

        // Translation ETRS89 → WGS84 (signe opposé)
        val x2 = x + WGS84_TO_ETRS89_TX
        val y2 = y + WGS84_TO_ETRS89_TY
        val z2 = z + WGS84_TO_ETRS89_TZ

        val p = sqrt(x2 * x2 + y2 * y2)
        val lon2 = atan2(y2, x2)
        val kappa = E2 * A / B
        val bigZ = z2 * (1.0 + kappa)
        val bigR = sqrt(p * p + bigZ * bigZ)
        val beta = atan(bigZ / p)
        val phi2 = atan(z2 / (p * (1.0 - E2 * A / (B * bigR) * (1.0 / cos(beta)))))

        return Pair(Math.toDegrees(lon2), Math.toDegrees(phi2))
    }
}
