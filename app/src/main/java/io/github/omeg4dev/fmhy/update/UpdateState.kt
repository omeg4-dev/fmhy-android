package io.github.omeg4dev.fmhy.update

/**
 * UI and lifecycle states representing update check and download progression.
 */
sealed interface UpdateState {
    data object Idle : UpdateState

    data class Checking(val manual: Boolean) : UpdateState

    data class UpToDate(val manual: Boolean) : UpdateState

    data class Available(val release: ReleaseInfo) : UpdateState

    /** Auto check/download paused because Wi-Fi only is on and we are on mobile data. UI text: "Waiting for Wi-Fi". */
    data class WaitingForWifi(val release: ReleaseInfo?) : UpdateState

    data class Downloading(val release: ReleaseInfo, val bytes: Long, val total: Long) : UpdateState {
        val fraction: Float? = if (total <= 0L) null else (bytes.toFloat() / total).coerceIn(0f, 1f)
    }

    data class ReadyToInstall(val release: ReleaseInfo, val filePath: String) : UpdateState

    data class DownloadFailed(val release: ReleaseInfo, val reason: String) : UpdateState

    data class CheckFailed(val reason: String) : UpdateState

    /** System refused: signature does not match the installed app. */
    data class SignatureMismatch(val release: ReleaseInfo) : UpdateState
}
