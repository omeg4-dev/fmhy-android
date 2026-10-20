package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.safety.SafetyStatus
import io.github.omeg4dev.fmhy.data.safety.Verdict

enum class SiteBarKind {
    STARRED,
    LISTED,
    FMHY_SITE,
    NOT_ON_FMHY,
    POTENTIALLY_UNSAFE,
    UNSAFE,
}

data class SiteBarState(
    val kind: SiteBarKind,
    val text: String,
    val verdict: Verdict,
    val entry: Entry?,
)

object SiteBarModel {
    fun from(
        verdict: Verdict,
        isRedirectedFromEntry: Boolean,
        isDismissed: Boolean = false,
        sectionTitle: String? = null,
    ): SiteBarState? {
        if (isDismissed) return null

        val entry = verdict.matches.firstOrNull()
        val path = entry?.headingPath?.ifBlank { null }
            ?: sectionTitle?.ifBlank { null }
            ?: entry?.sectionId?.replace('-', ' ')?.replaceFirstChar { it.uppercase() }
        val suffix = if (path != null) " · $path" else ""

        return when (verdict.status) {
            SafetyStatus.STARRED -> {
                SiteBarState(
                    kind = SiteBarKind.STARRED,
                    text = "Starred on FMHY$suffix",
                    verdict = verdict,
                    entry = entry,
                )
            }
            SafetyStatus.LISTED -> {
                SiteBarState(
                    kind = SiteBarKind.LISTED,
                    text = "On FMHY$suffix",
                    verdict = verdict,
                    entry = entry,
                )
            }
            SafetyStatus.FMHY_SITE -> {
                SiteBarState(
                    kind = SiteBarKind.FMHY_SITE,
                    text = "Official FMHY site",
                    verdict = verdict,
                    entry = entry,
                )
            }
            SafetyStatus.POTENTIALLY_UNSAFE -> {
                SiteBarState(
                    kind = SiteBarKind.POTENTIALLY_UNSAFE,
                    text = "Potentially unsafe on FMHY",
                    verdict = verdict,
                    entry = entry,
                )
            }
            SafetyStatus.UNSAFE -> {
                SiteBarState(
                    kind = SiteBarKind.UNSAFE,
                    text = "Unsafe on FMHY",
                    verdict = verdict,
                    entry = entry,
                )
            }
            SafetyStatus.NO_DATA -> {
                if (isRedirectedFromEntry) {
                    SiteBarState(
                        kind = SiteBarKind.NOT_ON_FMHY,
                        text = "Not on FMHY",
                        verdict = verdict,
                        entry = null,
                    )
                } else {
                    null
                }
            }
            SafetyStatus.SEARCH_ENGINE,
            SafetyStatus.INVALID -> null
        }
    }
}
