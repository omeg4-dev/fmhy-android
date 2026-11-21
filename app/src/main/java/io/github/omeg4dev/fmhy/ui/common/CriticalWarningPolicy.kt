package io.github.omeg4dev.fmhy.ui.common

import io.github.omeg4dev.fmhy.data.model.CriticalNote

enum class OpenStep {
    UNSAFE_DIALOG,
    CRITICAL_DIALOG,
    OPEN_DIRECTLY,
}

object CriticalWarningPolicy {

    /**
     * Decides whether to show the critical note warning dialog before opening a link.
     *
     * @param switchOn Whether the protection setting "Warn before opening sites with a critical note" is enabled.
     * @param note The critical note guarding the site, or null if there is none.
     * @param hidden Map of note ID to stored content hash for warnings the user chose not to show again.
     * @return True if the dialog should be shown, false otherwise.
     */
    fun shouldWarn(
        switchOn: Boolean,
        note: CriticalNote?,
        hidden: Map<String, String>,
    ): Boolean {
        if (!switchOn || note == null) return false
        val storedHash = hidden[note.id]
        return storedHash != note.hash
    }

    /**
     * Determines the next step when opening a site, enforcing that the unsafe-site warning
     * is shown first (if applicable), then the critical note warning, then opening directly.
     */
    fun nextStep(hasUnsafeReason: Boolean, shouldWarnCritical: Boolean): OpenStep {
        return when {
            hasUnsafeReason -> OpenStep.UNSAFE_DIALOG
            shouldWarnCritical -> OpenStep.CRITICAL_DIALOG
            else -> OpenStep.OPEN_DIRECTLY
        }
    }
}
