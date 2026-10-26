package io.github.omeg4dev.fmhy.update

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object NoOpUpdateController : UpdateController {
    override val state: StateFlow<UpdateState> = MutableStateFlow(UpdateState.Idle)
    override val availableVersion: StateFlow<String?> = MutableStateFlow(null)
    override val enabled: Boolean = false
    override val pendingMobileAsk: StateFlow<Boolean> = MutableStateFlow(false)
    override val needsInstallPermission: StateFlow<Boolean> = MutableStateFlow(false)

    override fun checkOnStartup() {}
    override fun checkNow(overrideMobileData: Boolean) {}
    override fun install(overrideMobileData: Boolean) {}
    override fun retry() {}
    override fun later() {}
    override fun skip() {}
    override fun dismissMessage() {}
    override fun onInstallerResult(status: Int, message: String?, extraIntent: Intent?) {}
    override fun confirmMobileAsk() {}
    override fun cancelMobileAsk() {}
    override fun confirmInstallPermission() {}
    override fun cancelInstallPermission() {}
}
