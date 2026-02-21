package li.songe.gkd.sdp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

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

    @Test
    fun autoReenableUiStateComputesDailyRemaining() {
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 2, 21, 10, 0).atZone(zoneId).toInstant().toEpochMilli()
        val dayStart = LocalDateTime.of(2026, 2, 21, 0, 0).atZone(zoneId).toInstant().toEpochMilli()
        val state = FocusLockVm.evaluateAutoReenableUiState(
            intervalMinutes = 30,
            lastChangedAt = 0L,
            dailyDisableLimit = 3,
            dailyDisableUsed = 1,
            dailyDisableDayStartAt = dayStart,
            now = now
        )

        assertEquals(3, state.dailyDisableLimit)
        assertEquals(1, state.dailyDisableUsed)
        assertEquals(2, state.dailyDisableRemaining)
        assertEquals(dayStart + 24L * 60 * 60 * 1000, state.nextDailyResetAt)
    }

    @Test
    fun dailyDisableLimitIsNormalizedInUiState() {
        val now = 4_000_000_000L
        val state = FocusLockVm.evaluateAutoReenableUiState(
            intervalMinutes = 30,
            lastChangedAt = 0L,
            dailyDisableLimit = 99,
            dailyDisableUsed = 99,
            dailyDisableDayStartAt = 0L,
            now = now
        )

        assertEquals(5, state.dailyDisableLimit)
        assertEquals(0, state.dailyDisableUsed)
        assertEquals(5, state.dailyDisableRemaining)
    }
}
