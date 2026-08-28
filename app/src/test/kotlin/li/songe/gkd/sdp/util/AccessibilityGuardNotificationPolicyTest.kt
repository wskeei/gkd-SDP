package li.songe.gkd.sdp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityGuardNotificationPolicyTest {
    @Test
    fun elapsedMinuteLabelsFollowTheCumulativeReminderSchedule() {
        assertEquals(
            listOf(15, 25, 30, 33, 35, 36),
            AccessibilityGuardNotificationPolicy.ELAPSED_MINUTES.toList(),
        )
        assertEquals(
            AccessibilityGuardPolicy.REMINDER_OFFSETS_MS
                .map { (it / AccessibilityGuardPolicy.MINUTE_MS).toInt() },
            AccessibilityGuardNotificationPolicy.ELAPSED_MINUTES.toList(),
        )
    }

    @Test
    fun onlySixReminderIndexesAreValid() {
        assertFalse(AccessibilityGuardNotificationPolicy.isValidIndex(-1))
        assertTrue(AccessibilityGuardNotificationPolicy.isValidIndex(0))
        assertTrue(AccessibilityGuardNotificationPolicy.isValidIndex(5))
        assertFalse(AccessibilityGuardNotificationPolicy.isValidIndex(6))
    }

    @Test
    fun finalWarningUsesTheFullScreenReminderCopy() {
        assertEquals(
            "已关闭 36 分钟，将显示全屏提醒，请前往重新开启",
            AccessibilityGuardNotificationPolicy.text(5),
        )
    }

    @Test
    fun newSessionCountsDownToTheFirstReminder() {
        val status = AccessibilityGuardNotificationPolicy.status(
            disabledAtEpochMs = 1_000L,
            lastReminderIndex = -1,
            enforcementStarted = false,
        )

        assertEquals(
            AccessibilityGuardNotificationPolicy.GuardStatusNotification(
                text = "距离第 1 次提醒",
                targetEpochMs = 1_000L + AccessibilityGuardPolicy.REMINDER_OFFSETS_MS[0],
                nextReminderIndex = 0,
            ),
            status,
        )
    }

    @Test
    fun statusMovesToTheNextCumulativeCheckpointAfterEachReminder() {
        val status = AccessibilityGuardNotificationPolicy.status(
            disabledAtEpochMs = 10_000L,
            lastReminderIndex = 3,
            enforcementStarted = false,
        )

        assertEquals(4, status?.nextReminderIndex)
        assertEquals(
            10_000L + AccessibilityGuardPolicy.REMINDER_OFFSETS_MS[4],
            status?.targetEpochMs,
        )
    }

    @Test
    fun finalEnforcementUsesStaticTextWithoutAChronometerTarget() {
        assertEquals(
            AccessibilityGuardNotificationPolicy.GuardStatusNotification(
                text = "最后提醒已发送，请立即开启无障碍",
                targetEpochMs = null,
                nextReminderIndex = null,
            ),
            AccessibilityGuardNotificationPolicy.status(
                disabledAtEpochMs = 10_000L,
                lastReminderIndex = 5,
                enforcementStarted = true,
            ),
        )
    }

    @Test
    fun malformedOrEmptySessionsHaveNoStableNotification() {
        assertEquals(
            null,
            AccessibilityGuardNotificationPolicy.status(
                disabledAtEpochMs = 0L,
                lastReminderIndex = -1,
                enforcementStarted = false,
            ),
        )
        assertEquals(
            null,
            AccessibilityGuardNotificationPolicy.status(
                disabledAtEpochMs = 1_000L,
                lastReminderIndex = 6,
                enforcementStarted = false,
            ),
        )
        assertEquals(
            null,
            AccessibilityGuardNotificationPolicy.status(
                disabledAtEpochMs = 1_000L,
                lastReminderIndex = 4,
                enforcementStarted = true,
            ),
        )
    }
}
