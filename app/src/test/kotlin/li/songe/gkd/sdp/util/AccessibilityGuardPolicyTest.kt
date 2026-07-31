package li.songe.gkd.sdp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityGuardPolicyTest {
    private val disabledAt = 1_000_000L

    @Test
    fun reminderIntervalsUseCumulativeOffsets() {
        assertEquals(listOf(15, 10, 5, 3, 2, 1), AccessibilityGuardPolicy.REMINDER_INTERVALS_MINUTES.toList())
        assertEquals(
            listOf(15L, 25L, 30L, 33L, 35L, 36L).map { it * AccessibilityGuardPolicy.MINUTE_MS },
            AccessibilityGuardPolicy.REMINDER_OFFSETS_MS.toList(),
        )
    }

    @Test
    fun nothingIsDueBeforeTheFirstFifteenMinuteBoundary() {
        val evaluation = AccessibilityGuardPolicy.evaluate(
            disabledAtEpochMs = disabledAt,
            lastReminderIndex = -1,
            enforcementStarted = false,
            nowEpochMs = disabledAt + 15L * AccessibilityGuardPolicy.MINUTE_MS - 1L,
        )

        assertNull(evaluation.reminderIndex)
        assertFalse(evaluation.enforce)
        assertEquals(
            disabledAt + 15L * AccessibilityGuardPolicy.MINUTE_MS,
            evaluation.nextWakeAt,
        )
    }

    @Test
    fun eachBoundaryMapsToTheNextReminderIndex() {
        AccessibilityGuardPolicy.REMINDER_OFFSETS_MS.forEachIndexed { index, offset ->
            val evaluation = AccessibilityGuardPolicy.evaluate(
                disabledAtEpochMs = disabledAt,
                lastReminderIndex = index - 1,
                enforcementStarted = false,
                nowEpochMs = disabledAt + offset,
            )

            assertEquals(index, evaluation.reminderIndex)
            assertEquals(index == AccessibilityGuardPolicy.REMINDER_OFFSETS_MS.lastIndex, evaluation.enforce)
        }
    }

    @Test
    fun lateProcessResumeEmitsOnlyTheLatestDueReminder() {
        val evaluation = AccessibilityGuardPolicy.evaluate(
            disabledAtEpochMs = disabledAt,
            lastReminderIndex = -1,
            enforcementStarted = false,
            nowEpochMs = disabledAt + 34L * AccessibilityGuardPolicy.MINUTE_MS,
        )

        assertEquals(3, evaluation.reminderIndex)
        assertFalse(evaluation.enforce)
        assertEquals(
            disabledAt + 35L * AccessibilityGuardPolicy.MINUTE_MS,
            evaluation.nextWakeAt,
        )
    }

    @Test
    fun finalReminderAndEnforcementAreDueAtThirtySixMinutes() {
        val evaluation = AccessibilityGuardPolicy.evaluate(
            disabledAtEpochMs = disabledAt,
            lastReminderIndex = 4,
            enforcementStarted = false,
            nowEpochMs = disabledAt + 36L * AccessibilityGuardPolicy.MINUTE_MS,
        )

        assertEquals(5, evaluation.reminderIndex)
        assertTrue(evaluation.enforce)
        assertNull(evaluation.nextWakeAt)
    }

    @Test
    fun deliveredReminderAndStartedEnforcementDoNotRepeatActions() {
        val evaluation = AccessibilityGuardPolicy.evaluate(
            disabledAtEpochMs = disabledAt,
            lastReminderIndex = 5,
            enforcementStarted = true,
            nowEpochMs = disabledAt + 90L * AccessibilityGuardPolicy.MINUTE_MS,
        )

        assertNull(evaluation.reminderIndex)
        assertFalse(evaluation.enforce)
        assertNull(evaluation.nextWakeAt)
    }

    @Test
    fun temporaryBlockedShutdownIsIgnoredButManualDisableIsGuarded() {
        assertEquals(
            AccessibilityGuardPolicy.SessionMode.TEMPORARY_SHUTDOWN,
            AccessibilityGuardPolicy.sessionMode(
                featureEnabled = true,
                useA11yMode = true,
                temporaryShutdown = true,
                currentAppBlocked = true,
            ),
        )
        assertEquals(
            AccessibilityGuardPolicy.SessionMode.MANUAL_DISABLED,
            AccessibilityGuardPolicy.sessionMode(
                featureEnabled = true,
                useA11yMode = true,
                temporaryShutdown = false,
                currentAppBlocked = true,
            ),
        )
        assertEquals(
            AccessibilityGuardPolicy.SessionMode.DISABLED,
            AccessibilityGuardPolicy.sessionMode(
                featureEnabled = false,
                useA11yMode = true,
                temporaryShutdown = false,
                currentAppBlocked = false,
            ),
        )
        assertEquals(
            AccessibilityGuardPolicy.SessionMode.DISABLED,
            AccessibilityGuardPolicy.sessionMode(
                featureEnabled = true,
                useA11yMode = false,
                temporaryShutdown = false,
                currentAppBlocked = false,
            ),
        )
    }

    @Test
    fun overlayRequiresEnforcementAndHidesForGrantFlowOrVisibleApp() {
        val base = AccessibilityGuardPolicy.OverlayInput(
            sessionMode = AccessibilityGuardPolicy.SessionMode.MANUAL_DISABLED,
            enforcementStarted = true,
            accessibilityEnabled = false,
            appVisible = false,
            grantFlowActive = false,
            overlayPermissionGranted = true,
            screenInteractive = true,
            nowEpochMs = disabledAt,
        )

        assertTrue(AccessibilityGuardPolicy.shouldShowOverlay(base))
        assertFalse(AccessibilityGuardPolicy.shouldShowOverlay(base.copy(enforcementStarted = false)))
        assertFalse(AccessibilityGuardPolicy.shouldShowOverlay(base.copy(accessibilityEnabled = true)))
        assertFalse(AccessibilityGuardPolicy.shouldShowOverlay(base.copy(appVisible = true)))
        assertFalse(AccessibilityGuardPolicy.shouldShowOverlay(base.copy(grantFlowActive = true)))
    }

    @Test
    fun overlayRequiresPermissionAndInteractiveScreenAndHonorsSuppression() {
        val base = AccessibilityGuardPolicy.OverlayInput(
            sessionMode = AccessibilityGuardPolicy.SessionMode.MANUAL_DISABLED,
            enforcementStarted = true,
            accessibilityEnabled = false,
            appVisible = false,
            grantFlowActive = false,
            overlayPermissionGranted = true,
            screenInteractive = true,
            suppressedUntilEpochMs = disabledAt + 1_000L,
            nowEpochMs = disabledAt,
        )

        assertFalse(AccessibilityGuardPolicy.shouldShowOverlay(base))
        assertTrue(
            AccessibilityGuardPolicy.shouldShowOverlay(
                base.copy(nowEpochMs = disabledAt + 1_000L)
            )
        )
        assertFalse(AccessibilityGuardPolicy.shouldShowOverlay(base.copy(overlayPermissionGranted = false)))
        assertFalse(AccessibilityGuardPolicy.shouldShowOverlay(base.copy(screenInteractive = false)))
        assertFalse(
            AccessibilityGuardPolicy.shouldShowOverlay(
                base.copy(sessionMode = AccessibilityGuardPolicy.SessionMode.TEMPORARY_SHUTDOWN)
            )
        )
    }

    @Test
    fun resetSessionHasNoDueActions() {
        val evaluation = AccessibilityGuardPolicy.evaluate(
            disabledAtEpochMs = 0L,
            lastReminderIndex = 5,
            enforcementStarted = true,
            nowEpochMs = disabledAt + 90L * AccessibilityGuardPolicy.MINUTE_MS,
        )

        assertNull(evaluation.reminderIndex)
        assertFalse(evaluation.enforce)
        assertNull(evaluation.nextWakeAt)
    }
}
