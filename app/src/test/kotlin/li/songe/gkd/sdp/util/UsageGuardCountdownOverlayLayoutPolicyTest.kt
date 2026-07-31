package li.songe.gkd.sdp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardCountdownOverlayLayoutPolicyTest {
    @Test
    fun initialPositionUsesStatusBarHeightWhenItIsTallerThanMargin() {
        val position = UsageGuardCountdownOverlayLayoutPolicy.initialPosition(
            marginPx = 12,
            statusBarHeightPx = 64,
        )

        assertEquals(12, position.x)
        assertEquals(64, position.y)
    }

    @Test
    fun initialPositionFallsBackToMarginWhenStatusBarHeightIsSmaller() {
        val position = UsageGuardCountdownOverlayLayoutPolicy.initialPosition(
            marginPx = 12,
            statusBarHeightPx = 0,
        )

        assertEquals(12, position.x)
        assertEquals(12, position.y)
    }

    @Test
    fun shouldResetPositionWhenSessionRecordChanges() {
        assertTrue(
            UsageGuardCountdownOverlayLayoutPolicy.shouldResetPosition(
                previousAppId = "com.example.reader",
                previousRecordId = 7L,
                nextAppId = "com.example.reader",
                nextRecordId = 8L,
            )
        )
    }

    @Test
    fun shouldKeepPositionWhenSessionIdentityMatches() {
        assertFalse(
            UsageGuardCountdownOverlayLayoutPolicy.shouldResetPosition(
                previousAppId = "com.example.reader",
                previousRecordId = 7L,
                nextAppId = "com.example.reader",
                nextRecordId = 7L,
            )
        )
    }

    @Test
    fun maxPillWidthKeepsBothHorizontalMargins() {
        assertEquals(
            1032,
            UsageGuardCountdownOverlayLayoutPolicy.maxPillWidthPx(
                screenWidthPx = 1080,
                horizontalMarginPx = 24,
            ),
        )
    }

    @Test
    fun maxPillWidthNeverReturnsNegativePixels() {
        assertEquals(
            0,
            UsageGuardCountdownOverlayLayoutPolicy.maxPillWidthPx(
                screenWidthPx = 30,
                horizontalMarginPx = 24,
            ),
        )
    }
}
