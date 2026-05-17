package com.forestry.counter.domain.location

import android.content.Context
import android.util.Log
import com.forestry.counter.data.local.entity.StationEnvironnementaleEntity
import com.forestry.counter.domain.repository.StationEnvironnementaleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

// ──────────────────────────────────────────────────────────────────────────────
// Contrats minimaux — implémentés dans les services embarqués respectifs
// ──────────────────────────────────────────────────────────────────────────────
interface DemContextProvider {
    data class DemContext(val altitudeM: Double?, val slopePct: Double?, val aspectDeg: Double?, val aspectLabel: String?)
    fun getContext(lat: Double, lon: Double): DemContext?
}

interface SoilContextProvider {
    data class SoilContext(val soilPh: Double?, val soilRumMm: Double?, val soilTexture: String?, val soilDrainage: String?)
    fun getContext(lat: Double, lon: Double): SoilContext?
}

interface GeologyContextProvider {
    data class GeoContext(val rocheMere: String?, val lithologie: String?, val phIndicatif: Double?)
    fun getContext(lat: Double, lon: Double): GeoContext?
}

interface NormalesContextProvider {
    data class NormalesContext(
        val tMoyC: Double?, val tMinC: Double?, val tMaxC: Double?,
        val pMmAn: Double?, val pEteMm: Double?, val etpMm: Double?,
        val jGel: Int?, val jSec: Int?, val ensoleilH: Double?
    )
    fun getByLocation(lat: Double, lon: Double): NormalesContext?
}

/**
 * Agrège toutes les données environnementales d'une station :
 *   1. DEM SRTM embarqué  → altitude, pente, exposition
 *   2. Géologie embarquée → roche mère, lithologie, pH indicatif
 *   3. Normales climatiques embarquées → T, P, ETP, jGel, jSec
 *   4. WMS INRAE BDGSF (en ligne) → RU, texture, pH sol forestier
 *   5. DVF Cerema open (en ligne) → prix médian €/m²
 *   Met à jour StationEnvironnementaleEntity en DB.
 */
class StationDataAggregator(
    private val context: Context,
    private val stationRepository: StationEnvironnementaleRepository,
    private val demService: DemContextProvider?,
    private val soilService: SoilContextProvider?,
    private val geologyService: GeologyContextProvider?,
    private val normalesService: NormalesContextProvider?
) {
    private val tag = "StationDataAggregator"

    data class AggregationStatus(
        val demOk: Boolean,
        val soilOk: Boolean,
        val geologyOk: Boolean,
        val normalesOk: Boolean,
        val bdgsfOk: Boolean,
        val dvfOk: Boolean
    )

    suspend fun aggregate(
        parcelleId: String,
        lat: Double,
        lon: Double,
        serCode: String?
    ): AggregationStatus = withContext(Dispatchers.IO) {
        val initialStation = stationRepository.getByParcelleOnce(parcelleId)
            ?: buildEmptyStation(parcelleId).also { stationRepository.insert(it) }

        var demOk = false
        var soilOk = false
        var geologyOk = false
        var normalesOk = false
        var bdgsfOk = false
        var dvfOk = false

        var updated: StationEnvironnementaleEntity = initialStation

        // 1. DEM embarqué
        try {
            val dem = demService?.getContext(lat, lon)
            if (dem != null) {
                updated = updated.copy(
                    altitudeM = dem.altitudeM,
                    slopePct = dem.slopePct,
                    aspectDeg = dem.aspectDeg,
                    aspectLabel = dem.aspectLabel
                )
                demOk = true
            }
        } catch (e: Exception) {
            Log.w(tag, "DEM: ${e.message}")
        }

        // 2. Sol embarqué IDW
        try {
            val soil = soilService?.getContext(lat, lon)
            if (soil != null) {
                updated = updated.copy(
                    soilPh = soil.soilPh,
                    soilRumMm = soil.soilRumMm,
                    soilTexture = soil.soilTexture,
                    soilDrainage = soil.soilDrainage
                )
                soilOk = true
            }
        } catch (e: Exception) {
            Log.w(tag, "Soil: ${e.message}")
        }

        // 3. Géologie embarquée
        try {
            val geo = geologyService?.getContext(lat, lon)
            if (geo != null) {
                updated = updated.copy(
                    rocheMere = geo.rocheMere,
                    lithologie = geo.lithologie,
                    phIndicatif = geo.phIndicatif
                )
                geologyOk = true
            }
        } catch (e: Exception) {
            Log.w(tag, "Geology: ${e.message}")
        }

        // 4. Normales climatiques embarquées
        try {
            val norm = normalesService?.getByLocation(lat, lon)
            if (norm != null) {
                updated = updated.copy(
                    tempMoyC = norm.tMoyC,
                    tempMinJanvC = norm.tMinC,
                    tempMaxJuillC = norm.tMaxC,
                    precipMmAn = norm.pMmAn,
                    precipEteMm = norm.pEteMm,
                    etpMm = norm.etpMm,
                    joursGel = norm.jGel,
                    joursSecs = norm.jSec,
                    ensoleilH = norm.ensoleilH
                )
                normalesOk = true
            }
        } catch (e: Exception) {
            Log.w(tag, "Normales: ${e.message}")
        }

        // 5. WMS INRAE BDGSF (en ligne, non bloquant)
        try {
            val bdgsf = fetchBdgsfData(lat, lon)
            if (bdgsf != null) {
                updated = updated.copy(
                    rumClasseBdgsf = bdgsf.optString("rum_classe").takeIf { it.isNotEmpty() },
                    profondeurSolClasse = bdgsf.optString("profondeur_classe").takeIf { it.isNotEmpty() },
                    phSolForestier = bdgsf.optDouble("ph_sol", Double.NaN).takeIf { !it.isNaN() },
                    cOrganiqueTha = bdgsf.optDouble("c_organique_tha", Double.NaN).takeIf { !it.isNaN() },
                    typeWrbBdgsf = bdgsf.optString("type_wrb").takeIf { it.isNotEmpty() },
                    pierrositeClassePct = bdgsf.optString("pierrosite_classe").takeIf { it.isNotEmpty() }
                )
                bdgsfOk = true
            }
        } catch (e: Exception) {
            Log.w(tag, "BDGSF: ${e.message}")
        }

        // 6. DVF Cerema (en ligne, non bloquant)
        try {
            val dvf = fetchDvfData(lat, lon)
            if (dvf != null) {
                updated = updated.copy(
                    dvfPrixMedianEurM2 = dvf.first,
                    dvfNbTransactions = dvf.second,
                    dvfDateFetch = System.currentTimeMillis()
                )
                dvfOk = true
            }
        } catch (e: Exception) {
            Log.w(tag, "DVF: ${e.message}")
        }

        updated = updated.copy(fetchedAt = System.currentTimeMillis())
        stationRepository.update(updated)

        AggregationStatus(demOk, soilOk, geologyOk, normalesOk, bdgsfOk, dvfOk)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // WMS INRAE BDGSF — GetFeatureInfo sur couche bdgsf_classe_ru
    // ──────────────────────────────────────────────────────────────────────────
    private fun fetchBdgsfData(lat: Double, lon: Double): JSONObject? {
        val urlStr = buildBdgsfUrl(lat, lon)
        return try {
            val json = httpGet(urlStr, 6000)
            parseBdgsfResponse(json)
        } catch (e: Exception) {
            null
        }
    }

    private fun buildBdgsfUrl(lat: Double, lon: Double): String {
        val bboxDelta = 0.001
        val bbox = "${lon - bboxDelta},${lat - bboxDelta},${lon + bboxDelta},${lat + bboxDelta}"
        return "https://geodata.inrae.fr/geoserver/inra_bdgsf/wms" +
            "?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetFeatureInfo" +
            "&LAYERS=inra_bdgsf:bdgsf_classe_ru&QUERY_LAYERS=inra_bdgsf:bdgsf_classe_ru" +
            "&BBOX=$bbox&WIDTH=3&HEIGHT=3&X=1&Y=1&CRS=EPSG:4326" +
            "&INFO_FORMAT=application/json"
    }

    private fun parseBdgsfResponse(json: String): JSONObject? {
        return try {
            val root = JSONObject(json)
            val features = root.optJSONArray("features") ?: return null
            if (features.length() == 0) return null
            features.getJSONObject(0).optJSONObject("properties")
        } catch (e: Exception) {
            null
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DVF Cerema open — prix foncier forestier
    // ──────────────────────────────────────────────────────────────────────────
    private fun fetchDvfData(lat: Double, lon: Double): Pair<Double, Int>? {
        val urlStr = "https://apidf-preprod.cerema.fr/dvf_opendata/geomutations/" +
            "?lat=$lat&lon=$lon&rayon=1000&nature_culture_code=B"
        return try {
            val json = httpGet(urlStr, 8000)
            parseDvfResponse(json)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDvfResponse(json: String): Pair<Double, Int>? {
        return try {
            val root = JSONObject(json)
            val results = root.optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            val prices = mutableListOf<Double>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val surface = item.optDouble("surface_terrain", 0.0)
                val valeur = item.optDouble("valeur_fonciere", 0.0)
                if (surface > 0 && valeur > 0) {
                    prices += valeur / surface
                }
            }
            if (prices.isEmpty()) return null
            prices.sort()
            val median = prices[prices.size / 2]
            Pair(median, prices.size)
        } catch (e: Exception) {
            null
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildEmptyStation(parcelleId: String): StationEnvironnementaleEntity =
        StationEnvironnementaleEntity(
            stationId = UUID.randomUUID().toString(),
            parcelleId = parcelleId,
            altitudeM = null, slopePct = null, aspectDeg = null, aspectLabel = null,
            soilPh = null, soilRumMm = null, soilRufMm = null, soilTexture = null, soilDrainage = null,
            soilProfondeurCm = null, soilHydromorphieCm = null, soilTypeWrb = null, soilPhTerrain = null,
            rumClasseBdgsf = null, profondeurSolClasse = null, phSolForestier = null,
            cOrganiqueTha = null, typeWrbBdgsf = null, pierrositeClassePct = null,
            rocheMere = null, lithologie = null, phIndicatif = null,
            tempMoyC = null, tempMinJanvC = null, tempMaxJuillC = null,
            precipMmAn = null, precipEteMm = null, etpMm = null,
            joursGel = null, joursSecs = null, ensoleilH = null, climateType = null,
            idhe = null, spei6Score = null, indiceProductivite = null, scoreVulnCC2050 = null,
            codeSer = null, nomSer = null,
            dvfPrixMedianEurM2 = null, dvfNbTransactions = null, dvfDateFetch = null,
            vulnerabiliteActuelle = null, vulnerabilite2050 = null,
            natura2000Code = null, natura2000Nom = null,
            znieffType1 = false, znieffType2 = false, isForetAncienne = false,
            risqueIncendieZone = null, risqueInondation = null,
            surfaceCadastraleHa = null, geometrieWkt = null, natureCadastraleCode = null,
            sourceDataQualityJson = null, fetchedAt = null
        )

    private fun httpGet(urlStr: String, timeoutMs: Int = 8000): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        return try {
            BufferedReader(InputStreamReader(conn.inputStream)).readText()
        } finally {
            conn.disconnect()
        }
    }
}
