package li.songe.gkd.sdp.service

import li.songe.gkd.sdp.store.AccessibilityGuardSession
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityGuardRuntimeTest {
    @Test
    fun markingTemporaryShutdownPreservesTheExistingSession() {
        val session = AccessibilityGuardSession(
            generation = 4L,
            disabledAtEpochMs = 1_000L,
            lastReminderIndex = 2,
            enforcementStarted = true,
            grantFlowUntilEpochMs = 2_000L,
        )

        assertEquals(
            session.copy(temporaryShutdownExpected = true),
            markTemporaryShutdownSession(session),
        )
    }

    @Test
    fun resetInvalidatesAnActiveSessionOnce() {
        val active = AccessibilityGuardSession(
            generation = 4L,
            disabledAtEpochMs = 1_000L,
            lastReminderIndex = 2,
            enforcementStarted = true,
            temporaryShutdownExpected = true,
            grantFlowUntilEpochMs = 2_000L,
        )

        val reset = resetAccessibilityGuardSession(active)

        assertEquals(AccessibilityGuardSession(generation = 5L), reset)
        assertEquals(reset, resetAccessibilityGuardSession(reset))
    }
}
