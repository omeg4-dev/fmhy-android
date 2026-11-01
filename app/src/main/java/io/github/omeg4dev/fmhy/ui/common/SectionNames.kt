package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.omeg4dev.fmhy.data.model.Section

val SECTION_DISPLAY_NAMES: Map<String, String> = mapOf(
    "privacy" to "Adblocking / Privacy",
    "ai" to "Artificial Intelligence",
    "video" to "Streaming",
    "audio" to "Listening",
    "gaming" to "Gaming",
    "reading" to "Reading",
    "downloading" to "Downloading",
    "torrenting" to "Torrenting",
    "educational" to "Educational",
    "mobile" to "Android / iOS",
    "linux-macos" to "Linux / macOS",
    "non-english" to "Non-English",
    "misc" to "Miscellaneous",
    "gaming-tools" to "Gaming Tools",
    "developer-tools" to "Developer Tools",
    "image-tools" to "Image Tools",
    "system-tools" to "System Tools",
    "file-tools" to "File Tools",
    "video-tools" to "Video Tools",
    "text-tools" to "Text Tools",
    "internet-tools" to "Internet Tools",
    "social-media-tools" to "Social Media Tools",
    "storage" to "Storage",
)

/**
 * Settings > Lists & gestures > "Category names from the FMHY website". It is Compose snapshot state, so every
 * composable that calls [sectionDisplayTitle] recomposes when the switch changes; the main activity feeds it from
 * the saved preference, and the widget and the update worker set it before they build their text.
 */
object SectionNamePrefs {
    var websiteNames by mutableStateOf(true)
}

/** Display name of a main category: the FMHY website name, or the wiki title when the setting is off or the id is new. */
fun sectionDisplayTitle(id: String, wikiTitle: String): String =
    if (SectionNamePrefs.websiteNames) SECTION_DISPLAY_NAMES[id] ?: wikiTitle else wikiTitle

val Section.displayTitle: String get() = sectionDisplayTitle(id, title)
