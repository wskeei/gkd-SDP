package li.songe.gkd.sdp.ui.component

import li.songe.gkd.sdp.util.DigitalSelfDisciplineReviewPolicy
import li.songe.gkd.sdp.util.SelfControlIntervalPolicy
import li.songe.gkd.sdp.util.UsageRequestRhythmPolicy
import li.songe.gkd.sdp.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Pure text and chart data for the digital self-discipline review page. */
object DigitalSelfDisciplineReviewPresentation {
    const val MAX_TREND_POINTS = 30
    // i18n-ignore: legacy fallback or non-display heuristic data
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
            rows.size <= summary.range.maxChartPoints ->
                rows.map { (item, value) ->
                    TrendPoint(
                        label = formatTime(item.occurredAt, zoneId),
                        value = value,
                        sampleCount = 1,
                        occurredAt = item.occurredAt,
                    )
                }
            else -> bucketByRange(rows, summary, zoneId)
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
        // i18n-ignore: legacy fallback or non-display heuristic data
        val coverageText = "有效样本 $validCount 条 / 总记录 ${summary.coverage.eventCount} 条 · 图形点 ${points.size} 个" +
            if (excludedCount > 0) {
                // i18n-ignore: legacy fallback or non-display heuristic data
                " · 未纳入 $excludedCount 条"
            } else {
                ""
            }
        val textRows = points.map { point ->
            "${point.label}：${formatMetricValue(point.value, safeMetric)}" +
                // i18n-ignore: legacy fallback or non-display heuristic data
                if (point.sampleCount > 1) "，平均 ${point.sampleCount} 条" else ""
        }
        // i18n-ignore: legacy fallback or non-display heuristic data
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
                // i18n-ignore: legacy fallback or non-display heuristic data
                MetricCard("申请次数", "${summary.coverage.eventCount} 次"),
                // i18n-ignore: legacy fallback or non-display heuristic data
                MetricCard("申请总时长", "${summary.usageDetails?.totalRequestedMinutes ?: 0L} 分钟"),
                // i18n-ignore: legacy fallback or non-display heuristic data
                MetricCard("平均未使用间隔", formatMetricValue(summary.intervalStats.averageMs?.toDouble(), DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP)),
                // i18n-ignore: legacy fallback or non-display heuristic data
                MetricCard("平均间用比", formatMetricValue(summary.ratioStats?.average, DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO)),
            )
        } else {
            listOf(
                // i18n-ignore: legacy fallback or non-display heuristic data
                MetricCard("拦截次数", "${summary.coverage.eventCount} 次"),
                // i18n-ignore: legacy fallback or non-display heuristic data
                MetricCard("有效间隔", "${summary.coverage.validIntervalCount} 条"),
                // i18n-ignore: legacy fallback or non-display heuristic data
                MetricCard("平均间隔", formatMetricValue(summary.intervalStats.averageMs?.toDouble(), DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL)),
                // i18n-ignore: legacy fallback or non-display heuristic data
                MetricCard("中位间隔", formatMetricValue(summary.intervalStats.medianMs?.toDouble(), DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL)),
            )
        }
        val distributions = summary.usageDetails?.let { details ->
            listOf(
                // i18n-ignore: legacy fallback or non-display heuristic data
                "应用分布" to rankedBars(details.appBreakdown),
                // i18n-ignore: legacy fallback or non-display heuristic data
                "标签分布" to rankedBars(details.tagBreakdown),
                // i18n-ignore: legacy fallback or non-display heuristic data
                "结束状态" to rankedBars(details.endReasonBreakdown),
                // i18n-ignore: legacy fallback or non-display heuristic data
                "集中时段" to listOfNotNull(details.busiestPeriod).map { rankedBars(listOf(it)).first() },
            )
        // i18n-ignore: legacy fallback or non-display heuristic data
        } ?: listOf("高频拦截目标" to rankedBars(summary.rankedTargets))
        val recentRows = summary.recentIntervals.map { item ->
            val time = formatTime(item.occurredAt, zoneId)
            val metricText = if (summary.reviewType == DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest) {
                val tags = item.tagNames.map { it.trim() }.filter { it.isNotEmpty() }
                    .ifEmpty { listOf("—") }
                    .joinToString("、")
                // i18n-ignore: legacy fallback or non-display heuristic data
                "间用比 ${formatMetricValue(item.ratio, DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO)} · 未使用间隔 ${formatMetricValue(item.intervalMs?.toDouble(), DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP)} · 申请 ${item.requestedDurationMinutes ?: 0} 分钟 · 标签：$tags · 结束状态：${endReasonLabel(item.endReason)}"
            } else {
                // i18n-ignore: legacy fallback or non-display heuristic data
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
        // i18n-ignore: legacy fallback or non-display heuristic data
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
        // i18n-ignore: legacy fallback or non-display heuristic data
        "今日 ${requestCount.coerceAtLeast(0)} 次申请 · ${interceptCount.coerceAtLeast(0)} 次拦截"

    fun showInterceptFilters(reviewType: DigitalSelfDisciplineReviewPolicy.ReviewType): Boolean =
        reviewType == DigitalSelfDisciplineReviewPolicy.ReviewType.InterceptAttempt

    private fun bucketByRange(
        rows: List<Pair<DigitalSelfDisciplineReviewPolicy.RecentIntervalItem, Double>>,
        summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary,
        zoneId: ZoneId,
    ): List<TrendPoint> {
        val start = summary.dailyBuckets.firstOrNull()?.bucketStartAt
            ?: rows.first().first.occurredAt
        val bucketMs = summary.range.bucketMs
        val maxBucketIndex = (summary.range.maxChartPoints - 1).coerceAtLeast(0)
        return rows.groupBy {
            ((it.first.occurredAt - start).coerceAtLeast(0L) / bucketMs)
                .coerceAtMost(maxBucketIndex.toLong())
        }
            .toSortedMap()
            .map { (bucket, bucketRows) ->
                val at = start + bucket * bucketMs
                TrendPoint(
                    label = formatBucketTime(at, bucketMs, zoneId),
                    value = bucketRows.map { it.second }.average(),
                    sampleCount = bucketRows.size,
                    occurredAt = at,
                )
            }
    }

    private fun rankedBars(items: List<DigitalSelfDisciplineReviewPolicy.RankedShare>): List<RankedBar> {
        val visible = items.take(5).map { item ->
            RankedBar(
                label = item.label,
                // i18n-ignore: legacy fallback or non-display heuristic data
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
                // i18n-ignore: legacy fallback or non-display heuristic data
                label = "其他",
                // i18n-ignore: legacy fallback or non-display heuristic data
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
        // i18n-ignore: legacy fallback or non-display heuristic data
        DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO -> "间用比"
        // i18n-ignore: legacy fallback or non-display heuristic data
        DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP -> "未使用间隔"
        // i18n-ignore: legacy fallback or non-display heuristic data
        DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL -> "拦截间隔"
    }

    private fun coverageText(summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary): String =
        if (summary.reviewType == DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest) {
            // i18n-ignore: legacy fallback or non-display heuristic data
            "总申请 ${summary.coverage.eventCount} 条 · 有效间隔 ${summary.coverage.validIntervalCount} 条 · 有效间用比 ${summary.coverage.validRatioCount} 条 · 未纳入间隔 ${summary.coverage.excludedIntervalCount} 条 · 未纳入间用比 ${summary.coverage.excludedRatioCount} 条"
        } else {
            // i18n-ignore: legacy fallback or non-display heuristic data
            "总拦截 ${summary.coverage.eventCount} 条 · 已完成间隔 ${summary.coverage.validIntervalCount} 条 · 首次或未完成 ${summary.coverage.excludedIntervalCount} 条"
        }

    private fun formatTime(timestamp: Long, zoneId: ZoneId): String =
        DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT)
            .format(Instant.ofEpochMilli(timestamp).atZone(zoneId))

    private fun formatBucketTime(
        timestamp: Long,
        bucketMs: Long,
        zoneId: ZoneId,
    ): String {
        val pattern = if (bucketMs >= 24L * 60L * 60L * 1_000L) {
            "MM-dd"
        } else if (bucketMs >= 60L * 60L * 1_000L) {
            "MM-dd HH:mm"
        } else {
            "HH:mm"
        }
        return DateTimeFormatter.ofPattern(pattern, Locale.ROOT)
            .format(Instant.ofEpochMilli(timestamp).atZone(zoneId))
    }

    private fun eventKindLabel(kind: Int?): String = when (kind) {
        // i18n-ignore: legacy fallback or non-display heuristic data
        1 -> "应用拦截"
        // i18n-ignore: legacy fallback or non-display heuristic data
        2 -> "选择器拦截"
        // i18n-ignore: legacy fallback or non-display heuristic data
        3 -> "网址拦截"
        // i18n-ignore: legacy fallback or non-display heuristic data
        else -> "拦截"
    }

    private fun endReasonLabel(reason: Int?): String = when (reason) {
        // i18n-ignore: legacy fallback or non-display heuristic data
        0 -> "进行中"
        // i18n-ignore: legacy fallback or non-display heuristic data
        1 -> "到期"
        // i18n-ignore: legacy fallback or non-display heuristic data
        2 -> "离开应用"
        // i18n-ignore: legacy fallback or non-display heuristic data
        3 -> "被替换"
        // i18n-ignore: legacy fallback or non-display heuristic data
        4 -> "返回桌面"
        // i18n-ignore: legacy fallback or non-display heuristic data
        5 -> "主动结束"
        // i18n-ignore: legacy fallback or non-display heuristic data
        null -> "未知"
        // i18n-ignore: legacy fallback or non-display heuristic data
        else -> "其他结束状态"
    }
}

@Immutable
sealed interface LocalizedValue {
    @Immutable
    data class Text(val resId: Int, val args: List<Any> = emptyList()) : LocalizedValue
    @Immutable
    data class Count(val count: Int, val unitRes: Int) : LocalizedValue
    @Immutable
    data class Duration(val ms: Long?) : LocalizedValue
    @Immutable
    data class Ratio(val value: Double?) : LocalizedValue
    @Immutable
    data class Raw(val text: String) : LocalizedValue
}

@Composable
fun LocalizedValue.render(): String = when (this) {
    is LocalizedValue.Text -> stringResource(resId, *args.map { renderLocalizedArg(it) }.toTypedArray())
    is LocalizedValue.Count -> stringResource(unitRes, count)
    is LocalizedValue.Duration -> formatDurationLocalized(ms)
    is LocalizedValue.Ratio -> formatRatioLocalized(value)
    is LocalizedValue.Raw -> text
}

@Composable
internal fun renderLocalizedArg(arg: Any): Any = when (arg) {
    is LocalizedValue -> arg.render()
    else -> arg
}

@Immutable
data class LocalizedMetricCard(
    val labelRes: Int,
    val value: LocalizedValue,
)

@Immutable
data class LocalizedTrend(
    val metricRes: Int,
    val currentAverage: LocalizedValue,
    val previousAverage: LocalizedValue,
    val delta: LocalizedValue,
    val coverage: LocalizedValue.Text,
    val semantic: LocalizedValue.Text,
    val points: List<DigitalSelfDisciplineReviewPresentation.TrendPoint>,
    val textRows: List<DigitalSelfDisciplineReviewPresentation.TrendPoint>,
    val empty: Boolean,
)

@Immutable
data class LocalizedRankedBar(
    val label: String,
    val count: Int,
    val share: Float,
    val labelRes: Int? = null,
)

@Immutable
data class LocalizedDistribution(
    val titleRes: Int,
    val bars: List<LocalizedRankedBar>,
)

@Immutable
data class LocalizedRecentRow(
    val primary: LocalizedValue.Text,
    val secondary: LocalizedValue.Text,
)

@Immutable
data class LocalizedPage(
    val overview: List<LocalizedMetricCard>,
    val coverage: LocalizedValue.Text,
    val trend: LocalizedTrend,
    val distributions: List<LocalizedDistribution>,
    val recentRows: List<LocalizedRecentRow>,
)

fun localizedReviewPage(
    summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary,
    metric: DigitalSelfDisciplineReviewPolicy.ReviewMetric =
        DigitalSelfDisciplineReviewPresentation.defaultMetric(summary.reviewType),
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalizedPage {
    val oldTrend = DigitalSelfDisciplineReviewPresentation.trend(summary, metric, zoneId)
    val safeMetric = oldTrend.metric
    val validCount = if (safeMetric == DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO) {
        summary.coverage.validRatioCount
    } else {
        summary.coverage.validIntervalCount
    }
    val excludedCount = if (safeMetric == DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO) {
        summary.coverage.excludedRatioCount
    } else {
        summary.coverage.excludedIntervalCount
    }
    val currentAverage = localizedMetricValue(summary, safeMetric, current = true)
    val previousAverage = localizedMetricValue(summary, safeMetric, current = false)
    val delta = localizedDeltaValue(summary, safeMetric)
    val coverage = if (excludedCount > 0) {
        LocalizedValue.Text(
            R.string.review_coverage_text_excluded,
            listOf(validCount, summary.coverage.eventCount, oldTrend.points.size, excludedCount),
        )
    } else {
        LocalizedValue.Text(
            R.string.review_coverage_text,
            listOf(validCount, summary.coverage.eventCount, oldTrend.points.size),
        )
    }
    val semantic = LocalizedValue.Text(
        R.string.review_semantic,
        listOf(
            LocalizedValue.Text(safeMetric.labelRes()),
            currentAverage,
            previousAverage,
            delta,
            coverage,
        ),
    )
    val overview = if (summary.reviewType == DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest) {
        listOf(
            LocalizedMetricCard(
                R.string.review_metric_count_request,
                LocalizedValue.Count(summary.coverage.eventCount, R.string.review_count_times),
            ),
            LocalizedMetricCard(
                R.string.review_metric_total_requested,
                LocalizedValue.Count(
                    summary.usageDetails?.totalRequestedMinutes?.toInt() ?: 0,
                    R.string.review_count_minutes,
                ),
            ),
            LocalizedMetricCard(
                R.string.review_metric_avg_gap,
                LocalizedValue.Duration(summary.intervalStats.averageMs),
            ),
            LocalizedMetricCard(
                R.string.review_metric_avg_ratio,
                LocalizedValue.Ratio(summary.ratioStats?.average),
            ),
        )
    } else {
        listOf(
            LocalizedMetricCard(
                R.string.review_metric_intercept_count,
                LocalizedValue.Count(summary.coverage.eventCount, R.string.review_count_times),
            ),
            LocalizedMetricCard(
                R.string.review_metric_valid_intervals,
                LocalizedValue.Count(summary.coverage.validIntervalCount, R.string.review_count_items),
            ),
            LocalizedMetricCard(
                R.string.review_metric_avg_interval,
                LocalizedValue.Duration(summary.intervalStats.averageMs),
            ),
            LocalizedMetricCard(
                R.string.review_metric_median_interval,
                LocalizedValue.Duration(summary.intervalStats.medianMs),
            ),
        )
    }
    val coverageRow = if (summary.reviewType == DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest) {
        LocalizedValue.Text(
            R.string.review_coverage_request,
            listOf(
                summary.coverage.eventCount,
                summary.coverage.validIntervalCount,
                summary.coverage.validRatioCount,
                summary.coverage.excludedIntervalCount,
                summary.coverage.excludedRatioCount,
            ),
        )
    } else {
        LocalizedValue.Text(
            R.string.review_coverage_intercept,
            listOf(
                summary.coverage.eventCount,
                summary.coverage.validIntervalCount,
                summary.coverage.excludedIntervalCount,
            ),
        )
    }
    val distributions = localizedDistributions(summary)
    val recentRows = summary.recentIntervals.map { item ->
        val time = formatRecentTime(item.occurredAt, zoneId)
        val primary = LocalizedValue.Text(
            R.string.review_recent_primary,
            listOf(time, item.label),
        )
        val secondary = if (summary.reviewType == DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest) {
            val tags = item.tagNames.map { it.trim() }.filter { it.isNotEmpty() }
                .ifEmpty { listOf("—") }
                .joinToString(", ")
            LocalizedValue.Text(
                R.string.review_recent_usage_line,
                listOf(
                    LocalizedValue.Ratio(item.ratio),
                    LocalizedValue.Duration(item.intervalMs),
                    item.requestedDurationMinutes ?: 0,
                    tags,
                    LocalizedValue.Text(endReasonLabelRes(item.endReason)),
                ),
            )
        } else {
            LocalizedValue.Text(
                R.string.review_recent_intercept_line,
                listOf(
                    LocalizedValue.Duration(item.intervalMs),
                    LocalizedValue.Text(eventKindLabelRes(item.eventKind)),
                ),
            )
        }
        LocalizedRecentRow(primary, secondary)
    }
    return LocalizedPage(
        overview = overview,
        coverage = coverageRow,
        trend = LocalizedTrend(
            metricRes = safeMetric.labelRes(),
            currentAverage = currentAverage,
            previousAverage = previousAverage,
            delta = delta,
            coverage = coverage,
            semantic = semantic,
            points = oldTrend.points,
            textRows = oldTrend.points,
            empty = oldTrend.points.isEmpty(),
        ),
        distributions = distributions,
        recentRows = recentRows,
    )
}

private fun localizedMetricValue(
    summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary,
    metric: DigitalSelfDisciplineReviewPolicy.ReviewMetric,
    current: Boolean,
): LocalizedValue = when (metric) {
    DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO ->
        LocalizedValue.Ratio(
            if (current) summary.ratioStats?.average else summary.comparison.previousRatioAverage,
        )
    DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP,
    DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL ->
        LocalizedValue.Duration(
            if (current) summary.intervalStats.averageMs else summary.comparison.previousIntervalAverageMs,
        )
}

private fun localizedDeltaValue(
    summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary,
    metric: DigitalSelfDisciplineReviewPolicy.ReviewMetric,
): LocalizedValue = when (metric) {
    DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_RATIO ->
        LocalizedValue.Ratio(summary.comparison.ratioDelta)
    DigitalSelfDisciplineReviewPolicy.ReviewMetric.USAGE_GAP,
    DigitalSelfDisciplineReviewPolicy.ReviewMetric.INTERCEPT_INTERVAL ->
        LocalizedValue.Duration(summary.comparison.intervalDeltaMs)
}

private fun localizedDistributions(
    summary: DigitalSelfDisciplineReviewPolicy.ReviewSummary,
): List<LocalizedDistribution> {
    if (summary.reviewType == DigitalSelfDisciplineReviewPolicy.ReviewType.UsageRequest) {
        val details = summary.usageDetails ?: return emptyList()
        return listOf(
            LocalizedDistribution(
                R.string.review_distribution_apps,
                details.appBreakdown.map(::localizedRankedBar),
            ),
            LocalizedDistribution(
                R.string.review_distribution_tags,
                details.tagBreakdown.map(::localizedRankedBar),
            ),
            LocalizedDistribution(
                R.string.review_distribution_end_reasons,
                details.endReasonBreakdown.map(::localizedRankedBar),
            ),
            LocalizedDistribution(
                R.string.review_distribution_period,
                listOfNotNull(details.busiestPeriod).map(::localizedRankedBar),
            ),
        )
    }
    return listOf(
        LocalizedDistribution(
            R.string.review_distribution_top_targets,
            summary.rankedTargets.map(::localizedRankedBar),
        ),
    )
}

private fun localizedRankedBar(
    item: DigitalSelfDisciplineReviewPolicy.RankedShare,
): LocalizedRankedBar = LocalizedRankedBar(
    label = item.label,
    count = item.count,
    share = item.share.toFloat().coerceIn(0f, 1f),
    labelRes = localizedRankedLabelRes(item.label),
)

private fun localizedRankedLabelRes(label: String): Int? = when (label) {
    // i18n-ignore: legacy fallback or non-display heuristic data
    "其他" -> R.string.review_other
    // i18n-ignore: legacy fallback or non-display heuristic data
    "上午" -> R.string.review_period_morning
    // i18n-ignore: legacy fallback or non-display heuristic data
    "午间" -> R.string.review_period_noon
    // i18n-ignore: legacy fallback or non-display heuristic data
    "下午" -> R.string.review_period_afternoon
    // i18n-ignore: legacy fallback or non-display heuristic data
    "晚间" -> R.string.review_period_evening
    // i18n-ignore: legacy fallback or non-display heuristic data
    "夜间" -> R.string.review_period_night
    else -> null
}

private fun endReasonLabelRes(reason: Int?): Int = when (reason) {
    0 -> R.string.review_end_active
    1 -> R.string.review_end_expired
    2 -> R.string.review_end_left
    3 -> R.string.review_end_replaced
    4 -> R.string.review_end_home
    5 -> R.string.review_end_terminated
    null -> R.string.review_end_unknown
    else -> R.string.review_end_other
}

private fun eventKindLabelRes(kind: Int?): Int = when (kind) {
    1 -> R.string.review_event_kind_app
    2 -> R.string.review_event_kind_selector
    3 -> R.string.review_event_kind_url
    else -> R.string.review_event_kind_other
}

private fun formatRecentTime(timestamp: Long, zoneId: ZoneId): String =
    DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT)
        .format(Instant.ofEpochMilli(timestamp).atZone(zoneId))
