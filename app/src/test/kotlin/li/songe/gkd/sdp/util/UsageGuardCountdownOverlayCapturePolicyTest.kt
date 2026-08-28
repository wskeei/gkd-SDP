package li.songe.gkd.sdp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardCountdownOverlayCapturePolicyTest {
    @Test
    fun screenshotHideDurationIsTenSeconds() {
        assertEquals(
            10_000L,
            UsageGuardCountdownOverlayCapturePolicy.HIDE_DURATION_MS,
        )
    }

    @Test
    fun sameUnexpiredSessionCanRestore() {
        val session = session(expiresAt = 20_001L)

        assertTrue(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hidden = session,
                current = session,
                now = 20_000L,
            ),
        )
    }

    @Test
    fun expiryOrInvalidSessionCannotRestore() {
        val session = session(expiresAt = 20_000L)

        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hidden = session,
                current = session,
                now = 20_000L,
            ),
        )
        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hidden = session(appId = ""),
                current = session(appId = ""),
                now = 19_999L,
            ),
        )
        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hidden = session(recordId = 0L),
                current = session(recordId = 0L),
                now = 19_999L,
            ),
        )
    }

    @Test
    fun replacementSessionCannotRestoreHiddenOverlay() {
        val hidden = session()

        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hidden = hidden,
                current = session(appId = "com.example.other"),
                now = 19_999L,
            ),
        )
        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hidden = hidden,
                current = session(recordId = hidden.recordId + 1L),
                now = 19_999L,
            ),
        )
        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hidden = hidden,
                current = session(runtimeGeneration = hidden.runtimeGeneration + 1L),
                now = 19_999L,
            ),
        )
    }

    private fun session(
        appId: String = "com.example.target",
        recordId: Long = 7L,
        expiresAt: Long = 20_001L,
        leaseId: Long = 11L,
        runtimeGeneration: Long = 5L,
    ) = UsageGuardCountdownOverlaySession(
        appId = appId,
        recordId = recordId,
        expiresAt = expiresAt,
        leaseId = leaseId,
        runtimeGeneration = runtimeGeneration,
    )
}
