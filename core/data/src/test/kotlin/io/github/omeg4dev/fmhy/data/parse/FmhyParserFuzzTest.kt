package io.github.omeg4dev.fmhy.data.parse

import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test

/**
 * Parser fuzz probe testing untrusted markdown inputs against invariants.
 */
class FmhyParserFuzzTest {

    data class FuzzCase(
        val index: Int,
        val seedVal: Long,
        val seedFile: String,
        val mutations: List<String>,
        val input: String,
        val result: Result<ParsedSection>,
        val isDeterministic: Boolean,
        val determinismDiff: String? = null,
    )

    enum class MutationType {
        DROP_LINES,
        DUPLICATE_LINE,
        CUT_LINE,
        INSERT_UNICODE,
        LONG_LINE,
        NEST_LISTS,
        STRAY_PIPES,
        BASE64_BLOB,
        CRLF,
        TABS,
        UNBALANCED_MARKUP,
        SCHEME_TARGETS,
    }

    companion object {
        private val UNICODE_POOL = listOf(
            "\u202E", // RTL override
            "\u200F", // RLM
            "\u200B", // zero-width space
            "\uFEFF", // BOM / zero-width no-break space
            "\u0300", // combining grave accent
            "\u0301", // combining acute accent
            "\u0308", // combining diaeresis
            "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67", // ZWJ family 👨‍👩‍👧
            "\uD83C\uDDE9\uD83C\uDDEA", // German flag 🇩🇪
            "\uD800", // Lone surrogate
        )

        private val LONG_LINE_TOKENS = listOf(
            "[", "]", "(", ")", "*", "a", "b", "c", "x", "y", "z", "1", "2", "3", "https://", " ", "/", ":", "-"
        )

        private val SCHEMES = listOf(
            "javascript:alert(1)",
            "ftp://example.com/file.zip",
            "magnet:?xt=urn:btih:xyz",
            "//host/path",
            "#anchor",
            "relative/path/page.md",
            "ttps://example.com",
            "HTTP://EXAMPLE.COM/PATH",
        )

        private val UNBALANCED_TOKENS = listOf(
            "**", "`", "[", "(", "```", "<!--", "**[", "](", "#"
        )

        val baseSeed: Long = System.getenv("FUZZ_SEED")?.toLongOrNull() ?: 20261120L

        private val fuzzSection = SectionInfo(
            id = "fuzz",
            title = "Fuzz",
            description = "",
            file = "fuzz.md",
            ord = 0,
        )

        private val b64Map = Base64Map("")

        val seeds: List<Pair<String, String>> by lazy {
            val resourceUrl = FmhyParserFuzzTest::class.java.classLoader.getResource("parse/fuzz")
                ?: error("Resource parse/fuzz not found")
            val dir = File(resourceUrl.toURI())
            val files = dir.listFiles { _, name -> name.endsWith(".md") } ?: emptyArray()
            require(files.isNotEmpty()) { "No .md seed files found in parse/fuzz" }
            files.sortedBy { it.name }.map { it.name to it.readText() }
        }

        private fun parseCatching(markdown: String): Result<ParsedSection> {
            return try {
                Result.success(FmhyParser.parseSection(fuzzSection, markdown, b64Map))
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

        private fun applyMutation(
            type: MutationType,
            text: String,
            rng: Random,
        ): Pair<String, String> {
            val lines = text.lines().toMutableList()
            return when (type) {
                MutationType.DROP_LINES -> {
                    if (lines.isEmpty()) {
                        text to "drop_0_lines"
                    } else {
                        val count = rng.nextInt(1, 21).coerceAtMost(lines.size)
                        val indicesToDrop = mutableSetOf<Int>()
                        while (indicesToDrop.size < count) {
                            indicesToDrop.add(rng.nextInt(lines.size))
                        }
                        val remaining = lines.filterIndexed { index, _ -> index !in indicesToDrop }
                        remaining.joinToString("\n") to "drop_${count}_lines"
                    }
                }
                MutationType.DUPLICATE_LINE -> {
                    if (lines.isEmpty()) {
                        text to "dup_0"
                    } else {
                        val candidates = lines.indices.filter { lines[it].length < 10000 }
                        val idx = if (candidates.isNotEmpty()) candidates[rng.nextInt(candidates.size)] else rng.nextInt(lines.size)
                        val times = rng.nextInt(1, 6)
                        val line = lines[idx]
                        repeat(times) {
                            lines.add(idx + 1, line)
                        }
                        lines.joinToString("\n") to "dup_line_${idx}_${times}x"
                    }
                }
                MutationType.CUT_LINE -> {
                    if (lines.isEmpty()) {
                        text to "cut_0"
                    } else {
                        val linkLines = lines.indices.filter { "](" in lines[it] }
                        val targetIdx = if (rng.nextBoolean() && linkLines.isNotEmpty()) {
                            linkLines[rng.nextInt(linkLines.size)]
                        } else {
                            rng.nextInt(lines.size)
                        }
                        val line = lines[targetIdx]
                        val cutPos = if ("](" in line && rng.nextBoolean()) {
                            val p = line.indexOf("](") + rng.nextInt(2)
                            p.coerceIn(0, line.length)
                        } else {
                            if (line.isEmpty()) 0 else rng.nextInt(line.length + 1)
                        }
                        lines[targetIdx] = line.substring(0, cutPos)
                        lines.joinToString("\n") to "cut_line_${targetIdx}_at_$cutPos"
                    }
                }
                MutationType.INSERT_UNICODE -> {
                    val count = rng.nextInt(1, 6)
                    val sb = StringBuilder(text)
                    repeat(count) {
                        val cp = UNICODE_POOL[rng.nextInt(UNICODE_POOL.size)]
                        val pos = if (sb.isEmpty()) 0 else rng.nextInt(sb.length + 1)
                        sb.insert(pos, cp)
                    }
                    sb.toString() to "insert_${count}_unicode"
                }
                MutationType.LONG_LINE -> {
                    val sb = StringBuilder(50000)
                    val sampleWords = listOf("word", "text", "description", "link", "section", "entry", "item", "page", "title", "name", "category", "abc", "xyz")
                    val specialTokens = listOf("[", "]", "(", ")", "*", "https://", " ")
                    while (sb.length < 50000) {
                        if (rng.nextFloat() < 0.75f) {
                            sb.append(sampleWords[rng.nextInt(sampleWords.size)])
                            sb.append(" ")
                        } else {
                            sb.append(specialTokens[rng.nextInt(specialTokens.size)])
                        }
                    }
                    val longLine = sb.substring(0, 50000)
                    val insertPos = if (lines.isEmpty()) 0 else rng.nextInt(lines.size + 1)
                    lines.add(insertPos, longLine)
                    lines.joinToString("\n") to "add_50k_line"
                }
                MutationType.NEST_LISTS -> {
                    if (lines.isEmpty()) {
                        text to "nest_0"
                    } else {
                        val count = rng.nextInt(1, 6).coerceAtMost(lines.size)
                        val spaces = listOf("  ", "    ", "        ")[rng.nextInt(3)]
                        val marker = listOf("* ", "- ", "1. ")[rng.nextInt(3)]
                        repeat(count) {
                            val idx = rng.nextInt(lines.size)
                            lines[idx] = spaces + marker + lines[idx].trimStart()
                        }
                        lines.joinToString("\n") to "nest_${count}_lines"
                    }
                }
                MutationType.STRAY_PIPES -> {
                    val fragments = listOf("|", "| a | b |", "|---|---|", "|||", "| col1 | col2 |")
                    val frag = fragments[rng.nextInt(fragments.size)]
                    if (lines.isEmpty()) {
                        frag to "stray_pipe"
                    } else {
                        val idx = rng.nextInt(lines.size)
                        lines[idx] = lines[idx] + " " + frag
                        lines.joinToString("\n") to "stray_pipe"
                    }
                }
                MutationType.BASE64_BLOB -> {
                    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
                    val len = rng.nextInt(2048, 8193)
                    val chars = CharArray(len) { alphabet[rng.nextInt(alphabet.length)] }
                    val blob = String(chars)
                    val blobLine = if (rng.nextBoolean()) {
                        "[x](https://rentry.co/FMHYB64#$blob)"
                    } else {
                        blob
                    }
                    val insertPos = if (lines.isEmpty()) 0 else rng.nextInt(lines.size + 1)
                    lines.add(insertPos, blobLine)
                    lines.joinToString("\n") to "base64_blob_${len}b"
                }
                MutationType.CRLF -> {
                    val crlfText = text.replace("\r", "").replace("\n", "\r\n")
                    crlfText to "crlf"
                }
                MutationType.TABS -> {
                    if (lines.isEmpty()) {
                        "\t" to "tabs"
                    } else {
                        val count = rng.nextInt(1, 6).coerceAtMost(lines.size)
                        repeat(count) {
                            val idx = rng.nextInt(lines.size)
                            lines[idx] = "\t" + lines[idx]
                        }
                        lines.joinToString("\n") to "tabs"
                    }
                }
                MutationType.UNBALANCED_MARKUP -> {
                    val count = rng.nextInt(1, 6)
                    val sb = StringBuilder(text)
                    repeat(count) {
                        val tok = UNBALANCED_TOKENS[rng.nextInt(UNBALANCED_TOKENS.size)]
                        val pos = if (sb.isEmpty()) 0 else rng.nextInt(sb.length + 1)
                        sb.insert(pos, tok)
                    }
                    sb.toString() to "unbalanced_${count}_tokens"
                }
                MutationType.SCHEME_TARGETS -> {
                    val scheme = SCHEMES[rng.nextInt(SCHEMES.size)]
                    val linkStr = "[fuzzLink]($scheme)"
                    val insertPos = if (lines.isEmpty()) 0 else rng.nextInt(lines.size + 1)
                    lines.add(insertPos, "* $linkStr - Description for fuzz scheme")
                    lines.joinToString("\n") to "scheme_${scheme.substringBefore(':')}"
                }
            }
        }

        val cases: List<FuzzCase> by lazy {
            val list = mutableListOf<FuzzCase>()
            val mutationTypes = MutationType.values()

            for (i in 0 until 2000) {
                val caseSeed = baseSeed + i
                val rng = Random(caseSeed)
                val (seedFile, seedContent) = seeds[rng.nextInt(seeds.size)]
                val mutationCount = rng.nextInt(1, 5)
                var currentText = seedContent
                val appliedMutations = mutableListOf<String>()

                repeat(mutationCount) {
                    val type = mutationTypes[rng.nextInt(mutationTypes.size)]
                    val (newText, desc) = applyMutation(type, currentText, rng)
                    currentText = newText
                    appliedMutations.add(desc)
                }

                val res1 = parseCatching(currentText)
                val res2 = parseCatching(currentText)
                var detDiff: String? = null
                val isDet = if (res1.isSuccess && res2.isSuccess) {
                    val p1 = res1.getOrThrow()
                    val p2 = res2.getOrThrow()
                    val eq = p1 == p2
                    if (!eq) {
                        detDiff = "p1 entries=${p1.entries.size} vs p2 entries=${p2.entries.size}"
                    }
                    eq
                } else if (res1.isFailure && res2.isFailure) {
                    val t1 = res1.exceptionOrNull()!!
                    val t2 = res2.exceptionOrNull()!!
                    val eq = t1.javaClass == t2.javaClass && t1.message == t2.message
                    if (!eq) {
                        detDiff = "t1=${t1.javaClass.name} vs t2=${t2.javaClass.name}"
                    }
                    eq
                } else {
                    detDiff = "One parse succeeded while the other failed"
                    false
                }

                list.add(
                    FuzzCase(
                        index = i,
                        seedVal = caseSeed,
                        seedFile = seedFile,
                        mutations = appliedMutations,
                        input = currentText,
                        result = res1,
                        isDeterministic = isDet,
                        determinismDiff = detDiff,
                    )
                )
            }

            // Fixed edge inputs
            val edgeInputs = listOf(
                Triple("fixed:empty", listOf("empty_string"), ""),
                Triple("fixed:headings", listOf("only_headings"), "# H1\n## H2\n### H3\n#### H4\n"),
                Triple("fixed:star", listOf("single_star"), "*"),
            )

            for ((idx, edge) in edgeInputs.withIndex()) {
                val (name, muts, input) = edge
                val res1 = parseCatching(input)
                val res2 = parseCatching(input)
                var detDiff: String? = null
                val isDet = if (res1.isSuccess && res2.isSuccess) {
                    val p1 = res1.getOrThrow()
                    val p2 = res2.getOrThrow()
                    val eq = p1 == p2
                    if (!eq) {
                        detDiff = "p1 entries=${p1.entries.size} vs p2 entries=${p2.entries.size}"
                    }
                    eq
                } else if (res1.isFailure && res2.isFailure) {
                    val t1 = res1.exceptionOrNull()!!
                    val t2 = res2.exceptionOrNull()!!
                    val eq = t1.javaClass == t2.javaClass && t1.message == t2.message
                    if (!eq) {
                        detDiff = "t1=${t1.javaClass.name} vs t2=${t2.javaClass.name}"
                    }
                    eq
                } else {
                    detDiff = "One parse succeeded while the other failed"
                    false
                }

                list.add(
                    FuzzCase(
                        index = 2000 + idx,
                        seedVal = baseSeed,
                        seedFile = name,
                        mutations = muts,
                        input = input,
                        result = res1,
                        isDeterministic = isDet,
                        determinismDiff = detDiff,
                    )
                )
            }

            list
        }

        private fun snippetAround(text: String, lineNum: Int, maxChars: Int = 300): String {
            val lines = text.lines()
            val targetIdx = (lineNum - 1).coerceIn(0, maxOf(0, lines.size - 1))
            val startIdx = maxOf(0, targetIdx - 2)
            val endIdx = minOf(lines.size, targetIdx + 3)
            val chunk = lines.subList(startIdx, endIdx).joinToString("\n")
            return if (chunk.length <= maxChars) chunk else chunk.take(maxChars) + "..."
        }

        private fun formatFailure(
            invariant: String,
            c: FuzzCase,
            detail: String,
            offendingLine: Int = 1,
        ): String {
            val snippet = snippetAround(c.input, offendingLine)
            return buildString {
                appendLine("Invariant $invariant failed for case ${c.index} (seed=${c.seedVal}, file=${c.seedFile}):")
                appendLine("  Detail: $detail")
                appendLine("  Mutations: ${c.mutations.joinToString(", ")}")
                appendLine("  Snippet around line $offendingLine:")
                appendLine("    " + snippet.replace("\n", "\n    "))
                appendLine("  reproduce: FUZZ_SEED=$baseSeed tools/testq :core:data:test --tests '*FmhyParserFuzzTest'")
            }
        }

        private fun countSubstring(s: String, sub: String): Int {
            var count = 0
            var idx = 0
            while (true) {
                idx = s.indexOf(sub, idx)
                if (idx == -1) break
                count++
                idx += sub.length
            }
            return count
        }

        private fun countSubstringIgnoreCase(s: String, sub: String): Int {
            var count = 0
            var idx = 0
            while (true) {
                idx = s.indexOf(sub, idx, ignoreCase = true)
                if (idx == -1) break
                count++
                idx += sub.length
            }
            return count
        }

        private fun isHttp(url: String): Boolean {
            return url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
        }
    }

    @Test
    fun neverThrows() {
        val failing = cases.filter { it.result.isFailure }
        if (failing.isNotEmpty()) {
            val report = failing.take(5).joinToString("\n---\n") { c ->
                val err = c.result.exceptionOrNull()!!
                formatFailure("neverThrows", c, "${err.javaClass.name}: ${err.message}")
            }
            fail("neverThrows failed for ${failing.size} cases:\n$report")
        }
    }

    @Ignore("hunt finding: parser does not drop non-http schemes")
    @Test
    fun linksHaveHttpScheme() {
        data class Violation(val c: FuzzCase, val detail: String, val line: Int)
        val failing = mutableListOf<Violation>()
        for (c in cases) {
            val parsed = c.result.getOrNull() ?: continue
            for (entry in parsed.entries) {
                if (!isHttp(entry.url)) {
                    failing.add(Violation(c, "entry.url '${entry.url}' has non-http scheme at line ${entry.line}", entry.line))
                    break
                }
                val badMirror = entry.mirrors.firstOrNull { !isHttp(it.url) }
                if (badMirror != null) {
                    failing.add(Violation(c, "mirror.url '${badMirror.url}' has non-http scheme at line ${entry.line}", entry.line))
                    break
                }
                val badExtra = entry.extraLinks.firstOrNull { !isHttp(it.url) }
                if (badExtra != null) {
                    failing.add(Violation(c, "extraLink.url '${badExtra.url}' has non-http scheme at line ${entry.line}", entry.line))
                    break
                }
            }
        }
        if (failing.isNotEmpty()) {
            val report = failing.take(5).joinToString("\n---\n") { v ->
                formatFailure("linksHaveHttpScheme", v.c, v.detail, v.line)
            }
            fail("linksHaveHttpScheme failed for ${failing.size} cases:\n$report")
        }
    }

    @Test
    fun entryCountBounded() {
        // Line number in FmhyParser is 1-based (from `val lineNumber = i + 1` in FmhyParser.kt).
        val failing = mutableListOf<Pair<FuzzCase, String>>()
        for (c in cases) {
            val parsed = c.result.getOrNull() ?: continue
            val bound = countSubstring(c.input, "](") + countSubstringIgnoreCase(c.input, "http")
            if (parsed.entries.size > bound) {
                failing.add(c to "entries.size (${parsed.entries.size}) > bound ($bound)")
                continue
            }
            val totalLines = c.input.lines().size
            val maxLine = maxOf(1, totalLines)
            val badLineEntry = parsed.entries.firstOrNull { it.line !in 1..maxLine }
            if (badLineEntry != null) {
                failing.add(c to "entry '${badLineEntry.name}' has line ${badLineEntry.line} outside 1..$maxLine")
            }
        }
        if (failing.isNotEmpty()) {
            val report = failing.take(5).joinToString("\n---\n") { (c, detail) ->
                formatFailure("entryCountBounded", c, detail)
            }
            fail("entryCountBounded failed for ${failing.size} cases:\n$report")
        }
    }

    @Test
    fun deterministic() {
        val failing = cases.filter { !it.isDeterministic }
        if (failing.isNotEmpty()) {
            val report = failing.take(5).joinToString("\n---\n") { c ->
                formatFailure("deterministic", c, c.determinismDiff ?: "Determinism failure")
            }
            fail("deterministic failed for ${failing.size} cases:\n$report")
        }
    }

    @Ignore("hunt finding: parser allows blank entry names for empty or emoji-only link labels")
    @Test
    fun noEmptyTitle() {
        data class Violation(val c: FuzzCase, val detail: String, val line: Int)
        val failing = mutableListOf<Violation>()
        for (c in cases) {
            val parsed = c.result.getOrNull() ?: continue
            val badEntry = parsed.entries.firstOrNull { it.name.isBlank() }
            if (badEntry != null) {
                failing.add(Violation(c, "entry at line ${badEntry.line} has blank name '${badEntry.name}' (url='${badEntry.url}')", badEntry.line))
            }
        }
        if (failing.isNotEmpty()) {
            val report = failing.take(5).joinToString("\n---\n") { v ->
                formatFailure("noEmptyTitle", v.c, v.detail, v.line)
            }
            fail("noEmptyTitle failed for ${failing.size} cases:\n$report")
        }
    }

    @Test
    fun realSeedsSatisfyInvariants() {
        for ((name, content) in seeds) {
            val parsed = try {
                FmhyParser.parseSection(fuzzSection, content, b64Map)
            } catch (t: Throwable) {
                fail("Seed $name threw exception: ${t.javaClass.name}: ${t.message}")
                return
            }

            // Invariant 2: linksHaveHttpScheme
            for (entry in parsed.entries) {
                assertTrue("Seed $name entry '${entry.name}' url '${entry.url}' must start with http(s)", isHttp(entry.url))
                for (m in entry.mirrors) {
                    assertTrue("Seed $name mirror url '${m.url}' must start with http(s)", isHttp(m.url))
                }
                for (e in entry.extraLinks) {
                    assertTrue("Seed $name extraLink url '${e.url}' must start with http(s)", isHttp(e.url))
                }
            }

            // Invariant 3: entryCountBounded
            val bound = countSubstring(content, "](") + countSubstringIgnoreCase(content, "http")
            assertTrue("Seed $name entries.size (${parsed.entries.size}) <= bound ($bound)", parsed.entries.size <= bound)
            val lineCount = maxOf(1, content.lines().size)
            for (entry in parsed.entries) {
                assertTrue("Seed $name entry line ${entry.line} in 1..$lineCount", entry.line in 1..lineCount)
            }

            // Invariant 4: deterministic
            val parsed2 = FmhyParser.parseSection(fuzzSection, content, b64Map)
            assertEquals("Seed $name deterministic parse", parsed, parsed2)

            // Invariant 5: noEmptyTitle
            for (entry in parsed.entries) {
                assertTrue("Seed $name entry at line ${entry.line} must not have blank name", entry.name.isNotBlank())
            }
        }
    }
}
