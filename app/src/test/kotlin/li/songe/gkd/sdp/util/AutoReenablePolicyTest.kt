package li.songe.gkd.sdp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AutoReenablePolicyTest {
    @Test
    fun normalizeIntervalClampsToBounds() {
        assertEquals(0, AutoReenablePolicy.normalizeIntervalMinutes(-1))
        assertEquals(240, AutoReenablePolicy.normalizeIntervalMinutes(999))
    }

    @Test
    fun cooldownBlocksEditBefore72Hours() {
        val now = 1_000_000_000L
        val last = now - (71L * 60 * 60 * 1000)
        assertFalse(AutoReenablePolicy.canChangeInterval(last, now))
    }

    @Test
    fun cooldownAllowsEditAt72HoursOrLater() {
        val now = 1_000_000_000L
        val last = now - (72L * 60 * 60 * 1000)
        assertTrue(AutoReenablePolicy.canChangeInterval(last, now))
    }

    @Test
    fun normalizeDailyDisableLimitClampsToBounds() {
        assertEquals(1, AutoReenablePolicy.normalizeDailyDisableLimit(0))
        assertEquals(5, AutoReenablePolicy.normalizeDailyDisableLimit(9))
    }

    @Test
    fun shouldResetDailyCounterOnlyWhenCrossingLocalDay() {
        val zoneId = ZoneId.systemDefault()
        val sameDayNow = LocalDateTime.of(2026, 2, 21, 9, 30)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val sameDayDayStart = LocalDateTime.of(2026, 2, 21, 0, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val nextDayNow = LocalDateTime.of(2026, 2, 22, 9, 30)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        assertFalse(AutoReenablePolicy.shouldResetDailyCounter(sameDayDayStart, sameDayNow))
        assertTrue(AutoReenablePolicy.shouldResetDailyCounter(sameDayDayStart, nextDayNow))
    }
}
