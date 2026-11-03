package io.github.omeg4dev.fmhy.ui.home

enum class HomeBlock {
    SEARCH,
    NEW_SINCE,
    RANDOM_PICK,
    PINNED,
    NEW_IN_PINNED,
    FAVOURITES,
    SUGGESTIONS,
    SECTIONS,
}

data class HomeBlockEntry(
    val block: HomeBlock,
    val enabled: Boolean,
)

data class HomeLayout(
    val entries: List<HomeBlockEntry> = DEFAULT_ENTRIES,
) {
    init {
        require(entries.size == HomeBlock.entries.size && entries.map { it.block }.toSet().size == HomeBlock.entries.size) {
            "HomeLayout must contain each HomeBlock exactly once"
        }
    }

    val enabledCount: Int
        get() = entries.count { it.enabled }

    fun enabledBlocks(): List<HomeBlock> =
        entries.filter { it.enabled }.map { it.block }

    fun isEnabled(b: HomeBlock): Boolean =
        entries.firstOrNull { it.block == b }?.enabled ?: false

    fun withEnabled(b: HomeBlock, on: Boolean): HomeLayout =
        HomeLayout(entries.map { if (it.block == b) it.copy(enabled = on) else it })

    fun move(from: Int, to: Int): HomeLayout {
        if (from == to || from !in entries.indices || to !in entries.indices) return this
        val mutable = entries.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        return HomeLayout(mutable)
    }

    fun reset(): HomeLayout = DEFAULT

    fun encode(): String =
        entries.joinToString(",") { "${it.block.name}:${if (it.enabled) "1" else "0"}" }

    companion object {
        private val DEFAULT_ENTRIES: List<HomeBlockEntry> = listOf(
            HomeBlockEntry(HomeBlock.SEARCH, true),
            HomeBlockEntry(HomeBlock.NEW_SINCE, true),
            HomeBlockEntry(HomeBlock.RANDOM_PICK, true),
            HomeBlockEntry(HomeBlock.PINNED, false),
            HomeBlockEntry(HomeBlock.NEW_IN_PINNED, false),
            HomeBlockEntry(HomeBlock.FAVOURITES, false),
            HomeBlockEntry(HomeBlock.SUGGESTIONS, false),
            HomeBlockEntry(HomeBlock.SECTIONS, true),
        )

        val DEFAULT: HomeLayout = HomeLayout(DEFAULT_ENTRIES)

        fun default(): HomeLayout = DEFAULT

        fun fromLegacy(personalHome: Boolean, showRandomPick: Boolean): HomeLayout =
            DEFAULT.withEnabled(HomeBlock.PINNED, personalHome)
                .withEnabled(HomeBlock.RANDOM_PICK, showRandomPick)

        /**
         * Decodes a layout from an encoded string (format: "NAME:0|1,...").
         *
         * - null, blank, or strings without any parseable entries return [default].
         * - Unknown block names and invalid flag tokens are dropped.
         * - If duplicate block names appear, the first occurrence is kept.
         * - Missing blocks are inserted at their default relative position:
         *   for each missing block (in enum order), it is inserted immediately after
         *   the last block currently present that precedes it in enum order. If no
         *   preceding block is present, it is inserted before the first succeeding
         *   block in enum order, or appended at the end if none. Its enabled state
         *   defaults to its standard initial enabled state.
         * - Never throws.
         */
        fun decode(s: String?): HomeLayout {
            if (s.isNullOrBlank()) return DEFAULT
            return try {
                val tokens = s.split(',')
                val parsed = mutableListOf<HomeBlockEntry>()
                val seen = mutableSetOf<HomeBlock>()

                for (token in tokens) {
                    val parts = token.trim().split(':')
                    if (parts.size != 2) continue
                    val name = parts[0].trim()
                    val flag = parts[1].trim()
                    val enabled = when (flag) {
                        "1" -> true
                        "0" -> false
                        else -> continue
                    }
                    val block = try {
                        HomeBlock.valueOf(name)
                    } catch (_: IllegalArgumentException) {
                        continue
                    }
                    if (seen.add(block)) {
                        parsed.add(HomeBlockEntry(block, enabled))
                    }
                }

                if (parsed.isEmpty()) return DEFAULT

                val result = parsed.toMutableList()
                for (block in HomeBlock.entries) {
                    if (block !in seen) {
                        val defaultEntry = DEFAULT_ENTRIES.first { it.block == block }
                        val lastPrecedingIndex = result.indexOfLast { it.block.ordinal < block.ordinal }
                        if (lastPrecedingIndex >= 0) {
                            result.add(lastPrecedingIndex + 1, defaultEntry)
                        } else {
                            val firstSucceedingIndex = result.indexOfFirst { it.block.ordinal > block.ordinal }
                            if (firstSucceedingIndex >= 0) {
                                result.add(firstSucceedingIndex, defaultEntry)
                            } else {
                                result.add(defaultEntry)
                            }
                        }
                    }
                }

                HomeLayout(result)
            } catch (_: Throwable) {
                DEFAULT
            }
        }
    }
}
