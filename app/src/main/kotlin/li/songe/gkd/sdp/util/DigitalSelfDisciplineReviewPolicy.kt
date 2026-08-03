package li.songe.gkd.sdp.util

import li.songe.gkd.sdp.data.SelfControlAttempt
import li.songe.gkd.sdp.data.SelfControlAttemptEvent
import li.songe.gkd.sdp.data.UsageGuardRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure aggregation rules for the Digital Self-Discipline review page.
 *
 * The input may contain one predecessor record before the selected range. An interval is
 * attributed to the date of its later event, so a range boundary never drops its first sample.
 */
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

    data class DailyIntervalBucket(
        val date: LocalDate,
        val sampleCount: Int,
        val medianMs: Long,
        val averageMs: Long,
    )

    data class RecentIntervalItem(
        val occurredAt: Long,
        val intervalMs: Long,
        val label: String,
        val key: String,
    )

    data class RankedTarget(
        val key: String,
        val label: String,
        val count: Int,
    )

    data class PeriodComparison(
        val currentSampleCount: Int,
        val previousSampleCount: Int,
        val deltaAverageMs: Long?,
        val message: String,
    )

    data class ReviewSummary(
        val reviewType: ReviewType,
        val range: Range,
        val eventCount: Int,
        val requestCount: Int,
        val interceptCount: Int,
        val intervalsMs: List<Long>,
        val stats: SelfControlIntervalPolicy.Stats,
        val dailyBuckets: List<DailyIntervalBucket>,
        val chartDates: List<LocalDate>,
        val recentIntervals: List<RecentIntervalItem>,
        val rankedTargets: List<RankedTarget>,
        val comparison: PeriodComparison,
    )

    private data class Sample(
        val occurredAt: Long,
        val intervalMs: Long,
        val key: String,
        val label: String,
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
            startAt = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            endAt = endDateExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            previousStartDate = previousStartDate,
            previousEndDateExclusive = previousEndDateExclusive,
            previousStartAt = previousStartDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            previousEndAt = previousEndDateExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli(),
        )
    }

    fun hasCrossedDateBoundary(
        previousNow: Long,
        currentNow: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        return Instant.ofEpochMilli(previousNow).atZone(zoneId).toLocalDate() !=
            Instant.ofEpochMilli(currentNow).atZone(zoneId).toLocalDate()
    }

    fun summarize(
        records: List<UsageGuardRecord>,
        events: List<SelfControlAttemptEvent>,
        bounds: RangeBounds,
        reviewType: ReviewType,
        interceptFilter: InterceptKindFilter,
        zoneId: ZoneId = bounds.zoneId,
        previousSummary: ReviewSummary? = null,
    ): ReviewSummary {
        val currentRecords = records.filter(bounds::contains)
        val currentEvents = events.filter { event ->
            bounds.contains(event.occurredAt) &&
                (interceptFilter.eventKind == null || event.eventKind == interceptFilter.eventKind)
        }
        val samples = when (reviewType) {
            ReviewType.UsageRequest -> requestSamples(records, bounds)
            ReviewType.InterceptAttempt -> currentEvents.mapNotNull { event ->
                event.intervalMs?.takeIf { it >= 0L }?.let { interval ->
                    Sample(
                        occurredAt = event.occurredAt,
                        intervalMs = interval,
                        key = event.eventKey,
                        label = event.subjectLabel.ifBlank { event.subjectId },
                    )
                }
            }
        }.sortedBy { it.occurredAt }
        val intervals = samples.map { it.intervalMs }
        val stats = SelfControlIntervalPolicy.statsFor(intervals)
        val dailyBuckets = samples
            .groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zoneId).toLocalDate() }
            .toList()
            .sortedBy { it.first }
            .map { (date, daySamples) ->
                val dayStats = SelfControlIntervalPolicy.statsFor(daySamples.map { it.intervalMs })
                DailyIntervalBucket(
                    date = date,
                    sampleCount = dayStats.sampleCount,
                    medianMs = dayStats.medianMs ?: 0L,
                    averageMs = dayStats.averageMs ?: 0L,
                )
            }
        val recent = samples
            .asReversed()
            .take(10)
            .map { RecentIntervalItem(it.occurredAt, it.intervalMs, it.label, it.key) }
        val rankedTargets = when (reviewType) {
            ReviewType.UsageRequest -> currentRecords
                .groupingBy { it.appId }
                .eachCount()
                .map { (key, count) ->
                    val label = currentRecords.firstOrNull { it.appId == key }?.appName
                        ?.ifBlank { key } ?: key
                    RankedTarget(key, label, count)
                }
            ReviewType.InterceptAttempt -> currentEvents
                .groupingBy { it.eventKey }
                .eachCount()
                .map { (key, count) ->
                    val event = currentEvents.firstOrNull { it.eventKey == key }
                    RankedTarget(key, event?.subjectLabel?.ifBlank { event.subjectId } ?: key, count)
                }
        }.sortedWith(compareByDescending<RankedTarget> { it.count }.thenBy { it.label }).take(5)

        val chartDates = if (bounds.range == Range.Today) {
            recent.asReversed().map {
                Instant.ofEpochMilli(it.occurredAt).atZone(zoneId).toLocalDate()
            }
        } else {
            dailyBuckets.map { it.date }
        }
        val comparison = previousSummary?.let { compare(stats, it.stats) }
            ?: PeriodComparison(stats.sampleCount, 0, null, "暂无上一周期数据")
        return ReviewSummary(
            reviewType = reviewType,
            range = bounds.range,
            eventCount = when (reviewType) {
                ReviewType.UsageRequest -> currentRecords.size
                ReviewType.InterceptAttempt -> currentEvents.size
            },
            requestCount = currentRecords.size,
            interceptCount = currentEvents.size,
            intervalsMs = intervals,
            stats = stats,
            dailyBuckets = dailyBuckets,
            chartDates = chartDates,
            recentIntervals = recent,
            rankedTargets = rankedTargets,
            comparison = comparison,
        )
    }

    fun compare(
        current: SelfControlIntervalPolicy.Stats,
        previous: SelfControlIntervalPolicy.Stats,
    ): PeriodComparison {
        if (current.sampleCount < 3 || previous.sampleCount < 3 ||
            current.averageMs == null || previous.averageMs == null
        ) {
            return PeriodComparison(
                currentSampleCount = current.sampleCount,
                previousSampleCount = previous.sampleCount,
                deltaAverageMs = null,
                message = "样本不足（双方至少各需要 3 个有效间隔），暂不比较。",
            )
        }
        val delta = current.averageMs - previous.averageMs
        val message = when {
            delta > 0L -> "平均间隔比上一周期延长 ${SelfControlIntervalPolicy.formatDurationCompact(delta)}。"
            delta < 0L -> "平均间隔比上一周期缩短 ${SelfControlIntervalPolicy.formatDurationCompact(-delta)}。"
            else -> "平均间隔与上一周期相同。"
        }
        return PeriodComparison(current.sampleCount, previous.sampleCount, delta, message)
    }

    private fun requestSamples(
        records: List<UsageGuardRecord>,
        bounds: RangeBounds,
    ): List<Sample> {
        return records
            .groupBy { it.appId }
            .values
            .flatMap { appRecords ->
                appRecords
                    .sortedWith(compareBy<UsageGuardRecord> { it.requestedAt }.thenBy { it.id })
                    .zipWithNext()
                    .mapNotNull { (previous, current) ->
                        if (!bounds.contains(current.requestedAt)) return@mapNotNull null
                        val interval = current.requestedAt - previous.requestedAt
                        if (interval < 0L) return@mapNotNull null
                        Sample(
                            occurredAt = current.requestedAt,
                            intervalMs = interval,
                            key = current.appId,
                            label = current.appName.ifBlank { current.appId },
                        )
                    }
            }
    }
}
