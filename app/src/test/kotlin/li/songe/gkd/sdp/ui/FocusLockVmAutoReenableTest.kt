package li.songe.gkd.sdp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusLockVmAutoReenableTest {
    @Test
    fun updateIntervalRejectedDuringCooldown() {
        val now = 1_000_000_000L
        val result = FocusLockVm.evaluateAutoReenableIntervalUpdate(
            currentIntervalMinutes = 30,
            lastChangedAt = now - (60L * 60 * 1000),
            requestedIntervalMinutes = 120,
            now = now
        )
        assertFalse(result.accepted)
        assertTrue(result.remainingCooldownMs > 0)
    }

    @Test
    fun autoReenableUiStateCooldownDisablesEdit() {
        val now = 2_000_000_000L
        val state = FocusLockVm.evaluateAutoReenableUiState(
            intervalMinutes = 30,
            lastChangedAt = now - (60L * 60 * 1000),
            now = now
        )
        assertFalse(state.canEditInterval)
        assertTrue(state.nextEditableAt > now)
    }

    @Test
    fun autoReenableUiStateComputesNextEnforceAt() {
        val now = 3_000_000_000L
        val state = FocusLockVm.evaluateAutoReenableUiState(
            intervalMinutes = 15,
            lastChangedAt = 0L,
            now = now
        )
        assertTrue(state.canEditInterval)
        assertEquals(now + 15L * 60_000L, state.nextEnforceAt)
    }
}
