package com.forestry.counter.data.location.trace

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.forestry.counter.R
import com.forestry.counter.domain.location.trace.TraceCaptureToken
import com.forestry.counter.presentation.MainActivity
import com.forestry.counter.presentation.screens.forestry.trace.MapTraceController
import com.google.android.gms.location.*

/** Application owns the sole controller; a Service restart cannot create an authority. */
interface MapTraceOwner { val mapTraceController: MapTraceController }

class MapTraceForegroundService : Service() {
    private val controller get() = (application as? MapTraceOwner)?.mapTraceController
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var token: TraceCaptureToken? = null
    private var callback: LocationCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requested = intent?.getStringExtra(SESSION)?.let { id -> TraceCaptureToken(id, intent.getLongExtra(GENERATION, -1)) }
        val owner = controller
        if (requested == null || owner == null || !owner.sourceIsCurrent(requested)) {
            if (token == null) stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            PAUSE -> { owner.pause(requested); return START_NOT_STICKY }
            FINISH -> { owner.finish(requested); return START_NOT_STICKY }
        }
        if (!hasPermission(this)) { owner.sourceStopped(requested); stopSelf(); return START_NOT_STICKY }
        if (token == requested && callback != null) return START_NOT_STICKY
        removeCallback()
        token = requested
        try { showNotification(requested) }
        catch (_: RuntimeException) { owner.sourceStopped(requested); stopSelf(); return START_NOT_STICKY }
        val bound = requested
        val listener = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (token != bound || !hasPermission(this@MapTraceForegroundService) || owner.sourceIsCurrent(bound).not()) return
                result.locations.forEach { owner.acceptLocation(bound, it) }
            }
            override fun onLocationAvailability(availability: LocationAvailability) {
                if (token == bound && !availability.isLocationAvailable) owner.sourceUnavailable(bound)
            }
        }
        callback = listener
        try {
            client.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
                .setMinUpdateIntervalMillis(1_000L).build(), listener, Looper.getMainLooper())
                .addOnFailureListener { if (token == bound) { owner.sourceStopped(bound); stopSelf() } }
        } catch (_: SecurityException) { owner.sourceStopped(bound); stopSelf() }
        return START_NOT_STICKY
    }
    private fun removeCallback() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }
    override fun onDestroy() {
        val previous = token
        token = null
        removeCallback()
        if (previous != null) controller?.sourceStopped(previous)
        super.onDestroy()
    }
    private fun showNotification(bound: TraceCaptureToken) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.map_trace_notification_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(name: String, requestCode: Int) = PendingIntent.getService(this, requestCode,
            intent(this, bound).setAction(name).setData(android.net.Uri.parse("geosylva-trace://${bound.sessionId}/${bound.generation}/$requestCode")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.map_trace_notification_title))
            .setContentText(getString(R.string.map_trace_notification_body)).setContentIntent(open).setOngoing(true).setSilent(true)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.map_trace_notification_pause), action(PAUSE, 1))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.map_trace_notification_finish), action(FINISH, 2)).build()
        ServiceCompat.startForeground(this, NOTIFICATION, notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
    }
    companion object {
        private const val SESSION = "trace_session"
        private const val GENERATION = "trace_generation"
        private const val PAUSE = "com.forestry.counter.MAP_TRACE_PAUSE"
        private const val FINISH = "com.forestry.counter.MAP_TRACE_FINISH"
        private const val CHANNEL = "cartographic_trace"
        private const val NOTIFICATION = 7401
        fun hasPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        private fun intent(context: Context, token: TraceCaptureToken) = Intent(context, MapTraceForegroundService::class.java)
            .putExtra(SESSION, token.sessionId).putExtra(GENERATION, token.generation)
        fun start(context: Context, token: TraceCaptureToken): Boolean = try {
            ContextCompat.startForegroundService(context, intent(context, token)); true
        } catch (_: RuntimeException) { false }
        fun stop(context: Context, token: TraceCaptureToken) {
            val owner = (context.applicationContext as? MapTraceOwner)?.mapTraceController
            // An old callback/notification can never stop a replacement session's Service.
            if (owner?.currentSourceToken()?.let { it != token } == true) return
            context.stopService(intent(context, token))
        }
    }
}
