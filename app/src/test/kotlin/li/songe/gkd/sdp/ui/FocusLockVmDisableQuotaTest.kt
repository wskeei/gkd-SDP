package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.util.AutoReenableDisableGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FocusLockVmDisableQuotaTest {
    private val zoneId = ZoneId.systemDefault()

    @Test
    fun disableTransitionAllowedThenBlockedWithinSameDay() {
        val now = LocalDateTime.of(2026, 2, 21, 10, 0).atZone(zoneId).toInstant().toEpochMilli()
        val dayStart = LocalDateTime.of(2026, 2, 21, 0, 0).atZone(zoneId).toInstant().toEpochMilli()

        assertTrue(FocusLockVm.shouldConsumeDisableQuota(currentEnabled = true, requestedEnabled = false))

        val (first, firstState) = AutoReenableDisableGuard.evaluateDisableAttempt(
            limit = 1,
            used = 0,
            dayStartAt = dayStart,
            now = now,
            consume = true
        )
        assertTrue(first.allowed)

        val (second, _) = AutoReenableDisableGuard.evaluateDisableAttempt(
            limit = first.limit,
            used = firstState.used,
            dayStartAt = firstState.dayStartAt,
            now = now,
            consume = true
        )
        assertFalse(second.allowed)
    }

    @Test
    fun dayRolloverAllowsDisableAgain() {
        val dayStart = LocalDateTime.of(2026, 2, 21, 0, 0).atZone(zoneId).toInstant().toEpochMilli()
        val nextDay = LocalDateTime.of(2026, 2, 22, 10, 0).atZone(zoneId).toInstant().toEpochMilli()
        val (result, _) = AutoReenableDisableGuard.evaluateDisableAttempt(
            limit = 1,
            used = 1,
            dayStartAt = dayStart,
            now = nextDay,
            consume = true
        )
        assertTrue(result.allowed)
    }
}
