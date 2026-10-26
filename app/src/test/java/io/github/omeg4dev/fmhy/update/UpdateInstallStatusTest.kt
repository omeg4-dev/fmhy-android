package io.github.omeg4dev.fmhy.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateInstallStatusTest {

    @Test
    fun map_pendingUserAction() {
        assertEquals(
            UpdateInstallOutcome.PENDING_USER_ACTION,
            UpdateInstallStatus.map(UpdateInstallStatus.STATUS_PENDING_USER_ACTION, null),
        )
        assertEquals(
            UpdateInstallOutcome.PENDING_USER_ACTION,
            UpdateInstallStatus.map(UpdateInstallStatus.STATUS_PENDING_USER_ACTION, "User action needed"),
        )
    }

    @Test
    fun map_success() {
        assertEquals(
            UpdateInstallOutcome.SUCCESS,
            UpdateInstallStatus.map(UpdateInstallStatus.STATUS_SUCCESS, null),
        )
        assertEquals(
            UpdateInstallOutcome.SUCCESS,
            UpdateInstallStatus.map(UpdateInstallStatus.STATUS_SUCCESS, "Installed successfully"),
        )
    }

    @Test
    fun map_aborted() {
        assertEquals(
            UpdateInstallOutcome.ABORTED,
            UpdateInstallStatus.map(UpdateInstallStatus.STATUS_FAILURE_ABORTED, null),
        )
        assertEquals(
            UpdateInstallOutcome.ABORTED,
            UpdateInstallStatus.map(UpdateInstallStatus.STATUS_FAILURE_ABORTED, "User cancelled"),
        )
    }

    @Test
    fun map_signatureMismatch() {
        val statuses = listOf(
            UpdateInstallStatus.STATUS_FAILURE_CONFLICT,
            UpdateInstallStatus.STATUS_FAILURE_INCOMPATIBLE,
            UpdateInstallStatus.STATUS_FAILURE_INVALID,
        )

        val signatureMessages = listOf(
            "INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match",
            "Package signatures do not match previously installed version; ignoring!",
            "Mismatching signature detected",
            "failed: install_failed_update_incompatible",
            "SIGNATURE error",
        )

        for (status in statuses) {
            for (msg in signatureMessages) {
                assertEquals(
                    "Expected SIGNATURE_MISMATCH for status=$status, message=$msg",
                    UpdateInstallOutcome.SIGNATURE_MISMATCH,
                    UpdateInstallStatus.map(status, msg),
                )
            }
        }
    }

    @Test
    fun map_conflictOrIncompatibleWithoutSignature_mapsToFailure() {
        val nonSignatureMessages = listOf(
            null,
            "",
            "INSTALL_FAILED_DEXOPT",
            "Package corrupted",
            "Incompatible ABI",
        )

        for (msg in nonSignatureMessages) {
            assertEquals(
                UpdateInstallOutcome.FAILURE,
                UpdateInstallStatus.map(UpdateInstallStatus.STATUS_FAILURE_CONFLICT, msg),
            )
            assertEquals(
                UpdateInstallOutcome.FAILURE,
                UpdateInstallStatus.map(UpdateInstallStatus.STATUS_FAILURE_INCOMPATIBLE, msg),
            )
            assertEquals(
                UpdateInstallOutcome.FAILURE,
                UpdateInstallStatus.map(UpdateInstallStatus.STATUS_FAILURE_INVALID, msg),
            )
        }
    }

    @Test
    fun map_otherFailures_mapsToFailure() {
        val otherStatuses = listOf(
            UpdateInstallStatus.STATUS_FAILURE,
            UpdateInstallStatus.STATUS_FAILURE_BLOCKED,
            UpdateInstallStatus.STATUS_FAILURE_STORAGE,
            999, // unknown
        )

        for (status in otherStatuses) {
            assertEquals(
                UpdateInstallOutcome.FAILURE,
                UpdateInstallStatus.map(status, null),
            )
            assertEquals(
                UpdateInstallOutcome.FAILURE,
                UpdateInstallStatus.map(status, "Some error reason with signature keyword"),
            )
        }
    }
}
