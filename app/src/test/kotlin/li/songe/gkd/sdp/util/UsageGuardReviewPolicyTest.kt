package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.UsageGuardRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class UsageGuardReviewPolicyTest {
    private val zoneId = ZoneId.systemDefault()

    @Test
    fun summarizeDayCountsRequestsMinutesRanksAndEndReasons() {
        val records = listOf(
            recordAt(9, "chat.app", "微信", listOf("回复消息"), 10, UsageGuardRecord.END_REASON_EXPIRED),
            recordAt(12, "chat.app", "微信", listOf("回复消息", "联系工作"), 5, UsageGuardRecord.END_REASON_LEFT_APP),
            recordAt(22, "video.app", "视频", listOf("其他"), 30, UsageGuardRecord.END_REASON_HOME_BUTTON),
        )

        val summary = UsageGuardReviewPolicy.summarize(
            records = records,
            now = at(23),
            zoneId = zoneId,
        )

        assertEquals(3, summary.requestCount)
        assertEquals(45, summary.totalRequestedMinutes)
        assertEquals("微信", summary.topApps.first().label)
        assertEquals(2, summary.topApps.first().count)
        assertEquals("回复消息", summary.topTags.first().label)
        assertEquals(2, summary.topTags.first().count)
        assertEquals(1, summary.endReasonCounts.getValue(UsageGuardRecord.END_REASON_EXPIRED))
        assertEquals(1, summary.endReasonCounts.getValue(UsageGuardRecord.END_REASON_LEFT_APP))
        assertEquals(1, summary.endReasonCounts.getValue(UsageGuardRecord.END_REASON_HOME_BUTTON))
        assertEquals("夜间", summary.riskPeriod.label)
        assertEquals(1, summary.riskPeriod.count)
    }

    @Test
    fun widgetSummaryPrefersHighSignalTopAppAndRiskPeriod() {
        val summary = UsageGuardReviewPolicy.summarize(
            records = listOf(
                recordAt(21, "chat.app", "微信", listOf("回复消息"), 10, UsageGuardRecord.END_REASON_EXPIRED),
                recordAt(22, "chat.app", "微信", listOf("回复消息"), 10, UsageGuardRecord.END_REASON_EXPIRED),
            ),
            now = at(23),
            zoneId = zoneId,
        )

        val widget = UsageGuardReviewPolicy.widgetSummary(summary)

        assertEquals("今日申请 2 次", widget.title)
        assertEquals("累计使用 20 分钟 · 高频 微信", widget.metric)
        assertEquals("夜间申请偏多，睡前先收紧入口。", widget.hint)
    }

    @Test
    fun emptyWidgetSummaryGivesCalmEmptyState() {
        val summary = UsageGuardReviewPolicy.summarize(
            records = emptyList(),
            now = at(10),
            zoneId = zoneId,
        )

        val widget = UsageGuardReviewPolicy.widgetSummary(summary)

        assertEquals("今日申请 0 次", widget.title)
        assertEquals("保持安静", widget.metric)
        assertEquals("还没有新的使用申请。", widget.hint)
    }

    @Test
    fun manualTerminationUsesActualElapsedTimeInSummaryAndWidget() {
        val requestedAt = at(9)
        val record = UsageGuardRecord(
            id = 99L,
            appId = "chat.app",
            appName = "微信",
            tagNames = listOf("回复消息"),
            reasonText = "回一条消息",
            requestedDurationMinutes = 2,
            requestedAt = requestedAt,
            grantedAt = requestedAt,
            expiresAt = requestedAt + 2 * 60_000L,
            endedAt = requestedAt + 10_000L,
            endReason = UsageGuardRecord.END_REASON_USER_TERMINATED,
        )

        val summary = UsageGuardReviewPolicy.summarize(
            records = listOf(record),
            now = requestedAt + 20_000L,
            zoneId = zoneId,
        )
        val widget = UsageGuardReviewPolicy.widgetSummary(summary)

        assertEquals(10, summary.totalUsedSeconds)
        assertEquals("累计使用 10 秒 · 高频 微信", widget.metric)
        assertEquals("主动终止", UsageGuardReviewPolicy.endReasonLabel(UsageGuardRecord.END_REASON_USER_TERMINATED))
    }

    private fun recordAt(
        hour: Int,
        appId: String,
        appName: String,
        tags: List<String>,
        minutes: Int,
        endReason: Int,
    ): UsageGuardRecord {
        val requestedAt = at(hour)
        return UsageGuardRecord(
            id = requestedAt,
            appId = appId,
            appName = appName,
            tagNames = tags,
            reasonText = "临时申请",
            requestedDurationMinutes = minutes,
            requestedAt = requestedAt,
            grantedAt = requestedAt,
            expiresAt = requestedAt + minutes * 60_000L,
            endedAt = requestedAt + minutes * 60_000L,
            endReason = endReason,
        )
    }

    private fun at(hour: Int): Long {
        return LocalDateTime.of(LocalDate.of(2026, 4, 9), java.time.LocalTime.of(hour, 0))
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }
}
