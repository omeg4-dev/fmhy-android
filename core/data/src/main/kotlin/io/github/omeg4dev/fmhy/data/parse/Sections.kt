package io.github.omeg4dev.fmhy.data.parse

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object Sections {
    val SIDEBAR_ORDER = listOf(
        "privacy", "ai", "video", "audio", "gaming", "reading",
        "downloading", "torrenting", "educational", "mobile",
        "linux-macos", "non-english", "misc", "system-tools",
        "file-tools", "internet-tools", "social-media-tools",
        "text-tools", "gaming-tools", "image-tools", "video-tools",
        "developer-tools", "storage"
    )

    private val EXCLUDED = setOf(
        "index.md",
        "posts.md",
        "feedback.md",
        "sandbox.md",
        "startpage.md",
        "unsafe.md",
        "beginners-guide.md",
        "readme.md",
        "recently-removed.md",
        "base64.md"
    )

    private val ENTRY_RE = Regex("""['"]([^'"]+\.md)['"]\s*:\s*\{([^}]+)\}""")
    private val TITLE_RE = Regex("""title\s*:\s*['"]([^'"]*)['"]""")
    private val DESC_RE = Regex("""description\s*:\s*['"]([^'"]*)['"]""")

    /**
     * Section list from the upstream metadata. Current upstream keeps it in
     * `docs/.vitepress/transformer/headers.json`; older revisions had it as an object literal
     * in `constants.ts`. Both are accepted, JSON first.
     */
    fun parse(text: String): List<SectionInfo> =
        parseJson(text) ?: parseTs(text)

    private fun parseJson(text: String): List<SectionInfo>? {
        if (!text.trimStart().startsWith("{")) return null
        val root = try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return null
        val entries = root.entries.filter { it.key.endsWith(".md") && it.value is JsonObject }
        if (entries.isEmpty()) return null
        val result = mutableListOf<SectionInfo>()
        var ordVal = 0
        for ((fname, value) in root.entries) {
            if (!fname.endsWith(".md")) continue
            val body = value as? JsonObject ?: continue
            if (fname.lowercase() !in EXCLUDED) {
                result.add(
                    SectionInfo(
                        id = fname.removeSuffix(".md"),
                        title = body["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        description = body["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        file = fname,
                        ord = ordVal,
                    )
                )
            }
            ordVal++
        }
        return result
    }

    private fun parseTs(constantsTs: String): List<SectionInfo> {
        val result = mutableListOf<SectionInfo>()
        var ordVal = 0

        for (m in ENTRY_RE.findAll(constantsTs)) {
            val fname = m.groupValues[1]
            val body = m.groupValues[2]
            val title = TITLE_RE.find(body)?.groupValues?.get(1).orEmpty()
            val desc = DESC_RE.find(body)?.groupValues?.get(1).orEmpty()

            if (fname.lowercase() !in EXCLUDED) {
                result.add(
                    SectionInfo(
                        id = fname.removeSuffix(".md"),
                        title = title,
                        description = desc,
                        file = fname,
                        ord = ordVal
                    )
                )
            }
            ordVal++
        }

        return result
    }
}
