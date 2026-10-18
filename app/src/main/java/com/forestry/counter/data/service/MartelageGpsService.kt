package com.forestry.counter.data.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.forestry.counter.ForestryCounterApplication
import com.forestry.counter.data.repository.MartelageGpsTraceRecorder
import com.forestry.counter.data.sync.hierarchy.BusinessMutationGuard
import com.forestry.counter.domain.model.InventaireSession
import com.forestry.counter.domain.model.MartelageGpsPoint
import com.forestry.counter.domain.model.MartelageGpsTraceCodec
import com.forestry.counter.domain.model.MartelageStates
import com.forestry.counter.presentation.MainActivity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MartelageGpsStatus(val sessionId: String? = null, val message: String = "GPS non démarré", val recording: Boolean = false)

/** Service privé, visible par notification ; aucune reprise silencieuse après perte du processus. */
class MartelageGpsService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val app get() = application as ForestryCounterApplication
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var subscription: Job? = null
    private var callback: LocationCallback? = null
    private var active: InventaireSession? = null
    private var requestedId: String? = null
    private var sensorStarted = 0L
    @Volatile private var requestVersion = 0L
    @Volatile private var callbackVersion = 0L
    private var authority: () -> Boolean = { false }
    private val recorder by lazy {
        MartelageGpsTraceRecorder(app.database.inventaireSessionDao(), app.metadataService,
            AndroidMartelageClock(this), BusinessMutationGuard(app.identityRepository)::capture)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sessionId = intent?.getStringExtra(SESSION_ID)
        if (intent?.action == STOP) {
            if (sessionId == requestedId) finish("GPS arrêté")
            return START_NOT_STICKY
        }
        if (sessionId.isNullOrBlank() || !hasPermission(this)) {
            finish("Autorisation GPS nécessaire")
            return START_NOT_STICKY
        }
        try {
            showNotification()
        } catch (_: RuntimeException) {
            finish("GPS indisponible : rouvrir le martelage")
            return START_NOT_STICKY
        }
        if (sessionId == requestedId && subscription?.isActive == true) return START_NOT_STICKY
        subscription?.cancel()
        removeCallback()
        requestVersion += 1L
        val expectedRequest = requestVersion
        requestedId = sessionId
        authority = BusinessMutationGuard(app.identityRepository).capture(app.identityRepository.session.value?.accountId)
        status.value = MartelageGpsStatus(sessionId, "Recherche du signal GPS…", true)
        subscription = scope.launch {
            try {
                val session = app.inventaireSessionRepository.getById(sessionId)
                val plotId = session?.placetteId
                if (session == null || plotId == null || session.martelageState != MartelageStates.RUNNING || !authority()) {
                    if (requestVersion == expectedRequest) finish("GPS arrêté : martelage indisponible")
                    return@launch
                }
                if (requestVersion != expectedRequest) return@launch
                active = session
                sensorStarted = SystemClock.elapsedRealtime()
                installCallback(expectedRequest)
                app.inventaireSessionRepository.getByPlacette(plotId).collect { sessions ->
                    val current = sessions.firstOrNull { it.sessionId == sessionId }
                    if (!authority() || current == null || current.martelageState != MartelageStates.RUNNING) {
                        if (requestVersion == expectedRequest) finish("GPS arrêté")
                    } else if (requestVersion == expectedRequest) {
                        if (current.segmentStartedElapsedMs != active?.segmentStartedElapsedMs ||
                            current.segmentBootCount != active?.segmentBootCount) {
                            removeCallback()
                            active = current
                            sensorStarted = SystemClock.elapsedRealtime()
                            installCallback(expectedRequest)
                        } else active = current
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (requestVersion == expectedRequest) finish("Enregistrement GPS interrompu")
            }
        }
        return START_NOT_STICKY
    }

    private fun installCallback(expectedRequest: Long) {
        val expectedSensor = ++callbackVersion
        val value = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // Les relevés restent ordonnés même si Play Services livre un lot de positions.
                result.locations.forEach { location -> scope.launch {
                    mutex.withLock { if (requestVersion == expectedRequest && callbackVersion == expectedSensor)
                        record(location, expectedRequest, expectedSensor) }
                } }
            }
        }
        callback = value
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(5_000L).build()
        try {
            client.requestLocationUpdates(request, value, Looper.getMainLooper())
                .addOnFailureListener { if (requestVersion == expectedRequest && callbackVersion == expectedSensor)
                    finish("Signal GPS indisponible") }
        } catch (_: SecurityException) { finish("Autorisation GPS retirée") }
    }

    private suspend fun record(location: Location, expectedRequest: Long, expectedSensor: Long) {
        val session = active ?: return
        if (!authority() || !hasPermission(this)) { finish("GPS arrêté : accès modifié"); return }
        val boot = session.segmentBootCount ?: return
        val start = session.segmentStartedElapsedMs ?: return
        @Suppress("DEPRECATION")
        val isMock = if (Build.VERSION.SDK_INT >= 31) location.isMock else location.isFromMockProvider
        if (isMock || !location.hasAccuracy() || location.accuracy <= 0f ||
            location.accuracy > MartelageGpsTraceCodec.MAX_ACCURACY_M) {
            status.value = MartelageGpsStatus(session.sessionId, "Position non retenue : signal insuffisant", true)
            return
        }
        val point = MartelageGpsPoint(location.latitude, location.longitude, location.time,
            location.elapsedRealtimeNanos / 1_000_000L, location.accuracy.toDouble(),
            location.altitude.takeIf { location.hasAltitude() }, "$boot:$start:$sensorStarted")
        if (!runCatching { MartelageGpsTraceCodec.validatePoint(point) }.isSuccess ||
            !MartelageGpsTraceCodec.isCurrentPoint(point, session, AndroidMartelageClock(this).read())) return
        try {
            val initiatorGuard = authority
            if (recorder.append(session.sessionId, point) {
                requestVersion == expectedRequest && callbackVersion == expectedSensor && initiatorGuard()
            } && requestVersion == expectedRequest && callbackVersion == expectedSensor) {
                status.value = MartelageGpsStatus(session.sessionId,
                    "GPS actif pour cette placette · précision annoncée ±${kotlin.math.ceil(location.accuracy.toDouble()).toInt()} m", true)
            }
        } catch (e: CancellationException) { throw e
        } catch (e: IllegalStateException) {
            if (requestVersion == expectedRequest && callbackVersion == expectedSensor)
                finish(if (e.message == "MARTELAGE_TRACE_FULL") "Limite du tracé atteinte (10 000 positions)" else "GPS arrêté : martelage modifié")
        } catch (_: Exception) { if (requestVersion == expectedRequest && callbackVersion == expectedSensor)
            finish("Enregistrement GPS interrompu") }
    }

    private fun showNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "GPS du martelage", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("GeoSylva · GPS du martelage")
            .setContentText("Positions enregistrées pendant le martelage. Pause et arrêt depuis GeoSylva.")
            .setContentIntent(open).setOngoing(true).setSilent(true).build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
    }

    private fun removeCallback() {
        callbackVersion += 1L
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
        active = null
    }

    private fun finish(message: String) {
        status.value = MartelageGpsStatus(requestedId, message)
        requestVersion += 1L
        removeCallback()
        subscription?.cancel()
        subscription = null
        requestedId = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (requestedId != null) status.value = MartelageGpsStatus(requestedId, "GPS interrompu")
        requestVersion += 1L
        removeCallback()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val SESSION_ID = "session_id"
        private const val STOP = "com.forestry.counter.STOP_MARTELAGE_GPS"
        private const val CHANNEL = "martelage_gps"
        private const val NOTIFICATION_ID = 7301
        private val status = MutableStateFlow(MartelageGpsStatus())
        val currentStatus = status.asStateFlow()
        fun hasPermission(context: Context) = ContextCompat.checkSelfPermission(context,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        fun start(context: Context, sessionId: String) {
            try { ContextCompat.startForegroundService(context, Intent(context, MartelageGpsService::class.java).putExtra(SESSION_ID, sessionId)) }
            catch (_: RuntimeException) { status.value = MartelageGpsStatus(sessionId, "GPS indisponible : rouvrir le martelage") }
        }
        fun stop(context: Context, sessionId: String) {
            if (status.value.sessionId != sessionId || !status.value.recording) return
            runCatching { context.startService(Intent(context, MartelageGpsService::class.java).setAction(STOP).putExtra(SESSION_ID, sessionId)) }
        }
    }
}
