package com.forestry.counter.data.packs.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class OfflinePackNetwork(val connected: Boolean, val wifi: Boolean)

/** No GPS subscription or permission is needed to prepare a map. */
fun observeOfflinePackNetwork(context: Context) = callbackFlow {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    fun publish() {
        val capabilities = manager.activeNetwork?.let(manager::getNetworkCapabilities)
        trySend(OfflinePackNetwork(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true))
    }
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish()
        override fun onLost(network: Network) = publish()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = publish()
    }
    manager.registerDefaultNetworkCallback(callback)
    publish()
    awaitClose { manager.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
