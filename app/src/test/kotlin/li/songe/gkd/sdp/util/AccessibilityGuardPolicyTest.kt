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

        assertNull(evaluation.dueReminderIndex)
        assertFalse(evaluation.startEnforcement)
        assertEquals(
            disabledAt + 15L * AccessibilityGuardPolicy.MINUTE_MS,
            evaluation.nextWakeAtEpochMs,
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

            assertEquals(index, evaluation.dueReminderIndex)
            assertEquals(index == AccessibilityGuardPolicy.REMINDER_OFFSETS_MS.lastIndex, evaluation.startEnforcement)
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

        assertEquals(3, evaluation.dueReminderIndex)
        assertFalse(evaluation.startEnforcement)
        assertEquals(
            disabledAt + 35L * AccessibilityGuardPolicy.MINUTE_MS,
            evaluation.nextWakeAtEpochMs,
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

        assertEquals(5, evaluation.dueReminderIndex)
        assertTrue(evaluation.startEnforcement)
        assertNull(evaluation.nextWakeAtEpochMs)
    }

    @Test
    fun deliveredReminderAndStartedEnforcementDoNotRepeatActions() {
        val evaluation = AccessibilityGuardPolicy.evaluate(
            disabledAtEpochMs = disabledAt,
            lastReminderIndex = 5,
            enforcementStarted = true,
            nowEpochMs = disabledAt + 90L * AccessibilityGuardPolicy.MINUTE_MS,
        )

        assertNull(evaluation.dueReminderIndex)
        assertFalse(evaluation.startEnforcement)
        assertNull(evaluation.nextWakeAtEpochMs)
    }

    @Test
    fun temporaryBlockedShutdownIsIgnoredButManualDisableIsGuarded() {
        assertEquals(
            AccessibilityGuardPolicy.SessionMode.SUPPRESSED_TEMPORARY,
            AccessibilityGuardPolicy.sessionMode(
                featureEnabled = true,
                strictChannelAvailable = true,
                useA11yMode = true,
                a11yEnabled = false,
                temporaryShutdownExpected = true,
                currentAppBlocked = true,
            ),
        )
        assertEquals(
            AccessibilityGuardPolicy.SessionMode.TRACK,
            AccessibilityGuardPolicy.sessionMode(
                featureEnabled = true,
                strictChannelAvailable = true,
                useA11yMode = true,
                a11yEnabled = false,
                temporaryShutdownExpected = false,
                currentAppBlocked = true,
            ),
        )
        assertTrue(
            AccessibilityGuardPolicy.shouldGuard(
                featureEnabled = true,
                strictChannelAvailable = true,
                useA11yMode = true,
                a11yEnabled = false,
                temporaryShutdownExpected = false,
                currentAppBlocked = true,
            )
        )
        assertEquals(
            AccessibilityGuardPolicy.SessionMode.TRACK,
            AccessibilityGuardPolicy.sessionMode(
                featureEnabled = true,
                strictChannelAvailable = true,
                useA11yMode = true,
                a11yEnabled = false,
                temporaryShutdownExpected = true,
                currentAppBlocked = false,
            ),
        )
        assertEquals(
            AccessibilityGuardPolicy.SessionMode.RESET,
            AccessibilityGuardPolicy.sessionMode(
                featureEnabled = false,
                strictChannelAvailable = true,
                useA11yMode = true,
                a11yEnabled = false,
                temporaryShutdownExpected = false,
                currentAppBlocked = false,
            ),
        )
        assertEquals(
            AccessibilityGuardPolicy.SessionMode.RESET,
            AccessibilityGuardPolicy.sessionMode(
                featureEnabled = true,
                strictChannelAvailable = true,
                useA11yMode = false,
                a11yEnabled = false,
                temporaryShutdownExpected = false,
                currentAppBlocked = false,
            ),
        )
    }

    @Test
    fun strictChannelUnavailableDisablesTheGuard() {
        assertEquals(
            AccessibilityGuardPolicy.SessionMode.RESET,
            AccessibilityGuardPolicy.sessionMode(
                featureEnabled = true,
                strictChannelAvailable = false,
                useA11yMode = true,
                a11yEnabled = false,
                temporaryShutdownExpected = false,
                currentAppBlocked = false,
            ),
        )
        assertFalse(
            AccessibilityGuardPolicy.shouldGuard(
                featureEnabled = true,
                strictChannelAvailable = false,
                useA11yMode = true,
                a11yEnabled = false,
                temporaryShutdownExpected = false,
                currentAppBlocked = false,
            )
        )
    }

    @Test
    fun recoveredAccessibilityPermissionResetsTheGuardSession() {
        assertEquals(
            AccessibilityGuardPolicy.SessionMode.RESET,
            AccessibilityGuardPolicy.sessionMode(
                featureEnabled = true,
                strictChannelAvailable = true,
                useA11yMode = true,
                a11yEnabled = true,
                temporaryShutdownExpected = false,
                currentAppBlocked = false,
            ),
        )
        assertFalse(
            AccessibilityGuardPolicy.shouldGuard(
                featureEnabled = true,
                strictChannelAvailable = true,
                useA11yMode = true,
                a11yEnabled = true,
                temporaryShutdownExpected = false,
                currentAppBlocked = false,
            )
        )
    }

    @Test
    fun overlayRequiresEnforcementAndHidesForGrantFlowOrVisibleApp() {
        val base = AccessibilityGuardPolicy.OverlayInput(
            enforcementStarted = true,
            a11yEnabled = false,
            appVisible = false,
            grantFlowUntilEpochMs = disabledAt,
            nowEpochMs = disabledAt,
            canDrawOverlays = true,
            screenInteractive = true,
            keyguardLocked = false,
        )

        assertTrue(AccessibilityGuardPolicy.shouldShowOverlay(base))
        assertFalse(AccessibilityGuardPolicy.shouldShowOverlay(base.copy(enforcementStarted = false)))
        assertFalse(AccessibilityGuardPolicy.shouldShowOverlay(base.copy(a11yEnabled = true)))
        assertFalse(AccessibilityGuardPolicy.shouldShowOverlay(base.copy(appVisible = true)))
    }

    @Test
    fun overlayRequiresPermissionAndInteractiveScreenAndHonorsGrantTimestamp() {
        val base = AccessibilityGuardPolicy.OverlayInput(
            enforcementStarted = true,
            a11yEnabled = false,
            appVisible = false,
            grantFlowUntilEpochMs = disabledAt + 1_000L,
            nowEpochMs = disabledAt,
            canDrawOverlays = true,
            screenInteractive = true,
            keyguardLocked = false,
        )

        assertFalse(AccessibilityGuardPolicy.shouldShowOverlay(base))
        assertTrue(
            AccessibilityGuardPolicy.shouldShowOverlay(
                base.copy(nowEpochMs = disabledAt + 1_000L)
            )
        )
        assertFalse(AccessibilityGuardPolicy.shouldShowOverlay(base.copy(canDrawOverlays = false)))
        assertFalse(AccessibilityGuardPolicy.shouldShowOverlay(base.copy(screenInteractive = false)))
        assertFalse(AccessibilityGuardPolicy.shouldShowOverlay(base.copy(keyguardLocked = true)))
    }

    @Test
    fun resetSessionHasNoDueActions() {
        val evaluation = AccessibilityGuardPolicy.evaluate(
            disabledAtEpochMs = 0L,
            lastReminderIndex = 5,
            enforcementStarted = true,
            nowEpochMs = disabledAt + 90L * AccessibilityGuardPolicy.MINUTE_MS,
        )

        assertNull(evaluation.dueReminderIndex)
        assertFalse(evaluation.startEnforcement)
        assertNull(evaluation.nextWakeAtEpochMs)
    }
}
