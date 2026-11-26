package io.github.omeg4dev.fmhy.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.FmhyApp
import io.github.omeg4dev.fmhy.R
import io.github.omeg4dev.fmhy.data.model.Link
import io.github.omeg4dev.fmhy.theme.LocalCalloutColors

fun noteIdOf(url: String): String? {
    val path = extractNoteInfo(url)?.first ?: return null
    val fileName = path.substringAfterLast('/')
    return if (fileName.endsWith(".md", ignoreCase = true)) fileName.dropLast(3) else fileName
}

@DrawableRes
fun linkIconFor(link: Link): Int? =
    if (noteIdOf(link.url) != null) R.drawable.ic_link_note else socialIconFor(link.label)

fun noteLinkTint(
    url: String,
    criticalIds: Set<String>,
    accent: Color,
    critical: Color,
): Color {
    val id = noteIdOf(url)
    return if (id != null && id in criticalIds) critical else accent
}

@Composable
fun linkIconTint(url: String): Color {
    val primary = MaterialTheme.colorScheme.primary
    if (noteIdOf(url) == null) return primary
    val container = LocalAppContainer.current
        ?: (LocalContext.current.applicationContext as? FmhyApp)?.container
    val criticalIds = if (container != null) {
        container.criticalNoteIds.collectAsStateWithLifecycle().value
    } else {
        emptySet()
    }
    return noteLinkTint(
        url = url,
        criticalIds = criticalIds,
        accent = primary,
        critical = LocalCalloutColors.current.danger.accent,
    )
}
