package com.forestry.counter.domain.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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

/**
 * Fournit la navigation boussole + distance vers un arbre cible.
 * Utilise le GPS (FusedLocation) pour la position courante et
 * le magnétomètre/accéléromètre pour l'orientation (azimut).
 */
class TreeNavigator(private val context: Context) : SensorEventListener {

    data class Target(
        val tigeId: String,
        val essenceName: String,
        val essenceCode: String,
        val diamCm: Double,
        val hauteurM: Double?,
        val lat: Double,
        val lon: Double
    )

    data class NavigationState(
        val isActive: Boolean = false,
        val target: Target? = null,
        val userLat: Double? = null,
        val userLon: Double? = null,
        val distanceM: Float? = null,
        val bearingToTargetDeg: Float? = null,
        val deviceAzimuthDeg: Float? = null,
        /** Angle relatif : direction de la cible par rapport à l'orientation de l'appareil. */
        val relativeBearingDeg: Float? = null,
        val userAccuracyM: Float? = null,
        val arrived: Boolean = false
    )

    private val _state = MutableStateFlow(NavigationState())
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var sensorManager: SensorManager? = null

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasMagnetic = false

    companion object {
        /** Distance en mètres en-dessous de laquelle on considère être "arrivé". */
        const val ARRIVAL_THRESHOLD_M = 5f
    }

    /**
     * Démarre la navigation vers un arbre cible.
     */
    fun startNavigation(target: Target): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) return false

        _state.value = NavigationState(isActive = true, target = target)

        // GPS updates
        fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
            .setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(1f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                updatePosition(loc)
            }
        }

        val callback = locationCallback ?: run {
            _state.value = NavigationState()
            return false
        }
        try {
            fusedClient?.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            _state.value = NavigationState()
            return false
        }

        // Compass sensors
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorManager?.let { sm ->
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
            sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }

        return true
    }

    /** Arrête la navigation. */
    fun stopNavigation() {
        locationCallback?.let { fusedClient?.removeLocationUpdates(it) }
        locationCallback = null
        fusedClient = null
        sensorManager?.unregisterListener(this)
        sensorManager = null
        hasGravity = false
        hasMagnetic = false
        _state.value = NavigationState()
    }

    private fun updatePosition(loc: Location) {
        val current = _state.value
        val target = current.target ?: return

        val results = FloatArray(2)
        Location.distanceBetween(loc.latitude, loc.longitude, target.lat, target.lon, results)
        val distance = results[0]
        val bearing = results[1] // bearing in degrees from North

        val normalizedBearing = ((bearing % 360f) + 360f) % 360f
        val azimuth = current.deviceAzimuthDeg
        val relativeBearing = if (azimuth != null) {
            ((normalizedBearing - azimuth + 360f) % 360f)
        } else null

        _state.value = current.copy(
            userLat = loc.latitude,
            userLon = loc.longitude,
            distanceM = distance,
            bearingToTargetDeg = normalizedBearing,
            relativeBearingDeg = relativeBearing,
            userAccuracyM = loc.accuracy,
            arrived = distance <= ARRIVAL_THRESHOLD_M
        )
    }

    // ── SensorEventListener ──

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, 3)
                hasGravity = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                hasMagnetic = true
            }
        }

        if (hasGravity && hasMagnetic) {
            val r = FloatArray(9)
            val i = FloatArray(9)
            if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)
                val azimuthRad = orientation[0]
                val azimuthDeg = ((Math.toDegrees(azimuthRad.toDouble()).toFloat() % 360f) + 360f) % 360f

                val current = _state.value
                val bearingToTarget = current.bearingToTargetDeg
                val relativeBearing = if (bearingToTarget != null) {
                    ((bearingToTarget - azimuthDeg + 360f) % 360f)
                } else null

                _state.value = current.copy(
                    deviceAzimuthDeg = azimuthDeg,
                    relativeBearingDeg = relativeBearing
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
