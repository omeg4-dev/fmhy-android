package io.github.omeg4dev.fmhy.update

import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

interface UpdateController {
    val state: StateFlow<UpdateState>
    /** Pending release if any (for the settings marker), survives Later/Skip. null if none or installed. */
    val availableVersion: StateFlow<String?>
    val enabled: Boolean            // = BuildConfig.UPDATER_ENABLED
    val pendingMobileAsk: StateFlow<Boolean>
    val needsInstallPermission: StateFlow<Boolean>

    fun checkOnStartup()            // honours autoCheck + once a day + Wi-Fi rule (auto: WAIT_FOR_WIFI → state WaitingForWifi and retry when NetworkMonitor.kind becomes WIFI)
    fun checkNow(overrideMobileData: Boolean = false)  // manual
    fun install(overrideMobileData: Boolean = false)   // download then hand to installer
    fun retry()
    fun later()                     // persists laterUntil = UpdateCheckPolicy.laterUntil(now); state → Idle
    fun skip()                      // persists skipped = release.version; state → Idle
    fun dismissMessage()            // UpToDate/CheckFailed/SignatureMismatch → Idle
    fun onInstallerResult(status: Int, message: String?, extraIntent: Intent? = null)
    fun confirmMobileAsk()
    fun cancelMobileAsk()
    fun confirmInstallPermission()
    fun cancelInstallPermission()
}
