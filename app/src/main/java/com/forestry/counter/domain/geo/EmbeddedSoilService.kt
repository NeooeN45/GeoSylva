package com.forestry.counter.domain.geo

import com.forestry.counter.domain.model.station.Drainage
import com.forestry.counter.domain.model.station.TextureSol
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Résultat de contexte pédologique interpolé.
 */
data class SoilContextData(
    val phSurface: Double,          // pH horizon A (0–10 cm)
    val rumMm: Int,                  // Réserve Utile Maximale estimée (mm)
    val texture: TextureSol,
    val drainage: Drainage,
    val source: String = "IDW_embedded"
)

/**
 * Service de données pédologiques France entière, 100% offline.
 *
 * Données embarquées : ~110 points représentatifs des grandes unités
 * pédologiques françaises (Référentiel Pédologique INRAE, LUCAS Soil 2015).
 *
 * Méthode : IDW (Inverse Distance Weighting, exposant 2) sur les 6 voisins
 * les plus proches. Précision indicative (~25–50 km) — pertinente pour
 * le typage écologique (espèces indicatrices, gradient trophique/hydrique),
 * NON pour l'analyse agronomique fine.
 *
 * Légende texture : 0=ARGILEUSE 1=LIMONEUSE 2=SABLEUSE 3=ARGILO_LIMONEUSE
 *                   4=ARGILO_SABLEUSE 5=LIMONO_SABLEUSE 6=GRAVELEUSE
 * Légende drainage : 0=EXCESSIF 1=BON 2=NORMAL 3=IMPARFAIT 4=MAUVAIS
 */
object EmbeddedSoilService {

    // ─── Données embarquées ────────────────────────────────────────────────────
    // Format : floatArrayOf(lat, lon, pH×10, RUM_mm, textureCode, drainageCode)

    private val POINTS = arrayOf(
        // === BRETAGNE ===
        floatArrayOf(48.0f, -4.0f, 57f, 78f, 5f, 2f),
        floatArrayOf(47.5f, -2.5f, 60f, 82f, 1f, 2f),
        floatArrayOf(48.3f, -1.5f, 62f, 88f, 1f, 2f),
        floatArrayOf(47.8f, -1.0f, 64f, 94f, 3f, 2f),
        // === NORMANDIE ===
        floatArrayOf(49.4f, -0.5f, 70f, 120f, 1f, 2f),
        floatArrayOf(49.2f,  0.8f, 73f, 125f, 1f, 2f),
        floatArrayOf(48.8f,  0.2f, 68f, 112f, 1f, 2f),
        floatArrayOf(48.6f,  1.5f, 72f, 108f, 3f, 2f),
        // === PARIS / ÎLE-DE-FRANCE ===
        floatArrayOf(48.8f,  2.3f, 77f, 140f, 3f, 2f),
        floatArrayOf(48.5f,  2.8f, 80f, 150f, 0f, 3f),
        floatArrayOf(48.0f,  2.5f, 76f, 145f, 3f, 2f),
        floatArrayOf(49.0f,  2.5f, 75f, 132f, 1f, 2f),
        floatArrayOf(48.7f,  3.5f, 78f, 138f, 3f, 2f),
        // === CHAMPAGNE ===
        floatArrayOf(49.2f,  4.0f, 82f, 94f,  1f, 1f),
        floatArrayOf(48.5f,  4.5f, 80f, 88f,  1f, 1f),
        floatArrayOf(48.8f,  5.5f, 77f, 108f, 3f, 2f),
        // === NORD / PICARDIE ===
        floatArrayOf(50.5f,  2.5f, 72f, 138f, 1f, 2f),
        floatArrayOf(49.5f,  3.0f, 73f, 132f, 1f, 2f),
        floatArrayOf(50.0f,  2.0f, 74f, 128f, 1f, 2f),
        // === ALSACE / LORRAINE ===
        floatArrayOf(48.5f,  7.7f, 70f, 150f, 3f, 2f),
        floatArrayOf(47.8f,  7.3f, 68f, 145f, 3f, 2f),
        floatArrayOf(49.0f,  6.5f, 72f, 118f, 3f, 2f),
        floatArrayOf(48.3f,  6.3f, 66f, 108f, 3f, 2f),
        // === VOSGES (grès + granite) ===
        floatArrayOf(48.1f,  6.8f, 49f, 62f,  5f, 1f),
        floatArrayOf(48.5f,  7.0f, 51f, 58f,  5f, 1f),
        // === ARDENNES ===
        floatArrayOf(49.7f,  4.8f, 50f, 68f,  4f, 2f),
        // === BOURGOGNE ===
        floatArrayOf(47.3f,  4.8f, 82f, 128f, 0f, 2f),
        floatArrayOf(46.5f,  4.5f, 78f, 118f, 0f, 2f),
        floatArrayOf(47.8f,  3.5f, 75f, 108f, 3f, 2f),
        // === MORVAN (granite) ===
        floatArrayOf(47.0f,  4.0f, 52f, 72f,  4f, 1f),
        // === BRESSE (argile) ===
        floatArrayOf(46.7f,  5.2f, 72f, 142f, 0f, 3f),
        floatArrayOf(46.0f,  4.8f, 70f, 135f, 0f, 3f),
        // === JURA ===
        floatArrayOf(46.8f,  5.8f, 75f, 115f, 3f, 2f),
        floatArrayOf(46.5f,  6.0f, 72f, 108f, 3f, 2f),
        // === CENTRE / SOLOGNE ===
        floatArrayOf(47.5f,  1.5f, 57f, 52f,  2f, 1f),
        floatArrayOf(47.0f,  2.0f, 65f, 88f,  5f, 2f),
        floatArrayOf(47.3f,  0.7f, 68f, 98f,  3f, 2f),
        floatArrayOf(47.7f,  1.0f, 70f, 102f, 3f, 2f),
        floatArrayOf(46.8f,  1.0f, 64f, 88f,  5f, 2f),
        // === PAYS DE LA LOIRE ===
        floatArrayOf(47.0f, -1.0f, 65f, 94f,  3f, 2f),
        floatArrayOf(47.4f, -0.5f, 66f, 88f,  3f, 2f),
        floatArrayOf(47.9f, -0.2f, 64f, 84f,  1f, 2f),
        floatArrayOf(47.6f,  0.0f, 68f, 98f,  5f, 2f),
        // === POITOU / CHARENTE ===
        floatArrayOf(46.5f, -0.3f, 72f, 110f, 0f, 2f),
        floatArrayOf(46.2f,  0.5f, 74f, 115f, 0f, 2f),
        floatArrayOf(46.0f, -1.2f, 68f, 98f,  3f, 2f),
        floatArrayOf(45.7f,  0.2f, 77f, 118f, 3f, 2f),
        floatArrayOf(45.9f,  0.8f, 72f, 105f, 3f, 2f),
        // === LIMOUSIN (granite) ===
        floatArrayOf(45.8f,  1.5f, 52f, 68f,  4f, 2f),
        floatArrayOf(45.3f,  1.8f, 50f, 62f,  4f, 2f),
        floatArrayOf(46.0f,  2.2f, 54f, 70f,  5f, 2f),
        // === AUVERGNE / MASSIF CENTRAL ===
        floatArrayOf(45.8f,  3.1f, 57f, 74f,  3f, 2f),
        floatArrayOf(45.4f,  3.0f, 53f, 63f,  4f, 2f),
        floatArrayOf(44.8f,  3.5f, 50f, 58f,  2f, 1f),
        floatArrayOf(44.5f,  2.8f, 52f, 64f,  4f, 2f),
        floatArrayOf(45.5f,  2.5f, 55f, 70f,  4f, 2f),
        // === PÉRIGORD / LOT ===
        floatArrayOf(44.8f,  0.5f, 75f, 108f, 3f, 2f),
        floatArrayOf(44.5f,  1.2f, 77f, 118f, 3f, 2f),
        floatArrayOf(44.0f,  2.0f, 73f, 113f, 3f, 2f),
        // === GIRONDE ===
        floatArrayOf(44.8f, -0.5f, 65f, 94f,  3f, 2f),
        // === LANDES (sable acide) ===
        floatArrayOf(44.5f, -1.0f, 50f, 44f,  2f, 0f),
        floatArrayOf(44.0f, -0.7f, 48f, 38f,  2f, 0f),
        floatArrayOf(43.7f, -0.5f, 52f, 52f,  5f, 1f),
        // === PYRÉNÉES ===
        floatArrayOf(43.2f, -0.3f, 62f, 88f,  4f, 1f),
        floatArrayOf(43.0f,  1.0f, 60f, 84f,  4f, 1f),
        floatArrayOf(42.8f,  1.8f, 65f, 98f,  3f, 2f),
        floatArrayOf(42.7f,  2.8f, 67f, 94f,  3f, 1f),
        floatArrayOf(43.5f, -0.2f, 63f, 86f,  4f, 2f),
        floatArrayOf(42.9f,  1.5f, 62f, 90f,  3f, 2f),
        // === HAUTE-GARONNE / TARN ===
        floatArrayOf(43.6f,  1.4f, 73f, 114f, 3f, 2f),
        floatArrayOf(43.3f,  0.5f, 70f, 104f, 3f, 2f),
        floatArrayOf(44.0f,  1.5f, 72f, 108f, 3f, 2f),
        floatArrayOf(43.2f,  2.5f, 68f, 93f,  4f, 2f),
        floatArrayOf(43.1f,  0.8f, 65f, 93f,  3f, 2f),
        // === RHÔNE-ALPES ===
        floatArrayOf(45.7f,  4.8f, 68f, 118f, 3f, 2f),
        floatArrayOf(45.2f,  5.7f, 65f, 108f, 3f, 2f),
        floatArrayOf(44.6f,  4.5f, 65f, 98f,  4f, 2f),
        floatArrayOf(44.7f,  5.2f, 70f, 104f, 3f, 2f),
        floatArrayOf(44.5f,  4.8f, 72f, 113f, 3f, 2f),
        // === ALPES ===
        floatArrayOf(44.8f,  6.2f, 58f, 78f,  6f, 1f),
        floatArrayOf(45.5f,  6.5f, 55f, 68f,  6f, 1f),
        floatArrayOf(45.8f,  6.1f, 60f, 83f,  6f, 1f),
        floatArrayOf(46.2f,  6.5f, 62f, 88f,  6f, 1f),
        floatArrayOf(44.2f,  6.5f, 70f, 73f,  6f, 1f),
        floatArrayOf(43.8f,  7.0f, 73f, 68f,  6f, 1f),
        floatArrayOf(44.5f,  6.8f, 65f, 63f,  6f, 1f),
        // === ARDÈCHE ===
        floatArrayOf(44.6f,  4.2f, 62f, 85f,  4f, 2f),
        // === CAMARGUE ===
        floatArrayOf(43.5f,  4.5f, 80f, 158f, 0f, 4f),
        // === PROVENCE ===
        floatArrayOf(43.5f,  5.5f, 78f, 68f,  6f, 1f),
        floatArrayOf(43.7f,  5.0f, 76f, 73f,  3f, 1f),
        floatArrayOf(43.4f,  6.2f, 74f, 63f,  6f, 1f),
        floatArrayOf(43.2f,  5.8f, 77f, 58f,  6f, 1f),
        floatArrayOf(43.7f,  7.3f, 75f, 60f,  6f, 1f),
        // === LANGUEDOC ===
        floatArrayOf(43.6f,  3.8f, 72f, 73f,  3f, 2f),
        floatArrayOf(43.8f,  4.3f, 74f, 78f,  3f, 2f),
        floatArrayOf(43.0f,  3.0f, 70f, 63f,  6f, 1f),
        floatArrayOf(42.8f,  2.8f, 68f, 58f,  6f, 1f),
        // === VALLÉE DU RHÔNE ===
        floatArrayOf(44.0f,  4.5f, 73f, 108f, 3f, 2f),
        // === CORSE ===
        floatArrayOf(42.2f,  9.0f, 58f, 63f,  4f, 1f),
        floatArrayOf(42.5f,  9.2f, 60f, 68f,  4f, 2f),
        floatArrayOf(42.7f,  8.7f, 56f, 60f,  4f, 1f),
        floatArrayOf(41.8f,  8.8f, 62f, 73f,  3f, 2f)
    )

    private val TEXTURE_VALUES = TextureSol.values()
    private val DRAINAGE_VALUES = Drainage.values()

    // ─── API publique ──────────────────────────────────────────────────────────

    /**
     * Retourne les données pédologiques estimées pour une position.
     * Interpolation IDW sur les 6 voisins les plus proches (toujours disponible, offline).
     */
    fun getSoilData(lat: Double, lon: Double): SoilContextData {
        data class Neighbor(val dist: Double, val ph: Double, val rum: Double,
                            val texCode: Int, val drCode: Int)

        val neighbors = POINTS.map { p ->
            val dlat = lat - p[0]
            val dlon = lon - p[1]
            val dist = sqrt(dlat * dlat + dlon * dlon).coerceAtLeast(1e-6)
            Neighbor(dist, p[2] / 10.0, p[3].toDouble(), p[4].toInt(), p[5].toInt())
        }.sortedBy { it.dist }.take(6)

        var wSum = 0.0; var phW = 0.0; var rumW = 0.0
        val texCount = IntArray(TEXTURE_VALUES.size)
        val drCount  = IntArray(DRAINAGE_VALUES.size)

        for (n in neighbors) {
            val w = 1.0 / n.dist.pow(2)
            wSum  += w
            phW   += w * n.ph
            rumW  += w * n.rum
            texCount[n.texCode.coerceIn(0, texCount.lastIndex)]++
            drCount[n.drCode.coerceIn(0, drCount.lastIndex)]++
        }

        val ph      = (phW / wSum * 10).toLong() / 10.0
        val rum     = (rumW / wSum).toInt()
        val texture = TEXTURE_VALUES[texCount.indices.maxByOrNull { texCount[it] } ?: 7]
        val drain   = DRAINAGE_VALUES[drCount.indices.maxByOrNull { drCount[it] } ?: 2]

        return SoilContextData(ph, rum, texture, drain)
    }
}
