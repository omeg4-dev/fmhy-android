package io.github.omeg4dev.fmhy

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SectionNavKeySerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testSerializationWithoutFocusEntryId() {
        val legacyJson = """{"sectionId":"video","anchor":"streaming"}"""
        val key = json.decodeFromString<SectionNavKey>(legacyJson)
        assertEquals("video", key.sectionId)
        assertEquals("streaming", key.anchor)
        assertNull(key.focusEntryId)
    }

    @Test
    fun testSerializationWithFocusEntryId() {
        val jsonStr = """{"sectionId":"video","anchor":"streaming","focusEntryId":"entry_42"}"""
        val key = json.decodeFromString<SectionNavKey>(jsonStr)
        assertEquals("video", key.sectionId)
        assertEquals("streaming", key.anchor)
        assertEquals("entry_42", key.focusEntryId)
    }

    @Test
    fun testRoundTrip() {
        val keyWithFocus = SectionNavKey(sectionId = "audio", anchor = "music", focusEntryId = "entry_99")
        val serialized = json.encodeToString(keyWithFocus)
        val deserialized = json.decodeFromString<SectionNavKey>(serialized)
        assertEquals(keyWithFocus, deserialized)

        val keyWithoutFocus = SectionNavKey(sectionId = "gaming")
        val serializedNoFocus = json.encodeToString(keyWithoutFocus)
        val deserializedNoFocus = json.decodeFromString<SectionNavKey>(serializedNoFocus)
        assertEquals(keyWithoutFocus, deserializedNoFocus)
        assertNull(deserializedNoFocus.focusEntryId)
    }
}
