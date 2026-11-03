package io.github.omeg4dev.fmhy.data.sync

import io.github.omeg4dev.fmhy.data.parse.Sections
import java.net.URL
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live canary: the section list upstream must still be readable by [Sections.parse].
 * When FMHY moves or reshapes it (constants.ts -> headers.json once emptied every install),
 * this fails before a release instead of after. Run it as part of the release gate.
 */
class UpstreamSectionsCanaryTest {
    @Test
    fun liveSectionListIsReadable() {
        val url = "https://raw.githubusercontent.com/fmhy/edit/main/docs/.vitepress/transformer/headers.json"
        val text = URL(url).readText()
        val sections = Sections.parse(text)
        val ids = sections.map { it.id }.toSet()
        assertTrue("only ${sections.size} sections parsed from $url", sections.size >= 20)
        for (expected in listOf("privacy", "video", "audio")) {
            assertTrue("section '$expected' missing, got $ids", expected in ids)
        }
    }
}
