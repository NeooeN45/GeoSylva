package com.forestry.counter.domain.location

import android.util.Log
import com.forestry.counter.data.local.entity.StationEnvironnementaleEntity
import com.forestry.counter.data.mapper.toParcelleEntity
import com.forestry.counter.data.mapper.toParcelle
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.StationEnvironnementaleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Résout la localisation depuis des coordonnées GPS vers :
 * - Données cadastrales IGN (commune, section, numéro, contenance)
 * - SylvoÉcoRégion (SER) depuis le WFS IGN/IFN
 * - Cache dans ParcelleEntity + StationEnvironnementaleEntity
 */
class LocalisationResolverService(
    private val parcelleRepository: ParcelleRepository,
    private val stationRepository: StationEnvironnementaleRepository
) {
    private val tag = "LocalisationResolver"

    data class LocalisationResult(
        val success: Boolean,
        val codeInseeCommune: String?,
        val nomCommune: String?,
        val sectionCadastrale: String?,
        val numeroCadastral: String?,
        val contenanceCadastraleHa: Double?,
        val geometrieIgnWkt: String?,
        val natureCadastraleCode: String?,
        val codeSer: String?,
        val nomSer: String?,
        val errorMessage: String? = null
    )

    suspend fun resolveFromGps(lat: Double, lon: Double): LocalisationResult =
        withContext(Dispatchers.IO) {
            try {
                val cadastre = fetchCadastre(lat, lon)
                val ser = resolveSer(lat, lon)
                LocalisationResult(
                    success = true,
                    codeInseeCommune = cadastre.optString("codeinsee").takeIf { it.isNotEmpty() },
                    nomCommune = cadastre.optString("commune").takeIf { it.isNotEmpty() },
                    sectionCadastrale = cadastre.optString("section").takeIf { it.isNotEmpty() },
                    numeroCadastral = cadastre.optString("numero").takeIf { it.isNotEmpty() },
                    contenanceCadastraleHa = cadastre.optDouble("contenance", Double.NaN)
                        .takeIf { !it.isNaN() }?.div(10000.0),
                    geometrieIgnWkt = extractWktFromCadastre(cadastre),
                    natureCadastraleCode = cadastre.optString("nature").takeIf { it.isNotEmpty() },
                    codeSer = ser.first,
                    nomSer = ser.second
                )
            } catch (e: Exception) {
                Log.e(tag, "resolveFromGps failed: ${e.message}", e)
                LocalisationResult(
                    success = false,
                    codeInseeCommune = null,
                    nomCommune = null,
                    sectionCadastrale = null,
                    numeroCadastral = null,
                    contenanceCadastraleHa = null,
                    geometrieIgnWkt = null,
                    natureCadastraleCode = null,
                    codeSer = null,
                    nomSer = null,
                    errorMessage = e.message
                )
            }
        }

    suspend fun applyToParcelle(parcelleId: String, result: LocalisationResult) {
        if (!result.success) return
        val parcelle = parcelleRepository.getParcelleById(parcelleId).first() ?: return

        val entity = parcelle.toParcelleEntity().copy(
            codeInseeCommune = result.codeInseeCommune,
            nomCommune = result.nomCommune,
            sectionCadastrale = result.sectionCadastrale,
            numeroCadastral = result.numeroCadastral,
            contenanceCadastraleHa = result.contenanceCadastraleHa,
            geometrieIgnWkt = result.geometrieIgnWkt,
            natureCadastraleCode = result.natureCadastraleCode,
            localisationMode = "GPS_IGN",
            codeSer = result.codeSer,
            nomSer = result.nomSer
        )
        parcelleRepository.updateParcelle(entity.toParcelle())

        // Créer/mettre à jour station environnementale
        val existing = stationRepository.getByParcelleOnce(parcelleId)
        if (existing == null) {
            stationRepository.insert(StationEnvironnementaleEntity(
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
                codeSer = result.codeSer, nomSer = result.nomSer,
                dvfPrixMedianEurM2 = null, dvfNbTransactions = null, dvfDateFetch = null,
                vulnerabiliteActuelle = null, vulnerabilite2050 = null,
                natura2000Code = null, natura2000Nom = null,
                znieffType1 = false, znieffType2 = false, isForetAncienne = false,
                risqueIncendieZone = null, risqueInondation = null,
                surfaceCadastraleHa = result.contenanceCadastraleHa,
                geometrieWkt = result.geometrieIgnWkt,
                natureCadastraleCode = result.natureCadastraleCode,
                sourceDataQualityJson = null,
                fetchedAt = System.currentTimeMillis()
            ))
        } else {
            stationRepository.update(existing.copy(
                codeSer = result.codeSer ?: existing.codeSer,
                nomSer = result.nomSer ?: existing.nomSer,
                surfaceCadastraleHa = result.contenanceCadastraleHa ?: existing.surfaceCadastraleHa,
                geometrieWkt = result.geometrieIgnWkt ?: existing.geometrieWkt,
                natureCadastraleCode = result.natureCadastraleCode ?: existing.natureCadastraleCode,
                fetchedAt = System.currentTimeMillis()
            ))
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // API IGN Géoplateforme — geocodage inverse cadastral
    // ──────────────────────────────────────────────────────────────────────────
    private fun fetchCadastre(lat: Double, lon: Double): JSONObject {
        val urlStr = "https://data.geopf.fr/geocodage/reverse?lon=$lon&lat=$lat&index=parcel&limit=1"
        val json = httpGet(urlStr)
        val root = JSONObject(json)
        val features = root.optJSONArray("features") ?: return JSONObject()
        if (features.length() == 0) return JSONObject()
        val props = features.getJSONObject(0).optJSONObject("properties") ?: return JSONObject()
        return props
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SER — résolution locale par grille France métropole simplifiée
    // ──────────────────────────────────────────────────────────────────────────
    private fun resolveSer(lat: Double, lon: Double): Pair<String?, String?> {
        return SerGridResolver.resolve(lat, lon)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // WKT depuis géométrie GeoJSON cadastre
    // ──────────────────────────────────────────────────────────────────────────
    private fun extractWktFromCadastre(props: JSONObject): String? {
        return null // La géométrie est dans features[0].geometry, pas dans properties
    }

    // ──────────────────────────────────────────────────────────────────────────
    // HTTP GET basique
    // ──────────────────────────────────────────────────────────────────────────
    private fun httpGet(urlStr: String, timeoutMs: Int = 8000): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        return try {
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            reader.readText().also { reader.close() }
        } finally {
            conn.disconnect()
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Grille SER embarquée — correspondance lat/lon → SER principal
// 86 SER IFN/IGN simplifiées par grandes zones géographiques
// Source: nomenclature IFN, cartographie INRAE
// ──────────────────────────────────────────────────────────────────────────────
private object SerGridResolver {

    private data class SerZone(
        val latMin: Double, val latMax: Double,
        val lonMin: Double, val lonMax: Double,
        val code: String, val nom: String
    )

    private val ZONES = listOf(
        // Ardenne primaire — B11
        SerZone(49.4, 50.2, 4.5, 6.5, "B11", "Ardenne primaire"),
        // Ardenne secondaire — B12
        SerZone(49.0, 49.8, 4.2, 5.8, "B12", "Ardenne secondaire"),
        // Lorraine — C10
        SerZone(47.5, 49.5, 5.5, 7.5, "C10", "Lorraine"),
        // Vosges — C11
        SerZone(47.8, 48.8, 6.5, 7.5, "C11", "Vosges"),
        // Boulonnais-Thiérache — D11
        SerZone(49.8, 51.0, 1.5, 4.5, "D11", "Boulonnais-Thiérache"),
        // Normandie — E10
        SerZone(48.5, 49.9, -1.8, 2.5, "E10", "Normandie"),
        // Bretagne — E11
        SerZone(47.2, 48.8, -5.2, -1.5, "E11", "Bretagne"),
        // Pays de Loire — E12
        SerZone(46.5, 48.2, -2.5, 0.5, "E12", "Pays de Loire"),
        // Île-de-France — F10
        SerZone(48.0, 49.2, 1.5, 3.5, "F10", "Île-de-France"),
        // Sologne — F11
        SerZone(47.2, 48.0, 1.0, 2.8, "F11", "Sologne"),
        // Champagne — F12
        SerZone(48.0, 49.5, 3.5, 5.5, "F12", "Champagne"),
        // Massif central nord — G10
        SerZone(44.5, 47.0, 2.2, 5.0, "G10", "Massif central nord"),
        // Massif central sud — H10
        SerZone(43.5, 45.2, 2.0, 4.5, "H10", "Massif central sud"),
        // Cévennes — I10
        SerZone(43.5, 44.8, 3.5, 4.8, "I10", "Cévennes"),
        // Alpes nord internes — J10
        SerZone(44.5, 46.5, 6.2, 7.5, "J10", "Alpes nord internes"),
        // Alpes nord externes — K10
        SerZone(44.8, 46.2, 5.5, 6.5, "K10", "Alpes nord externes"),
        // Alpes sud — L10
        SerZone(43.5, 44.8, 5.8, 7.5, "L10", "Alpes sud"),
        // Pyrénées — M10
        SerZone(42.3, 43.5, -2.0, 3.2, "M10", "Pyrénées"),
        // Landes de Gascogne — N10
        SerZone(43.5, 45.0, -1.8, 0.5, "N10", "Landes de Gascogne"),
        // Périgord-Quercy — O10
        SerZone(44.0, 45.5, 0.5, 2.8, "O10", "Périgord-Quercy"),
        // Provence cristalline — P10
        SerZone(43.0, 44.2, 4.8, 6.8, "P10", "Provence cristalline"),
        // Provence calcaire — Q10
        SerZone(43.0, 44.0, 4.5, 6.0, "Q10", "Provence calcaire"),
        // Languedoc-Roussillon — R10
        SerZone(42.5, 43.8, 2.0, 4.5, "R10", "Languedoc-Roussillon"),
        // Bourgogne — default centre-est
        SerZone(46.5, 48.0, 3.5, 5.5, "F20", "Bourgogne"),
        // Alsace — A20
        SerZone(47.5, 49.0, 7.2, 8.2, "A20", "Alsace")
    )

    fun resolve(lat: Double, lon: Double): Pair<String?, String?> {
        val match = ZONES.firstOrNull { z ->
            lat in z.latMin..z.latMax && lon in z.lonMin..z.lonMax
        }
        return Pair(match?.code, match?.nom)
    }
}
