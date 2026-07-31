package li.songe.gkd.sdp.service

import li.songe.gkd.sdp.store.AccessibilityGuardSession
import li.songe.gkd.sdp.util.AccessibilityGuardPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun trackTransitionClearsAStaleTemporaryMarkerAndStartsANewSession() {
        val before = AccessibilityGuardSession(
            generation = 4L,
            temporaryShutdownExpected = true,
        )

        assertEquals(
            AccessibilityGuardSession(
                generation = 5L,
                disabledAtEpochMs = 9_000L,
            ),
            transitionAccessibilityGuardSession(
                session = before,
                mode = AccessibilityGuardPolicy.SessionMode.TRACK,
                currentAppBlocked = false,
                nowEpochMs = 9_000L,
            ),
        )
    }

    @Test
    fun suppressedTemporaryTransitionRetainsTheMarkerAndExistingSession() {
        val before = AccessibilityGuardSession(
            generation = 4L,
            disabledAtEpochMs = 1_000L,
            lastReminderIndex = 2,
            enforcementStarted = true,
            temporaryShutdownExpected = true,
        )

        assertEquals(
            before,
            transitionAccessibilityGuardSession(
                session = before,
                mode = AccessibilityGuardPolicy.SessionMode.SUPPRESSED_TEMPORARY,
                currentAppBlocked = true,
                nowEpochMs = 9_000L,
            ),
        )
    }

    @Test
    fun trackTransitionClearsOnlyTheMarkerWhenAnExistingSessionIsActive() {
        val before = AccessibilityGuardSession(
            generation = 4L,
            disabledAtEpochMs = 1_000L,
            lastReminderIndex = 2,
            enforcementStarted = true,
            temporaryShutdownExpected = true,
        )

        assertEquals(
            before.copy(temporaryShutdownExpected = false),
            transitionAccessibilityGuardSession(
                session = before,
                mode = AccessibilityGuardPolicy.SessionMode.TRACK,
                currentAppBlocked = false,
                nowEpochMs = 9_000L,
            ),
        )
    }

    @Test
    fun sideEffectFenceRequiresTheCurrentTrackGenerationAndEnabledFeature() {
        assertTrue(
            canApplyAccessibilityGuardSideEffect(
                expectedGeneration = 4L,
                currentGeneration = 4L,
                mode = AccessibilityGuardPolicy.SessionMode.TRACK,
                featureEnabled = true,
            )
        )
        assertFalse(
            canApplyAccessibilityGuardSideEffect(
                expectedGeneration = 4L,
                currentGeneration = 5L,
                mode = AccessibilityGuardPolicy.SessionMode.TRACK,
                featureEnabled = true,
            )
        )
        assertFalse(
            canApplyAccessibilityGuardSideEffect(
                expectedGeneration = 4L,
                currentGeneration = 4L,
                mode = AccessibilityGuardPolicy.SessionMode.SUPPRESSED_TEMPORARY,
                featureEnabled = true,
            )
        )
        assertFalse(
            canApplyAccessibilityGuardSideEffect(
                expectedGeneration = 4L,
                currentGeneration = 4L,
                mode = AccessibilityGuardPolicy.SessionMode.TRACK,
                featureEnabled = false,
            )
        )
    }

    @Test
    fun accessibilityGuardRequestFenceRejectsSupersededOrCancelledRequests() {
        assertTrue(isAccessibilityGuardRequestCurrent(4L, 4L, desired = true))
        assertFalse(isAccessibilityGuardRequestCurrent(4L, 5L, desired = true))
        assertFalse(isAccessibilityGuardRequestCurrent(4L, 4L, desired = false))
    }

    @Test
    fun pendingActivationMustBeInvalidatedBeforeAUserDisableCanReturn() {
        assertTrue(shouldInvalidateAccessibilityGuardActivation(activationInFlight = true))
        assertFalse(shouldInvalidateAccessibilityGuardActivation(activationInFlight = false))
    }
}
