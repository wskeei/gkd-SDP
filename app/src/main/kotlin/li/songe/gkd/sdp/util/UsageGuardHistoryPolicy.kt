package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.UsageGuardRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object UsageGuardHistoryPolicy {
    data class DayBucket(
        val date: LocalDate,
        val records: List<UsageGuardRecord>,
    )

    fun bucketRecordsByDate(
        records: List<UsageGuardRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<DayBucket> {
        return records
            .groupBy { Instant.ofEpochMilli(it.requestedAt).atZone(zoneId).toLocalDate() }
            .toList()
            .sortedByDescending { it.first }
            .map { (date, dayRecords) ->
                DayBucket(
                    date = date,
                    records = dayRecords.sortedByDescending { it.requestedAt },
                )
            }
    }

    fun dayRange(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Pair<Long, Long> {
        val startAt = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endAt = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return startAt to endAt
    }

    fun recordsForDate(
        records: List<UsageGuardRecord>,
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<UsageGuardRecord> {
        return records
            .filter { Instant.ofEpochMilli(it.requestedAt).atZone(zoneId).toLocalDate() == date }
            .sortedByDescending { it.requestedAt }
    }
}
