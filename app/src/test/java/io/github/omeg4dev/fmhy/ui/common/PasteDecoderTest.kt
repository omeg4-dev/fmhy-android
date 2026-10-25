package io.github.omeg4dev.fmhy.ui.common

import io.github.omeg4dev.fmhy.data.link.PasteResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasteDecoderTest {

    @Test
    fun linksCallsOnLinksAndTracksDecoding() = runTest {
        val gate = CompletableDeferred<PasteResult>()
        val decoder = PasteDecoder(this) { gate.await() }
        var onLinksCalled: List<String>? = null
        var onUseRawCalled = false

        decoder.decode(
            url = "https://pastebin.com/raw/test1234",
            action = PasteAction.OPEN,
            onLinks = { onLinksCalled = it },
            onUseRaw = { onUseRawCalled = true },
        )

        assertTrue(decoder.decoding)
        assertFalse(onUseRawCalled)
        assertNull(onLinksCalled)

        gate.complete(PasteResult.Links(listOf("https://link1.com", "https://link2.com")))
        advanceUntilIdle()

        assertFalse(decoder.decoding)
        assertFalse(onUseRawCalled)
        assertEquals(listOf("https://link1.com", "https://link2.com"), onLinksCalled)
        assertNull(decoder.failure)
    }

    @Test
    fun notHiddenCallsOnUseRaw() = runTest {
        val decoder = PasteDecoder(this) { PasteResult.NotHidden }
        var onLinksCalled = false
        var onUseRawCalled = false

        decoder.decode(
            url = "https://example.com",
            action = PasteAction.OPEN,
            onLinks = { onLinksCalled = true },
            onUseRaw = { onUseRawCalled = true },
        )
        advanceUntilIdle()

        assertFalse(decoder.decoding)
        assertTrue(onUseRawCalled)
        assertFalse(onLinksCalled)
        assertNull(decoder.failure)
    }

    @Test
    fun failedSetsFailureAndCallsNeither() = runTest {
        val decoder = PasteDecoder(this) { PasteResult.Failed }
        var onLinksCalled = false
        var onUseRawCalled = false

        decoder.decode(
            url = "https://pastebin.com/raw/test1234",
            action = PasteAction.COPY,
            onLinks = { onLinksCalled = true },
            onUseRaw = { onUseRawCalled = true },
        )
        advanceUntilIdle()

        assertFalse(decoder.decoding)
        assertFalse(onLinksCalled)
        assertFalse(onUseRawCalled)
        assertNotNull(decoder.failure)
        assertEquals(PasteAction.COPY, decoder.failure?.action)
        assertEquals("https://pastebin.com/raw/test1234", decoder.failure?.url)
    }

    @Test
    fun retryReRunsResolveAndCallsOnLinks() = runTest {
        var resolveCount = 0
        val decoder = PasteDecoder(this) {
            resolveCount++
            if (resolveCount == 1) PasteResult.Failed else PasteResult.Links(listOf("https://example.com"))
        }
        var onLinksCalled: List<String>? = null
        var onUseRawCalled = false

        decoder.decode(
            url = "https://pastebin.com/raw/test1234",
            action = PasteAction.SHARE,
            onLinks = { onLinksCalled = it },
            onUseRaw = { onUseRawCalled = true },
        )
        advanceUntilIdle()

        assertEquals(1, resolveCount)
        assertNotNull(decoder.failure)
        assertNull(onLinksCalled)

        decoder.retry()
        assertNull(decoder.failure)
        advanceUntilIdle()

        assertEquals(2, resolveCount)
        assertFalse(decoder.decoding)
        assertNull(decoder.failure)
        assertEquals(listOf("https://example.com"), onLinksCalled)
        assertFalse(onUseRawCalled)
    }

    @Test
    fun useRawAnywayCallsOnUseRawAndClearsFailure() = runTest {
        val decoder = PasteDecoder(this) { PasteResult.Failed }
        var onUseRawCalled = false

        decoder.decode(
            url = "https://pastebin.com/raw/test1234",
            action = PasteAction.OPEN,
            onLinks = {},
            onUseRaw = { onUseRawCalled = true },
        )
        advanceUntilIdle()

        assertNotNull(decoder.failure)
        assertFalse(onUseRawCalled)

        decoder.useRawAnyway()
        assertTrue(onUseRawCalled)
        assertNull(decoder.failure)
    }

    @Test
    fun dismissFailureClearsFailureWithoutCallingCallbacks() = runTest {
        val decoder = PasteDecoder(this) { PasteResult.Failed }
        var onLinksCalled = false
        var onUseRawCalled = false

        decoder.decode(
            url = "https://pastebin.com/raw/test1234",
            action = PasteAction.OPEN,
            onLinks = { onLinksCalled = true },
            onUseRaw = { onUseRawCalled = true },
        )
        advanceUntilIdle()

        assertNotNull(decoder.failure)

        decoder.dismissFailure()
        assertNull(decoder.failure)
        assertFalse(onLinksCalled)
        assertFalse(onUseRawCalled)
    }

    @Test
    fun secondDecodeCancelsFirstAndFirstCallbacksNeverRun() = runTest {
        val gate1 = CompletableDeferred<PasteResult>()
        val gate2 = CompletableDeferred<PasteResult>()
        var callCount = 0
        val decoder = PasteDecoder(this) {
            callCount++
            if (callCount == 1) gate1.await() else gate2.await()
        }

        var firstOnLinksCalled = false
        var firstOnUseRawCalled = false
        var secondOnLinksCalled = false
        var secondOnUseRawCalled = false

        decoder.decode(
            url = "https://pastebin.com/raw/first",
            action = PasteAction.OPEN,
            onLinks = { firstOnLinksCalled = true },
            onUseRaw = { firstOnUseRawCalled = true },
        )
        assertTrue(decoder.decoding)

        decoder.decode(
            url = "https://pastebin.com/raw/second",
            action = PasteAction.OPEN,
            onLinks = { secondOnLinksCalled = true },
            onUseRaw = { secondOnUseRawCalled = true },
        )
        assertTrue(decoder.decoding)

        gate1.complete(PasteResult.Links(listOf("https://first.com")))
        gate2.complete(PasteResult.Links(listOf("https://second.com")))
        advanceUntilIdle()

        assertFalse(decoder.decoding)
        assertFalse(firstOnLinksCalled)
        assertFalse(firstOnUseRawCalled)
        assertTrue(secondOnLinksCalled)
        assertFalse(secondOnUseRawCalled)
    }

    @Test
    fun linkTextNullDecoderCallsOnTextSynchronously() {
        val decoder: PasteDecoder? = null
        var resultText: String? = null
        decoder.linkText("https://pastebin.com/raw/test1234", PasteAction.COPY) {
            resultText = it
        }
        assertEquals("https://pastebin.com/raw/test1234", resultText)
    }

    @Test
    fun linkTextNonPastebinUrlCallsOnTextWithoutResolve() = runTest {
        var resolveCalled = false
        val decoder = PasteDecoder(this) {
            resolveCalled = true
            PasteResult.Failed
        }
        var resultText: String? = null
        decoder.linkText("https://example.com/not-paste", PasteAction.COPY) {
            resultText = it
        }
        assertEquals("https://example.com/not-paste", resultText)
        assertFalse(resolveCalled)
    }

    @Test
    fun linkTextSeveralLinksJoinedWithNewline() = runTest {
        val decoder = PasteDecoder(this) {
            PasteResult.Links(listOf("https://link1.com", "https://link2.com", "https://link3.com"))
        }
        var resultText: String? = null
        decoder.linkText("https://pastebin.com/raw/test1234", PasteAction.SHARE) {
            resultText = it
        }
        advanceUntilIdle()
        assertEquals("https://link1.com\nhttps://link2.com\nhttps://link3.com", resultText)
    }
}
