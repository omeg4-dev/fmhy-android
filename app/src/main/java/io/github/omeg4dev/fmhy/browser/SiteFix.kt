package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.data.user.SitePrefs

/**
 * "Fix this site" (BRW-23): a manual per-host switch that lets every ad load. Network blocking,
 * element hiding and scriptlets are all off for the host; the app stubs and hides nothing, so the
 * page's own ad-block check sees ads load and its content (for example a download section that
 * shares a container with the "disable your ad blocker" text) stays visible.
 */
object SiteFix {
    /** Whether ad blocking applies to a page with [sitePrefs] given the global switch [globalOn]. */
    fun adblockActive(globalOn: Boolean, sitePrefs: SitePrefs?): Boolean =
        globalOn && sitePrefs?.adblock != false && sitePrefs?.fixSite != true
}
