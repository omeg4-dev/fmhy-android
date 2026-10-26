package io.github.omeg4dev.fmhy.browser

import io.github.omeg4dev.fmhy.browser.adblock.AllowRule

object BlockedList {
    fun rowText(count: Int): String = when (count) {
        0 -> "Nothing blocked on this page"
        1 -> "1 blocked on this page"
        else -> "$count blocked on this page"
    }

    fun sorted(hosts: List<BlockedHost>, pageHost: String?): List<BlockedHost> {
        val site = pageHost?.let { AllowRule.pageSite(it) }?.lowercase()
        return hosts.sortedWith(
            compareBy<BlockedHost> { host ->
                val isOwn = site != null && (host.host.equals(site, ignoreCase = true) || host.host.lowercase().endsWith(".$site"))
                !isOwn
            }
                .thenByDescending { it.count }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.host }
        )
    }

    fun filter(hosts: List<BlockedHost>, query: String): List<BlockedHost> {
        if (query.isBlank()) return hosts
        val trimmed = query.trim()
        return hosts.filter { it.host.contains(trimmed, ignoreCase = true) }
    }

    fun showSearch(size: Int): Boolean = size > 15

    fun middleEllipsis(host: String, max: Int = 32): String {
        if (host.length <= max) return host
        if (max <= 1) return host.take(max)
        val rem = max - 1
        val lastDot = host.lastIndexOf('.')
        val tldLen = if (lastDot != -1) host.length - lastDot else 0

        val endLen = when {
            tldLen in 1 until rem -> maxOf(rem / 2, tldLen)
            else -> rem / 2
        }
        val startLen = rem - endLen
        return host.take(startLen) + "…" + host.takeLast(endLen)
    }
}
