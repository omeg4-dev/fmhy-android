package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.safety.SafeGuard
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A listed site that moves to a new domain (dropdrive.org -> dropdrive.rip) looks like a copy of itself.
 * The flag stays for lookalikes of anything else.
 */
object LookalikeAllow {
    private fun registrable(host: String): String? {
        val clean = host.trim().lowercase().removePrefix("www.")
        val url = "http://$clean/".toHttpUrlOrNull() ?: return null
        return RedirectPolicy.getTopPrivateDomain(url)
    }

    /** True when every lookalike entry is the site the redirect chain started on (url or mirrors). */
    fun isStartSite(lookalikes: List<Entry>, startHost: String?): Boolean {
        if (lookalikes.isEmpty() || startHost.isNullOrBlank()) return false
        val start = registrable(startHost) ?: return false
        return lookalikes.all { entry ->
            (listOf(entry.url) + entry.mirrors.map { it.url }).any { url ->
                SafeGuard.extractHostFromUrl(url)?.let(::registrable) == start
            }
        }
    }
}
