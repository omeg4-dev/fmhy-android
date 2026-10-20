package io.github.omeg4dev.fmhy.ui.posts

import org.junit.Assert.assertEquals
import org.junit.Test

class PostsListingGitHubTest {
    @Test
    fun keepsOnlyMarkdownFiles() {
        val body = """
            [
              {"name": "sept-2026.md", "type": "file"},
              {"name": "images", "type": "dir"},
              {"name": "notes.txt", "type": "file"},
              {"name": "Nov-2025.md", "type": "file"}
            ]
        """.trimIndent()
        assertEquals(listOf("sept-2026.md", "Nov-2025.md"), PostsListing.parseGitHubListing(body))
    }
}
