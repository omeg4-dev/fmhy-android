package io.github.omeg4dev.fmhy.data.adblock

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

data class FilterListMeta(
    val title: String?,
    val expires: Duration?
) {
    companion object {
        private val EXPIRES_REGEX = Regex("""(?i)^\s*!\s*Expires:\s*(\d+)\s*(days?|d|hours?|h)""")
        private val TITLE_REGEX = Regex("""(?i)^\s*!\s*Title:\s*(.+)""")

        fun parse(text: String): FilterListMeta {
            var title: String? = null
            var expires: Duration? = null

            for (line in text.lineSequence()) {
                val trimmed = line.trim()
                if (!trimmed.startsWith("!")) {
                    if (trimmed.startsWith("[Adblock")) continue
                    if (trimmed.isNotEmpty()) break
                    continue
                }
                if (title == null) {
                    val titleMatch = TITLE_REGEX.find(trimmed)
                    if (titleMatch != null) {
                        title = titleMatch.groupValues[1].trim()
                    }
                }
                if (expires == null) {
                    val expiresMatch = EXPIRES_REGEX.find(trimmed)
                    if (expiresMatch != null) {
                        val num = expiresMatch.groupValues[1].toIntOrNull()
                        val unit = expiresMatch.groupValues[2].lowercase()
                        if (num != null) {
                            expires = if (unit.startsWith("d")) num.days else num.hours
                        }
                    }
                }
                if (title != null && expires != null) break
            }
            return FilterListMeta(title, expires)
        }
    }
}
