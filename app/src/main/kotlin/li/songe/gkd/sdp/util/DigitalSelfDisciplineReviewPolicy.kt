package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.SelfControlAttempt
import li.songe.gkd.sdp.data.SelfControlAttemptEvent
import li.songe.gkd.sdp.data.UsageReviewRow
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Pure aggregation rules for the Digital Self-Discipline review page. */
object DigitalSelfDisciplineReviewPolicy {
    enum class Range(val label: String, val days: Long) {
        Today("今日", 1L),
        SevenDays("近 7 天", 7L),
        ThirtyDays("近 30 天", 30L),
    }

    enum class ReviewType(val label: String) {
        UsageRequest("使用申请"),
        InterceptAttempt("拦截"),
    }

    enum class ReviewMetric {
        USAGE_RATIO,
        USAGE_GAP,
        INTERCEPT_INTERVAL,
    }

    enum class InterceptKindFilter(val label: String, val eventKind: Int? = null) {
        All("全部"),
        AppBlocker("应用拦截", SelfControlAttempt.KIND_APP_BLOCKER),
        Selector("选择器拦截", SelfControlAttempt.KIND_SELECTOR_INTERCEPT),
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
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): RangeBounds {
        val startDate = today.minusDays(range.days - 1L)
        val endDateExclusive = today.plusDays(1L)
        val previousStartDate = startDate.minusDays(range.days)
        val previousEndDateExclusive = startDate
        return RangeBounds(
            range = range,
            zoneId = zoneId,
            startDate = startDate,
            endDateExclusive = endDateExclusive,
            previousStartDate = previousStartDate,
            previousEndDateExclusive = previousEndDateExclusive,
            startAt = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            endAt = endDateExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            previousStartAt = previousStartDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            previousEndAt = previousEndDateExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli(),
        )
    }

    fun rangeBounds(
        range: Range,
        nowEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): RangeBounds = rangeBounds(
        range = range,
        today = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate(),
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
        val dailyBuckets = dailyBuckets(samples, eventCountRows = when (reviewType) {
            ReviewType.UsageRequest -> currentRows.map { it.requestedAt }
            ReviewType.InterceptAttempt -> currentEvents.map { it.occurredAt }
        }, zoneId = zoneId)
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
            delta == null -> "上一周期暂无有效样本"
            delta > 0.0 -> "本期平均比上一周期高 ${formatMetricDelta(delta, reviewType)}"
            delta < 0.0 -> "本期平均比上一周期低 ${formatMetricDelta(-delta, reviewType)}"
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
    ): List<DailyIntervalBucket> {
        val dates = eventCountRows.groupingBy { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
            .eachCount()
        val samplesByDate = samples.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zoneId).toLocalDate() }
        return dates.keys.sorted().map { date ->
            val daySamples = samplesByDate[date].orEmpty()
            val intervals = daySamples.mapNotNull { it.intervalMs }
            val intervalStats = SelfControlIntervalPolicy.statsFor(intervals)
            val ratios = daySamples.mapNotNull { it.ratio }
            val dayRatioStats = ratioStats(ratios)
            DailyIntervalBucket(
                date = date,
                eventCount = dates[date] ?: 0,
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
        0 -> "进行中"
        1 -> "到期"
        2 -> "离开应用"
        3 -> "被替换"
        4 -> "返回桌面"
        5 -> "主动结束"
        else -> "其他结束状态"
    }

    private fun periodLabel(hour: Int): String = when (hour) {
        in 6..10 -> "上午"
        in 11..13 -> "午间"
        in 14..17 -> "下午"
        in 18..21 -> "晚间"
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
