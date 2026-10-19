package io.github.omeg4dev.fmhy.data.parse

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

    fun parse(constantsTs: String): List<SectionInfo> {
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
