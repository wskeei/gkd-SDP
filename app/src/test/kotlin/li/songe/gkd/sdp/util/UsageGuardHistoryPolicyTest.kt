package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.UsageGuardRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class UsageGuardHistoryPolicyTest {
    private val zoneId = ZoneId.systemDefault()

    @Test
    fun bucketRecordsByDateGroupsTodayBeforeOlderDays() {
        val records = listOf(
            recordAt(2026, 4, 9, 21, 0, "night.app"),
            recordAt(2026, 4, 9, 8, 0, "morning.app"),
            recordAt(2026, 4, 8, 18, 0, "yesterday.app"),
        )

        val grouped = UsageGuardHistoryPolicy.bucketRecordsByDate(records, zoneId)

        assertEquals(LocalDate.of(2026, 4, 9), grouped[0].date)
        assertEquals(2, grouped[0].records.size)
        assertEquals(LocalDate.of(2026, 4, 8), grouped[1].date)
    }

    @Test
    fun recordsForDateReturnsOnlySelectedDayRecords() {
        val records = listOf(
            recordAt(2026, 4, 9, 21, 0, "today.app"),
            recordAt(2026, 4, 8, 18, 0, "yesterday.app"),
        )

        val filtered = UsageGuardHistoryPolicy.recordsForDate(
            records = records,
            date = LocalDate.of(2026, 4, 8),
            zoneId = zoneId,
        )

        assertEquals(listOf("yesterday.app"), filtered.map { it.appId })
    }

    @Test
    fun dayRangeCoversWholeSelectedLocalDate() {
        val (startAt, endAt) = UsageGuardHistoryPolicy.dayRange(
            date = LocalDate.of(2026, 4, 8),
            zoneId = zoneId,
        )

        val start = java.time.Instant.ofEpochMilli(startAt).atZone(zoneId).toLocalDateTime()
        val end = java.time.Instant.ofEpochMilli(endAt - 1).atZone(zoneId).toLocalDateTime()

        assertEquals(LocalDateTime.of(2026, 4, 8, 0, 0), start)
        assertEquals(LocalDateTime.of(2026, 4, 8, 23, 59, 59, 999_000_000), end)
    }

    private fun recordAt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        appId: String,
    ): UsageGuardRecord {
        val requestedAt = LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        return UsageGuardRecord(
            id = requestedAt,
            appId = appId,
            appName = appId,
            tagNames = listOf("查资料"),
            reasonText = "临时申请",
            requestedDurationMinutes = 15,
            requestedAt = requestedAt,
            grantedAt = requestedAt,
            expiresAt = requestedAt + 15 * 60_000L,
        )
    }
}
