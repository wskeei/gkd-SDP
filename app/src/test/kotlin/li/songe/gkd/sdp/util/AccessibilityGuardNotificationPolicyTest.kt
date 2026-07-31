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
}
