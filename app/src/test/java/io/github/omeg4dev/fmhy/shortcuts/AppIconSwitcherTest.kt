package io.github.omeg4dev.fmhy.shortcuts

import io.github.omeg4dev.fmhy.data.AppIcon
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AppIconSwitcherTest {
    @Test fun everyIconMapsToADistinctAlias() {
        val mappedAliases = AppIcon.entries.map { AppIconSwitcher.aliasFor(it) }
        assertEquals(AppIcon.entries.size, mappedAliases.toSet().size)
    }

    @Test fun aliasesEqualManifestDeclarations() {
        val manifestFile = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).first { it.exists() }

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile)
        val aliasNodes = doc.getElementsByTagName("activity-alias")
        val manifestAliases = mutableSetOf<String>()
        for (i in 0 until aliasNodes.length) {
            val element = aliasNodes.item(i) as Element
            manifestAliases.add(element.getAttribute("android:name"))
        }

        val switcherAliases = AppIcon.entries.map { AppIconSwitcher.aliasFor(it) }.toSet()
        assertEquals(manifestAliases, switcherAliases)
        assertEquals(manifestAliases, AppIconSwitcher.ALL_ALIASES.toSet())
    }

    @Test fun exactlyOneAliasIsEnabled() {
        for (icon in AppIcon.entries) {
            val states = AppIconSwitcher.desiredStates(icon)
            assertEquals(AppIconSwitcher.ALL_ALIASES.toSet(), states.keys)
            assertEquals(listOf(AppIconSwitcher.aliasFor(icon)), states.filterValues { it }.keys.toList())
        }
    }
}
