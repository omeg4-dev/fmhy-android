package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.safety.SafetyStatus
import io.github.omeg4dev.fmhy.data.safety.Verdict

enum class SiteBarKind {
    STARRED,
    LISTED,
    DOMAIN_LISTED,
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
            SafetyStatus.DOMAIN_LISTED -> {
                SiteBarState(
                    kind = SiteBarKind.DOMAIN_LISTED,
                    text = "Domain on FMHY · not this page",
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

/** Glyph shown by the FMHY status icon in the address pill. */
enum class StatusGlyph { STAR, VERIFIED, INFO, WARNING, ERROR }

/** Colour role of the status icon, mapped to the theme where it is drawn. */
enum class StatusTone { PRIMARY, TERTIARY, NEUTRAL, ERROR }

data class StatusIconSpec(val glyph: StatusGlyph, val tone: StatusTone)

/** Pure mapping from a site state to the small icon in the address pill. */
fun statusIconSpec(kind: SiteBarKind): StatusIconSpec = when (kind) {
    SiteBarKind.STARRED -> StatusIconSpec(StatusGlyph.STAR, StatusTone.TERTIARY)
    SiteBarKind.LISTED -> StatusIconSpec(StatusGlyph.VERIFIED, StatusTone.PRIMARY)
    SiteBarKind.DOMAIN_LISTED -> StatusIconSpec(StatusGlyph.VERIFIED, StatusTone.PRIMARY)
    SiteBarKind.FMHY_SITE -> StatusIconSpec(StatusGlyph.VERIFIED, StatusTone.PRIMARY)
    SiteBarKind.NOT_ON_FMHY -> StatusIconSpec(StatusGlyph.INFO, StatusTone.NEUTRAL)
    SiteBarKind.POTENTIALLY_UNSAFE -> StatusIconSpec(StatusGlyph.WARNING, StatusTone.ERROR)
    SiteBarKind.UNSAFE -> StatusIconSpec(StatusGlyph.ERROR, StatusTone.ERROR)
}

/** Popover title for a state; the section/reason follow underneath. */
fun statusTitle(kind: SiteBarKind): String = when (kind) {
    SiteBarKind.STARRED -> "Starred on FMHY"
    SiteBarKind.LISTED -> "Listed on FMHY"
    SiteBarKind.DOMAIN_LISTED -> "Site is on FMHY"
    SiteBarKind.FMHY_SITE -> "Official FMHY site"
    SiteBarKind.NOT_ON_FMHY -> "Not on FMHY"
    SiteBarKind.POTENTIALLY_UNSAFE -> "Potentially unsafe"
    SiteBarKind.UNSAFE -> "Unsafe site"
}
