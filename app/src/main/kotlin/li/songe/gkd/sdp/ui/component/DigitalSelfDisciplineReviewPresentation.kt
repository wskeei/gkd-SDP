package li.songe.gkd.sdp.ui.component

import li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy
import li.songe.gkd.sdp.util.SelfControlIntervalPolicy
import li.songe.gkd.sdp.util.UsageRequestRhythmPolicy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import li.songe.gkd.sdp.R

/** Pure text and chart data for the digital self-discipline review page. */
object DigitalSelfDisciplineReviewPresentation {
    const val MAX_TREND_POINTS = 30
    val emptyText: String = "所选范围暂无可绘制的有效样本"

    data class MetricCard(
        val label: String,
        val value: String,
        val supportingText: String? = null,
    )

    data class CoverageRow(
        val text: String,
    )

    data class TrendPoint(
        val label: String,
        val value: Double,
        val sampleCount: Int,
        val occurredAt: Long,
    )

    data class TrendPresentation(
        val metric: DigitalSelfDisciplineReviewPolicy.ReviewMetric,
        val metricLabel: String,
        val currentAverageText: String,
        val previousAverageText: String,
        val deltaText: String,
        val coverageText: String,
        val points: List<TrendPoint>,
        val textRows: List<String>,
        val semanticSummary: String,
        val empty: Boolean,
    )

    data class RankedBar(
        val label: String,
        val countText: String,
        val shareText: String,
        val share: Float,
    )

    data class RecentRow(
        val primaryText: String,
        val secondaryText: String,
    )

    data class PagePresentation(
        val overview: List<MetricCard>,
        val coverage: CoverageRow,
        val trend: TrendPresentation,
        val distributions: List<Pair<String, List<RankedBar>>>,
        val recentRows: List<RecentRow>,
    )

    data class ChartPoint(
        val label: String,
        val valueMs: Long,
    )

    fun defaultMetric(type: DigitalSelfDisciplineReviewPolicy.ReviewType): DigitalSelfDisciplineReviewPolicy.ReviewMetric =
        if (type == DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest) {
            DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO
        } else {
            DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL
        }

    fun trend(
        summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary,
        metric: DigitalSelfDisciplineReviewPolicy.ReviewMetric = defaultMetric(summary.reviewType),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): TrendPresentation {
        val safeMetric = when {
            summary.reviewType == DigitalSelfDisciplineReviewPolicy.ReviewType.InterceptAttempt ->
                DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL
            metric == DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL ->
                DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO
            else -> metric
        }
        val rows = summary.trendIntervals.mapNotNull { item ->
            valueFor(item, safeMetric)?.let { item to it }
        }
        val points = when {
            rows.isEmpty() -> emptyList()
            summary.range == DigitalSelfDisciplineReviewPolicy.Range.Today && rows.size <= 24 ->
                rows.map { (item, value) ->
                    TrendPoint(
                        label = formatTime(item.occurredAt, zoneId),
                        value = value,
                        sampleCount = 1,
                        occurredAt = item.occurredAt,
                    )
                }
            summary.range == DigitalSelfDisciplineReviewPolicy.Range.Today ->
                bucketByHour(rows, summary, zoneId)
            else -> bucketByDate(rows, zoneId)
        }
        val average = metricValue(summary, safeMetric)
        val previous = when (safeMetric) {
            DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO ->
                summary.comparison.previousRatioAverage
            DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP,
            DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL ->
                summary.comparison.previousIntervalAverageMs?.toDouble()
        }
        val delta = when (safeMetric) {
            DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO ->
                summary.comparison.ratioDelta
            DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP,
            DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL ->
                summary.comparison.intervalDeltaMs?.toDouble()
        }
        val currentText = formatMetricValue(average, safeMetric)
        val previousText = formatMetricValue(previous, safeMetric)
        val deltaText = when {
            delta == null -> "—"
            delta == 0.0 -> "0.0"
            delta > 0.0 -> "+${formatMetricValue(delta, safeMetric)}"
            else -> "-${formatMetricValue(abs(delta), safeMetric)}"
        }
        val validCount = coverageValidCount(summary, safeMetric)
        val excludedCount = when (safeMetric) {
            DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO -> summary.coverage.excludedRatioCount
            DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP,
            DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL -> summary.coverage.excludedIntervalCount
        }
        val coverageText = "有效样本 $validCount 条 / 总记录 ${summary.coverage.eventCount} 条 · 图形点 ${points.size} 个" +
            if (excludedCount > 0) {
                " · 未纳入 $excludedCount 条"
            } else {
                ""
            }
        val textRows = points.map { point ->
            "${point.label}：${formatMetricValue(point.value, safeMetric)}" +
                if (point.sampleCount > 1) "，平均 ${point.sampleCount} 条" else ""
        }
        val semantic = "${summary.range.label}${metricLabel(safeMetric)}趋势；本期平均 $currentText；上一周期 $previousText；差值 $deltaText；$coverageText。"
        return TrendPresentation(
            metric = safeMetric,
            metricLabel = metricLabel(safeMetric),
            currentAverageText = currentText,
            previousAverageText = previousText,
            deltaText = deltaText,
            coverageText = coverageText,
            points = points,
            textRows = textRows,
            semanticSummary = semantic,
            empty = points.isEmpty(),
        )
    }

    fun page(
        summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary,
        metric: DigitalSelfDisciplineReviewPolicy.ReviewMetric = defaultMetric(summary.reviewType),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PagePresentation {
        val trend = trend(summary, metric, zoneId)
        val overview = if (summary.reviewType == DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest) {
            listOf(
                MetricCard("申请次数", "${summary.coverage.eventCount} 次"),
                MetricCard("申请总时长", "${summary.usageDetails?.totalRequestedMinutes ?: 0L} 分钟"),
                MetricCard("平均未使用间隔", formatMetricValue(summary.intervalStats.averageMs?.toDouble(), DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP)),
                MetricCard("平均间用比", formatMetricValue(summary.ratioStats?.average, DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO)),
            )
        } else {
            listOf(
                MetricCard("拦截次数", "${summary.coverage.eventCount} 次"),
                MetricCard("有效间隔", "${summary.coverage.validIntervalCount} 条"),
                MetricCard("平均间隔", formatMetricValue(summary.intervalStats.averageMs?.toDouble(), DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL)),
                MetricCard("中位间隔", formatMetricValue(summary.intervalStats.medianMs?.toDouble(), DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL)),
            )
        }
        val distributions = summary.usageDetails?.let { details ->
            listOf(
                "应用分布" to rankedBars(details.appBreakdown),
                "标签分布" to rankedBars(details.tagBreakdown),
                "结束状态" to rankedBars(details.endReasonBreakdown),
                "集中时段" to listOfNotNull(details.busiestPeriod).map { rankedBars(listOf(it)).first() },
            )
        } ?: listOf("高频拦截目标" to rankedBars(summary.rankedTargets))
        val recentRows = summary.recentIntervals.map { item ->
            val time = formatTime(item.occurredAt, zoneId)
            val metricText = if (summary.reviewType == DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest) {
                val tags = item.tagNames.map { it.trim() }.filter { it.isNotEmpty() }
                    .ifEmpty { listOf("—") }
                    .joinToString("、")
                "间用比 ${formatMetricValue(item.ratio, DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO)} · 未使用间隔 ${formatMetricValue(item.intervalMs?.toDouble(), DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP)} · 申请 ${item.requestedDurationMinutes ?: 0} 分钟 · 标签：$tags · 结束状态：${endReasonLabel(item.endReason)}"
            } else {
                "间隔 ${formatMetricValue(item.intervalMs?.toDouble(), DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL)} · ${eventKindLabel(item.eventKind)}"
            }
            RecentRow(
                primaryText = "$time · ${item.label}",
                secondaryText = metricText,
            )
        }
        return PagePresentation(
            overview = overview,
            coverage = CoverageRow(coverageText(summary)),
            trend = trend,
            distributions = distributions,
            recentRows = recentRows,
        )
    }

    fun chartPoints(summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary): List<ChartPoint> =
        trend(summary).points.map { ChartPoint(it.label, it.value.toLong()) }

    fun formatTrendValue(
        value: Double,
        metric: DigitalSelfDisciplineReviewPolicy.ReviewMetric,
    ): String = formatMetricValue(value, metric)

    fun axisUnitLabel(metric: DigitalSelfDisciplineReviewPolicy.ReviewMetric): String =
        if (metric == DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO) "×" else "自适应时间"

    fun xAxisLabels(points: List<TrendPoint>, maxLabels: Int = 6): List<String> {
        if (points.isEmpty()) return emptyList()
        val count = minOf(maxLabels.coerceAtLeast(2), points.size)
        if (count == points.size) return points.map { it.label }
        val step = (points.lastIndex.toDouble() / (count - 1)).coerceAtLeast(1.0)
        return (0 until count).map { index ->
            points[(index * step).toInt().coerceAtMost(points.lastIndex)].label
        }.distinct()
    }

    fun homeSummary(requestCount: Int, interceptCount: Int): String =
        "今日 ${requestCount.coerceAtLeast(0)} 次申请 · ${interceptCount.coerceAtLeast(0)} 次拦截"

    fun showInterceptFilters(reviewType: DigitalSelfDisciplineReviewPolicy.ReviewType): Boolean =
        reviewType == DigitalSelfDisciplineReviewPolicy.ReviewType.InterceptAttempt

    private fun bucketByHour(
        rows: List<Pair<DigitalSelfDisciplineReviewPolicy.RecentIntervalItem, Double>>,
        summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary,
        zoneId: ZoneId,
    ): List<TrendPoint> {
        val start = summary.dailyBuckets.firstOrNull()?.date?.atStartOfDay(zoneId)?.toInstant()?.toEpochMilli()
            ?: rows.first().first.occurredAt
        return rows.groupBy { ((it.first.occurredAt - start).coerceAtLeast(0L) / 3_600_000L) }
            .toSortedMap()
            .map { (bucket, bucketRows) ->
                val at = start + bucket * 3_600_000L
                TrendPoint(
                    label = formatTime(at, zoneId),
                    value = bucketRows.map { it.second }.average(),
                    sampleCount = bucketRows.size,
                    occurredAt = at,
                )
            }
    }

    private fun bucketByDate(
        rows: List<Pair<DigitalSelfDisciplineReviewPolicy.RecentIntervalItem, Double>>,
        zoneId: ZoneId,
    ): List<TrendPoint> = rows.groupBy {
        Instant.ofEpochMilli(it.first.occurredAt).atZone(zoneId).toLocalDate()
    }.toSortedMap().map { (date, dateRows) ->
        TrendPoint(
            label = date.format(DateTimeFormatter.ofPattern("MM-dd", Locale.ROOT)),
            value = dateRows.map { it.second }.average(),
            sampleCount = dateRows.size,
            occurredAt = date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
        )
    }

    private fun rankedBars(items: List<DigitalSelfDisciplineReviewPolicy.RankedShare>): List<RankedBar> {
        val visible = items.take(5).map { item ->
            RankedBar(
                label = item.label,
                countText = "${item.count} 次",
                shareText = "${(item.share * 100.0).let { "%.1f".format(Locale.ROOT, it) }}%",
                share = item.share.toFloat().coerceIn(0f, 1f),
            )
        }
        val remaining = items.drop(5)
        return if (remaining.isEmpty()) {
            visible
        } else {
            visible + RankedBar(
                label = li.songe.gkd.sdp.app.getString(R.string.s_1a26edf94a),
                countText = "${remaining.sumOf { it.count }} 次",
                shareText = "${(remaining.sumOf { it.share } * 100.0).let { "%.1f".format(Locale.ROOT, it) }}%",
                share = remaining.sumOf { it.share }.toFloat().coerceIn(0f, 1f),
            )
        }
    }

    private fun valueFor(
        item: DigitalSelfDisciplineReviewPolicy.RecentIntervalItem,
        metric: DigitalSelfDisciplineReviewPolicy.ReviewMetric,
    ): Double? = when (metric) {
        DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO -> item.ratio
        DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP,
        DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL -> item.intervalMs?.toDouble()
    }

    private fun metricValue(
        summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary,
        metric: DigitalSelfDisciplineReviewPolicy.ReviewMetric,
    ): Double? = when (metric) {
        DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO -> summary.ratioStats?.average
        DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP,
        DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL -> summary.intervalStats.averageMs?.toDouble()
    }

    private fun coverageValidCount(
        summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary,
        metric: DigitalSelfDisciplineReviewPolicy.ReviewMetric,
    ): Int = if (metric == DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO) {
        summary.coverage.validRatioCount
    } else {
        summary.coverage.validIntervalCount
    }

    private fun formatMetricValue(
        value: Double?,
        metric: DigitalSelfDisciplineReviewPolicy.ReviewMetric,
    ): String = when {
        value == null || !value.isFinite() -> "—"
        metric == DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO ->
            "${UsageRequestRhythmPolicy.formatRatio(value) ?: "—"}×"
        else -> SelfControlIntervalPolicy.formatDurationCompact(value.toLong())
    }

    private fun metricLabel(metric: DigitalSelfDisciplineReviewPolicy.ReviewMetric): String = when (metric) {
        DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO -> "间用比"
        DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP -> "未使用间隔"
        DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL -> "拦截间隔"
    }

    private fun coverageText(summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary): String =
        if (summary.reviewType == DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest) {
            "总申请 ${summary.coverage.eventCount} 条 · 有效间隔 ${summary.coverage.validIntervalCount} 条 · 有效间用比 ${summary.coverage.validRatioCount} 条 · 未纳入间隔 ${summary.coverage.excludedIntervalCount} 条 · 未纳入间用比 ${summary.coverage.excludedRatioCount} 条"
        } else {
            "总拦截 ${summary.coverage.eventCount} 条 · 已完成间隔 ${summary.coverage.validIntervalCount} 条 · 首次或未完成 ${summary.coverage.excludedIntervalCount} 条"
        }

    private fun formatTime(timestamp: Long, zoneId: ZoneId): String =
        DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT)
            .format(Instant.ofEpochMilli(timestamp).atZone(zoneId))

    private fun eventKindLabel(kind: Int?): String = when (kind) {
        1 -> "应用拦截"
        2 -> "选择器拦截"
        3 -> "网址拦截"
        else -> "拦截"
    }

    private fun endReasonLabel(reason: Int?): String = when (reason) {
        0 -> "进行中"
        1 -> "到期"
        2 -> "离开应用"
        3 -> "被替换"
        4 -> "返回桌面"
        5 -> "主动结束"
        null -> "未知"
        else -> "其他结束状态"
    }
}
