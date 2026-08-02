package com.chronie.homemoney.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reactive network connectivity monitor.
 *
 * Uses Android's [ConnectivityManager.NetworkCallback] to emit [NetworkStatus]
 * updates as a Flow. Only considers a network "available" when both
 * INTERNET and VALIDATED capabilities are present — this prevents treating
 * a captive portal or a Wi-Fi network without internet as available.
 *
 * The flow uses [distinctUntilChanged] to avoid redundant emissions when
 * network capabilities change but availability status remains the same.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    companion object {
        private const val TAG = "NetworkMonitor"
    }
    
    /**
     * Observe network connection status and emit network status updates
     */
    fun observeNetworkStatus(): Flow<NetworkStatus> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                trySend(NetworkStatus.Available)
            }
            
            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                trySend(NetworkStatus.Unavailable)
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasInternet = networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                )
                val hasValidated = networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                )
                
                if (hasInternet && hasValidated) {
                    Log.d(TAG, "Network capabilities changed: Available")
                    trySend(NetworkStatus.Available)
                } else {
                    Log.d(TAG, "Network capabilities changed: Unavailable")
                    trySend(NetworkStatus.Unavailable)
                }
            }
        }
        
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(networkRequest, callback)
        
        // Send initial status update
        trySend(getCurrentNetworkStatus())
        
        awaitClose {
            Log.d(TAG, "Unregistering network callback")
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
    
    /**
     * Get current network status
     */
    fun getCurrentNetworkStatus(): NetworkStatus {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        return if (capabilities != null &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        ) {
            NetworkStatus.Available
        } else {
            NetworkStatus.Unavailable
        }
    }
    
    /**
     * Check if there is a network connection
     */
    fun isNetworkAvailable(): Boolean {
        return getCurrentNetworkStatus() == NetworkStatus.Available
    }
}

/**
 * Network Status Enum
 */
sealed class NetworkStatus {
    object Available : NetworkStatus()
    object Unavailable : NetworkStatus()
}
