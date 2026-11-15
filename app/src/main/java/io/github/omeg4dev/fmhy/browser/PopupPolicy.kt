package io.github.omeg4dev.fmhy.browser

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface PopupDecision {
    data class Allow(val isBackground: Boolean) : PopupDecision
    data class Block(val targetUrl: String, val reason: String) : PopupDecision
}

object PopupPolicy {
    private val TWO_PART_TLDS = setOf("co.uk", "com.au", "org.uk", "gov.uk", "net.au", "co.jp", "com.br", "co.nz", "co.in")

    fun getTopPrivateDomain(httpUrl: HttpUrl): String {
        val domain = try {
            httpUrl.topPrivateDomain()
        } catch (_: Throwable) {
            null
        }
        if (domain != null) return domain

        val host = httpUrl.host
        val parts = host.split('.')
        if (parts.size <= 2) return host
        val lastTwo = "${parts[parts.size - 2]}.${parts.last()}"
        return if (TWO_PART_TLDS.contains(lastTwo) && parts.size >= 3) {
            "${parts[parts.size - 3]}.$lastTwo"
        } else {
            lastTwo
        }
    }

    fun isSameSite(url1: String, url2: String): Boolean {
        val u1 = url1.toHttpUrlOrNull() ?: return false
        val u2 = url2.toHttpUrlOrNull() ?: return false
        val domain1 = getTopPrivateDomain(u1)
        val domain2 = getTopPrivateDomain(u2)
        return domain1.equals(domain2, ignoreCase = true)
    }

    fun evaluate(
        targetUrl: String,
        currentUrl: String,
        isUserGesture: Boolean,
        popupBlockerEnabled: Boolean,
        popupsSitePref: Boolean?,
        isBlockedByEngine: (targetUrl: String, currentUrl: String) -> Boolean = { _, _ -> false }
    ): PopupDecision {
        // A site override wins over the global switch in both directions (stored value = pop-ups allowed).
        val blockerActive = SitePopupPref.blockSwitch(popupsSitePref, popupBlockerEnabled)
        if (!blockerActive) {
            // Blocker off -> every window opens as a new tab
            return PopupDecision.Allow(isBackground = false)
        }

        if (!isUserGesture) {
            // A new window WITHOUT isUserGesture is blocked
            return PopupDecision.Block(targetUrl, "No user gesture")
        }

        // WITH a gesture it opens in a new background tab only if the target is same-site
        // (eTLD+1 via okhttp3.HttpUrl.topPrivateDomain()) or the engine doesn't block it as ResourceType.POPUP; otherwise blocked.
        if (isSameSite(currentUrl, targetUrl)) {
            return PopupDecision.Allow(isBackground = true)
        }

        if (isBlockedByEngine(targetUrl, currentUrl)) {
            return PopupDecision.Block(targetUrl, "Blocked as adblock popup")
        }

        return PopupDecision.Allow(isBackground = true)
    }
}

/**
 * `SitePrefs.popups` is stored as "pop-ups allowed" (true = allowed, false = blocked, null = follow
 * the global blocker). Every "Block pop-ups" switch maps through here so they all read it the same way.
 */
object SitePopupPref {
    /** What a "Block pop-ups" switch shows for a stored value. */
    fun blockSwitch(stored: Boolean?, globalBlocker: Boolean): Boolean = stored?.let { !it } ?: globalBlocker

    /** The value to store when a "Block pop-ups" switch is set to [blockPopups]. */
    fun toStored(blockPopups: Boolean): Boolean = !blockPopups

    /** Short label for the site list. */
    fun label(stored: Boolean): String = if (stored) "pop-ups allowed" else "pop-ups blocked"
}
