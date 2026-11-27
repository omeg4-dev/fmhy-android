package io.github.omeg4dev.fmhy.data.newbadge

import java.net.URL
import java.time.LocalDate
import java.util.Locale

data class Mark(val url: String, val date: LocalDate)

data class ParseResult(
    val marks: List<Mark>,
    val skipped: Int,
    val sawBadge: Boolean,
)

object NewBadgeParser {
    private val LI_TAG_REGEX = Regex("""<li\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val CLASS_ATTR_REGEX = Regex("""\bclass\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE)
    private val DATE_REGEX = Regex("""aria-label\s*=\s*(["'])\s*New,\s*added\s+(\d{4}-\d{2}-\d{2})\s*\1""", RegexOption.IGNORE_CASE)
    private val HREF_REGEX = Regex("""<a\b[^>]*\bhref\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE)
    private val WHITESPACE_REGEX = Regex("""\s+""")

    fun parse(html: String, baseUrl: String): ParseResult {
        val marks = mutableListOf<Mark>()
        var skipped = 0
        val sawBadgeInPage = html.contains("new-badge", ignoreCase = true) ||
            html.contains("New, added", ignoreCase = true)

        // One Matcher for the whole page: findAll() makes a new one per match, and on Android
        // each Matcher copies the full input into native memory (GBs per page).
        val li = LI_TAG_REGEX.toPattern().matcher(html)
        while (li.find()) {
            val attrs = li.group(1).orEmpty()
            val classMatch = CLASS_ATTR_REGEX.find(attrs) ?: continue
            val classVal = classMatch.groupValues[2]
            val tokens = classVal.split(WHITESPACE_REGEX)
            if ("new" !in tokens) continue

            val tagEnd = li.end()
            val nextClose = html.indexOf("</li>", startIndex = tagEnd, ignoreCase = true)
            val nextOpen = html.indexOf("<li", startIndex = tagEnd, ignoreCase = true)
            val segEnd = when {
                nextClose >= 0 && nextOpen >= 0 -> minOf(nextClose, nextOpen)
                nextClose >= 0 -> nextClose
                nextOpen >= 0 -> nextOpen
                else -> html.length
            }
            val segment = html.substring(tagEnd, segEnd)

            val dateMatch = DATE_REGEX.find(segment)
            val hrefMatch = HREF_REGEX.find(segment)

            if (dateMatch == null || hrefMatch == null) {
                skipped++
                continue
            }

            val date = try {
                LocalDate.parse(dateMatch.groupValues[2])
            } catch (_: Exception) {
                null
            }

            val rawHref = hrefMatch.groupValues[2]
            val resolvedUrl = resolveUrl(baseUrl, decodeHtmlEntities(rawHref))

            if (date == null || resolvedUrl == null) {
                skipped++
                continue
            }

            marks.add(Mark(resolvedUrl, date))
        }

        val sawBadge = sawBadgeInPage || marks.isNotEmpty() || skipped > 0
        return ParseResult(marks = marks, skipped = skipped, sawBadge = sawBadge)
    }

    private fun resolveUrl(baseUrl: String, href: String): String? = try {
        val trimmed = href.trim()
        if (trimmed.isEmpty()) {
            null
        } else {
            val resolved = URL(URL(baseUrl), trimmed)
            val proto = resolved.protocol.lowercase(Locale.ROOT)
            if (proto == "http" || proto == "https") {
                resolved.toString()
            } else {
                null
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun decodeHtmlEntities(input: String): String {
        if (!input.contains('&')) return input
        val sb = StringBuilder(input.length)
        var i = 0
        val n = input.length
        while (i < n) {
            val c = input[i]
            if (c == '&') {
                val semi = input.indexOf(';', i + 1)
                if (semi in (i + 1)..(i + 10)) {
                    val entity = input.substring(i + 1, semi)
                    val replacement = when {
                        entity.equals("amp", ignoreCase = true) -> "&"
                        entity.equals("quot", ignoreCase = true) -> "\""
                        entity.equals("apos", ignoreCase = true) -> "'"
                        entity.equals("lt", ignoreCase = true) -> "<"
                        entity.equals("gt", ignoreCase = true) -> ">"
                        entity.startsWith("#x", ignoreCase = true) -> try {
                            val code = entity.substring(2).toInt(16)
                            Character.toChars(code).concatToString()
                        } catch (_: Exception) {
                            null
                        }
                        entity.startsWith("#") -> try {
                            val code = entity.substring(1).toInt(10)
                            Character.toChars(code).concatToString()
                        } catch (_: Exception) {
                            null
                        }
                        else -> null
                    }
                    if (replacement != null) {
                        sb.append(replacement)
                        i = semi + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }
}
