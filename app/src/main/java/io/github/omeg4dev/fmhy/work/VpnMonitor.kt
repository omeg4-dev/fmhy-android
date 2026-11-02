package io.github.omeg4dev.fmhy.work

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VpnState { ACTIVE, INACTIVE }

object VpnPolicy {
    fun stateOf(hasVpnTransport: Boolean): VpnState =
        if (hasVpnTransport) VpnState.ACTIVE else VpnState.INACTIVE

    /** null when the mark/row must be hidden (setting off), else the state. */
    fun markFor(state: VpnState, showVpnState: Boolean): VpnState? =
        if (showVpnState) state else null
}

open class VpnMonitor(
    context: Context? = null,
    initial: VpnState = VpnState.INACTIVE,
) {
    private val connectivityManager: ConnectivityManager? = runCatching {
        context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }.getOrNull()

    protected val _state = MutableStateFlow(if (connectivityManager != null) computeState() else initial)
    open val state: StateFlow<VpnState> = _state.asStateFlow()

    init {
        try {
            connectivityManager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val caps = connectivityManager.getNetworkCapabilities(network)
                    _state.value = VpnPolicy.stateOf(caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true)
                }

                override fun onLost(network: Network) {
                    _state.value = VpnState.INACTIVE
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    _state.value = VpnPolicy.stateOf(networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
                }
            })
        } catch (_: Throwable) {
            // Ignored in unit tests or when background permissions are restricted
        }
    }

    internal fun setStateForTesting(newState: VpnState) {
        _state.value = newState
    }

    private fun computeState(caps: NetworkCapabilities? = null): VpnState {
        return try {
            val cm = connectivityManager ?: return VpnState.INACTIVE
            val activeCaps = caps ?: cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return VpnState.INACTIVE
            VpnPolicy.stateOf(activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
        } catch (_: Throwable) {
            VpnState.INACTIVE
        }
    }
}
