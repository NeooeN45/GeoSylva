package com.forestry.counter.presentation.components.maps

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Visible-map position only. This subscription never records a trace or changes business data. */
@Composable
internal fun rememberMapPhoneLocation(permissionGranted: Boolean): Location? {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var received by remember(context) { mutableStateOf<Location?>(null) }
    var nowNanos by remember { mutableStateOf(SystemClock.elapsedRealtimeNanos()) }
    LaunchedEffect(lifecycle, permissionGranted) {
        if (permissionGranted) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                nowNanos = SystemClock.elapsedRealtimeNanos()
                delay(1_000)
            }
        }
    }
    DisposableEffect(context, lifecycle, permissionGranted) {
        val client = LocationServices.getFusedLocationProviderClient(context)
        var active = false
        var disposed = false
        var generation = 0L
        var requestedAtNanos = 0L
        fun permitted() = permissionGranted && (
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (active && !disposed && permitted()) {
                    received = result.lastLocation?.takeIf { it.elapsedRealtimeNanos >= requestedAtNanos }?.let(::Location)
                    nowNanos = SystemClock.elapsedRealtimeNanos()
                }
            }
        }
        fun sync() {
            val shouldObserve = !disposed && permitted() && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!shouldObserve && active) {
                active = false
                generation++
                client.removeLocationUpdates(callback)
                received = null
            } else if (shouldObserve && !active) {
                active = true
                val requestGeneration = ++generation
                requestedAtNanos = SystemClock.elapsedRealtimeNanos()
                try {
                    client.requestLocationUpdates(
                        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000).build(),
                        callback, Looper.getMainLooper(),
                    ).addOnFailureListener {
                        if (!disposed && generation == requestGeneration) { active = false; received = null }
                    }
                } catch (_: SecurityException) { active = false; received = null }
            }
        }
        val observer = LifecycleEventObserver { _, _ -> sync() }
        lifecycle.addObserver(observer)
        sync()
        onDispose {
            disposed = true
            active = false
            generation++
            lifecycle.removeObserver(observer)
            client.removeLocationUpdates(callback)
        }
    }
    // Display policy, not a forestry measurement protocol or guaranteed accuracy.
    return received?.takeIf {
        permissionGranted && (
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) &&
            it.latitude.isFinite() && it.latitude in -90.0..90.0 &&
            it.longitude.isFinite() && it.longitude in -180.0..180.0 &&
            it.elapsedRealtimeNanos > 0 && nowNanos >= it.elapsedRealtimeNanos &&
            nowNanos - it.elapsedRealtimeNanos <= 30_000_000_000L
    }
}
