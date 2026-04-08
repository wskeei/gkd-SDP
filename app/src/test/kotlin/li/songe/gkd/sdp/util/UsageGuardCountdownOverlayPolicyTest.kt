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
    fun formatRemainingTextRoundsUpAtHourBoundary() {
        val text = UsageGuardCountdownOverlayPolicy.formatRemainingText(
            expiresAt = 3_599_001L,
            now = 0L,
        )

        assertEquals("1:00:00", text)
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

    @Test
    fun shouldDisplayRejectsMissingActiveRecord() {
        assertFalse(
            UsageGuardCountdownOverlayPolicy.shouldDisplay(
                activeRecord = null,
                foregroundAppId = "com.example.reader",
                requestOverlayAppId = null,
                timeoutOverlayAppId = null,
                now = 1_000L,
            )
        )
    }

    @Test
    fun shouldDisplayRejectsEndedRecord() {
        val endedRecord = activeRecord(endedAt = 1_000L)

        assertFalse(
            UsageGuardCountdownOverlayPolicy.shouldDisplay(
                activeRecord = endedRecord,
                foregroundAppId = "com.example.reader",
                requestOverlayAppId = null,
                timeoutOverlayAppId = null,
                now = 1_000L,
            )
        )
    }

    @Test
    fun shouldDisplayRejectsExpiredRecord() {
        val expiredRecord = activeRecord(expiresAt = 1_000L)

        assertFalse(
            UsageGuardCountdownOverlayPolicy.shouldDisplay(
                activeRecord = expiredRecord,
                foregroundAppId = "com.example.reader",
                requestOverlayAppId = null,
                timeoutOverlayAppId = null,
                now = 1_000L,
            )
        )
    }

    private fun activeRecord(
        appId: String = "com.example.reader",
        expiresAt: Long = 600_000L,
        endedAt: Long = 0L,
    ): UsageGuardRecord {
        return UsageGuardRecord(
            id = 7L,
            appId = appId,
            appName = "Reader",
            tagNames = listOf("查资料"),
            reasonText = "读一篇文章",
            requestedDurationMinutes = 10,
            requestedAt = 0L,
            grantedAt = 0L,
            expiresAt = expiresAt,
            endedAt = endedAt,
        )
    }
}
