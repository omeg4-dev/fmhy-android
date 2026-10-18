package io.github.omeg4dev.fmhy.data.model

/** Entry quality tier: 0 = normal, 1 = starred, 2 = top pick. */
enum class Tier(val value: Int) {
    NORMAL(0),
    STAR(1),
    TOP(2);

    companion object {
        fun fromInt(v: Int): Tier = when (v) {
            2 -> TOP
            1 -> STAR
            else -> NORMAL
        }
    }
}
