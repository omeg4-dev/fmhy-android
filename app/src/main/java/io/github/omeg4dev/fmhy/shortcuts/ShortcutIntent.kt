package io.github.omeg4dev.fmhy.shortcuts

/** Extra key on MainActivity intents (static shortcuts, widget taps). */
const val EXTRA_OPEN = "open"
const val EXTRA_FILTER = "filter"
const val EXTRA_ENTRY_ID = "entry_id"
const val EXTRA_SECTION_ID = "section_id"
const val EXTRA_HEADING_ID = "heading_id"

/** Where an `open` extra sends the user. Pure so it can be unit-tested without Android. */
sealed interface ShortcutDestination {
    data object Search : ShortcutDestination
    data object Saved : ShortcutDestination
    data class WhatsNew(val filter: String) : ShortcutDestination
    data object CheckLink : ShortcutDestination
    data object Posts : ShortcutDestination

    /** The widget's random pick; [entryId] is the one the widget showed, null = choose a new one. */
    data class Random(val entryId: String?) : ShortcutDestination

    /** A pinned section (headingId empty) or category, from the widget. */
    data class Pin(val sectionId: String, val headingId: String) : ShortcutDestination
}

fun shortcutDestination(
    open: String?,
    filter: String? = null,
    entryId: String? = null,
    sectionId: String? = null,
    headingId: String? = null,
): ShortcutDestination? = when (open) {
    "search" -> ShortcutDestination.Search
    "saved" -> ShortcutDestination.Saved
    "whats_new" -> ShortcutDestination.WhatsNew(filter?.takeIf { it.isNotEmpty() } ?: "all")
    "check" -> ShortcutDestination.CheckLink
    "posts" -> ShortcutDestination.Posts
    "random" -> ShortcutDestination.Random(entryId?.takeIf { it.isNotEmpty() })
    "pin" -> sectionId?.takeIf { it.isNotEmpty() }?.let { ShortcutDestination.Pin(it, headingId.orEmpty()) }
    else -> null
}
