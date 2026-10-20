package io.github.omeg4dev.fmhy.data.model

/** Kind of change tracked in the `changes` table. */
enum class ChangeKind(val value: String) {
    ADDED("added"),
    REMOVED("removed"),
    STARRED("starred"),
    UNSTARRED("unstarred"),
    MOVED("moved"),
    RENAMED("renamed"),
    URL_CHANGED("url_changed"),
    EDITED("edited");

    companion object {
        private val map = entries.associateBy { it.value }
        fun fromString(s: String): ChangeKind =
            map[s] ?: throw IllegalArgumentException("Unknown change kind: $s")
    }
}
