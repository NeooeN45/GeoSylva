package com.forestry.counter.domain.geo

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.URL
import kotlin.math.*

/**
 * Résultat SRTM : altitude, pente et exposition calculées depuis une grille 3×3.
 */
data class SrtmTerrainData(
    val altitudeM: Int,
    val slopePct: Int,
    val aspectDeg: Int,
    val aspectLabel: String   // N, NE, E, SE, S, SW, W, NW
)

/**
 * Service de récupération des données MNT SRTM 30m via l'API OpenTopoData.
 *
 * Stratégie :
 *  1. Demande une grille 3×3 de points espacés de ~90m autour de la position cible.
 *  2. Calcule pente (algo de Horn) et exposition géographique depuis cette grille.
 *  3. Cache les résultats dans un fichier JSON local (résolution 0.01°).
 *
 * Source : https://api.opentopodata.org/v1/srtm30m
 * Limite : 100 localisations / requête, 1 requête/seconde (usage raisonnable).
 */
object SrtmElevationService {

    private const val API_URL = "https://api.opentopodata.org/v1/srtm30m"
    private const val CELL_M   = 90.0       // espacement de la grille ≈ 90 m
    private const val DEG_PER_M = 1.0 / 111_320.0  // 1° ≈ 111 320 m latitude

    /**
     * Retourne les données terrain SRTM pour une coordonnée donnée.
     * Vérifie d'abord le cache local ; si absent, télécharge et met en cache.
     *
     * @return [SrtmTerrainData] ou null si hors-ligne ou erreur réseau
     */
    fun getTerrainData(context: Context, lat: Double, lon: Double): SrtmTerrainData? {
        val key = cacheKey(lat, lon)
        val cached = loadFromCache(context, key)
        if (cached != null) return cached

        return try {
            val result = fetchFromApi(lat, lon)
            if (result != null) saveToCache(context, key, result)
            result
        } catch (_: Exception) {
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Calcul de grille 3×3 + Horn's algorithm
    // ─────────────────────────────────────────────────────────────────────────

    private fun fetchFromApi(lat: Double, lon: Double): SrtmTerrainData? {
        val delta = CELL_M * DEG_PER_M
        // Grille 3×3 : ligne par ligne NW → SE
        //  [0]=NW [1]=N  [2]=NE
        //  [3]=W  [4]=C  [5]=E
        //  [6]=SW [7]=S  [8]=SE
        val pts = listOf(
            Pair(lat + delta, lon - delta), Pair(lat + delta, lon), Pair(lat + delta, lon + delta),
            Pair(lat,         lon - delta), Pair(lat,         lon), Pair(lat,         lon + delta),
            Pair(lat - delta, lon - delta), Pair(lat - delta, lon), Pair(lat - delta, lon + delta)
        )

        val locations = pts.joinToString("|") { (la, lo) -> "$la,$lo" }
        val url = "$API_URL?locations=$locations"
        val json = JSONObject(URL(url).readText())

        if (json.optString("status") != "OK") return null

        val results = json.getJSONArray("results")
        if (results.length() < 9) return null

        val z = Array(9) { i ->
            results.getJSONObject(i).optInt("elevation", 0).toDouble()
        }

        val altitudeM = z[4].roundToInt()
        val (slopePct, aspectDeg) = computeSlopeAspect(z, CELL_M)
        val aspectLabel = degToCardinal(aspectDeg)

        return SrtmTerrainData(
            altitudeM   = altitudeM,
            slopePct    = slopePct,
            aspectDeg   = aspectDeg,
            aspectLabel = aspectLabel
        )
    }

    /**
     * Calcule pente (%) et exposition (°N) depuis une grille 3×3 (Horn 1981).
     *
     * @param z       Tableau de 9 valeurs d'élévation, ordre : NW N NE / W C E / SW S SE
     * @param cellM   Espacement entre cellules en mètres
     */
    private fun computeSlopeAspect(z: Array<Double>, cellM: Double): Pair<Int, Int> {
        val nw = z[0]; val n  = z[1]; val ne = z[2]
        val w  = z[3];                val e  = z[5]
        val sw = z[6]; val s  = z[7]; val se = z[8]

        val dzdx = ((ne + 2.0 * e + se) - (nw + 2.0 * w + sw)) / (8.0 * cellM)
        val dzdy = ((nw + 2.0 * n + ne) - (sw + 2.0 * s + se)) / (8.0 * cellM)

        val slopeRad = atan(sqrt(dzdx * dzdx + dzdy * dzdy))
        val slopePct = (tan(slopeRad) * 100.0).roundToInt().coerceIn(0, 999)

        // Convention géographique : 0°=N, 90°=E, 180°=S, 270°=W
        var aspectDeg = Math.toDegrees(atan2(-dzdy, dzdx)).toInt()
        aspectDeg = (90 - aspectDeg + 360) % 360

        return Pair(slopePct, aspectDeg)
    }

    private fun degToCardinal(deg: Int): String {
        val dirs = listOf("N","NE","E","SE","S","SO","O","NO")
        return dirs[((deg + 22) % 360) / 45]
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cache JSON local
    // ─────────────────────────────────────────────────────────────────────────

    private fun cacheKey(lat: Double, lon: Double): String {
        val la = (lat * 100).roundToInt()
        val lo = (lon * 100).roundToInt()
        return "${la}_${lo}"
    }

    private fun cacheFile(context: Context): File =
        File(context.filesDir, "srtm_cache.json").also {
            if (!it.exists()) it.writeText("{}")
        }

    private fun loadFromCache(context: Context, key: String): SrtmTerrainData? {
        return try {
            val obj = JSONObject(cacheFile(context).readText())
            if (!obj.has(key)) return null
            val e = obj.getJSONObject(key)
            SrtmTerrainData(
                altitudeM   = e.getInt("alt"),
                slopePct    = e.getInt("slp"),
                aspectDeg   = e.getInt("asp"),
                aspectLabel = e.getString("lbl")
            )
        } catch (_: Exception) { null }
    }

    private fun saveToCache(context: Context, key: String, data: SrtmTerrainData) {
        try {
            val file = cacheFile(context)
            val obj  = JSONObject(file.readText())
            val entry = JSONObject().apply {
                put("alt", data.altitudeM)
                put("slp", data.slopePct)
                put("asp", data.aspectDeg)
                put("lbl", data.aspectLabel)
            }
            obj.put(key, entry)
            file.writeText(obj.toString())
        } catch (_: Exception) {}
    }

    /**
     * Efface le cache SRTM local (utile pour forcer un re-téléchargement).
     */
    fun clearCache(context: Context) {
        cacheFile(context).writeText("{}")
    }
}
