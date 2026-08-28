package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.UsageGuardRecord
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
        )?.let { RiskPeriod(it.key, it.value) } ?: RiskPeriod("平稳", 0)

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

    fun widgetSummary(summary: Summary, periodLabel: String = "今日"): WidgetSummary {
        if (summary.requestCount == 0) {
            return WidgetSummary(
                title = "${periodLabel}申请 0 次",
                metric = "保持安静",
                hint = if (periodLabel == "今日") {
                    "还没有新的使用申请。"
                } else {
                    "${periodLabel}还没有新的使用申请。"
                },
            )
        }
        val topAppText = summary.topApps.firstOrNull()?.let { " · 高频 ${it.label}" }.orEmpty()
        return WidgetSummary(
            title = "${periodLabel}申请 ${summary.requestCount} 次",
            metric = "累计使用 ${formatUsedDuration(summary.totalUsedSeconds)}$topAppText",
            hint = riskHint(summary.riskPeriod).replace("今天", periodLabel),
        )
    }

    fun endReasonLabel(endReason: Int): String {
        return when (endReason) {
            UsageGuardRecord.END_REASON_ACTIVE -> "进行中"
            UsageGuardRecord.END_REASON_EXPIRED -> "已到时"
            UsageGuardRecord.END_REASON_LEFT_APP -> "离开结束"
            UsageGuardRecord.END_REASON_REPLACED -> "被替换"
            UsageGuardRecord.END_REASON_HOME_BUTTON -> "回桌面"
            UsageGuardRecord.END_REASON_USER_TERMINATED -> "主动终止"
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
        if (safeSeconds < 60L) return "${safeSeconds} 秒"
        val minutes = safeSeconds / 60L
        val seconds = safeSeconds % 60L
        if (seconds == 0L) return "${minutes} 分钟"
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
            in 5..10 -> "上午"
            in 11..13 -> "午间"
            in 14..17 -> "下午"
            in 18..20 -> "晚间"
            else -> "夜间"
        }
    }

    private fun periodPriority(label: String): Int {
        return when (label) {
            "夜间" -> 4
            "晚间" -> 3
            "午间" -> 2
            "下午" -> 1
            else -> 0
        }
    }

    private fun riskHint(period: RiskPeriod): String {
        return when (period.label) {
            "夜间" -> "夜间申请偏多，睡前先收紧入口。"
            "晚间" -> "晚间申请偏多，先安排离线缓冲。"
            "午间" -> "午间申请偏多，休息前先定边界。"
            "下午" -> "下午申请偏多，注意任务切换成本。"
            "上午" -> "上午申请偏多，先保护开局节奏。"
            else -> "今天的申请节奏很安静。"
        }
    }
}
