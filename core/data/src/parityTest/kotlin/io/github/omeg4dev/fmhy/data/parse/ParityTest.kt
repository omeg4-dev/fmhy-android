package io.github.omeg4dev.fmhy.data.parse

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@Serializable
data class JsonSectionDoc(
    val section: JsonSection,
    val headings: List<JsonHeading>,
    val entries: List<JsonEntry>,
    val notes: List<JsonNote>,
    val unparsed: Int
)

@Serializable
data class JsonSection(
    val id: String,
    val title: String,
    val description: String,
    val file: String,
    val ord: Int
)

@Serializable
data class JsonHeading(
    val id: String,
    val parent_id: String? = null,
    val level: Int,
    val title: String,
    val anchor: String,
    val ord: Int
)

@Serializable
data class JsonEntry(
    val id: String,
    val name: String,
    val url: String,
    val original_url: String? = null,
    val is_base64: Int,
    val tier: Int,
    val kind: Int = 0,
    val is_inline_more: Int,
    val mirrors: List<JsonLink> = emptyList(),
    val extra_links: List<JsonLink> = emptyList(),
    val description: String = "",
    val tags: String = "",
    val heading_id: String? = null,
    val heading_path: String = "",
    val ord: Int
)

@Serializable
data class JsonLink(
    val label: String,
    val url: String
)

@Serializable
data class JsonNote(
    val heading_id: String? = null,
    val ord: Int,
    val text_md: String
)

class ParityTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testParityAgainstReference() {
        val cacheDir = File(".cache/parity")
        if (!cacheDir.exists() || !cacheDir.isDirectory) {
            throw AssertionError("Missing .cache/parity directory at: ${cacheDir.absolutePath}")
        }

        val inputsDir = File(cacheDir, "inputs")
        val constantsFile = File(inputsDir, "constants.ts")
        val rentryFile = File(inputsDir, "rentry-FMHYB64.html")
        val indexFile = File(cacheDir, "index.json")

        assertTrue("inputs/constants.ts must exist", constantsFile.exists())
        assertTrue("inputs/rentry-FMHYB64.html must exist", rentryFile.exists())
        assertTrue("index.json must exist", indexFile.exists())

        val indexJson = indexFile.readText(Charsets.UTF_8)
        val expectedSectionIds = json.decodeFromString<List<String>>(indexJson)

        val constantsTs = constantsFile.readText(Charsets.UTF_8)
        val parsedCandidates = Sections.parse(constantsTs)
        val sections = parsedCandidates.filter { File(inputsDir, it.file).exists() }

        assertEquals("Section count mismatch", expectedSectionIds.size, sections.size)
        for (i in sections.indices) {
            val sec = sections[i]
            val expectedId = expectedSectionIds[i]
            assertEquals("Section id mismatch at $i", expectedId, sec.id)

            val refFile = File(cacheDir, "${sec.id}.json")
            val refDoc = json.decodeFromString<JsonSectionDoc>(refFile.readText(Charsets.UTF_8))
            assertEquals("Section title mismatch for ${sec.id}", refDoc.section.title, sec.title)
            assertEquals("Section ord mismatch for ${sec.id}", refDoc.section.ord, sec.ord)
            assertEquals("Section file mismatch for ${sec.id}", refDoc.section.file, sec.file)
        }

        val rentryHtml = rentryFile.readText(Charsets.UTF_8)
        val b64Map = Base64Map(rentryHtml)

        // Pre-load markdowns for clean timing & testing
        val markdownMap = sections.associateWith { sec ->
            File(inputsDir, sec.file).readText(Charsets.UTF_8)
        }

        // Warm-up pass
        for (sec in sections) {
            FmhyParser.parseSection(sec, markdownMap.getValue(sec), b64Map)
        }

        // Timed pass
        val startNano = System.nanoTime()
        val parsedSections = sections.map { sec ->
            FmhyParser.parseSection(sec, markdownMap.getValue(sec), b64Map)
        }
        val elapsedMs = (System.nanoTime() - startNano) / 1_000_000

        val mismatches = mutableListOf<String>()
        var totalEntries = 0
        var totalHeadings = 0
        var totalNotes = 0

        println("\n=== PARITY TEST SUMMARY ===")
        for (i in sections.indices) {
            val sec = sections[i]
            val parsed = parsedSections[i]
            val refFile = File(cacheDir, "${sec.id}.json")
            val refDoc = json.decodeFromString<JsonSectionDoc>(refFile.readText(Charsets.UTF_8))

            val secMismatches = mutableListOf<String>()

            // Compare section
            if (parsed.section.id != refDoc.section.id) {
                secMismatches.add("${sec.id} / -1 / section.id: expected ${refDoc.section.id} got ${parsed.section.id}")
            }
            if (parsed.section.title != refDoc.section.title) {
                secMismatches.add("${sec.id} / -1 / section.title: expected ${refDoc.section.title} got ${parsed.section.title}")
            }
            if (parsed.section.ord != refDoc.section.ord) {
                secMismatches.add("${sec.id} / -1 / section.ord: expected ${refDoc.section.ord} got ${parsed.section.ord}")
            }

            // Compare unparsed
            if (parsed.unparsedLines != refDoc.unparsed) {
                secMismatches.add("${sec.id} / -1 / unparsed: expected ${refDoc.unparsed} got ${parsed.unparsedLines}")
            }

            // Compare headings
            if (parsed.headings.size != refDoc.headings.size) {
                secMismatches.add("${sec.id} / -1 / headings.size: expected ${refDoc.headings.size} got ${parsed.headings.size}")
            }
            val headingLimit = minOf(parsed.headings.size, refDoc.headings.size)
            for (hIdx in 0 until headingLimit) {
                val act = parsed.headings[hIdx]
                val exp = refDoc.headings[hIdx]
                if (act.id != exp.id) {
                    secMismatches.add("${sec.id} / ${act.ord} / heading.id: expected ${exp.id} got ${act.id}")
                }
                if (act.parentId != exp.parent_id) {
                    secMismatches.add("${sec.id} / ${act.ord} / heading.parentId: expected ${exp.parent_id} got ${act.parentId}")
                }
                if (act.level != exp.level) {
                    secMismatches.add("${sec.id} / ${act.ord} / heading.level: expected ${exp.level} got ${act.level}")
                }
                if (act.title != exp.title) {
                    secMismatches.add("${sec.id} / ${act.ord} / heading.title: expected ${exp.title} got ${act.title}")
                }
                if (act.anchor != exp.anchor) {
                    secMismatches.add("${sec.id} / ${act.ord} / heading.anchor: expected ${exp.anchor} got ${act.anchor}")
                }
                if (act.ord != exp.ord) {
                    secMismatches.add("${sec.id} / ${act.ord} / heading.ord: expected ${exp.ord} got ${act.ord}")
                }
            }

            // Compare entries
            if (parsed.entries.size != refDoc.entries.size) {
                secMismatches.add("${sec.id} / -1 / entries.size: expected ${refDoc.entries.size} got ${parsed.entries.size}")
            }
            val entryLimit = minOf(parsed.entries.size, refDoc.entries.size)
            for (eIdx in 0 until entryLimit) {
                val act = parsed.entries[eIdx]
                val exp = refDoc.entries[eIdx]

                if (act.id != exp.id) {
                    secMismatches.add("${sec.id} / ${act.ord} / entry.id: expected ${exp.id} got ${act.id} (name: ${act.name})")
                }
                if (act.name != exp.name) {
                    secMismatches.add("${sec.id} / ${act.ord} / entry.name: expected ${exp.name} got ${act.name}")
                }
                if (act.url != exp.url) {
                    secMismatches.add("${sec.id} / ${act.ord} / entry.url: expected ${exp.url} got ${act.url}")
                }
                if (act.originalUrl != exp.original_url) {
                    secMismatches.add("${sec.id} / ${act.ord} / entry.originalUrl: expected ${exp.original_url} got ${act.originalUrl}")
                }
                val actB64 = if (act.isBase64) 1 else 0
                if (actB64 != exp.is_base64) {
                    secMismatches.add("${sec.id} / ${act.ord} / entry.isBase64: expected ${exp.is_base64} got $actB64")
                }
                if (act.tier != exp.tier) {
                    secMismatches.add("${sec.id} / ${act.ord} / entry.tier: expected ${exp.tier} got ${act.tier}")
                }
                if (act.kind != exp.kind) {
                    secMismatches.add("${sec.id} / ${act.ord} / entry.kind: expected ${exp.kind} got ${act.kind}")
                }
                val actMore = if (act.isInlineMore) 1 else 0
                if (actMore != exp.is_inline_more) {
                    secMismatches.add("${sec.id} / ${act.ord} / entry.isInlineMore: expected ${exp.is_inline_more} got $actMore")
                }
                if (act.description != exp.description) {
                    secMismatches.add("${sec.id} / ${act.ord} / entry.description: expected '${exp.description}' got '${act.description}'")
                }
                if (act.tags != exp.tags) {
                    secMismatches.add("${sec.id} / ${act.ord} / entry.tags: expected '${exp.tags}' got '${act.tags}'")
                }
                if (act.headingId != exp.heading_id) {
                    secMismatches.add("${sec.id} / ${act.ord} / entry.headingId: expected ${exp.heading_id} got ${act.headingId}")
                }
                if (act.headingPath != exp.heading_path) {
                    secMismatches.add("${sec.id} / ${act.ord} / entry.headingPath: expected '${exp.heading_path}' got '${act.headingPath}'")
                }
                if (act.ord != exp.ord) {
                    secMismatches.add("${sec.id} / ${act.ord} / entry.ord: expected ${exp.ord} got ${act.ord}")
                }

                val actMirrors = act.mirrors.map { JsonLink(it.label, it.url) }
                if (actMirrors != exp.mirrors) {
                    secMismatches.add("${sec.id} / ${act.ord} / entry.mirrors: expected ${exp.mirrors} got $actMirrors")
                }

                val actExtra = act.extraLinks.map { JsonLink(it.label, it.url) }
                if (actExtra != exp.extra_links) {
                    secMismatches.add("${sec.id} / ${act.ord} / entry.extraLinks: expected ${exp.extra_links} got $actExtra")
                }
            }

            // Compare notes
            if (parsed.notes.size != refDoc.notes.size) {
                secMismatches.add("${sec.id} / -1 / notes.size: expected ${refDoc.notes.size} got ${parsed.notes.size}")
            }
            val noteLimit = minOf(parsed.notes.size, refDoc.notes.size)
            for (nIdx in 0 until noteLimit) {
                val act = parsed.notes[nIdx]
                val exp = refDoc.notes[nIdx]
                if (act.headingId != exp.heading_id) {
                    secMismatches.add("${sec.id} / ${act.ord} / note.headingId: expected ${exp.heading_id} got ${act.headingId}")
                }
                if (act.ord != exp.ord) {
                    secMismatches.add("${sec.id} / ${act.ord} / note.ord: expected ${exp.ord} got ${act.ord}")
                }
                if (act.textMd != exp.text_md) {
                    secMismatches.add("${sec.id} / ${act.ord} / note.textMd: expected '${exp.text_md}' got '${act.textMd}'")
                }
            }

            totalEntries += parsed.entries.size
            totalHeadings += parsed.headings.size
            totalNotes += parsed.notes.size
            mismatches.addAll(secMismatches)

            val status = if (secMismatches.isEmpty()) "OK" else "MISMATCH (${secMismatches.size})"
            println("Section ${sec.id.padEnd(20)}: ${parsed.entries.size.toString().padStart(5)} entries, ${parsed.headings.size.toString().padStart(4)} headings, ${parsed.notes.size.toString().padStart(2)} notes, unparsed=${parsed.unparsedLines} -> $status")
        }

        println("\n=== SUMMARY TOTALS ===")
        println("Sections:     ${sections.size}")
        println("Entries:      $totalEntries")
        println("Headings:     $totalHeadings")
        println("Notes:        $totalNotes")
        println("Mismatches:   ${mismatches.size}")
        println("Parse time:   $elapsedMs ms")

        if (mismatches.isNotEmpty()) {
            println("\nFirst 30 mismatches:")
            mismatches.take(30).forEach { println("  $it") }
        }

        assertEquals("Total mismatches must be 0", 0, mismatches.size)
        assertEquals("Total sections must be 23", 23, sections.size)
        assertEquals("Total entries must be 21665", 21665, totalEntries)
        assertTrue("Parse time must be < 500 ms (was ${elapsedMs}ms)", elapsedMs < 500)
    }
}
