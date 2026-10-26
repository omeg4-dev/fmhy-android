package io.github.omeg4dev.fmhy.work

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkKind {
    WIFI,
    MOBILE,
    NONE,
}

object NetworkPolicy {
    fun kindOf(isWifiOrEthernet: Boolean, isConnected: Boolean, isMetered: Boolean): NetworkKind {
        if (!isConnected) return NetworkKind.NONE
        return if (isWifiOrEthernet && !isMetered) NetworkKind.WIFI else NetworkKind.MOBILE
    }

    fun allowed(switchOn: Boolean, kind: NetworkKind, overrideThisTime: Boolean = false): Boolean {
        if (!switchOn || overrideThisTime) return true
        return kind == NetworkKind.WIFI
    }

    fun needsAsk(manual: Boolean, switchOn: Boolean, kind: NetworkKind): Boolean {
        return manual && switchOn && kind == NetworkKind.MOBILE
    }

    fun firstWikiNeedsAsk(
        hasBuiltInWiki: Boolean,
        hasLocalWiki: Boolean,
        switchOn: Boolean,
        kind: NetworkKind,
    ): Boolean {
        return !hasBuiltInWiki && !hasLocalWiki && switchOn && kind == NetworkKind.MOBILE
    }
}

open class NetworkMonitor(
    context: Context? = null,
    initialKind: NetworkKind = NetworkKind.NONE,
) {
    private val connectivityManager: ConnectivityManager? = runCatching {
        context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }.getOrNull()

    protected val _kind = MutableStateFlow(if (connectivityManager != null) computeKind() else initialKind)
    open val kind: StateFlow<NetworkKind> = _kind.asStateFlow()

    init {
        try {
            connectivityManager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _kind.value = computeKind()
                }

                override fun onLost(network: Network) {
                    _kind.value = computeKind()
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    _kind.value = computeKind(networkCapabilities)
                }
            })
        } catch (_: Throwable) {
            // Ignored in unit tests or when background permissions are restricted
        }
    }

    fun setKindForTesting(newKind: NetworkKind) {
        _kind.value = newKind
    }

    private fun computeKind(caps: NetworkCapabilities? = null): NetworkKind {
        return try {
            val cm = connectivityManager ?: return NetworkKind.NONE
            val activeCaps = caps ?: cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return NetworkKind.NONE
            val isWifiOrEthernet = activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            val isMetered = !activeCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            NetworkPolicy.kindOf(
                isWifiOrEthernet = isWifiOrEthernet,
                isConnected = true,
                isMetered = isMetered,
            )
        } catch (_: Throwable) {
            NetworkKind.NONE
        }
    }
}
