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
    fun sameUnexpiredRecordCanRestore() {
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
    fun recordAtOrPastExpiryCannotRestore() {
        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hidden = session(expiresAt = 20_000L),
                current = session(expiresAt = 20_000L),
                now = 20_000L,
            ),
        )
        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hidden = session(expiresAt = 19_999L),
                current = session(expiresAt = 19_999L),
                now = 20_000L,
            ),
        )
    }

    @Test
    fun invalidOrReplacedRecordCannotRestore() {
        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hidden = session(appId = ""),
                current = session(appId = ""),
                now = 20_000L,
            ),
        )
        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hidden = session(recordId = 0L),
                current = session(recordId = 0L),
                now = 20_000L,
            ),
        )
        val hidden = session()
        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hidden = hidden,
                current = session(appId = "com.example.other"),
                now = 20_000L,
            ),
        )
        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hidden = hidden,
                current = session(recordId = 8L),
                now = 20_000L,
            ),
        )
        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hidden = hidden,
                current = session(runtimeGeneration = 6L),
                now = 20_000L,
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
