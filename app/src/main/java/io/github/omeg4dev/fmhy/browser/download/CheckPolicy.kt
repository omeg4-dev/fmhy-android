package io.github.omeg4dev.fmhy.browser.download

import io.github.omeg4dev.fmhy.browser.RiskyDownload
import io.github.omeg4dev.fmhy.data.CheckPrefs

data class AfterDownload(val hold: Boolean, val checkNow: Boolean, val keyPrompt: Boolean)

object CheckPolicy {
    /** What happens when a download's bytes are complete. */
    fun afterDownload(fileName: String, prefs: CheckPrefs): AfterDownload {
        val ready = prefs.readyServices.isNotEmpty()
        val risky = RiskyDownload.isAutoCheckType(fileName)
        val hold = prefs.holdUntilChecked
        val checkNow = ready && (hold || (prefs.autoCheckRisky && risky))
        val keyPrompt = !ready && !prefs.keyPromptShown && ((prefs.autoCheckRisky && risky) || hold)
        return AfterDownload(hold = hold, checkNow = checkNow, keyPrompt = keyPrompt)
    }

    /** Status an entry gets back after an app restart. */
    fun restoredStatus(status: DownloadStatus): DownloadStatus = when (status) {
        DownloadStatus.RUNNING -> DownloadStatus.WAITING
        DownloadStatus.CHECKING -> DownloadStatus.AWAITING_DECISION
        else -> status
    }

    /** Open / Install need an extra confirmation. */
    fun needsExtraConfirm(item: DownloadItem): Boolean =
        item.check?.verdict == ScanVerdict.DANGEROUS
}
