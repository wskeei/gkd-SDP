package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.UsageGuardRecord
import li.songe.gkd.sdp.R
import java.time.Instant
import java.time.ZoneId

object UsageGuardReviewPolicy {
    data class RankedItem(
        val label: String,
        val count: Int,
    )

    data class RiskPeriod(
        val label: String,
        val count: Int,
        val labelRes: Int = R.string.review_period_calm,
    )

    data class Summary(
        val requestCount: Int,
        val totalRequestedMinutes: Int,
        val totalUsedSeconds: Long,
        val topApps: List<RankedItem>,
        val topTags: List<RankedItem>,
        val endReasonCounts: Map<Int, Int>,
        val riskPeriod: RiskPeriod,
    )

    data class WidgetSummary(
        val title: String,
        val metric: String,
        val hint: String,
        val titleRes: Int,
        val metricRes: Int,
        val hintRes: Int,
        val periodLabelRes: Int,
        val requestCount: Int = 0,
        val metricDurationSeconds: Long? = null,
        val metricTopApp: String? = null,
    )

    fun summarize(
        records: List<UsageGuardRecord>,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Summary {
        val effectiveRecords = records.filter { it.requestedAt <= now }
        val periodCounts = effectiveRecords
            .groupingBy { periodLabel(it.requestedAt, zoneId) }
            .eachCount()
        val riskPeriod = periodCounts.maxWithOrNull(
            compareBy<Map.Entry<String, Int>> { it.value }
                .thenBy { periodPriority(it.key) }
        )?.let { RiskPeriod(it.key, it.value, periodLabelRes(it.key)) }
            // i18n-ignore: legacy fallback or non-display heuristic data
            ?: RiskPeriod("平稳", 0, R.string.review_period_calm)

        return Summary(
            requestCount = effectiveRecords.size,
            totalRequestedMinutes = effectiveRecords.sumOf { it.requestedDurationMinutes },
            totalUsedSeconds = effectiveRecords.sumOf { effectiveUsedSeconds(it, now) },
            topApps = rankByCount(effectiveRecords.map { it.appName.ifBlank { it.appId } }),
            topTags = rankByCount(effectiveRecords.flatMap { it.tagNames }),
            endReasonCounts = effectiveRecords.groupingBy { it.endReason }.eachCount(),
            riskPeriod = riskPeriod,
        )
    }

    // i18n-ignore: legacy fallback or non-display heuristic data
    fun widgetSummary(summary: Summary, periodLabel: String = "今日"): WidgetSummary {
        val periodLabelRes = periodLabelRes(periodLabel)
        if (summary.requestCount == 0) {
            return WidgetSummary(
                // i18n-ignore: legacy fallback or non-display heuristic data
                title = "${periodLabel}申请 0 次",
                // i18n-ignore: legacy fallback or non-display heuristic data
                metric = "保持安静",
                // i18n-ignore: legacy fallback or non-display heuristic data
                hint = if (periodLabel == "今日") {
                    // i18n-ignore: legacy fallback or non-display heuristic data
                    "还没有新的使用申请。"
                } else {
                    // i18n-ignore: legacy fallback or non-display heuristic data
                    "${periodLabel}还没有新的使用申请。"
                },
                titleRes = R.string.usage_guard_widget_title_empty,
                metricRes = R.string.usage_guard_widget_metric_calm,
                // i18n-ignore: legacy fallback or non-display heuristic data
                hintRes = if (periodLabel == "今日") {
                    R.string.usage_guard_widget_hint_empty_today
                } else {
                    R.string.usage_guard_widget_hint_empty_period
                },
                periodLabelRes = periodLabelRes,
                requestCount = summary.requestCount,
            )
        }
        // i18n-ignore: legacy fallback or non-display heuristic data
        val topAppText = summary.topApps.firstOrNull()?.let { " · 高频 ${it.label}" }.orEmpty()
        return WidgetSummary(
            // i18n-ignore: legacy fallback or non-display heuristic data
            title = "${periodLabel}申请 ${summary.requestCount} 次",
            // i18n-ignore: legacy fallback or non-display heuristic data
            metric = "累计使用 ${formatUsedDuration(summary.totalUsedSeconds)}$topAppText",
            // i18n-ignore: legacy fallback or non-display heuristic data
            hint = riskHint(summary.riskPeriod).replace("今天", periodLabel),
            titleRes = R.string.usage_guard_widget_title,
            metricRes = if (summary.topApps.firstOrNull() == null) {
                R.string.usage_guard_widget_metric_used
            } else {
                R.string.usage_guard_widget_metric_used_top
            },
            hintRes = riskHintRes(summary.riskPeriod),
            periodLabelRes = periodLabelRes,
            requestCount = summary.requestCount,
            metricDurationSeconds = summary.totalUsedSeconds,
            metricTopApp = summary.topApps.firstOrNull()?.label,
        )
    }

    fun endReasonLabelRes(endReason: Int): Int = when (endReason) {
        UsageGuardRecord.END_REASON_ACTIVE -> R.string.usage_guard_end_active
        UsageGuardRecord.END_REASON_EXPIRED -> R.string.usage_guard_end_expired
        UsageGuardRecord.END_REASON_LEFT_APP -> R.string.usage_guard_end_left
        UsageGuardRecord.END_REASON_REPLACED -> R.string.usage_guard_end_replaced
        UsageGuardRecord.END_REASON_HOME_BUTTON -> R.string.usage_guard_end_home
        UsageGuardRecord.END_REASON_USER_TERMINATED -> R.string.usage_guard_end_terminated
        else -> R.string.usage_guard_end_unknown
    }

    fun endReasonLabel(endReason: Int): String {
        return when (endReason) {
            // i18n-ignore: legacy fallback or non-display heuristic data
            UsageGuardRecord.END_REASON_ACTIVE -> "进行中"
            // i18n-ignore: legacy fallback or non-display heuristic data
            UsageGuardRecord.END_REASON_EXPIRED -> "已到时"
            // i18n-ignore: legacy fallback or non-display heuristic data
            UsageGuardRecord.END_REASON_LEFT_APP -> "离开结束"
            // i18n-ignore: legacy fallback or non-display heuristic data
            UsageGuardRecord.END_REASON_REPLACED -> "被替换"
            // i18n-ignore: legacy fallback or non-display heuristic data
            UsageGuardRecord.END_REASON_HOME_BUTTON -> "回桌面"
            // i18n-ignore: legacy fallback or non-display heuristic data
            UsageGuardRecord.END_REASON_USER_TERMINATED -> "主动终止"
            // i18n-ignore: legacy fallback or non-display heuristic data
            else -> "未知"
        }
    }

    fun effectiveUsedSeconds(record: UsageGuardRecord, now: Long = System.currentTimeMillis()): Long {
        val endAt = when {
            record.endedAt > 0L -> record.endedAt
            record.expiresAt > 0L -> minOf(now, record.expiresAt)
            else -> now
        }
        return ((endAt - record.grantedAt).coerceAtLeast(0L) / 1_000L)
    }

    fun formatUsedDuration(totalSeconds: Long): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0L)
        // i18n-ignore: legacy fallback or non-display heuristic data
        if (safeSeconds < 60L) return "${safeSeconds} 秒"
        val minutes = safeSeconds / 60L
        val seconds = safeSeconds % 60L
        // i18n-ignore: legacy fallback or non-display heuristic data
        if (seconds == 0L) return "${minutes} 分钟"
        // i18n-ignore: legacy fallback or non-display heuristic data
        return "${minutes} 分 ${seconds} 秒"
    }

    private fun rankByCount(labels: List<String>): List<RankedItem> {
        return labels
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .map { RankedItem(label = it.first, count = it.second) }
    }

    private fun periodLabel(timestamp: Long, zoneId: ZoneId): String {
        val hour = Instant.ofEpochMilli(timestamp).atZone(zoneId).hour
        return when (hour) {
            // i18n-ignore: legacy fallback or non-display heuristic data
            in 5..10 -> "上午"
            // i18n-ignore: legacy fallback or non-display heuristic data
            in 11..13 -> "午间"
            // i18n-ignore: legacy fallback or non-display heuristic data
            in 14..17 -> "下午"
            // i18n-ignore: legacy fallback or non-display heuristic data
            in 18..20 -> "晚间"
            // i18n-ignore: legacy fallback or non-display heuristic data
            else -> "夜间"
        }
    }

    private fun periodLabelRes(label: String): Int = when (label) {
        // i18n-ignore: legacy fallback or non-display heuristic data
        "今日" -> R.string.usage_guard_period_today
        // i18n-ignore: legacy fallback or non-display heuristic data
        "近 24 小时" -> R.string.review_range_24h
        // i18n-ignore: legacy fallback or non-display heuristic data
        "近 7 天" -> R.string.review_range_7d
        // i18n-ignore: legacy fallback or non-display heuristic data
        "近 30 天" -> R.string.review_range_30d
        else -> R.string.usage_guard_period_today
    }

    private fun periodPriority(label: String): Int {
        return when (label) {
            // i18n-ignore: legacy fallback or non-display heuristic data
            "夜间" -> 4
            // i18n-ignore: legacy fallback or non-display heuristic data
            "晚间" -> 3
            // i18n-ignore: legacy fallback or non-display heuristic data
            "午间" -> 2
            // i18n-ignore: legacy fallback or non-display heuristic data
            "下午" -> 1
            else -> 0
        }
    }

    private fun riskHint(period: RiskPeriod): String {
        return when (period.label) {
            // i18n-ignore: legacy fallback or non-display heuristic data
            "夜间" -> "夜间申请偏多，睡前先收紧入口。"
            // i18n-ignore: legacy fallback or non-display heuristic data
            "晚间" -> "晚间申请偏多，先安排离线缓冲。"
            // i18n-ignore: legacy fallback or non-display heuristic data
            "午间" -> "午间申请偏多，休息前先定边界。"
            // i18n-ignore: legacy fallback or non-display heuristic data
            "下午" -> "下午申请偏多，注意任务切换成本。"
            // i18n-ignore: legacy fallback or non-display heuristic data
            "上午" -> "上午申请偏多，先保护开局节奏。"
            // i18n-ignore: legacy fallback or non-display heuristic data
            else -> "今天的申请节奏很安静。"
        }
    }

    private fun riskHintRes(period: RiskPeriod): Int = when (period.label) {
        // i18n-ignore: legacy fallback or non-display heuristic data
        "夜间" -> R.string.usage_guard_widget_risk_night
        // i18n-ignore: legacy fallback or non-display heuristic data
        "晚间" -> R.string.usage_guard_widget_risk_evening
        // i18n-ignore: legacy fallback or non-display heuristic data
        "午间" -> R.string.usage_guard_widget_risk_noon
        // i18n-ignore: legacy fallback or non-display heuristic data
        "下午" -> R.string.usage_guard_widget_risk_afternoon
        // i18n-ignore: legacy fallback or non-display heuristic data
        "上午" -> R.string.usage_guard_widget_risk_morning
        else -> R.string.usage_guard_widget_risk_calm
    }
}
