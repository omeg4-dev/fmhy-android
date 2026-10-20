package io.github.omeg4dev.fmhy.nav

import androidx.navigation3.runtime.NavKey
import io.github.omeg4dev.fmhy.HomeNavKey
import io.github.omeg4dev.fmhy.SectionNavKey
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import java.net.URI

sealed interface FmhyLinkDestination {
    data class Navigate(val navKey: NavKey) : FmhyLinkDestination
    data class OpenBrowser(val url: String) : FmhyLinkDestination
}

fun resolveFmhyUri(url: String, crossLinks: CrossLinks? = null): FmhyLinkDestination? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return null

    val uri = try {
        URI(trimmed)
    } catch (_: Exception) {
        return null
    }

    val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
    if (host != "fmhy.net") return null

    val path = (uri.path ?: "").trimEnd('/')
    if (path.isEmpty() || path == "/") {
        return FmhyLinkDestination.Navigate(HomeNavKey)
    }

    val target = crossLinks?.resolve(trimmed)
    if (target != null) {
        val anchor = target.headingId ?: uri.fragment
        return FmhyLinkDestination.Navigate(SectionNavKey(target.sectionId, anchor))
    }

    return FmhyLinkDestination.OpenBrowser(trimmed)
}
