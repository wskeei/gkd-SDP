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
    fun widgetSummaryCanLabelASelectedReviewPeriod() {
        val summary = UsageGuardReviewPolicy.summarize(emptyList(), now = at(10), zoneId = zoneId)
        val widget = UsageGuardReviewPolicy.widgetSummary(summary, "近 30 天")

        assertEquals("近 30 天申请 0 次", widget.title)
        assertEquals("近 30 天还没有新的使用申请。", widget.hint)
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

    @Test
    fun futureRecordsAreExcludedFromSummary() {
        val summary = UsageGuardReviewPolicy.summarize(
            records = listOf(recordAt(12, "chat.app", "微信", emptyList(), 5, 1)),
            now = at(9),
            zoneId = zoneId,
        )

        assertEquals(0, summary.requestCount)
        assertEquals("平稳", summary.riskPeriod.label)
    }

    @Test
    fun riskPeriodsAndWidgetHintsCoverAllDayParts() {
        listOf(
            9 to "上午申请偏多，先保护开局节奏。",
            12 to "午间申请偏多，休息前先定边界。",
            14 to "下午申请偏多，注意任务切换成本。",
            18 to "晚间申请偏多，先安排离线缓冲。",
            22 to "夜间申请偏多，睡前先收紧入口。",
        ).forEach { (hour, hint) ->
            val summary = UsageGuardReviewPolicy.summarize(
                records = listOf(recordAt(hour, "app", "应用", emptyList(), 5, 1)),
                now = at(hour),
                zoneId = zoneId,
            )
            assertEquals(hint, UsageGuardReviewPolicy.widgetSummary(summary).hint)
        }
    }

    @Test
    fun endReasonLabelsCoverEveryStableState() {
        assertEquals("进行中", UsageGuardReviewPolicy.endReasonLabel(UsageGuardRecord.END_REASON_ACTIVE))
        assertEquals("已到时", UsageGuardReviewPolicy.endReasonLabel(UsageGuardRecord.END_REASON_EXPIRED))
        assertEquals("离开结束", UsageGuardReviewPolicy.endReasonLabel(UsageGuardRecord.END_REASON_LEFT_APP))
        assertEquals("被替换", UsageGuardReviewPolicy.endReasonLabel(UsageGuardRecord.END_REASON_REPLACED))
        assertEquals("回桌面", UsageGuardReviewPolicy.endReasonLabel(UsageGuardRecord.END_REASON_HOME_BUTTON))
        assertEquals("未知", UsageGuardReviewPolicy.endReasonLabel(999))
    }

    @Test
    fun effectiveUsedSecondsUsesExpiryAndCurrentTimeWithoutOverflow() {
        val requestedAt = at(9)
        val active = UsageGuardRecord(
            id = 1,
            appId = "app",
            appName = "应用",
            tagNames = emptyList(),
            reasonText = "synthetic",
            requestedDurationMinutes = 10,
            requestedAt = requestedAt,
            grantedAt = requestedAt,
            expiresAt = requestedAt + 10_000L,
            endedAt = 0L,
        )

        assertEquals(10L, UsageGuardReviewPolicy.effectiveUsedSeconds(active, requestedAt + 20_000L))
        assertEquals(
            20L,
            UsageGuardReviewPolicy.effectiveUsedSeconds(
                active.copy(expiresAt = 0L),
                requestedAt + 20_000L,
            ),
        )
    }

    @Test
    fun formatUsedDurationHandlesSecondsMinutesAndHours() {
        assertEquals("0 秒", UsageGuardReviewPolicy.formatUsedDuration(0))
        assertEquals("59 秒", UsageGuardReviewPolicy.formatUsedDuration(59))
        assertEquals("1 分钟", UsageGuardReviewPolicy.formatUsedDuration(60))
        assertEquals("1 分 1 秒", UsageGuardReviewPolicy.formatUsedDuration(61))
        assertEquals("60 分钟", UsageGuardReviewPolicy.formatUsedDuration(3_600))
    }

    @Test
    fun blankAppNamesAreExcludedFromTopAppRanking() {
        val summary = UsageGuardReviewPolicy.summarize(
            records = listOf(
                recordAt(9, "blank", "", emptyList(), 5, 1).copy(appId = "", appName = ""),
                recordAt(10, "named", "应用", emptyList(), 5, 1),
            ),
            now = at(11),
            zoneId = zoneId,
        )

        assertEquals(listOf("应用"), summary.topApps.map { it.label })
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
