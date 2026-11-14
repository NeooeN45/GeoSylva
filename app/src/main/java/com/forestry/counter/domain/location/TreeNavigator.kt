package com.forestry.counter.domain.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** GPS and compass stay independent: a disturbed compass does not hide a fresh GPS distance. */
class TreeNavigator(context: Context) : SensorEventListener {
    private val context = context.applicationContext

    data class Target(
        val tigeId: String,
        val essenceName: String,
        val essenceCode: String,
        val diamCm: Double,
        val hauteurM: Double?,
        val lat: Double,
        val lon: Double,
        val parcelleId: String = "",
        val recordedAccuracyM: Double? = null,
        val numero: Int? = null,
    )

    data class NavigationState(
        val isActive: Boolean = false,
        val target: Target? = null,
        /** Last known fix; positionStatus determines whether it is current. */
        val userLat: Double? = null,
        val userLon: Double? = null,
        val distanceM: Float? = null,
        val bearingToTargetDeg: Float? = null,
        /** True north, corrected for magnetic declination and display rotation. */
        val deviceAzimuthDeg: Float? = null,
        val relativeBearingDeg: Float? = null,
        val userAccuracyM: Float? = null,
        /** Retained for source compatibility; proximity never asserts arrival at a tree. */
        val arrived: Boolean = false,
        val compassAccuracy: Int? = null,
        val compassUnreliable: Boolean = false,
        val positionStatus: TreePositionStatus = TreePositionStatus.WAITING,
        val orientationStatus: TreeOrientationStatus = TreeOrientationStatus.WAITING,
        val lastFixTimestampMillis: Long? = null,
        val lastFixElapsedRealtimeNanos: Long? = null,
        val lastFixAgeMillis: Long? = null,
        val nearRecordedPoint: Boolean = false,
        val isPaused: Boolean = false,
    )

    private val mutable = MutableStateFlow(NavigationState())
    val state: StateFlow<NavigationState> = mutable.asStateFlow()
    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var sensorManager: SensorManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0L
    private var sessionStartNanos = 0L
    private var lastFix: Location? = null
    private var headingNanos: Long? = null
    private var headingAccuracy: Int? = null
    private var magneticAzimuth: Float? = null
    private var magneticDeclination: Float? = null
    private var sensorAvailable = false
    private var providerAvailable = true
    private var useRotationVector = false
    private val gravity = FloatArray(3)
    private val magnetic = FloatArray(3)
    private var gravityNanos: Long? = null
    private var magneticNanos: Long? = null

    companion object {
        /** UI cue only: never means a trunk was found or an accuracy circle was entered. */
        const val ARRIVAL_THRESHOLD_M = 5f
        private const val HEADING_SMOOTHING_ALPHA = 0.25f
        private const val STATUS_TICK_MILLIS = 1_000L
    }

    private val statusTicker = object : Runnable {
        override fun run() {
            if (!mutable.value.isActive || mutable.value.isPaused) return
            if (!hasPermission()) {
                pauseSubscriptions()
                mutable.value = mutable.value.copy(positionStatus = TreePositionStatus.PERMISSION_REQUIRED,
                    distanceM = null, bearingToTargetDeg = null, relativeBearingDeg = null,
                    userAccuracyM = null, nearRecordedPoint = false)
                return
            }
            publish()
            handler.postDelayed(this, STATUS_TICK_MILLIS)
        }
    }

    fun startNavigation(target: Target): Boolean {
        stopNavigation()
        if (target.tigeId.isBlank() || !validCoordinate(target.lat, target.lon) || !hasPermission()) return false
        mutable.value = NavigationState(isActive = true, target = target)
        return subscribe()
    }

    /** The host calls this on ON_STOP. No GPS/sensor acquisition continues in the background. */
    fun pauseNavigation() {
        if (!mutable.value.isActive) return
        pauseSubscriptions()
        mutable.value = mutable.value.copy(isPaused = true, positionStatus = TreePositionStatus.PAUSED,
            distanceM = null, bearingToTargetDeg = null, relativeBearingDeg = null,
            deviceAzimuthDeg = null, userAccuracyM = null, nearRecordedPoint = false)
    }

    fun resumeNavigation(): Boolean {
        if (!mutable.value.isActive) return false
        if (!hasPermission()) {
            mutable.value = mutable.value.copy(isPaused = false, positionStatus = TreePositionStatus.PERMISSION_REQUIRED)
            return false
        }
        if (locationCallback != null) return true
        // Resume needs a newly delivered fix and heading; no previous active arrow is reused.
        lastFix = null
        mutable.value = mutable.value.copy(isPaused = false, positionStatus = TreePositionStatus.WAITING,
            orientationStatus = TreeOrientationStatus.WAITING, distanceM = null, bearingToTargetDeg = null,
            relativeBearingDeg = null, deviceAzimuthDeg = null, userAccuracyM = null, nearRecordedPoint = false)
        return subscribe()
    }

    fun stopNavigation() {
        pauseSubscriptions()
        lastFix = null
        mutable.value = NavigationState()
    }

    private fun subscribe(): Boolean {
        val token = ++generation
        sessionStartNanos = SystemClock.elapsedRealtimeNanos()
        providerAvailable = true
        fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (token != generation || mutable.value.isPaused || !mutable.value.isActive) return
                val fix = result.lastLocation ?: return
                if (!validCoordinate(fix.latitude, fix.longitude) || fix.elapsedRealtimeNanos <= 0 ||
                    fix.elapsedRealtimeNanos > SystemClock.elapsedRealtimeNanos() ||
                    lastFix?.let { fix.elapsedRealtimeNanos <= it.elapsedRealtimeNanos } == true) return
                providerAvailable = true
                lastFix = Location(fix)
                magneticDeclination = GeomagneticField(fix.latitude.toFloat(), fix.longitude.toFloat(),
                    fix.altitude.takeIf { fix.hasAltitude() && it.isFinite() }?.toFloat() ?: 0f,
                    fix.time.takeIf { it > 0 } ?: System.currentTimeMillis()).declination
                publish()
            }
            override fun onLocationAvailability(availability: LocationAvailability) {
                if (token != generation || mutable.value.isPaused || !mutable.value.isActive) return
                providerAvailable = availability.isLocationAvailable
                publish()
            }
        }
        locationCallback = callback
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_500L)
                .setMinUpdateIntervalMillis(1_000L).build()
            fusedClient?.requestLocationUpdates(request, callback, Looper.getMainLooper())
                ?.addOnFailureListener {
                    if (token == generation && mutable.value.isActive) {
                        providerAvailable = false
                        publish()
                    }
                }
        } catch (_: SecurityException) {
            pauseSubscriptions()
            mutable.value = mutable.value.copy(positionStatus = TreePositionStatus.PERMISSION_REQUIRED)
            return false
        }
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sm = sensorManager
        val rotation = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        useRotationVector = rotation != null
        sensorAvailable = if (sm == null) false else if (rotation != null) {
            sm.registerListener(this, rotation, SensorManager.SENSOR_DELAY_UI)
        } else {
            val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (acc == null || mag == null) false else {
                val a = sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI)
                val m = sm.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
                if (!a || !m) sm.unregisterListener(this)
                a && m
            }
        }
        publish()
        handler.postDelayed(statusTicker, STATUS_TICK_MILLIS)
        return true
    }

    private fun pauseSubscriptions() {
        ++generation
        handler.removeCallbacks(statusTicker)
        locationCallback?.let { callback ->
            try { fusedClient?.removeLocationUpdates(callback) } catch (_: SecurityException) { /* Already revoked. */ }
        }
        locationCallback = null
        fusedClient = null
        sensorManager?.unregisterListener(this)
        sensorManager = null
        magneticAzimuth = null
        magneticDeclination = null
        headingNanos = null
        headingAccuracy = null
        gravityNanos = null
        magneticNanos = null
        sensorAvailable = false
    }

    private fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(context,
        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun locationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return try { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }
        catch (_: Exception) { false }
    }

    private fun publish() {
        val current = mutable.value
        val target = current.target ?: return
        if (!current.isActive) return
        val now = SystemClock.elapsedRealtimeNanos()
        val fix = lastFix
        val position = TreeGuidancePolicy.positionStatus(now, fix?.elapsedRealtimeNanos, current.isPaused,
            hasPermission(), providerAvailable && locationEnabled())
        val orientation = TreeGuidancePolicy.orientationStatus(now, headingNanos, sensorAvailable, headingAccuracy)
        val fresh = position == TreePositionStatus.FRESH
        val distance = if (fresh && fix != null) TreeGuidancePolicy.distanceM(fix.latitude, fix.longitude, target.lat, target.lon)?.toFloat() else null
        val bearing = if (fresh && fix != null) TreeGuidancePolicy.trueBearingDeg(fix.latitude, fix.longitude, target.lat, target.lon) else null
        val trueHeading = if (orientation == TreeOrientationStatus.READY && magneticDeclination != null)
            magneticAzimuth?.let { TreeGuidancePolicy.normalise(it + (magneticDeclination ?: 0f)) } else null
        mutable.value = current.copy(userLat = fix?.latitude ?: current.userLat, userLon = fix?.longitude ?: current.userLon,
            distanceM = distance, bearingToTargetDeg = bearing, deviceAzimuthDeg = trueHeading,
            relativeBearingDeg = TreeGuidancePolicy.relativeBearing(bearing, trueHeading, position, orientation),
            userAccuracyM = if (fresh) fix?.takeIf { it.hasAccuracy() && it.accuracy.isFinite() && it.accuracy > 0 }?.accuracy else null,
            arrived = false, compassAccuracy = headingAccuracy,
            compassUnreliable = orientation == TreeOrientationStatus.UNRELIABLE,
            positionStatus = position, orientationStatus = orientation,
            lastFixTimestampMillis = fix?.time ?: current.lastFixTimestampMillis,
            lastFixElapsedRealtimeNanos = fix?.elapsedRealtimeNanos ?: current.lastFixElapsedRealtimeNanos,
            lastFixAgeMillis = (fix?.elapsedRealtimeNanos ?: current.lastFixElapsedRealtimeNanos)?.let { time ->
                if (time in 1..now) (now - time) / 1_000_000 else null
            }, nearRecordedPoint = distance?.let { it <= TreeGuidancePolicy.NEAR_POINT_CUE_M } == true)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!mutable.value.isActive || mutable.value.isPaused || event.timestamp < sessionStartNanos) return
        val matrix = FloatArray(9)
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                if (event.values.size < 3) return
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                headingAccuracy = event.accuracy
            }
            Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD -> {
                if (useRotationVector || event.values.size < 3) return
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    event.values.copyInto(gravity, endIndex = 3)
                    gravityNanos = event.timestamp
                } else {
                    event.values.copyInto(magnetic, endIndex = 3)
                    magneticNanos = event.timestamp
                    headingAccuracy = event.accuracy
                }
                val a = gravityNanos ?: return
                val m = magneticNanos ?: return
                if (event.timestamp - minOf(a, m) > TreeGuidancePolicy.MAX_HEADING_AGE_NANOS ||
                    !SensorManager.getRotationMatrix(matrix, null, gravity, magnetic)) return
            }
            else -> return
        }
        val remapped = FloatArray(9)
        @Suppress("DEPRECATION")
        val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        val axes = when (rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        if (!SensorManager.remapCoordinateSystem(matrix, axes.first, axes.second, remapped)) return
        val orientation = FloatArray(3)
        SensorManager.getOrientation(remapped, orientation)
        val raw = TreeGuidancePolicy.normalise(Math.toDegrees(orientation[0].toDouble()).toFloat())
        if (!raw.isFinite()) return
        val previous = magneticAzimuth
        val gap = headingNanos?.let { event.timestamp - it } ?: Long.MAX_VALUE
        magneticAzimuth = if (previous == null || gap > TreeGuidancePolicy.MAX_HEADING_AGE_NANOS) raw else {
            val diff = ((raw - previous + 540f) % 360f) - 180f
            TreeGuidancePolicy.normalise(previous + HEADING_SMOOTHING_ALPHA * diff)
        }
        headingNanos = if (useRotationVector) event.timestamp else minOf(gravityNanos ?: event.timestamp, magneticNanos ?: event.timestamp)
        publish()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (!mutable.value.isActive || mutable.value.isPaused) return
        if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR || sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            headingAccuracy = accuracy
            if (accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) magneticAzimuth = null
            publish()
        }
    }

    private fun validCoordinate(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
}
