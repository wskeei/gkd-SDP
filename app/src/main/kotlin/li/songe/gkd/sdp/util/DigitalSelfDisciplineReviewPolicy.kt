package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.SelfControlAttempt
import li.songe.gkd.sdp.data.SelfControlAttemptEvent
import li.songe.gkd.sdp.data.UsageReviewRow
import li.songe.gkd.sdp.runtime.SdpClock
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Pure aggregation rules for the Digital Self-Discipline review page. */
object DigitalSelfDisciplineReviewPolicy {
    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60L * MINUTE_MS

    enum class Range(
        val label: String,
        val durationMs: Long,
        val bucketMs: Long,
        val maxChartPoints: Int,
    ) {
        // i18n-ignore: legacy fallback or non-display heuristic data
        LAST_24_HOURS("近 24 小时", 24L * HOUR_MS, HOUR_MS, 24),
        // i18n-ignore: legacy fallback or non-display heuristic data
        LAST_7_DAYS("近 7 天", 7L * 24L * HOUR_MS, 6L * HOUR_MS, 28),
        // i18n-ignore: legacy fallback or non-display heuristic data
        LAST_30_DAYS("近 30 天", 30L * 24L * HOUR_MS, 24L * HOUR_MS, 30),
    }

    enum class ReviewType(val label: String) {
        // i18n-ignore: legacy fallback or non-display heuristic data
        UsageRequest("使用申请"),
        // i18n-ignore: legacy fallback or non-display heuristic data
        InterceptAttempt("拦截"),
    }

    enum class ReviewMetric {
        USAGE_RATIO,
        USAGE_GAP,
        INTERCEPT_INTERVAL,
    }

    enum class InterceptKindFilter(val label: String, val eventKind: Int? = null) {
        // i18n-ignore: legacy fallback or non-display heuristic data
        All("全部"),
        // i18n-ignore: legacy fallback or non-display heuristic data
        AppBlocker("应用拦截", SelfControlAttempt.KIND_APP_BLOCKER),
        // i18n-ignore: legacy fallback or non-display heuristic data
        Selector("选择器拦截", SelfControlAttempt.KIND_SELECTOR_INTERCEPT),
        // i18n-ignore: legacy fallback or non-display heuristic data
        Url("网址拦截", SelfControlAttempt.KIND_URL_INTERCEPT),
    }

    data class RangeBounds(
        val range: Range,
        val zoneId: ZoneId,
        val startDate: LocalDate,
        val endDateExclusive: LocalDate,
        val startAt: Long,
        val endAt: Long,
        val previousStartDate: LocalDate,
        val previousEndDateExclusive: LocalDate,
        val previousStartAt: Long,
        val previousEndAt: Long,
        val bucketMs: Long,
        val maxChartPoints: Int,
    ) {
        fun contains(timestamp: Long): Boolean = timestamp >= startAt && timestamp < endAt
    }

    data class RatioStats(
        val sampleCount: Int,
        val average: Double?,
        val median: Double?,
        val min: Double?,
        val max: Double?,
    )

    data class DataCoverage(
        val eventCount: Int,
        val validIntervalCount: Int,
        val validRatioCount: Int,
        val excludedIntervalCount: Int,
        val excludedRatioCount: Int,
    )

    data class RankedShare(
        val key: String,
        val label: String,
        val count: Int,
        val share: Double,
    )

    data class UsageDetails(
        val totalRequestedMinutes: Long,
        val averageRequestedMinutes: Double?,
        val appBreakdown: List<RankedShare>,
        val tagBreakdown: List<RankedShare>,
        val endReasonBreakdown: List<RankedShare>,
        val busiestPeriod: RankedShare?,
    )

    data class DailyIntervalBucket(
        val date: LocalDate,
        val bucketStartAt: Long,
        val eventCount: Int,
        val validIntervalCount: Int,
        val validRatioCount: Int,
        val averageMs: Long?,
        val medianMs: Long?,
        val ratioAverage: Double?,
        val ratioMedian: Double?,
    ) {
        val sampleCount: Int get() = validIntervalCount
    }

    data class RecentIntervalItem(
        val occurredAt: Long,
        val intervalMs: Long?,
        val label: String,
        val key: String,
        val requestedDurationMinutes: Int? = null,
        val ratio: Double? = null,
        val tagNames: List<String> = emptyList(),
        val eventKind: Int? = null,
        val endReason: Int? = null,
    )

    data class PeriodComparison(
        val currentEventCount: Int,
        val previousEventCount: Int,
        val currentMetricValue: Double?,
        val previousMetricValue: Double?,
        val metricDelta: Double?,
        val currentSampleCount: Int,
        val previousSampleCount: Int,
        val deltaAverageMs: Long? = null,
        val currentIntervalAverageMs: Long? = null,
        val previousIntervalAverageMs: Long? = null,
        val intervalDeltaMs: Long? = null,
        val currentRatioAverage: Double? = null,
        val previousRatioAverage: Double? = null,
        val ratioDelta: Double? = null,
        val message: String,
    )

    data class ReviewSummary(
        val reviewType: ReviewType,
        val range: Range,
        val eventCount: Int,
        val requestCount: Int,
        val interceptCount: Int,
        val coverage: DataCoverage,
        val intervalStats: SelfControlIntervalPolicy.Stats,
        val ratioStats: RatioStats?,
        val usageDetails: UsageDetails?,
        val dailyBuckets: List<DailyIntervalBucket>,
        val trendIntervals: List<RecentIntervalItem>,
        val recentIntervals: List<RecentIntervalItem>,
        val rankedTargets: List<RankedShare>,
        val comparison: PeriodComparison,
    )

    private data class MetricSample(
        val occurredAt: Long,
        val intervalMs: Long?,
        val ratio: Double?,
        val key: String,
        val label: String,
        val requestedDurationMinutes: Int? = null,
        val tagNames: List<String> = emptyList(),
        val eventKind: Int? = null,
        val endReason: Int? = null,
    )

    fun rangeBounds(
        range: Range,
        nowEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): RangeBounds {
        val safeNow = nowEpochMs.coerceAtLeast(0L)
        val startAt = (safeNow - range.durationMs).coerceAtLeast(0L)
        val previousStartAt = (startAt - range.durationMs).coerceAtLeast(0L)
        fun dateAt(epochMs: Long): LocalDate =
            Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate()
        return RangeBounds(
            range = range,
            zoneId = zoneId,
            startDate = dateAt(startAt),
            endDateExclusive = dateAt(safeNow),
            startAt = startAt,
            endAt = safeNow,
            previousStartDate = dateAt(previousStartAt),
            previousEndDateExclusive = dateAt(startAt),
            previousStartAt = previousStartAt,
            previousEndAt = startAt,
            bucketMs = range.bucketMs,
            maxChartPoints = range.maxChartPoints,
        )
    }

    fun rangeBounds(
        range: Range,
        clock: SdpClock,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): RangeBounds = rangeBounds(
        range = range,
        nowEpochMs = clock.nowEpochMillis(),
        zoneId = zoneId,
    )

    fun hasCrossedDateBoundary(
        previousNow: Long,
        currentNow: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean = Instant.ofEpochMilli(previousNow).atZone(zoneId).toLocalDate() !=
        Instant.ofEpochMilli(currentNow).atZone(zoneId).toLocalDate()

    fun summarize(
        usageRows: List<UsageReviewRow>,
        events: List<SelfControlAttemptEvent>,
        bounds: RangeBounds,
        reviewType: ReviewType,
        interceptFilter: InterceptKindFilter,
        zoneId: ZoneId = bounds.zoneId,
        previousSummary: ReviewSummary? = null,
    ): ReviewSummary {
        val currentRows = usageRows.filter { bounds.contains(it.requestedAt) }
        val currentEvents = events.filter { event ->
            bounds.contains(event.occurredAt) &&
                (interceptFilter.eventKind == null || event.eventKind == interceptFilter.eventKind)
        }
        val samples = when (reviewType) {
            ReviewType.UsageRequest -> currentRows.map { row ->
                val interval = row.requestGapMs?.takeIf { it >= 0L }
                MetricSample(
                    occurredAt = row.requestedAt,
                    intervalMs = interval,
                    ratio = UsageRequestRhythmPolicy.ratio(interval, row.requestedDurationMinutes),
                    key = row.appId,
                    label = row.appName.ifBlank { row.appId },
                    requestedDurationMinutes = row.requestedDurationMinutes,
                    tagNames = row.tagNames,
                    endReason = row.endReason,
                )
            }
            ReviewType.InterceptAttempt -> currentEvents.map { event ->
                MetricSample(
                    occurredAt = event.occurredAt,
                    intervalMs = event.intervalMs?.takeIf { it >= 0L },
                    ratio = null,
                    key = event.eventKey,
                    label = event.subjectLabel.ifBlank { event.subjectId },
                    eventKind = event.eventKind,
                )
            }
        }.sortedWith(compareBy<MetricSample> { it.occurredAt }.thenBy { it.key })

        val intervals = samples.mapNotNull { it.intervalMs }
        val intervalStats = SelfControlIntervalPolicy.statsFor(intervals)
        val ratioValues = samples.mapNotNull { it.ratio }
        val ratioStats = ratioStats(ratioValues)
        val eventCount = when (reviewType) {
            ReviewType.UsageRequest -> currentRows.size
            ReviewType.InterceptAttempt -> currentEvents.size
        }
        val coverage = DataCoverage(
            eventCount = eventCount,
            validIntervalCount = intervals.size,
            validRatioCount = ratioValues.size,
            excludedIntervalCount = eventCount - intervals.size,
            excludedRatioCount = eventCount - ratioValues.size,
        )
        val dailyBuckets = dailyBuckets(
            samples = samples,
            eventCountRows = when (reviewType) {
                ReviewType.UsageRequest -> currentRows.map { it.requestedAt }
                ReviewType.InterceptAttempt -> currentEvents.map { it.occurredAt }
            },
            zoneId = zoneId,
            bounds = bounds,
        )
        val recentIntervals = samples.asReversed().take(10).map { sample ->
            sample.toRecentItem()
        }
        val trendIntervals = samples.map { it.toRecentItem() }
        val rankedTargets = rankShares(
            values = samples.map { it.key to it.label },
            denominator = eventCount,
        )
        val usageDetails = if (reviewType == ReviewType.UsageRequest) {
            buildUsageDetails(currentRows, samples, zoneId)
        } else {
            null
        }
        val comparison = previousSummary?.let {
            compareSummary(
                current = thisSummaryMetrics(eventCount, intervalStats, ratioStats, reviewType),
                previous = thisSummaryMetrics(it.eventCount, it.intervalStats, it.ratioStats, reviewType),
                currentEventCount = eventCount,
                previousEventCount = it.eventCount,
                reviewType = reviewType,
            )
        } ?: emptyComparison(eventCount, intervalStats, ratioStats, reviewType)
        return ReviewSummary(
            reviewType = reviewType,
            range = bounds.range,
            eventCount = eventCount,
            requestCount = currentRows.size,
            interceptCount = currentEvents.size,
            coverage = coverage,
            intervalStats = intervalStats,
            ratioStats = ratioStats,
            usageDetails = usageDetails,
            dailyBuckets = dailyBuckets,
            trendIntervals = trendIntervals,
            recentIntervals = recentIntervals,
            rankedTargets = rankedTargets,
            comparison = comparison,
        )
    }

    fun compare(
        current: SelfControlIntervalPolicy.Stats,
        previous: SelfControlIntervalPolicy.Stats,
    ): PeriodComparison = compareSummary(
        current = SummaryMetrics(
            sampleCount = current.sampleCount,
            metricValue = current.averageMs?.toDouble(),
            intervalAverageMs = current.averageMs,
            ratioAverage = null,
        ),
        previous = SummaryMetrics(
            sampleCount = previous.sampleCount,
            metricValue = previous.averageMs?.toDouble(),
            intervalAverageMs = previous.averageMs,
            ratioAverage = null,
        ),
        currentEventCount = current.sampleCount,
        previousEventCount = previous.sampleCount,
        reviewType = ReviewType.InterceptAttempt,
    )

    private data class SummaryMetrics(
        val sampleCount: Int,
        val metricValue: Double?,
        val intervalAverageMs: Long?,
        val ratioAverage: Double?,
    )

    private fun thisSummaryMetrics(
        eventCount: Int,
        intervalStats: SelfControlIntervalPolicy.Stats,
        ratioStats: RatioStats?,
        reviewType: ReviewType,
    ): SummaryMetrics = if (reviewType == ReviewType.UsageRequest) {
        SummaryMetrics(
            sampleCount = ratioStats?.sampleCount ?: 0,
            metricValue = ratioStats?.average,
            intervalAverageMs = intervalStats.averageMs,
            ratioAverage = ratioStats?.average,
        )
    } else {
        SummaryMetrics(
            sampleCount = intervalStats.sampleCount,
            metricValue = intervalStats.averageMs?.toDouble(),
            intervalAverageMs = intervalStats.averageMs,
            ratioAverage = null,
        )
    }

    private fun emptyComparison(
        eventCount: Int,
        intervalStats: SelfControlIntervalPolicy.Stats,
        ratioStats: RatioStats?,
        reviewType: ReviewType,
    ): PeriodComparison {
        val metrics = thisSummaryMetrics(eventCount, intervalStats, ratioStats, reviewType)
        return PeriodComparison(
            currentEventCount = eventCount,
            previousEventCount = 0,
            currentMetricValue = metrics.metricValue,
            previousMetricValue = null,
            metricDelta = null,
            currentSampleCount = metrics.sampleCount,
            previousSampleCount = 0,
            deltaAverageMs = null,
            currentIntervalAverageMs = metrics.intervalAverageMs,
            currentRatioAverage = metrics.ratioAverage,
            // i18n-ignore: legacy fallback or non-display heuristic data
            message = "上一周期暂无有效样本",
        )
    }

    private fun compareSummary(
        current: SummaryMetrics,
        previous: SummaryMetrics,
        currentEventCount: Int,
        previousEventCount: Int,
        reviewType: ReviewType,
    ): PeriodComparison {
        val delta = if (current.metricValue != null && previous.metricValue != null) {
            current.metricValue - previous.metricValue
        } else {
            null
        }
        val deltaAverageMs = if (reviewType == ReviewType.InterceptAttempt &&
            current.intervalAverageMs != null && previous.intervalAverageMs != null
        ) {
            SelfControlIntervalPolicy.deltaBetween(current.intervalAverageMs, previous.intervalAverageMs)
        } else {
            null
        }
        val intervalDeltaMs = if (current.intervalAverageMs != null && previous.intervalAverageMs != null) {
            SelfControlIntervalPolicy.deltaBetween(current.intervalAverageMs, previous.intervalAverageMs)
        } else {
            null
        }
        val ratioDelta = if (current.ratioAverage != null && previous.ratioAverage != null) {
            current.ratioAverage - previous.ratioAverage
        } else {
            null
        }
        val message = when {
            // i18n-ignore: legacy fallback or non-display heuristic data
            delta == null -> "上一周期暂无有效样本"
            // i18n-ignore: legacy fallback or non-display heuristic data
            delta > 0.0 -> "本期平均比上一周期高 ${formatMetricDelta(delta, reviewType)}"
            // i18n-ignore: legacy fallback or non-display heuristic data
            delta < 0.0 -> "本期平均比上一周期低 ${formatMetricDelta(-delta, reviewType)}"
            // i18n-ignore: legacy fallback or non-display heuristic data
            else -> "本期平均与上一周期相同"
        }
        return PeriodComparison(
            currentEventCount = currentEventCount,
            previousEventCount = previousEventCount,
            currentMetricValue = current.metricValue,
            previousMetricValue = previous.metricValue,
            metricDelta = delta,
            currentSampleCount = current.sampleCount,
            previousSampleCount = previous.sampleCount,
            deltaAverageMs = deltaAverageMs,
            currentIntervalAverageMs = current.intervalAverageMs,
            previousIntervalAverageMs = previous.intervalAverageMs,
            intervalDeltaMs = intervalDeltaMs,
            currentRatioAverage = current.ratioAverage,
            previousRatioAverage = previous.ratioAverage,
            ratioDelta = ratioDelta,
            message = message,
        )
    }

    private fun formatMetricDelta(value: Double, reviewType: ReviewType): String = if (
        reviewType == ReviewType.UsageRequest
    ) {
        "${UsageRequestRhythmPolicy.formatRatio(value) ?: "—"}×"
    } else {
        SelfControlIntervalPolicy.formatDurationCompact(value.toLong())
    }

    private fun dailyBuckets(
        samples: List<MetricSample>,
        eventCountRows: List<Long>,
        zoneId: ZoneId,
        bounds: RangeBounds,
    ): List<DailyIntervalBucket> {
        val bucketIndex = { timestamp: Long ->
            ((timestamp - bounds.startAt).coerceAtLeast(0L) / bounds.bucketMs)
                .coerceAtMost((bounds.maxChartPoints - 1).coerceAtLeast(0).toLong())
        }
        val counts = eventCountRows.groupingBy(bucketIndex).eachCount()
        val samplesByBucket = samples.groupBy { bucketIndex(it.occurredAt) }
        return counts.keys.sorted().map { index ->
            val bucketStartAt = bounds.startAt + index * bounds.bucketMs
            val bucketSamples = samplesByBucket[index].orEmpty()
            val intervals = bucketSamples.mapNotNull { it.intervalMs }
            val intervalStats = SelfControlIntervalPolicy.statsFor(intervals)
            val ratios = bucketSamples.mapNotNull { it.ratio }
            val dayRatioStats = ratioStats(ratios)
            DailyIntervalBucket(
                date = Instant.ofEpochMilli(bucketStartAt).atZone(zoneId).toLocalDate(),
                bucketStartAt = bucketStartAt,
                eventCount = counts[index] ?: 0,
                validIntervalCount = intervals.size,
                validRatioCount = ratios.size,
                averageMs = intervalStats.averageMs,
                medianMs = intervalStats.medianMs,
                ratioAverage = dayRatioStats?.average,
                ratioMedian = dayRatioStats?.median,
            )
        }
    }

    private fun buildUsageDetails(
        rows: List<UsageReviewRow>,
        samples: List<MetricSample>,
        zoneId: ZoneId,
    ): UsageDetails {
        val totalMinutes = rows.fold(0L) { total, row ->
            total + row.requestedDurationMinutes.coerceAtLeast(0).toLong()
        }
        val averageMinutes = rows.map { it.requestedDurationMinutes.coerceAtLeast(0) }
            .takeIf { it.isNotEmpty() }
            ?.average()
        val appBreakdown = rankShares(rows.map { it.appId to it.appName.ifBlank { it.appId } }, rows.size)
        val tagValues = rows.flatMap { row ->
            row.tagNames.mapNotNull { tag -> tag.trim().takeIf { it.isNotEmpty() } }
                .map { tag -> tag to tag }
        }
        val tagBreakdown = rankShares(tagValues, tagValues.size)
        val reasonValues = rows.map { row ->
            row.endReason.toString() to endReasonLabel(row.endReason)
        }
        val endReasonBreakdown = rankShares(reasonValues, rows.size)
        val periods = samples.map { sample ->
            val hour = Instant.ofEpochMilli(sample.occurredAt).atZone(zoneId).hour
            periodLabel(hour)
        }
        val busiestPeriod = rankShares(periods.map { it to it }, periods.size).firstOrNull()
        return UsageDetails(
            totalRequestedMinutes = totalMinutes,
            averageRequestedMinutes = averageMinutes,
            appBreakdown = appBreakdown,
            tagBreakdown = tagBreakdown,
            endReasonBreakdown = endReasonBreakdown,
            busiestPeriod = busiestPeriod,
        )
    }

    private fun rankShares(values: List<Pair<String, String>>, denominator: Int): List<RankedShare> {
        if (values.isEmpty()) return emptyList()
        return values.groupBy { it.first }
            .map { (key, keyedValues) ->
                val label = keyedValues.map { it.second.trim() }
                    .filter { it.isNotEmpty() }
                    .minOrNull()
                    ?: key
                val count = keyedValues.size
                RankedShare(
                    key = key,
                    label = label,
                    count = count,
                    share = if (denominator <= 0) 0.0 else count.toDouble() / denominator,
                )
            }
            .sortedWith(compareByDescending<RankedShare> { it.count }.thenBy { it.label })
    }

    private fun ratioStats(values: List<Double>): RatioStats? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val average = sorted.fold(BigDecimal.ZERO) { total, value ->
            total.add(BigDecimal.valueOf(value))
        }.divide(BigDecimal.valueOf(sorted.size.toLong()), 12, RoundingMode.HALF_UP).toDouble()
        val middle = sorted.size / 2
        val median = if (sorted.size % 2 == 1) sorted[middle] else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
        return RatioStats(sorted.size, average, median, sorted.first(), sorted.last())
    }

    private fun endReasonLabel(reason: Int): String = when (reason) {
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
        else -> "其他结束状态"
    }

    private fun periodLabel(hour: Int): String = when (hour) {
        // i18n-ignore: legacy fallback or non-display heuristic data
        in 6..10 -> "上午"
        // i18n-ignore: legacy fallback or non-display heuristic data
        in 11..13 -> "午间"
        // i18n-ignore: legacy fallback or non-display heuristic data
        in 14..17 -> "下午"
        // i18n-ignore: legacy fallback or non-display heuristic data
        in 18..21 -> "晚间"
        // i18n-ignore: legacy fallback or non-display heuristic data
        else -> "夜间"
    }

    private fun MetricSample.toRecentItem(): RecentIntervalItem = RecentIntervalItem(
        occurredAt = occurredAt,
        intervalMs = intervalMs,
        label = label,
        key = key,
        requestedDurationMinutes = requestedDurationMinutes,
        ratio = ratio,
        tagNames = tagNames,
        eventKind = eventKind,
        endReason = endReason,
    )
}
