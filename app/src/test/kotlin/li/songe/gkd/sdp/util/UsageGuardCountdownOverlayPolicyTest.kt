package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.UsageGuardRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardCountdownOverlayPolicyTest {
    @Test
    fun formatRemainingTextUsesMinuteSecondLayoutBelowOneHour() {
        val text = UsageGuardCountdownOverlayPolicy.formatRemainingText(
            expiresAt = 598_000L,
            now = 0L,
        )

        assertEquals("09:58", text)
    }

    @Test
    fun formatRemainingTextUsesHourLayoutAtOneHourOrMore() {
        val text = UsageGuardCountdownOverlayPolicy.formatRemainingText(
            expiresAt = 3_731_000L,
            now = 0L,
        )

        assertEquals("1:02:11", text)
    }

    @Test
    fun formatRemainingTextClampsExpiredSessionsToZero() {
        val text = UsageGuardCountdownOverlayPolicy.formatRemainingText(
            expiresAt = 1_000L,
            now = 1_500L,
        )

        assertEquals("00:00", text)
    }

    @Test
    fun shouldDisplayRequiresForegroundMatchAndNoCompetingOverlay() {
        val record = UsageGuardRecord(
            id = 7L,
            appId = "com.example.reader",
            appName = "Reader",
            tagNames = listOf("查资料"),
            reasonText = "读一篇文章",
            requestedDurationMinutes = 10,
            requestedAt = 0L,
            grantedAt = 0L,
            expiresAt = 600_000L,
        )

        assertTrue(
            UsageGuardCountdownOverlayPolicy.shouldDisplay(
                activeRecord = record,
                foregroundAppId = "com.example.reader",
                requestOverlayAppId = null,
                timeoutOverlayAppId = null,
                now = 1_000L,
            )
        )
        assertFalse(
            UsageGuardCountdownOverlayPolicy.shouldDisplay(
                activeRecord = record,
                foregroundAppId = "com.example.reader",
                requestOverlayAppId = "com.example.reader",
                timeoutOverlayAppId = null,
                now = 1_000L,
            )
        )
        assertFalse(
            UsageGuardCountdownOverlayPolicy.shouldDisplay(
                activeRecord = record,
                foregroundAppId = "com.example.other",
                requestOverlayAppId = null,
                timeoutOverlayAppId = null,
                now = 1_000L,
            )
        )
        assertFalse(
            UsageGuardCountdownOverlayPolicy.shouldDisplay(
                activeRecord = record,
                foregroundAppId = "com.example.reader",
                requestOverlayAppId = null,
                timeoutOverlayAppId = "com.example.reader",
                now = 1_000L,
            )
        )
    }
}
