package io.github.omeg4dev.fmhy.data.nsfw

import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.model.SectionItem

const val NSFW_SECTION_ID = "nsfw"
const val NSFW_SECTION_TITLE = "NSFW"
const val NSFW_CHECKPOINT_URL = "https://rentry.org/NSFW-Checkpoint"
const val NSFW_RAW_BASE_URL = "https://yaso.su"
const val NSFW_QUITTING_URL = "https://fmhy.net/miscguide#porn-quitting"

val NSFW_SECTION = Section(
    id = NSFW_SECTION_ID,
    title = NSFW_SECTION_TITLE,
    description = "",
    ord = Int.MAX_VALUE,
    entryCount = 0,
)

/** Result of loading the on-demand NSFW section list. */
sealed interface NsfwLoadResult {
    data class Success(
        val headings: List<Heading>,
        val items: List<SectionItem>,
        val entries: List<Entry>,
        val stale: Boolean,
    ) : NsfwLoadResult

    data object BlockedByWifi : NsfwLoadResult

    data class Failed(val reason: String? = null) : NsfwLoadResult
}
