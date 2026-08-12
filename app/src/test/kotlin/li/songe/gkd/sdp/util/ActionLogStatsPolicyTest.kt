package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.DailyStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ActionLogStatsPolicyTest {
    @Test
    fun recentWindowStartUsesLocalCalendarDayBoundary() {
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 4, 8, 15, 30)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val start = ActionLogStatsPolicy.windowStartEpochMs(
            now = now,
            days = 14,
            zoneId = zoneId,
        )

        val expected = LocalDateTime.of(2026, 3, 26, 0, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        assertEquals(expected, start)
    }

    @Test
    fun normalizeDailyStatsFillsMissingDaysWithZero() {
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 4, 8, 15, 30)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val normalized = ActionLogStatsPolicy.normalizeDailyStats(
            rawStats = listOf(
                DailyStat(date = "2026-04-06", count = 3),
                DailyStat(date = "2026-04-08", count = 1),
            ),
            now = now,
            days = 3,
            zoneId = zoneId,
        )

        assertEquals(
            listOf(
                DailyStat(date = "2026-04-06", count = 3),
                DailyStat(date = "2026-04-07", count = 0),
                DailyStat(date = "2026-04-08", count = 1),
            ),
            normalized,
        )
    }

    @Test
    fun normalizeDailyStatsDropsRowsOutsideRequestedWindow() {
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 4, 8, 15, 30)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val normalized = ActionLogStatsPolicy.normalizeDailyStats(
            rawStats = listOf(
                DailyStat(date = "2026-04-01", count = 9),
                DailyStat(date = "2026-04-08", count = 1),
            ),
            now = now,
            days = 3,
            zoneId = zoneId,
        )

        assertEquals(3, normalized.size)
        assertEquals("2026-04-06", normalized.first().date)
        assertEquals(0, normalized[0].count)
        assertEquals(1, normalized.last().count)
    }

    @Test
    fun windowStartAndRefreshRejectNonPositiveDaysAndStayPositive() {
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 4, 8, 15, 30)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        assertThrows { ActionLogStatsPolicy.windowStartEpochMs(now, 0, zoneId) }
        assertTrue(ActionLogStatsPolicy.nextWindowRefreshDelayMs(now, zoneId) > 0L)
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected failure")
        } catch (_: IllegalArgumentException) {
        }
    }
}
