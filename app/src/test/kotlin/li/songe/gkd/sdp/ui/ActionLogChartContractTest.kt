package li.songe.gkd.sdp.ui

import li.songe.gkd.sdp.data.DailyStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ActionLogChartContractTest {
    @Test
    fun statsUiStateAlwaysContainsFourteenOrderedDays() {
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 4, 8, 8, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val state = ActionLogVm.evaluateStatsUiState(
            rawStats = listOf(DailyStat("2026-04-08", 4)),
            now = now,
            days = 14,
            zoneId = zoneId,
        )

        assertTrue(state.hasAnyStats)
        assertEquals(14, state.stats.size)
        assertEquals("2026-03-26", state.stats.first().date)
        assertEquals("2026-04-08", state.stats.last().date)
        assertEquals(4, state.stats.last().count)
    }

    @Test
    fun statsUiStatePreservesEmptyPlaceholderSignalWhenRawStatsAreEmpty() {
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 4, 8, 8, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val state = ActionLogVm.evaluateStatsUiState(
            rawStats = emptyList(),
            now = now,
            days = 14,
            zoneId = zoneId,
        )

        assertFalse(state.hasAnyStats)
        assertEquals(14, state.stats.size)
        assertTrue(state.stats.all { it.count == 0 })
    }

    @Test
    fun filteredStatsStillNormalizeSparseRowsIntoVmState() {
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 4, 8, 8, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val state = ActionLogVm.evaluateStatsUiState(
            rawStats = listOf(DailyStat("2026-04-07", 2)),
            now = now,
            days = 3,
            zoneId = zoneId,
        )

        assertTrue(state.hasAnyStats)
        assertEquals(
            listOf("2026-04-06", "2026-04-07", "2026-04-08"),
            state.stats.map { it.date },
        )
        assertEquals(listOf(0, 2, 0), state.stats.map { it.count })
    }

    @Test
    fun statsWindowRefreshesAfterCrossingLocalMidnight() {
        val zoneId = ZoneId.systemDefault()
        val beforeMidnight = LocalDateTime.of(2026, 4, 8, 23, 59)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val sameDay = LocalDateTime.of(2026, 4, 8, 23, 59, 30)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val nextDay = LocalDateTime.of(2026, 4, 9, 0, 0, 1)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        assertFalse(
            ActionLogVm.shouldRefreshStatsWindow(
                anchorNow = beforeMidnight,
                currentNow = sameDay,
                zoneId = zoneId,
            )
        )
        assertTrue(
            ActionLogVm.shouldRefreshStatsWindow(
                anchorNow = beforeMidnight,
                currentNow = nextDay,
                zoneId = zoneId,
            )
        )
    }
}
