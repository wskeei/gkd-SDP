package li.songe.gkd.sdp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AutoReenableDisableGuardTest {
    private val zoneId = ZoneId.systemDefault()

    @Test
    fun freshDayFirstDisableAllowed() {
        val now = LocalDateTime.of(2026, 2, 21, 8, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val (result, state) = AutoReenableDisableGuard.evaluateDisableAttempt(
            limit = 1,
            used = 0,
            dayStartAt = 0L,
            now = now,
            consume = true
        )

        assertTrue(result.allowed)
        assertEquals(1, result.used)
        assertEquals(0, result.remaining)
        assertEquals(result.dayStartAt, state.dayStartAt)
    }

    @Test
    fun usedAtLimitBlocksDisable() {
        val now = LocalDateTime.of(2026, 2, 21, 8, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val dayStart = LocalDateTime.of(2026, 2, 21, 0, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val (result, _) = AutoReenableDisableGuard.evaluateDisableAttempt(
            limit = 1,
            used = 1,
            dayStartAt = dayStart,
            now = now,
            consume = true
        )

        assertFalse(result.allowed)
        assertEquals(1, result.used)
        assertEquals(0, result.remaining)
    }

    @Test
    fun nextLocalDayResetsCounter() {
        val now = LocalDateTime.of(2026, 2, 22, 8, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val previousDayStart = LocalDateTime.of(2026, 2, 21, 0, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val (result, _) = AutoReenableDisableGuard.evaluateDisableAttempt(
            limit = 2,
            used = 2,
            dayStartAt = previousDayStart,
            now = now,
            consume = true
        )

        assertTrue(result.allowed)
        assertEquals(1, result.used)
        assertEquals(1, result.remaining)
    }

    @Test
    fun batchConsumeIncrementsByOne() {
        val now = LocalDateTime.of(2026, 2, 21, 8, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val dayStart = LocalDateTime.of(2026, 2, 21, 0, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val (result, _) = AutoReenableDisableGuard.evaluateDisableAttempt(
            limit = 5,
            used = 2,
            dayStartAt = dayStart,
            now = now,
            consume = true
        )

        assertEquals(3, result.used)
        assertEquals(2, result.remaining)
    }
}
