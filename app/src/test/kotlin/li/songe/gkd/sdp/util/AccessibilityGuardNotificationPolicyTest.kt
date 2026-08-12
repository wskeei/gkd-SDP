package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.R
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
        assertEquals(R.string.a11y_guard_final_reminder, AccessibilityGuardNotificationPolicy.textRes(5))
        assertEquals(listOf(36), AccessibilityGuardNotificationPolicy.textArgs(5))
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
                textRes = R.string.a11y_guard_next_reminder,
                textArgs = listOf(1),
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
                textRes = R.string.a11y_guard_last_sent,
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
