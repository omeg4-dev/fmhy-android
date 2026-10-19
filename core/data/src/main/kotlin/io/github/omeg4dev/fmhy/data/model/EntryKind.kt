package io.github.omeg4dev.fmhy.data.model

/** Entry kind: 0 = normal, 1 = 🌐 index/directory, 2 = ↪ section link. */
enum class EntryKind(val value: Int) {
    NORMAL(0),
    INDEX(1),
    SECTION_LINK(2);

    companion object {
        fun fromInt(v: Int): EntryKind = when (v) {
            1 -> INDEX
            2 -> SECTION_LINK
            else -> NORMAL
        }
    }
}
