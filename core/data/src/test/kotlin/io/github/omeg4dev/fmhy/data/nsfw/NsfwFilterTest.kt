package io.github.omeg4dev.fmhy.data.nsfw

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NsfwFilterTest {

    @Test
    fun invisibleCharactersCannotSlipPastTheFilter() {
        // The parser strips some invisible characters before display, so the filter must ignore them too.
        val md = listOf(
            "## Streaming",
            "* [Clean Stream](https://clean-stream.example/watch) - Clean description",
            "* [Zero Width Le\u200Bak](https://one.example) - Normal",
            "* [Soft Hyphen Le\u00ADak](https://two.example) - Normal",
            "",
            "\uFEFF## Cam Models",
            "* [Cam Portal](https://cam-portal.example/live) - Live cams",
        ).joinToString("\n")

        val filtered = NsfwFilter.filter(md)

        assertTrue(filtered.contains("Clean Stream"))
        assertFalse(filtered.contains("one.example"))
        assertFalse(filtered.contains("two.example"))
        assertFalse(filtered.contains("Cam Portal"))
    }

    @Test
    fun dropsLeakAndCamModelsCategoriesCompletely() {
        val md = """
            ## Streaming
            * [Clean Stream](https://clean-stream.example/watch) - Clean description

            ## Celebrity Leaks
            > Note inside dropped category
            * [Bad Leak Site](https://bad-leak-host.example/one) - Some entry
            ### Subcategory Under Leaks
            * [Sub Entry](https://sub-dropped.example/two) - Another entry

            ## 📷 Cam Models
            * [Cam Portal](https://cam-portal.example/live) - Live cams

            ## Cam Advice
            * [Safe Guide](https://safe-guide.example/help) - Kept because title does not equal cam models
        """.trimIndent()

        val filtered = NsfwFilter.filter(md)

        assertTrue(filtered.contains("## Streaming"))
        assertTrue(filtered.contains("Clean Stream"))
        assertTrue(filtered.contains("## Cam Advice"))
        assertTrue(filtered.contains("Safe Guide"))

        assertFalse(filtered.contains("Celebrity Leaks"))
        assertFalse(filtered.contains("Subcategory Under Leaks"))
        assertFalse(filtered.contains("Bad Leak Site"))
        assertFalse(filtered.contains("Sub Entry"))
        assertFalse(filtered.contains("Cam Models"))
        assertFalse(filtered.contains("Cam Portal"))
    }

    @Test
    fun dropsLinesContainingAnyBannedKeywordAcrossFieldsAndAlternatives() {
        val terms = listOf(
            "leak", "coomer", "kemono", "onlyhaven", "pawchive", "bakemono",
            "onlyfans", "fansly", "thot", "camrip", "cam rip", "siterip",
            "site rip", "cyberdrop", "fanripper", "of-scraper", "pornfaceid", "pornstarbyface",
        )

        val sb = StringBuilder("## Mixed\n")
        sb.appendLine("* [Safe Primary](https://safe-primary.example) - Kept entry")
        terms.forEachIndexed { i, term ->
            when (i % 4) {
                0 -> sb.appendLine("* [Site$i $term](https://clean$i.example) - Normal desc")
                1 -> sb.appendLine("* [Site$i](https://clean$i.example) - Desc mentions ${term.uppercase()}")
                2 -> sb.appendLine("* [Site$i](https://clean$i.example) / [Alt ${term.uppercase()}](https://alt$i.example) - Normal")
                3 -> sb.appendLine("* [Site$i](https://clean$i.example) or [Alt](https://example.org/${term.replace(" ", "")}/path) - Normal")
            }
        }
        // Alternative + extra links on same line must all be dropped together
        sb.appendLine("* [KeptName](https://keptname.example) or [CoomerMirror](https://mirror.example) / [Extra](https://extra.example) - Shared desc")

        val filtered = NsfwFilter.filter(sb.toString())

        assertTrue(filtered.contains("Safe Primary"))
        assertFalse(filtered.contains("KeptName"))
        assertFalse(filtered.contains("Extra"))
        terms.indices.forEach { i ->
            assertFalse("Entry $i (${terms[i]}) must be dropped", filtered.contains("clean$i.example"))
        }
    }

    @Test
    fun dropsLinesWithPrimaryHostFromDroppedCategoryButKeepsGeneralPurposeHosts() {
        val md = """
            ## Private Leaks
            * [Leak Item](https://www.DroppedSite.example/room) - Bad
            * [Reddit Discussion](https://www.reddit.com/r/example_dropped) - Reddit link
            * [Github Repo](https://github.com/example/dropped-tool) - GitHub link
            * [Gitlab Repo](https://gitlab.com/example/tool) - GitLab link
            * [Tg Channel](https://t.me/dropped_chan) - Telegram link
            * [Tg Alt](https://telegram.me/dropped_chan2) - Telegram alt
            * [Discord Invite](https://discord.gg/dropped) - Discord invite
            * [Discord Link](https://discord.com/invite/dropped) - Discord link
            * [Script](https://greasyfork.org/scripts/123) - Userscript
            * [FMHY Link](https://fmhy.net/misc) - FMHY
            * [Rentry1](https://rentry.co/example-dropped) - Rentry co
            * [Rentry2](https://rentry.org/example-dropped) - Rentry org
            * [Paste](https://pastebin.com/raw/dropped) - Pastebin

            ## General
            * [CopiedHost](https://droppedsite.example/clean-looking) - Clean text but dropped primary host
            * [NonPrimaryHost](https://safe-primary.example) / [Mirror](https://droppedsite.example/m) - Safe primary host
            * [Safe Reddit](https://reddit.com/r/example_safe) - Kept general host
            * [Safe Github](https://github.com/example/safe-tool) - Kept general host
            * [Safe Gitlab](https://gitlab.com/example/safe) - Kept general host
            * [Safe Tme](https://t.me/safe_chan) - Kept general host
            * [Safe TgMe](https://telegram.me/safe_chan) - Kept general host
            * [Safe DiscordGg](https://discord.gg/safe) - Kept general host
            * [Safe DiscordCom](https://discord.com/invite/safe) - Kept general host
            * [Safe Greasy](https://greasyfork.org/scripts/999) - Kept general host
            * [Safe Fmhy](https://fmhy.net/video) - Kept general host
            * [Safe RentryCo](https://rentry.co/safe) - Kept general host
            * [Safe RentryOrg](https://rentry.org/safe) - Kept general host
            * [Safe Paste](https://pastebin.com/raw/safe) - Kept general host
        """.trimIndent()

        val filtered = NsfwFilter.filter(md)

        assertFalse(filtered.contains("CopiedHost"))
        assertTrue(filtered.contains("NonPrimaryHost"))
        assertTrue(filtered.contains("Safe Reddit"))
        assertTrue(filtered.contains("Safe Github"))
        assertTrue(filtered.contains("Safe Gitlab"))
        assertTrue(filtered.contains("Safe Tme"))
        assertTrue(filtered.contains("Safe TgMe"))
        assertTrue(filtered.contains("Safe DiscordGg"))
        assertTrue(filtered.contains("Safe DiscordCom"))
        assertTrue(filtered.contains("Safe Greasy"))
        assertTrue(filtered.contains("Safe Fmhy"))
        assertTrue(filtered.contains("Safe RentryCo"))
        assertTrue(filtered.contains("Safe RentryOrg"))
        assertTrue(filtered.contains("Safe Paste"))
    }

    @Test
    fun dropsTableOfContentsEmptyHeadingsAndBannedNotes() {
        val md = """
            ## Table of Contents
            * [Good Section](#good-section)
            * [Empty After Filter](#empty-after-filter)

            ## Good Section
            > Always use an adblocker.
            > Do not use cyberdrop mirrors here.
            * **Note** - Check links carefully.
            * **Note** - Avoid onlyfans scrapers.
            * [Good Site](https://good.example) - Great site

            ## Empty After Filter
            > Note in section that ends up empty
            * [Bad Only](https://bad.example) - Uses onlyfans

            ## Quitting
            > Need help quitting?
        """.trimIndent()

        val filtered = NsfwFilter.filter(md)

        assertFalse(filtered.contains("Table of Contents"))
        assertFalse(filtered.contains("#good-section"))
        assertTrue(filtered.contains("## Good Section"))
        assertTrue(filtered.contains("Always use an adblocker"))
        assertFalse(filtered.contains("cyberdrop"))
        assertTrue(filtered.contains("Check links carefully"))
        assertFalse(filtered.contains("onlyfans scrapers"))
        assertTrue(filtered.contains("Good Site"))

        assertFalse(filtered.contains("Empty After Filter"))
        assertFalse(filtered.contains("Note in section that ends up empty"))
        assertFalse(filtered.contains("## Quitting"))
        assertFalse(filtered.contains("Need help quitting?"))
    }
}
