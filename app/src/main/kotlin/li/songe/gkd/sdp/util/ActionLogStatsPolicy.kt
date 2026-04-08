package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.DailyStat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ActionLogStatsPolicy {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun windowStartEpochMs(
        now: Long,
        days: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val (startDate, _) = localDateRange(
            now = now,
            days = days,
            zoneId = zoneId,
        )
        return startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun normalizeDailyStats(
        rawStats: List<DailyStat>,
        now: Long,
        days: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<DailyStat> {
        val (startDate, endDate) = localDateRange(
            now = now,
            days = days,
            zoneId = zoneId,
        )
        val allowedDates = generateSequence(startDate) { date ->
            date.plusDays(1).takeIf { it <= endDate }
        }.map { it.format(dateFormatter) }.toSet()
        val countByDate = rawStats
            .asSequence()
            .filter { it.date in allowedDates }
            .associate { it.date to it.count }

        return (0 until days).map { index ->
            val date = startDate.plusDays(index.toLong()).format(dateFormatter)
            DailyStat(
                date = date,
                count = countByDate[date] ?: 0,
            )
        }
    }

    fun nextWindowRefreshDelayMs(
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val nextDayStart = localDate(now, zoneId)
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        return (nextDayStart - now).coerceAtLeast(1L)
    }

    fun localDate(
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): LocalDate {
        return Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
    }

    private fun localDateRange(
        now: Long,
        days: Int,
        zoneId: ZoneId,
    ): Pair<LocalDate, LocalDate> {
        require(days > 0)
        val endDate = localDate(now, zoneId)
        val startDate = endDate.minusDays((days - 1).toLong())
        return startDate to endDate
    }
}
