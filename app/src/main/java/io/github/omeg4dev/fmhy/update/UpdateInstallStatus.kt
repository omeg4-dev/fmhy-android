package io.github.omeg4dev.fmhy.update

enum class UpdateInstallOutcome {
    PENDING_USER_ACTION,
    SUCCESS,
    ABORTED,
    SIGNATURE_MISMATCH,
    FAILURE,
}

object UpdateInstallStatus {
    const val STATUS_PENDING_USER_ACTION = -1
    const val STATUS_SUCCESS = 0
    const val STATUS_FAILURE = 1
    const val STATUS_FAILURE_BLOCKED = 2
    const val STATUS_FAILURE_ABORTED = 3
    const val STATUS_FAILURE_INVALID = 4
    const val STATUS_FAILURE_CONFLICT = 5
    const val STATUS_FAILURE_STORAGE = 6
    const val STATUS_FAILURE_INCOMPATIBLE = 7

    fun map(status: Int, message: String?): UpdateInstallOutcome {
        return when (status) {
            STATUS_PENDING_USER_ACTION -> UpdateInstallOutcome.PENDING_USER_ACTION
            STATUS_SUCCESS -> UpdateInstallOutcome.SUCCESS
            STATUS_FAILURE_ABORTED -> UpdateInstallOutcome.ABORTED
            STATUS_FAILURE_CONFLICT, STATUS_FAILURE_INCOMPATIBLE, STATUS_FAILURE_INVALID -> {
                if (isSignatureMismatchMessage(message)) {
                    UpdateInstallOutcome.SIGNATURE_MISMATCH
                } else {
                    UpdateInstallOutcome.FAILURE
                }
            }
            else -> UpdateInstallOutcome.FAILURE
        }
    }

    private fun isSignatureMismatchMessage(message: String?): Boolean {
        if (message == null) return false
        return message.contains("signature", ignoreCase = true) ||
            message.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE", ignoreCase = true)
    }
}
