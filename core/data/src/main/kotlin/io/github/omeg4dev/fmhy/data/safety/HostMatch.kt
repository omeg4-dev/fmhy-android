package io.github.omeg4dev.fmhy.data.safety

import io.github.omeg4dev.fmhy.data.model.Entry

enum class Via {
    URL,
    MIRROR,
    EXTRA,
}

data class HostMatch(
    val entry: Entry,
    val matchedUrl: String,
    val via: Via,
)
