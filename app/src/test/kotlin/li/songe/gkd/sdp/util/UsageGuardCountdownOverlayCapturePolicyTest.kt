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
        assertTrue(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hiddenAppId = "com.example.target",
                hiddenRecordId = 7L,
                currentAppId = "com.example.target",
                currentRecordId = 7L,
                expiresAt = 20_001L,
                now = 20_000L,
            ),
        )
    }

    @Test
    fun recordAtOrPastExpiryCannotRestore() {
        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hiddenAppId = "com.example.target",
                hiddenRecordId = 7L,
                currentAppId = "com.example.target",
                currentRecordId = 7L,
                expiresAt = 20_000L,
                now = 20_000L,
            ),
        )
        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hiddenAppId = "com.example.target",
                hiddenRecordId = 7L,
                currentAppId = "com.example.target",
                currentRecordId = 7L,
                expiresAt = 19_999L,
                now = 20_000L,
            ),
        )
    }

    @Test
    fun invalidOrReplacedRecordCannotRestore() {
        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hiddenAppId = "",
                hiddenRecordId = 7L,
                currentAppId = "",
                currentRecordId = 7L,
                expiresAt = 20_001L,
                now = 20_000L,
            ),
        )
        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hiddenAppId = "com.example.target",
                hiddenRecordId = 0L,
                currentAppId = "com.example.target",
                currentRecordId = 0L,
                expiresAt = 20_001L,
                now = 20_000L,
            ),
        )
        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hiddenAppId = "com.example.target",
                hiddenRecordId = 7L,
                currentAppId = "com.example.other",
                currentRecordId = 7L,
                expiresAt = 20_001L,
                now = 20_000L,
            ),
        )
        assertFalse(
            UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
                hiddenAppId = "com.example.target",
                hiddenRecordId = 7L,
                currentAppId = "com.example.target",
                currentRecordId = 8L,
                expiresAt = 20_001L,
                now = 20_000L,
            ),
        )
    }
}
