package li.songe.gkd.sdp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityGuardControlPolicyTest {
    @Test
    fun guardCanBeEnabledWhenAccessibilityIsAlreadyOff() {
        assertEquals(
            AccessibilityGuardControlPolicy.EnableDecision.ALLOW,
            AccessibilityGuardControlPolicy.enableDecision(
                strictChannelAvailable = true,
                useA11yMode = true,
                accessibilityComponentEnabled = false,
            ),
        )
    }

    @Test
    fun unavailableStrictChannelHidesTheGuard() {
        assertEquals(
            AccessibilityGuardControlPolicy.EnableDecision.UNAVAILABLE_CHANNEL,
            AccessibilityGuardControlPolicy.enableDecision(
                strictChannelAvailable = false,
                useA11yMode = true,
                accessibilityComponentEnabled = false,
            ),
        )
    }

    @Test
    fun automationModeCannotEnableAccessibilityGuard() {
        assertEquals(
            AccessibilityGuardControlPolicy.EnableDecision.REQUIRE_A11Y_MODE,
            AccessibilityGuardControlPolicy.enableDecision(
                strictChannelAvailable = true,
                useA11yMode = false,
                accessibilityComponentEnabled = false,
            ),
        )
    }

    @Test
    fun activeLockBlocksDisableBeforeQuotaIsConsidered() {
        assertEquals(
            AccessibilityGuardControlPolicy.DisableDecision.BLOCKED_BY_LOCK,
            AccessibilityGuardControlPolicy.disableDecision(
                currentlyEnabled = true,
                anyActiveLock = true,
                quotaAllowed = false,
            ),
        )
    }

    @Test
    fun unlockedDisableUsesTheSharedQuotaDecision() {
        assertEquals(
            AccessibilityGuardControlPolicy.DisableDecision.ALLOW,
            AccessibilityGuardControlPolicy.disableDecision(
                currentlyEnabled = true,
                anyActiveLock = false,
                quotaAllowed = true,
            ),
        )
        assertEquals(
            AccessibilityGuardControlPolicy.DisableDecision.BLOCKED_BY_QUOTA,
            AccessibilityGuardControlPolicy.disableDecision(
                currentlyEnabled = true,
                anyActiveLock = false,
                quotaAllowed = false,
            ),
        )
    }

    @Test
    fun disablingAnAlreadyOffGuardIsANoOp() {
        assertEquals(
            AccessibilityGuardControlPolicy.DisableDecision.NO_CHANGE,
            AccessibilityGuardControlPolicy.disableDecision(
                currentlyEnabled = false,
                anyActiveLock = true,
                quotaAllowed = false,
            ),
        )
    }

    @Test
    fun autoReenableRequiresEnrollmentAndCurrentOffState() {
        assertTrue(
            AccessibilityGuardControlPolicy.shouldAutoReenable(
                strictChannelAvailable = true,
                useA11yMode = true,
                armed = true,
                currentlyEnabled = false,
            ),
        )
        assertFalse(
            AccessibilityGuardControlPolicy.shouldAutoReenable(
                strictChannelAvailable = true,
                useA11yMode = true,
                armed = false,
                currentlyEnabled = false,
            ),
        )
        assertFalse(
            AccessibilityGuardControlPolicy.shouldAutoReenable(
                strictChannelAvailable = true,
                useA11yMode = true,
                armed = true,
                currentlyEnabled = true,
            ),
        )
    }

    @Test
    fun autoReenableDoesNotCrossChannelOrModeBoundaries() {
        assertFalse(
            AccessibilityGuardControlPolicy.shouldAutoReenable(
                strictChannelAvailable = false,
                useA11yMode = true,
                armed = true,
                currentlyEnabled = false,
            ),
        )
        assertFalse(
            AccessibilityGuardControlPolicy.shouldAutoReenable(
                strictChannelAvailable = true,
                useA11yMode = false,
                armed = true,
                currentlyEnabled = false,
            ),
        )
    }
}
