package com.forestry.counter.domain.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.Manifest
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Résultat d'un moyennage GPS multi-lectures.
 */
data class AveragedGpsResult(
    val longitude: Double,
    val latitude: Double,
    val altitude: Double?,
    val precisionM: Double,
    val readingCount: Int,
    val wkt: String,
    val dispersionM: Double = 0.0,
    val outliersRemoved: Int = 0
) {
    val qualityLabel: GpsQuality
        get() = when {
            precisionM <= 3.0 -> GpsQuality.EXCELLENT
            precisionM <= 6.0 -> GpsQuality.GOOD
            precisionM <= 12.0 -> GpsQuality.MODERATE
            else -> GpsQuality.POOR
        }
}

enum class GpsQuality { EXCELLENT, GOOD, MODERATE, POOR }

/**
 * Données intermédiaires émises pendant le moyennage GPS.
 */
data class GpsReadingProgress(
    val currentReading: Int,
    val targetReadings: Int,
    val currentPrecisionM: Double?,
    val bestPrecisionM: Double?,
    val currentLon: Double?,
    val currentLat: Double?
)

/**
 * Utilitaire de moyennage GPS pour améliorer la précision de positionnement des tiges.
 *
 * Fonctionnalités :
 * - Moyenne pondérée par inverse-variance (les lectures précises pèsent plus)
 * - Rejet de la première lecture (cold fix souvent imprécis)
 * - Rejet des outliers par Median Absolute Deviation (MAD)
 * - Collecte de lectures supplémentaires pour permettre le filtrage
 * - Calcul de dispersion spatiale pour évaluer la cohérence
 */
object GpsAverager {

    private const val TAG = "GpsAverager"

    // Facteur MAD pour détection d'outliers (2.5 = modéré, 3.0 = conservateur)
    private const val MAD_THRESHOLD = 2.5
    // Approx. mètres par degré de latitude
    private const val DEG_TO_M = 111_320.0

    /**
     * Lance un moyennage GPS de [targetReadings] lectures.
     * Émet des [GpsReadingProgress] au fur et à mesure puis retourne le résultat final.
     *
     * @param context Application context
     * @param targetReadings Nombre de lectures à collecter (3–10 recommandé)
     * @param maxAccuracyM Précision max acceptée par lecture (filtre les lectures trop imprécises)
     * @param intervalMs Intervalle entre lectures
     */
    fun averageFlow(
        context: Context,
        targetReadings: Int = 5,
        maxAccuracyM: Float = 25f,
        intervalMs: Long = 800L
    ): Flow<GpsReadingProgress> = callbackFlow {
        // Vérifier les permissions avant d'accéder à la localisation
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            close(SecurityException("Location permissions not granted"))
            return@callbackFlow
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        val readings = mutableListOf<Location>()
        // Collecter des lectures supplémentaires pour le filtrage d'outliers
        val collectTarget = targetReadings + 2

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMaxUpdateDelayMillis(0L)
            .build()

        var totalReceived = 0
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (loc in result.locations) {
                    totalReceived++
                    // Rejeter la toute première lecture (cold fix souvent décalé)
                    if (totalReceived == 1) {
                        Log.d(TAG, "Discarding first reading (cold fix)")
                        continue
                    }
                    if (loc.accuracy > 0f && loc.accuracy <= maxAccuracyM) {
                        readings.add(loc)
                        val progress = GpsReadingProgress(
                            currentReading = readings.size,
                            targetReadings = targetReadings,
                            currentPrecisionM = loc.accuracy.toDouble(),
                            bestPrecisionM = readings.minOfOrNull { it.accuracy.toDouble() },
                            currentLon = loc.longitude,
                            currentLat = loc.latitude
                        )
                        trySend(progress)

                        if (readings.size >= collectTarget) {
                            client.removeLocationUpdates(this)
                            channel.close()
                            return
                        }
                    }
                }
            }
        }

        client.requestLocationUpdates(request, callback, android.os.Looper.getMainLooper())

        awaitClose {
            client.removeLocationUpdates(callback)
        }
    }

    /**
     * Rejette les outliers spatiaux par Median Absolute Deviation (MAD).
     * Retourne la liste filtrée (au moins 2 éléments conservés).
     */
    internal fun rejectOutliers(locations: List<Location>): List<Location> {
        if (locations.size < 4) return locations // Pas assez pour filtrer

        // Calculer la médiane des coordonnées
        val lats = locations.map { it.latitude }.sorted()
        val lons = locations.map { it.longitude }.sorted()
        val medLat = lats[lats.size / 2]
        val medLon = lons[lons.size / 2]

        // Distance en mètres de chaque point à la médiane
        val cosLat = cos(Math.toRadians(medLat))
        val distances = locations.map { loc ->
            val dLat = (loc.latitude - medLat) * DEG_TO_M
            val dLon = (loc.longitude - medLon) * DEG_TO_M * cosLat
            sqrt(dLat * dLat + dLon * dLon)
        }

        // Median Absolute Deviation
        val medDist = distances.sorted()[distances.size / 2]
        val deviations = distances.map { abs(it - medDist) }
        val mad = deviations.sorted()[deviations.size / 2]

        if (mad < 0.5) return locations // Lectures très groupées, pas d'outlier

        val threshold = medDist + MAD_THRESHOLD * mad * 1.4826 // 1.4826 = MAD scale factor for normal
        val filtered = locations.filterIndexed { i, _ -> distances[i] <= threshold }

        val removed = locations.size - filtered.size
        if (removed > 0) {
            Log.d(TAG, "Outlier rejection: removed $removed / ${locations.size} readings (MAD=${"%.1f".format(mad)}m, threshold=${"%.1f".format(threshold)}m)")
        }

        // Ne jamais garder moins de 2 lectures
        return if (filtered.size >= 2) filtered else locations
    }

    /**
     * Calcule la dispersion spatiale (écart-type des distances au centroïde) en mètres.
     */
    internal fun computeDispersion(locations: List<Location>, centroidLat: Double, centroidLon: Double): Double {
        if (locations.size < 2) return 0.0
        val cosLat = cos(Math.toRadians(centroidLat))
        val dists = locations.map { loc ->
            val dLat = (loc.latitude - centroidLat) * DEG_TO_M
            val dLon = (loc.longitude - centroidLon) * DEG_TO_M * cosLat
            sqrt(dLat * dLat + dLon * dLon)
        }
        val mean = dists.average()
        return sqrt(dists.sumOf { (it - mean) * (it - mean) } / dists.size)
    }

    /**
     * Calcule la position moyennée pondérée à partir d'une liste de Location.
     * Pondération : inverse du carré de la précision (plus la lecture est précise, plus elle pèse).
     * Applique le rejet d'outliers avant le moyennage.
     */
    fun computeAverage(locations: List<Location>): AveragedGpsResult? {
        if (locations.isEmpty()) return null

        val filtered = rejectOutliers(locations)
        val outliersRemoved = locations.size - filtered.size

        var weightSum = 0.0
        var lonSum = 0.0
        var latSum = 0.0
        var altSum = 0.0
        var altWeightSum = 0.0
        var bestAccuracy = Double.MAX_VALUE

        for (loc in filtered) {
            val w = if (loc.accuracy > 0f) 1.0 / (loc.accuracy * loc.accuracy) else 1.0
            weightSum += w
            lonSum += loc.longitude * w
            latSum += loc.latitude * w
            if (loc.accuracy > 0f) {
                bestAccuracy = minOf(bestAccuracy, loc.accuracy.toDouble())
            }
            if (loc.hasAltitude()) {
                altSum += loc.altitude * w
                altWeightSum += w
            }
        }

        if (weightSum <= 0.0) return null

        val lon = lonSum / weightSum
        val lat = latSum / weightSum
        val alt = if (altWeightSum > 0.0) altSum / altWeightSum else null

        // Dispersion spatiale des lectures retenues
        val dispersion = computeDispersion(filtered, lat, lon)

        // Précision combinée (approximation inverse-variance) : σ = sqrt(1 / Σ(1/σ_i²))
        // Intégrer la dispersion spatiale : si les lectures sont très dispersées, la précision
        // effective est pire que ce que l'inverse-variance indique
        val ivwPrecision = sqrt(1.0 / weightSum)
        val combinedPrecision = if (bestAccuracy.isFinite() && bestAccuracy < Double.MAX_VALUE) {
            maxOf(ivwPrecision, bestAccuracy * 0.5, dispersion * 0.7)
        } else {
            maxOf(ivwPrecision, dispersion * 0.7)
        }

        val wkt = if (alt != null) {
            String.format(Locale.US, "POINT Z (%.7f %.7f %.1f)", lon, lat, alt)
        } else {
            String.format(Locale.US, "POINT (%.7f %.7f)", lon, lat)
        }

        return AveragedGpsResult(
            longitude = lon,
            latitude = lat,
            altitude = alt,
            precisionM = combinedPrecision,
            readingCount = filtered.size,
            wkt = wkt,
            dispersionM = dispersion,
            outliersRemoved = outliersRemoved
        )
    }

    /**
     * Méthode simplifiée : collecte N lectures et retourne la position moyennée.
     * Pour usage avec coroutines dans un scope.
     *
     * Collecte targetReadings + 2 lectures brutes, rejette la première (cold fix)
     * et les outliers, puis calcule la moyenne pondérée.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    suspend fun collectAndAverage(
        context: Context,
        targetReadings: Int = 5,
        maxAccuracyM: Float = 25f,
        timeoutMs: Long = 15_000L
    ): AveragedGpsResult? {
        // Vérifier les permissions avant d'accéder à la localisation
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        
        val client = LocationServices.getFusedLocationProviderClient(context)
        val readings = mutableListOf<Location>()
        // Collecter des lectures supplémentaires pour permettre le filtrage
        val collectTarget = targetReadings + 2

        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 600L)
                .setMinUpdateIntervalMillis(300L)
                .setMaxUpdateDelayMillis(0L)
                .build()

            var totalReceived = 0
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    for (loc in result.locations) {
                        totalReceived++
                        // Rejeter la toute première lecture (cold fix souvent décalé)
                        if (totalReceived == 1) {
                            Log.d(TAG, "Discarding first reading (cold fix): acc=${loc.accuracy}m")
                            continue
                        }
                        if (loc.accuracy > 0f && loc.accuracy <= maxAccuracyM) {
                            readings.add(loc)
                            if (readings.size >= collectTarget) {
                                client.removeLocationUpdates(this)
                                if (!cont.isCompleted) {
                                    cont.resume(computeAverage(readings)) {}
                                }
                                return
                            }
                        }
                    }
                }
            }

            cont.invokeOnCancellation {
                client.removeLocationUpdates(callback)
            }

            client.requestLocationUpdates(request, callback, android.os.Looper.getMainLooper())

            // Timeout — retourne ce qu'on a si on n'a pas fini
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                client.removeLocationUpdates(callback)
                if (!cont.isCompleted) {
                    cont.resume(if (readings.isNotEmpty()) computeAverage(readings) else null) {}
                }
            }, timeoutMs)
        }
    }
}
