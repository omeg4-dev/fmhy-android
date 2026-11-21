package io.github.omeg4dev.fmhy.ui.common

import io.github.omeg4dev.fmhy.data.model.CriticalNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CriticalWarningPolicyTest {

    private val sampleNote = CriticalNote(
        id = "dodi-critical",
        title = "DODI Repacks Warning",
        textMd = "Warning text regarding DODI",
        hash = "3a639a399342",
    )

    @Test
    fun hiddenPerNoteIdAndSameHash_noDialog() {
        val hidden = mapOf("dodi-critical" to "3a639a399342")
        val shouldWarn = CriticalWarningPolicy.shouldWarn(
            switchOn = true,
            note = sampleNote,
            hidden = hidden,
        )
        assertFalse("Same hash should not warn", shouldWarn)
    }

    @Test
    fun changedHash_showsDialogAgain() {
        val hidden = mapOf("dodi-critical" to "old_hash_1234")
        val shouldWarn = CriticalWarningPolicy.shouldWarn(
            switchOn = true,
            note = sampleNote,
            hidden = hidden,
        )
        assertTrue("Changed hash should show warning dialog again", shouldWarn)
    }

    @Test
    fun notHidden_showsDialog() {
        val hidden = emptyMap<String, String>()
        val shouldWarn = CriticalWarningPolicy.shouldWarn(
            switchOn = true,
            note = sampleNote,
            hidden = hidden,
        )
        assertTrue("Unseen note should show dialog", shouldWarn)
    }

    @Test
    fun differentNoteHidden_showsDialog() {
        val hidden = mapOf("other-critical" to "3a639a399342")
        val shouldWarn = CriticalWarningPolicy.shouldWarn(
            switchOn = true,
            note = sampleNote,
            hidden = hidden,
        )
        assertTrue("Different note id hidden should not hide this note", shouldWarn)
    }

    @Test
    fun switchOff_neverShowsDialog() {
        val hidden = emptyMap<String, String>()
        val shouldWarn = CriticalWarningPolicy.shouldWarn(
            switchOn = false,
            note = sampleNote,
            hidden = hidden,
        )
        assertFalse("Switch off should never show dialog", shouldWarn)
    }

    @Test
    fun noNote_neverShowsDialog() {
        val hidden = emptyMap<String, String>()
        val shouldWarn = CriticalWarningPolicy.shouldWarn(
            switchOn = true,
            note = null,
            hidden = hidden,
        )
        assertFalse("No critical note should never show dialog", shouldWarn)
    }

    @Test
    fun order_unsafeFirstThenCritical() {
        // When both unsafe and critical note apply: unsafe dialog is step 1
        assertEquals(
            OpenStep.UNSAFE_DIALOG,
            CriticalWarningPolicy.nextStep(hasUnsafeReason = true, shouldWarnCritical = true)
        )

        // When only unsafe: unsafe dialog
        assertEquals(
            OpenStep.UNSAFE_DIALOG,
            CriticalWarningPolicy.nextStep(hasUnsafeReason = true, shouldWarnCritical = false)
        )

        // When unsafe is not present (or confirmed), but critical note applies: critical dialog is step 2
        assertEquals(
            OpenStep.CRITICAL_DIALOG,
            CriticalWarningPolicy.nextStep(hasUnsafeReason = false, shouldWarnCritical = true)
        )

        // When neither applies: opens directly
        assertEquals(
            OpenStep.OPEN_DIRECTLY,
            CriticalWarningPolicy.nextStep(hasUnsafeReason = false, shouldWarnCritical = false)
        )
    }
}
