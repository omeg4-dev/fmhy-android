package io.github.omeg4dev.fmhy.data.tracker

import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

@Serializable
private data class UpdatedPairCase(
    val description: String,
    val oldLine: String,
    val newLine: String,
    val expectedKinds: List<String>,
)

class ChangeClassifierTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testUpdatedPairsFromFixture() {
        val stream = ChangeClassifierTest::class.java.getResourceAsStream("/tracker/updated-pairs.json")
            ?: error("Missing /tracker/updated-pairs.json resource")
        val jsonText = stream.bufferedReader().use { it.readText() }
        val cases = json.decodeFromString<List<UpdatedPairCase>>(jsonText)

        assertTrue("Expected at least 12 fixture cases, found ${cases.size}", cases.size >= 12)

        for (c in cases) {
            val diff = ChangeClassifier.classify(c.oldLine, c.newLine)
            val actualKinds = diff.kinds.map { it.value }.sorted()
            val expectedKinds = c.expectedKinds.sorted()
            assertEquals("Failed for case: ${c.description}", expectedKinds, actualKinds)
        }
    }

    @Test
    fun testDescriptionOf() {
        val line1 = "* ⭐ **[Streamo](https://streamo.pro/)** - Movies / TV / Anime / [Discord](https://discord.gg/5pujSb9mh)"
        assertEquals("- Movies / TV / Anime / [Discord](https://discord.gg/5pujSb9mh)", ChangeClassifier.descriptionOf(line1))

        val line2 = "* [Streamo](https://streamo.pro/) - Movies / TV / Anime / [Discord](https://discord.gg/5pujSb9mh)"
        assertEquals("- Movies / TV / Anime / [Discord](https://discord.gg/5pujSb9mh)", ChangeClassifier.descriptionOf(line2))

        val line3 = "* [Streamo](https://streamo.pro/)"
        assertEquals("", ChangeClassifier.descriptionOf(line3))

        val line4 = "* ⭐ **[MixesDB](https://www.mixesdb.com/)** - DJ Mixes / Livesets / [Essentials List](https://rentry.co/dancemixbible/)"
        val line5 = "* [MixesDB](https://www.mixesdb.com/) - DJ Mixes / Livesets / [Essentials List](https://rentry.co/dancemixbible/)"
        assertEquals(ChangeClassifier.descriptionOf(line4), ChangeClassifier.descriptionOf(line5))
    }

    @Test
    fun testFullMonthlyCommitsClassificationCounts() {
        val realJsonFile = sequenceOf(
            File(".cache/ref/tracker/monthly_commits.json"),
            File("../.cache/ref/tracker/monthly_commits.json"),
            File("../../.cache/ref/tracker/monthly_commits.json")
        ).firstOrNull { it.exists() }

        assumeTrue(realJsonFile != null && realJsonFile.exists())
        val content = realJsonFile!!.readText()

        val trackerCommits = json.decodeFromString<TrackerPayloadDto>(content).commits

        val kindCounts = mutableMapOf<ChangeKind, Int>()
        var editedOnlyCount = 0
        var totalUpdated = 0

        for (commit in trackerCommits) {
            for (item in commit.added) {
                if (item.type == "updated" && item.relatedLink != null) {
                    totalUpdated++
                    val diff = ChangeClassifier.classify(item.relatedLink, item.link)
                    for (kind in diff.kinds) {
                        kindCounts[kind] = (kindCounts[kind] ?: 0) + 1
                    }
                    if (diff.kinds == setOf(ChangeKind.EDITED)) {
                        editedOnlyCount++
                    }
                }
            }
        }

        val renamedCount = kindCounts[ChangeKind.RENAMED] ?: 0
        val urlChangedCount = kindCounts[ChangeKind.URL_CHANGED] ?: 0
        val starredCount = kindCounts[ChangeKind.STARRED] ?: 0
        val unstarredCount = kindCounts[ChangeKind.UNSTARRED] ?: 0
        val editedCount = kindCounts[ChangeKind.EDITED] ?: 0

        println("\n=== MONTHLY COMMITS CLASSIFIER STATS ===")
        println("Total updated items: $totalUpdated")
        println("RENAMED: $renamedCount")
        println("URL_CHANGED: $urlChangedCount")
        println("STARRED: $starredCount")
        println("UNSTARRED: $unstarredCount")
        println("STARRED+UNSTARRED: ${starredCount + unstarredCount}")
        println("EDITED: $editedCount")
        println("EDITED_ONLY: $editedOnlyCount")
        println("=========================================\n")

        assertTrue("RENAMED should be between 5 and 20, was $renamedCount", renamedCount in 5..20)
        assertTrue("URL_CHANGED should be between 50 and 100, was $urlChangedCount", urlChangedCount in 50..100)

        // Find and print moves and same-source re-adds
        data class FlatItem(
            val sha: String,
            val date: String,
            val source: String,
            val link: String,
            val normUrl: String,
        )

        val allNew = mutableListOf<FlatItem>()
        val allRemoved = mutableListOf<FlatItem>()

        for (commit in trackerCommits.sortedBy { it.date }) {
            for (a in commit.added) {
                if (a.type == "new") {
                    val p = ChangeClassifier.parseLinkLine(a.link)
                    if (p != null) {
                        allNew.add(FlatItem(commit.sha, commit.date, a.source, a.link, DatasetStore.normalizeEntryUrl(p.url)))
                    }
                }
            }
            for (r in commit.removed) {
                val p = ChangeClassifier.parseLinkLine(r.link)
                if (p != null) {
                    allRemoved.add(FlatItem(commit.sha, commit.date, r.source, r.link, DatasetStore.normalizeEntryUrl(p.url)))
                }
            }
        }

        val pairedNew = mutableSetOf<Int>()
        var moveCount = 0
        var readdCount = 0
        val moveLogs = mutableListOf<String>()
        val readdLogs = mutableListOf<String>()

        for (rem in allRemoved) {
            val newIdx = allNew.indices.firstOrNull { idx ->
                idx !in pairedNew && allNew[idx].normUrl == rem.normUrl && allNew[idx].date >= rem.date
            }
            if (newIdx != null) {
                pairedNew.add(newIdx)
                val nw = allNew[newIdx]
                if (rem.source != nw.source) {
                    moveCount++
                    moveLogs.add("${rem.source} -> ${nw.source} (${nw.normUrl})")
                } else {
                    readdCount++
                    readdLogs.add("${nw.source} (${nw.normUrl})")
                }
            }
        }

        println("=== MOVES FOUND: $moveCount ===")
        moveLogs.forEach { println("  $it") }
        println("=== RE-ADDS FOUND: $readdCount ===")
        readdLogs.take(10).forEach { println("  $it") }
        if (readdLogs.size > 10) println("  ... and ${readdLogs.size - 10} more")
        println("===============================\n")
    }
}

@Serializable
private data class TrackerPayloadDto(
    val commits: List<TrackerCommitDto> = emptyList(),
)

@Serializable
private data class TrackerCommitDto(
    val sha: String,
    val date: String,
    val added: List<TrackerLinkDto> = emptyList(),
    val removed: List<TrackerLinkDto> = emptyList(),
)

@Serializable
private data class TrackerLinkDto(
    val link: String,
    val source: String,
    val type: String,
    val relatedLink: String? = null,
)
