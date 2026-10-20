package io.github.omeg4dev.fmhy.data.doc

object DocCleaner {
    private val SCRIPT_REGEX = Regex("""<script\b[^>]*>[\s\S]*?<\/script>""", RegexOption.IGNORE_CASE)
    private val HTML_COMMENT_REGEX = Regex("""<!--[\s\S]*?-->""")
    private val VUE_PAIRED_TAG_REGEX = Regex("""<([A-Z][A-Za-z0-9_-]*)(?:\s+[^>]*?)?>[\s\S]*?<\/\1>""")
    private val VUE_SELF_CLOSING_REGEX = Regex("""<[A-Z][A-Za-z0-9_-]*(?:\s+[^>]*?)?\s*\/?>""")
    private val CONTAINER_START_REGEX = Regex("""^:::\s*([a-zA-Z0-9_-]+)(?:\s+(.*))?$""")
    private val CONTAINER_END_REGEX = Regex("""^:::\s*$""")

    /**
     * Cleans raw FMHY markdown for in-app reading:
     * - Strips YAML front matter
     * - Strips <script setup> and <script> blocks
     * - Strips HTML comments
     * - Strips Vue components (<Component .../>, <Component>...</Component>)
     * - Converts ::: container fences to blockquotes (preserving inner text; ::: tip X -> blockquote with **X**)
     */
    fun clean(raw: String): String {
        var text = stripFrontMatter(raw)
        text = stripScripts(text)
        text = stripHtmlComments(text)
        text = stripVueComponents(text)
        text = transformContainers(text)
        return text.trim()
    }

    fun stripFrontMatter(raw: String): String {
        val s = raw.trimStart('\uFEFF')
        val regex = Regex("""^---\r?\n[\s\S]*?\r?\n---\r?\n?""")
        return regex.replace(s, "")
    }

    fun extractFrontMatter(raw: String): Map<String, String> {
        val s = raw.trimStart('\uFEFF')
        val regex = Regex("""^---\r?\n([\s\S]*?)\r?\n---""")
        val match = regex.find(s) ?: return emptyMap()
        val content = match.groupValues[1]
        val result = mutableMapOf<String, String>()
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val colon = trimmed.indexOf(':')
            if (colon != -1) {
                val key = trimmed.substring(0, colon).trim()
                var value = trimmed.substring(colon + 1).trim()
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))
                ) {
                    if (value.length >= 2) {
                        value = value.substring(1, value.length - 1)
                    }
                }
                result[key] = value
            }
        }
        return result
    }

    fun stripScripts(text: String): String =
        SCRIPT_REGEX.replace(text, "")

    fun stripHtmlComments(text: String): String =
        HTML_COMMENT_REGEX.replace(text, "")

    fun stripVueComponents(text: String): String {
        var s = text
        var prev: String
        do {
            prev = s
            s = VUE_PAIRED_TAG_REGEX.replace(s, "")
        } while (s != prev)
        return VUE_SELF_CLOSING_REGEX.replace(s, "")
    }

    fun transformContainers(text: String): String {
        val lines = text.lines()
        val result = ArrayList<String>(lines.size)
        var containerDepth = 0

        for (line in lines) {
            val trimmed = line.trim()
            val startMatch = CONTAINER_START_REGEX.matchEntire(trimmed)
            if (startMatch != null && !trimmed.equals(":::", ignoreCase = true)) {
                containerDepth++
                val prefix = "> ".repeat(containerDepth)
                val title = startMatch.groupValues[2].trim()
                if (title.isNotEmpty()) {
                    result.add("${prefix}**$title**")
                    result.add(prefix.trimEnd())
                }
            } else if (CONTAINER_END_REGEX.matches(trimmed)) {
                if (containerDepth > 0) {
                    containerDepth--
                }
            } else if (containerDepth > 0) {
                val prefix = "> ".repeat(containerDepth)
                if (line.isBlank()) {
                    result.add(prefix.trimEnd())
                } else {
                    result.add("$prefix$line")
                }
            } else {
                result.add(line)
            }
        }
        return result.joinToString("\n")
    }
}
