package com.forestry.counter.domain.geo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import com.forestry.counter.domain.calculation.pricing.GrecoRegion
import java.net.URL
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Résultat de l'auto-remplissage GPS pour une parcelle.
 */
data class ParcelleAutoFill(
    val commune: String?,
    val codeCommune: String?,
    val altitudeM: Double?,
    val slopePct: Int?,
    val aspectLabel: String?,
    val greco: GrecoRegion? = null,
    val errorMessage: String? = null
)

/**
 * Service d'auto-remplissage des champs d'une parcelle à partir de la position GPS courante.
 *
 * Sources :
 *  - Commune : https://geo.api.gouv.fr/communes?lat=X&lon=Y&fields=nom,code
 *  - Altitude : fournie par le LocationProvider (si disponible)
 */
object ParcelleAutoFillService {

    /**
     * Récupère la position GPS courante puis interroge l'API commune.
     * @return [ParcelleAutoFill] ou null si la permission est absente / timeout
     */
    suspend fun autoFillFromGps(context: Context): ParcelleAutoFill? {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        // Obtenir la dernière position connue (rapide, pas de délai)
        val location = withTimeoutOrNull(8_000L) { getLastLocation(context) }
            ?: return ParcelleAutoFill(
                commune = null, codeCommune = null, altitudeM = null,
                slopePct = null, aspectLabel = null, greco = null,
                errorMessage = "Impossible d'obtenir la position GPS."
            )

        val lat = location.latitude
        val lon = location.longitude
        val alt = if (location.hasAltitude() && location.altitude > -1000.0)
            (location.altitude * 10).roundToInt() / 10.0 else null

        return withContext(Dispatchers.IO) { fetchCommune(context, lat, lon, alt) }
    }

    @Suppress("MissingPermission")
    private suspend fun getLastLocation(context: Context): Location? =
        suspendCancellableCoroutine { cont ->
            val client = LocationServices.getFusedLocationProviderClient(context)
            client.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    cont.resume(loc)
                } else {
                    // Demander une position fraîche si lastLocation est null
                    val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 1000L)
                        .setMaxUpdates(1)
                        .build()
                    val callback = object : com.google.android.gms.location.LocationCallback() {
                        override fun onLocationResult(r: com.google.android.gms.location.LocationResult) {
                            cont.resume(r.lastLocation)
                        }
                    }
                    cont.invokeOnCancellation { client.removeLocationUpdates(callback) }
                    client.requestLocationUpdates(req, callback, android.os.Looper.getMainLooper())
                }
            }.addOnFailureListener { cont.resume(null) }
        }

    private fun fetchCommune(context: android.content.Context, lat: Double, lon: Double, gpsAltM: Double?): ParcelleAutoFill {
        // Commune (geo.api.gouv.fr)
        var commune: String? = null; var code: String? = null; var error: String? = null
        try {
            val url = "https://geo.api.gouv.fr/communes?lat=$lat&lon=$lon&fields=nom,code&format=json&geometry=centre"
            val arr = JSONArray(URL(url).readText())
            if (arr.length() > 0) {
                val obj = arr.getJSONObject(0)
                commune = obj.optString("nom", null)
                code    = obj.optString("code", null)
            } else {
                error = "Aucune commune trouvée pour cette position."
            }
        } catch (e: Exception) { error = "Erreur API commune : ${e.message}" }

        // SRTM terrain (altitude ± GPS, pente, exposition)
        val srtm = try { SrtmElevationService.getTerrainData(context, lat, lon) } catch (_: Exception) { null }
        val finalAlt: Double? = srtm?.altitudeM?.toDouble() ?: gpsAltM

        // Détection automatique de la GRECO à partir du code commune
        val greco = if (!code.isNullOrEmpty()) GrecoDetector.fromCodeCommune(code) else null

        return ParcelleAutoFill(
            commune      = commune,
            codeCommune  = code,
            altitudeM    = finalAlt,
            slopePct     = srtm?.slopePct,
            aspectLabel  = srtm?.aspectLabel,
            greco        = greco,
            errorMessage = error
        )
    }
}
