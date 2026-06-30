package com.forestry.counter.domain.geo

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.URL
import kotlin.math.roundToInt

/**
 * Type climatique simplifié pour l'écologie forestière française.
 */
enum class ClimateType(val labelFr: String) {
    OCEANIQ("Océanique"),
    SEMI_OCEANIQ("Semi-océanique"),
    CONTINENTAL("Continental"),
    SEMI_CONTINENTAL("Semi-continental"),
    MEDITERRANEEN("Méditerranéen"),
    MONTAGNARD("Montagnard"),
    SUBALPIN("Sub-alpin"),
    INCONNU("Inconnu")
}

/**
 * Données climatiques annuelles normalisées pour une localisation.
 */
data class ClimateData(
    val tempMoyC: Double,          // température moyenne annuelle (°C)
    val precipMmAn: Int,           // précipitations totales annuelles (mm)
    val precipEteMin: Int,         // précipitations estivales (juin–août) — critère méditerranéen
    val indiceAridite: Double,     // indice De Martonne = P / (T + 10)
    val climateType: ClimateType
)

/**
 * Service de récupération des données climatiques ERA5 via l'API Open-Meteo.
 *
 * Stratégie :
 *  1. Télécharge une année complète de données journalières ERA5 (2023) :
 *     température moyenne 2 m + précipitations.
 *  2. Calcule temp moy annuelle, précip totale, précip estivale.
 *  3. Classifie le climat selon les indices De Martonne + saisonnalité.
 *  4. Cache le résultat localement (résolution 0.1° ≈ 11 km).
 *
 * API : https://archive-api.open-meteo.com  (gratuit, sans clé)
 * Quota : aucun pour usage raisonnable.
 */
object ClimateContextService {

    private const val ARCHIVE_URL =
        "https://archive-api.open-meteo.com/v1/archive"
    private const val REF_YEAR_START = "2023-01-01"
    private const val REF_YEAR_END   = "2023-12-31"

    /**
     * Retourne les données climatiques pour une coordonnée.
     * Vérifie d'abord le cache ; télécharge si absent.
     *
     * @return [ClimateData] ou null si hors-ligne / erreur réseau
     */
    fun getClimateData(context: Context, lat: Double, lon: Double): ClimateData? {
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
    // Appel API + calcul
    // ─────────────────────────────────────────────────────────────────────────

    private fun fetchFromApi(lat: Double, lon: Double): ClimateData? {
        val url = "$ARCHIVE_URL?latitude=$lat&longitude=$lon" +
            "&start_date=$REF_YEAR_START&end_date=$REF_YEAR_END" +
            "&daily=temperature_2m_mean,precipitation_sum" +
            "&timezone=Europe%2FParis"

        val json = JSONObject(URL(url).readText())
        val daily = json.optJSONObject("daily") ?: return null

        val temps  = daily.optJSONArray("temperature_2m_mean") ?: return null
        val precips = daily.optJSONArray("precipitation_sum") ?: return null

        val n = temps.length()
        if (n < 300) return null   // données incomplètes

        var sumTemp   = 0.0
        var validTemp = 0
        val precipByMonth = IntArray(12)

        for (i in 0 until n) {
            if (!temps.isNull(i)) {
                sumTemp += temps.getDouble(i)
                validTemp++
            }
            // Extraire le mois depuis l'index (1 jan = mois 0 → ~31 j par mois)
            val month = (i * 12) / n
            if (!precips.isNull(i)) {
                precipByMonth[month] += precips.getDouble(i).roundToInt()
            }
        }

        val tempMoyC    = if (validTemp > 0) sumTemp / validTemp else 10.0
        val precipMmAn  = precipByMonth.sum()
        val precipEte   = precipByMonth[5] + precipByMonth[6] + precipByMonth[7]  // juin–août
        val aridity     = if (tempMoyC > -10) precipMmAn.toDouble() / (tempMoyC + 10) else 999.0

        val climateType = classifyClimate(tempMoyC, precipMmAn, precipEte, aridity)

        return ClimateData(
            tempMoyC      = (tempMoyC * 10).roundToInt() / 10.0,
            precipMmAn    = precipMmAn,
            precipEteMin  = precipEte,
            indiceAridite = (aridity * 10).roundToInt() / 10.0,
            climateType   = climateType
        )
    }

    /**
     * Classifie le climat selon :
     * - Indice De Martonne (P / T+10) : < 20 = semi-aride, 20–30 = sec, > 60 = humide
     * - Précipitations estivales (critère méditerranéen : été sec < 100 mm)
     * - Température moyenne (critère montagnard)
     */
    private fun classifyClimate(
        tempMoyC: Double,
        precipMmAn: Int,
        precipEte: Int,
        aridity: Double
    ): ClimateType = when {
        tempMoyC < 4.0                         -> ClimateType.SUBALPIN
        tempMoyC < 8.0 && precipMmAn > 900     -> ClimateType.MONTAGNARD
        tempMoyC < 8.0                         -> ClimateType.CONTINENTAL
        precipEte < 90 && tempMoyC > 12.0      -> ClimateType.MEDITERRANEEN
        aridity > 55 && precipMmAn > 750       -> ClimateType.OCEANIQ
        aridity > 40 && precipMmAn > 600       -> ClimateType.SEMI_OCEANIQ
        aridity > 30                           -> ClimateType.SEMI_CONTINENTAL
        else                                   -> ClimateType.CONTINENTAL
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cache JSON local (résolution 0.1° ≈ 11 km)
    // ─────────────────────────────────────────────────────────────────────────

    private fun cacheKey(lat: Double, lon: Double): String {
        val la = (lat * 10).roundToInt()
        val lo = (lon * 10).roundToInt()
        return "${la}_${lo}"
    }

    private fun cacheFile(context: Context): File =
        File(context.filesDir, "climate_cache.json").also {
            if (!it.exists()) it.writeText("{}")
        }

    private fun loadFromCache(context: Context, key: String): ClimateData? {
        return try {
            val obj = JSONObject(cacheFile(context).readText())
            if (!obj.has(key)) return null
            val e = obj.getJSONObject(key)
            ClimateData(
                tempMoyC      = e.getDouble("t"),
                precipMmAn    = e.getInt("p"),
                precipEteMin  = e.getInt("pe"),
                indiceAridite = e.getDouble("ia"),
                climateType   = ClimateType.valueOf(e.getString("ct"))
            )
        } catch (_: Exception) { null }
    }

    private fun saveToCache(context: Context, key: String, data: ClimateData) {
        try {
            val file  = cacheFile(context)
            val obj   = JSONObject(file.readText())
            val entry = JSONObject().apply {
                put("t",  data.tempMoyC)
                put("p",  data.precipMmAn)
                put("pe", data.precipEteMin)
                put("ia", data.indiceAridite)
                put("ct", data.climateType.name)
            }
            obj.put(key, entry)
            file.writeText(obj.toString())
        } catch (e: Exception) { android.util.Log.w("ClimateContext", "Cache write failed", e) }
    }

    fun clearCache(context: Context) {
        cacheFile(context).writeText("{}")
    }
}
