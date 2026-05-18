package com.forestry.counter.domain.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Enregistre un tracé GPS en temps réel pour dessiner le contour d'une parcelle.
 * Produit un polygone WKT et calcule la surface automatiquement.
 */
class GpsParcelTracer(private val context: Context) {

    data class TracePoint(
        val lat: Double,
        val lon: Double,
        val alt: Double?,
        val precisionM: Float,
        val timestampMs: Long = System.currentTimeMillis()
    )

    data class TraceState(
        val isRecording: Boolean = false,
        val points: List<TracePoint> = emptyList(),
        val surfaceM2: Double? = null,
        val surfaceHa: Double? = null,
        val perimeterM: Double? = null
    )

    private val _state = MutableStateFlow(TraceState())
    val state: StateFlow<TraceState> = _state.asStateFlow()

    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    /**
     * Démarre l'enregistrement du tracé GPS.
     * @param intervalMs intervalle entre les lectures GPS (défaut 2s)
     * @param minDistanceM distance minimale entre deux points enregistrés (défaut 3m)
     * @param maxAccuracyM précision maximale acceptée (défaut 15m)
     */
    fun startRecording(
        intervalMs: Long = 2000L,
        minDistanceM: Float = 3f,
        maxAccuracyM: Float = 15f
    ): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) return false

        _state.value = TraceState(isRecording = true)

        fusedClient = LocationServices.getFusedLocationProviderClient(context)

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMinUpdateDistanceMeters(minDistanceM)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                if (loc.accuracy > maxAccuracyM) return

                val point = TracePoint(
                    lat = loc.latitude,
                    lon = loc.longitude,
                    alt = if (loc.hasAltitude()) loc.altitude else null,
                    precisionM = loc.accuracy
                )

                val current = _state.value
                val lastPt = current.points.lastOrNull()

                // Filtre de distance minimale (évite les points trop proches)
                if (lastPt != null) {
                    val dist = floatArrayOf(0f)
                    Location.distanceBetween(lastPt.lat, lastPt.lon, point.lat, point.lon, dist)
                    if (dist[0] < minDistanceM) return
                }

                val newPoints = current.points + point
                val surface = if (newPoints.size >= 3) computeAreaM2(newPoints) else null
                val perimeter = if (newPoints.size >= 2) computePerimeterM(newPoints) else null

                _state.value = current.copy(
                    points = newPoints,
                    surfaceM2 = surface,
                    surfaceHa = surface?.let { it / 10_000.0 },
                    perimeterM = perimeter
                )
            }
        }

        val callback = locationCallback ?: run {
            _state.value = TraceState()
            return false
        }
        try {
            fusedClient?.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            _state.value = TraceState()
            return false
        }

        return true
    }

    /** Arrête l'enregistrement sans effacer les points. */
    fun stopRecording() {
        locationCallback?.let { fusedClient?.removeLocationUpdates(it) }
        locationCallback = null
        fusedClient = null
        _state.value = _state.value.copy(isRecording = false)
    }

    /** Ajoute un point manuellement (ex: position courante). */
    fun addManualPoint(lat: Double, lon: Double, alt: Double? = null, precisionM: Float = 0f) {
        val current = _state.value
        val point = TracePoint(lat, lon, alt, precisionM)
        val newPoints = current.points + point
        val surface = if (newPoints.size >= 3) computeAreaM2(newPoints) else null
        val perimeter = if (newPoints.size >= 2) computePerimeterM(newPoints) else null
        _state.value = current.copy(
            points = newPoints,
            surfaceM2 = surface,
            surfaceHa = surface?.let { it / 10_000.0 },
            perimeterM = perimeter
        )
    }

    /** Supprime le dernier point enregistré. */
    fun undoLastPoint() {
        val current = _state.value
        if (current.points.isEmpty()) return
        val newPoints = current.points.dropLast(1)
        val surface = if (newPoints.size >= 3) computeAreaM2(newPoints) else null
        val perimeter = if (newPoints.size >= 2) computePerimeterM(newPoints) else null
        _state.value = current.copy(
            points = newPoints,
            surfaceM2 = surface,
            surfaceHa = surface?.let { it / 10_000.0 },
            perimeterM = perimeter
        )
    }

    /** Efface tout le tracé. */
    fun clearTrace() {
        stopRecording()
        _state.value = TraceState()
    }

    /**
     * Génère le WKT POLYGON à partir des points enregistrés.
     * Le polygone est fermé automatiquement.
     */
    fun toWktPolygon(): String? {
        val pts = _state.value.points
        if (pts.size < 3) return null
        val coords = pts.map { "${it.lon} ${it.lat}" }.toMutableList()
        // Fermer le polygone
        coords.add("${pts.first().lon} ${pts.first().lat}")
        return "POLYGON ((${coords.joinToString(", ")}))"
    }

    /**
     * Génère un GeoJSON Polygon pour le rendu MapLibre.
     */
    fun toGeoJsonPolygon(): String? {
        val pts = _state.value.points
        if (pts.size < 3) return null
        val ring = pts.map { "[${it.lon},${it.lat}]" }.toMutableList()
        ring.add("[${pts.first().lon},${pts.first().lat}]")
        return """{"type":"Feature","geometry":{"type":"Polygon","coordinates":[[${ring.joinToString(",")}]]},"properties":{}}"""
    }

    /**
     * Génère un GeoJSON LineString (pour la ligne en cours de tracé).
     */
    fun toGeoJsonLine(): String? {
        val pts = _state.value.points
        if (pts.size < 2) return null
        val coords = pts.map { "[${it.lon},${it.lat}]" }
        return """{"type":"Feature","geometry":{"type":"LineString","coordinates":[${coords.joinToString(",")}]},"properties":{}}"""
    }

    /**
     * Génère un GeoJSON FeatureCollection avec les points individuels.
     */
    fun toGeoJsonPoints(): String {
        val pts = _state.value.points
        val features = pts.mapIndexed { idx, pt ->
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[${pt.lon},${pt.lat}]},"properties":{"index":$idx,"precision":${pt.precisionM}}}"""
        }
        return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
    }

    companion object {
        /**
         * Calcule la surface d'un polygone en m² à partir de coordonnées GPS.
         * Utilise la formule du lacet (Shoelace) avec correction de latitude.
         */
        fun computeAreaM2(points: List<TracePoint>): Double {
            if (points.size < 3) return 0.0
            val n = points.size
            var sum = 0.0

            // Conversion approx: 1° lat ≈ 111320m, 1° lon ≈ 111320 * cos(lat)
            val meanLat = points.sumOf { it.lat } / n
            val latFactor = 111320.0
            val lonFactor = 111320.0 * cos(Math.toRadians(meanLat))

            for (i in 0 until n) {
                val j = (i + 1) % n
                val xi = points[i].lon * lonFactor
                val yi = points[i].lat * latFactor
                val xj = points[j].lon * lonFactor
                val yj = points[j].lat * latFactor
                sum += xi * yj - xj * yi
            }
            return abs(sum) / 2.0
        }

        /**
         * Calcule le périmètre en mètres.
         */
        fun computePerimeterM(points: List<TracePoint>): Double {
            if (points.size < 2) return 0.0
            var total = 0.0
            for (i in 0 until points.size) {
                val j = (i + 1) % points.size
                val dist = floatArrayOf(0f)
                Location.distanceBetween(
                    points[i].lat, points[i].lon,
                    points[j].lat, points[j].lon,
                    dist
                )
                total += dist[0]
            }
            return total
        }
    }
}
