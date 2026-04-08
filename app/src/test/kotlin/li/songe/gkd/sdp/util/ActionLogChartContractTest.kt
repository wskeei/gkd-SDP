package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.DailyStat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ActionLogChartContractTest {
    @Test
    fun normalizedChartSeriesAlwaysContainsFourteenOrderedDays() {
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 4, 8, 8, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val stats = ActionLogStatsPolicy.normalizeDailyStats(
            rawStats = listOf(DailyStat("2026-04-08", 4)),
            now = now,
            days = 14,
            zoneId = zoneId,
        )

        assertEquals(14, stats.size)
        assertEquals("2026-03-26", stats.first().date)
        assertEquals("2026-04-08", stats.last().date)
        assertEquals(4, stats.last().count)
    }
}
