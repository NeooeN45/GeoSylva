package com.forestry.counter.presentation.components.maps

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forestry.counter.R

/** Test seam for the presentation hint only; it never changes Android or Mapbox network state. */
internal val LocalMapNetworkAvailableOverride = staticCompositionLocalOf<Boolean?> { null }

/**
 * Connectivity hint only, never proof that a style, satellite image or offline pack
 * is ready. No network request or process-wide routing is performed. Native SDK
 * resource states remain authoritative when a remote style is requested.
 */
@Composable
internal fun rememberMapNetworkAvailable(): Boolean {
    LocalMapNetworkAvailableOverride.current?.let { return it }
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val manager = remember(context) { context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager }
    var available by remember(manager) { mutableStateOf(defaultNetworkAvailable(manager)) }
    DisposableEffect(manager, lifecycle) {
        val handler = Handler(Looper.getMainLooper())
        var disposed = false
        var registered: ConnectivityManager.NetworkCallback? = null
        fun stop() {
            val callback = registered
            registered = null
            if (callback != null) runCatching { manager?.unregisterNetworkCallback(callback) }
        }
        fun sync() {
            if (disposed || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                stop()
                return
            }
            available = defaultNetworkAvailable(manager)
            if (registered != null || manager == null) return
            val callback = object : ConnectivityManager.NetworkCallback() {
                private fun refresh() {
                    if (!disposed && registered === this && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                        available = defaultNetworkAvailable(manager)
                }
                override fun onAvailable(network: Network) = refresh()
                override fun onLost(network: Network) = refresh()
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
            }
            registered = callback
            try {
                // minSdk26: callbacks and lifecycle disposal share the main looper.
                manager.registerDefaultNetworkCallback(callback, handler)
            } catch (_: RuntimeException) {
                registered = null
                // Preserve the observed connectivity result; a registration error
                // alone does not prove that the device has no internet access.
                available = defaultNetworkAvailable(manager)
            }
        }
        val observer = LifecycleEventObserver { _, _ -> sync() }
        lifecycle.addObserver(observer)
        sync()
        onDispose {
            disposed = true
            lifecycle.removeObserver(observer)
            stop()
        }
    }
    return available
}

/**
 * Android's validation probe may be blocked by a proxy even when Mapbox is reachable.
 * INTERNET permits a native request; only the SDK can confirm that its resources loaded.
 */
internal fun defaultNetworkAvailable(manager: ConnectivityManager?): Boolean = try {
    manager?.activeNetwork?.let { network -> manager.getNetworkCapabilities(network) }?.let {
        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } == true
} catch (_: SecurityException) {
    false
}

/** Connectivity hint only: no claim about cached imagery, vectors, completeness or SDK readiness. */
@Composable
internal fun MapOfflineConnectivityBadge(modifier: Modifier = Modifier) {
    Surface(modifier.widthIn(max = 240.dp), shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
        Text(stringResource(R.string.mapbox_local_mode_title), Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium)
    }
}
